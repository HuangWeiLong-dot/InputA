/**
 * Edge TTS over WebSocket, for the /api/tts route.
 *
 * Ported from LingKuma's src/plugin/edge_tts.js (MIT, see NOTICE.md). It lives on
 * the server for two reasons: the browser cannot set the headers this endpoint
 * wants, and routing it here keeps every outbound call in one place, matching the
 * rest of this backend.
 *
 * Zero new dependencies: Node 22+ has a global WebSocket, crypto.subtle and
 * TextEncoder, which is all the protocol needs.
 *
 * Deliberate differences from the original, each fixing something:
 *   - Binary frames are parsed by their real structure (a 2-byte big-endian
 *     header length, then the header text, then the audio). The original ran
 *     `blob.text()` over the whole frame and sliced the byte buffer at the
 *     resulting *character* index; that only lines up while every byte before
 *     the marker happens to be valid single-byte UTF-8, which is true only
 *     because header lengths are small. Parsing the length field removes the
 *     assumption.
 *   - Because frames are parsed synchronously, the original's pending-blob
 *     counter (and the race it guards) is unnecessary: WebSocket messages are
 *     ordered, so Path:turn.end always arrives after the audio.
 *   - The text is XML-escaped before it goes into the SSML. The original
 *     interpolated it raw, so a `&` or `<` in the sentence produced invalid SSML.
 *   - `xml:lang` is derived from the voice instead of being hardcoded to en-US
 *     (the original left `options.language` commented out).
 *   - Word-boundary metadata is not collected: nothing consumes it here.
 */

import { UpstreamError } from './upstreamError.ts';

const TRUSTED_CLIENT_TOKEN = '6A5AA1D4EAFF4E9FB37E23D68491D6F4';
const SYNTH_URL = 'wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1';
const VOICES_URL =
  'https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list';

/**
 * Microsoft rotates this alongside Edge releases. When it goes stale the service
 * answers 403 and every synthesis fails — this is the usual cause of Edge TTS
 * "suddenly breaking", so this constant is the first thing to bump.
 */
const SEC_MS_GEC_VERSION = '1-130.0.2849.68';

const AUDIO_FORMAT = 'audio-24khz-96kbitrate-mono-mp3';
const SYNTH_TIMEOUT_MS = 20_000;
const VOICES_TIMEOUT_MS = 10_000;

/**
 * The handshake is refused with 403 (surfacing as close code 1006) unless it
 * presents a browser User-Agent. Measured: the UA alone is sufficient — an
 * Origin header is neither required nor sent, so this does not impersonate any
 * particular extension, but it does mean presenting as a browser to an endpoint
 * that only documents this use for Edge's own read-aloud feature.
 */
const HANDSHAKE_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0';

/** One request is a word or a sentence, not a chapter. */
const MAX_TEXT_LENGTH = 2000;

const DEFAULT_VOICE = 'en-US-AriaNeural';

export const DEFAULT_TTS_VOICE = DEFAULT_VOICE;

/**
 * The `Sec-MS-GEC` anti-abuse token: SHA-256 of the Windows ticks (rounded down
 * to a 5-minute bucket) followed by the client token, hex, upper case.
 *
 * `now` is injectable so the value is testable.
 */
export async function generateSecMsGec(
  trustedClientToken: string,
  now: number = Date.now(),
): Promise<string> {
  const ticks = Math.floor(now / 1000) + 11644473600;
  const rounded = ticks - (ticks % 300);
  const windowsTicks = rounded * 10000000;

  const data = new TextEncoder().encode(windowsTicks + trustedClientToken);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return Buffer.from(digest).toString('hex').toUpperCase();
}

/** Guard the SSML attributes: these strings must not be able to close a quote. */
const VOICE_PATTERN = /^[A-Za-z0-9]+(?:-[A-Za-z0-9]+){2,}$/;

export function normalizeVoice(voice: unknown): string {
  const value = String(voice ?? '').trim();
  return VOICE_PATTERN.test(value) ? value : DEFAULT_VOICE;
}

/** Accepts "default" or a signed percentage; anything else falls back. */
export function normalizeProsody(value: unknown, fallback = 'default'): string {
  const trimmed = String(value ?? '').trim();
  if (trimmed === 'default') return 'default';
  return /^[+-]?\d{1,4}%$/.test(trimmed) ? trimmed : fallback;
}

function escapeXml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** `en-US-AriaNeural` → `en-US`. */
function languageFromVoice(voice: string): string {
  const parts = voice.split('-');
  return parts.length >= 2 ? `${parts[0]}-${parts[1]}` : 'en-US';
}

export interface SsmlOptions {
  voice: string;
  rate: string;
  pitch: string;
  volume: string;
}

export function buildSsml(text: string, { voice, rate, pitch, volume }: SsmlOptions): string {
  const requestId = crypto.randomUUID();
  return (
    `X-RequestId:${requestId}\r\n` +
    `X-Timestamp:${new Date().toString()}\r\n` +
    'Content-Type:application/ssml+xml\r\n' +
    'Path:ssml\r\n\r\n' +
    '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" ' +
    'xmlns:mstts="https://www.w3.org/2001/mstts" ' +
    `xml:lang="${languageFromVoice(voice)}">` +
    `<voice name="${voice}">` +
    `<prosody rate="${rate}" pitch="${pitch}" volume="${volume}">` +
    escapeXml(text) +
    '</prosody></voice></speak>'
  );
}

function buildConfigMessage(): string {
  return (
    `X-Timestamp:${new Date().toString()}\r\n` +
    'Content-Type:application/json; charset=utf-8\r\n' +
    'Path:speech.config\r\n\r\n' +
    JSON.stringify({
      context: {
        synthesis: {
          audio: {
            metadataoptions: { sentenceBoundaryEnabled: 'false', wordBoundaryEnabled: 'false' },
            outputFormat: AUDIO_FORMAT,
          },
        },
      },
    })
  );
}

async function buildSynthUrl(): Promise<string> {
  const secMsGec = await generateSecMsGec(TRUSTED_CLIENT_TOKEN);
  const params = new URLSearchParams({
    TrustedClientToken: TRUSTED_CLIENT_TOKEN,
    'Sec-MS-GEC': secMsGec,
    'Sec-MS-GEC-Version': SEC_MS_GEC_VERSION,
    ConnectionId: crypto.randomUUID(),
  });
  return `${SYNTH_URL}?${params.toString()}`;
}

/**
 * Split one binary message into its header text and payload.
 *
 * Layout: uint16 big-endian header length, that many bytes of header text, then
 * the payload. A frame too short to hold its own header is malformed.
 */
export interface BinaryFrame {
  header: string;
  payload: Buffer;
}

export function splitBinaryFrame(buffer: Buffer): BinaryFrame | null {
  if (buffer.length < 2) return null;

  const headerLength = buffer.readUInt16BE(0);
  if (buffer.length < 2 + headerLength) return null;

  return {
    header: buffer.subarray(2, 2 + headerLength).toString('utf8'),
    payload: buffer.subarray(2 + headerLength),
  };
}

/**
 * Synthesise `text` and resolve with the complete mp3.
 *
 * The whole clip is buffered rather than streamed to a MediaSource: the callers
 * here play a word or a sentence, and buffering removes a lot of machinery for
 * no perceptible delay.
 */
export interface SynthesizeOptions {
  voice?: string;
  rate?: string;
  pitch?: string;
  volume?: string;
}

export async function synthesize(
  text: unknown,
  options: SynthesizeOptions = {},
): Promise<Buffer> {
  const trimmed = String(text ?? '').trim();
  if (!trimmed) throw new UpstreamError(400, 'text is required');
  if (trimmed.length > MAX_TEXT_LENGTH) {
    throw new UpstreamError(413, `text is too long (max ${MAX_TEXT_LENGTH} characters)`);
  }

  const voice = normalizeVoice(options.voice);
  const ssml = buildSsml(trimmed, {
    voice,
    rate: normalizeProsody(options.rate),
    pitch: normalizeProsody(options.pitch),
    volume: normalizeProsody(options.volume),
  });

  const url = await buildSynthUrl();

  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let settled = false;

    // The second argument is undici's (undocumented) init bag, not `protocols` —
    // the spec-compliant WebSocket API has no way to set request headers, and
    // Node's global WebSocket is undici's. Node 22.18+ honours the headers key;
    // if a future Node drops it the handshake fails with a 403/1006, which the
    // close handler below reports.
    const ws = new WebSocket(url, { headers: { 'User-Agent': HANDSHAKE_USER_AGENT } });
    // Receive ArrayBuffers rather than Blobs: Buffer.from is then direct.
    ws.binaryType = 'arraybuffer';

    const timer = setTimeout(() => {
      finish(new UpstreamError(504, 'Edge TTS timed out'));
    }, SYNTH_TIMEOUT_MS);

    function finish(error: Error | null): void {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        ws.close();
      } catch {
        // Already closing; nothing to do.
      }
      if (error) reject(error);
      else resolve(Buffer.concat(chunks));
    }

    ws.addEventListener('open', () => {
      ws.send(buildConfigMessage());
      ws.send(ssml);
    });

    ws.addEventListener('message', (event) => {
      const data = event.data;

      if (typeof data === 'string') {
        // turn.end is the signal that the utterance is complete.
        if (data.includes('Path:turn.end')) finish(null);
        return;
      }

      const frame = splitBinaryFrame(Buffer.from(data));
      if (!frame || !frame.header.includes('Path:audio')) return;
      if (frame.payload.length > 0) chunks.push(frame.payload);
    });

    ws.addEventListener('error', () => {
      finish(new UpstreamError(502, 'Edge TTS websocket failed'));
    });

    ws.addEventListener('close', (event) => {
      // A close before turn.end means truncated or refused audio. A 403 here is
      // usually the stale Sec-MS-GEC-Version above.
      if (!settled) {
        const reason = event?.code ? ` (code ${event.code})` : '';
        finish(new UpstreamError(502, `Edge TTS closed before finishing${reason}`));
      }
    });
  });
}

/**
 * The voice catalogue. An unauthenticated GET, but it sends no CORS headers, so
 * the browser needs it proxied just like synthesis.
 */
export async function listVoices(): Promise<unknown> {
  const url = `${VOICES_URL}?trustedclienttoken=${TRUSTED_CLIENT_TOKEN}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), VOICES_TIMEOUT_MS);

  try {
    const response = await fetch(url, { signal: controller.signal });
    if (!response.ok) {
      throw new UpstreamError(response.status, `voice list returned HTTP ${response.status}`);
    }
    return await response.json();
  } catch (error) {
    if (error instanceof UpstreamError) throw error;
    const reason = error instanceof Error ? error.message : String(error);
    throw new UpstreamError(502, `voice list request failed: ${reason}`);
  } finally {
    clearTimeout(timer);
  }
}
