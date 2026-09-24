import { describe, it, expect } from 'vitest';
import {
  DEFAULT_TTS_VOICE,
  buildSsml,
  generateSecMsGec,
  normalizeProsody,
  normalizeVoice,
  splitBinaryFrame,
  synthesize,
} from './edgeTts.ts';

const TOKEN = '6A5AA1D4EAFF4E9FB37E23D68491D6F4';

/**
 * The exact SHA-256 the anti-abuse token is defined as. Pinned to a fixed
 * timestamp so a change to the ticks arithmetic (which silently makes Edge
 * answer 403) fails here rather than in production.
 */
describe('Sec-MS-GEC token', () => {
  it('hashes the 5-minute-rounded Windows ticks exactly as Edge expects', async () => {
    const value = await generateSecMsGec(TOKEN, 1_758_700_000_000);
    expect(value).toBe('11B00F95F54264412E8CD3B49D54D4782D3442A9DBDCF0A29B70840F12B831ED');
  });

  it('stays the same inside one 5-minute bucket and changes across buckets', async () => {
    // A time that sits exactly on a 300s boundary, so the two cases below are
    // unambiguous. (1_758_700_000_000 would not do: it is 100s into its bucket.)
    const boundary = 1_758_699_900_000;

    const atBoundary = await generateSecMsGec(TOKEN, boundary);
    expect(await generateSecMsGec(TOKEN, boundary + 299_000)).toBe(atBoundary);
    expect(await generateSecMsGec(TOKEN, boundary + 300_000)).not.toBe(atBoundary);
  });

  it('produces an upper-case hex digest', async () => {
    const value = await generateSecMsGec(TOKEN, 1_758_700_000_000);
    expect(value).toMatch(/^[0-9A-F]{64}$/);
  });
});

describe('Binary frame parsing', () => {
  /** Layout: uint16 big-endian header length, header text, payload. */
  function frame(header: string, payload: Buffer): Buffer {
    const headerBytes = Buffer.from(header, 'utf8');
    const length = Buffer.alloc(2);
    length.writeUInt16BE(headerBytes.length, 0);
    return Buffer.concat([length, headerBytes, payload]);
  }

  it('splits the header from the audio payload', () => {
    const payload = Buffer.from([0xff, 0xfb, 0x90, 0x00]);
    const parsed = splitBinaryFrame(
      frame('X-RequestId:abc\r\nContent-Type:audio/mpeg\r\nPath:audio\r\n', payload),
    );

    expect(parsed?.header).toContain('Path:audio');
    expect(parsed?.payload).toEqual(payload);
  });

  /**
   * The reason this parser exists: the original sliced the byte buffer at an
   * index found by searching the *decoded text*, which only lines up while every
   * byte before the marker is single-byte UTF-8.
   */
  it('keeps the payload aligned even when the header is not plain ASCII', () => {
    const payload = Buffer.from([0x00, 0x01, 0xfe, 0xff, 0x80]);
    const header = 'X-RequestId:音声\r\nPath:audio\r\n';
    const parsed = splitBinaryFrame(frame(header, payload));

    expect(parsed?.header).toBe(header);
    expect(parsed?.payload).toEqual(payload);
  });

  it('rejects frames too short to contain their own header', () => {
    expect(splitBinaryFrame(Buffer.from([0x00]))).toBeNull();
    // Claims a 100-byte header but carries 4 bytes.
    expect(splitBinaryFrame(Buffer.from([0x00, 0x64, 0x01, 0x02]))).toBeNull();
  });
});

describe('SSML construction', () => {
  it('escapes the text so book content cannot break the XML', () => {
    const ssml = buildSsml('Tom & Jerry <b>tag</b> "quoted"', {
      voice: 'en-US-AriaNeural',
      rate: 'default',
      pitch: 'default',
      volume: 'default',
    });

    expect(ssml).toContain('Tom &amp; Jerry &lt;b&gt;tag&lt;/b&gt; &quot;quoted&quot;');
    // The only tags in the document are ours.
    expect(ssml.match(/<b>/g)).toBeNull();
  });

  it('declares the language of the voice rather than always en-US', () => {
    const options = { rate: 'default', pitch: 'default', volume: 'default' };

    expect(buildSsml('hallo', { ...options, voice: 'de-DE-AmalaNeural' })).toContain(
      'xml:lang="de-DE"',
    );
    expect(buildSsml('你好', { ...options, voice: 'zh-CN-XiaoxiaoNeural' })).toContain(
      'xml:lang="zh-CN"',
    );
  });

  it('carries the prosody values through', () => {
    const ssml = buildSsml('hello', {
      voice: 'en-US-AriaNeural',
      rate: '+10%',
      pitch: '-5%',
      volume: 'default',
    });

    expect(ssml).toContain('<prosody rate="+10%" pitch="-5%" volume="default">');
  });
});

describe('Input validation', () => {
  it('falls back to the default voice for anything that is not a voice name', () => {
    expect(normalizeVoice('en-US-AriaNeural')).toBe('en-US-AriaNeural');
    expect(normalizeVoice('')).toBe(DEFAULT_TTS_VOICE);
    expect(normalizeVoice(undefined)).toBe(DEFAULT_TTS_VOICE);
    // A value that could close the SSML attribute must not get through.
    expect(normalizeVoice('en-US-A" onload="x')).toBe(DEFAULT_TTS_VOICE);
    expect(normalizeVoice('nonsense')).toBe(DEFAULT_TTS_VOICE);
  });

  it('only accepts a signed percentage for prosody', () => {
    expect(normalizeProsody('+10%')).toBe('+10%');
    expect(normalizeProsody('-100%')).toBe('-100%');
    expect(normalizeProsody('default')).toBe('default');
    expect(normalizeProsody('')).toBe('default');
    expect(normalizeProsody('fast')).toBe('default');
    expect(normalizeProsody('"><script>')).toBe('default');
  });

  /** These throw before any socket is opened, so they are safe to call here. */
  it('refuses empty and over-long text without touching the network', async () => {
    await expect(synthesize('')).rejects.toThrow(/text is required/);
    await expect(synthesize('   ')).rejects.toThrow(/text is required/);

    const tooLong = 'a'.repeat(2001);
    await expect(synthesize(tooLong)).rejects.toThrow(/too long/);
  });
});
