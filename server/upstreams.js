/**
 * Upstream requests for the language-reader CORS bridge.
 *
 * The browser cannot reach these hosts on its own:
 *   - Project Gutenberg sends no Access-Control-Allow-Origin header, and its
 *     "ebooks/<id>.txt.utf-8" links 302-redirect to a plain HTTP cache URL,
 *     which a page served over HTTPS refuses as mixed content.
 *   - api.dictionaryapi.dev returns 522 error pages without CORS headers while
 *     its origin is unhealthy (the "blocked by CORS policy" reports).
 *   - api.deepseek.com cannot be called from a browser at all.
 *
 * This server has no CORS rules to obey and may follow redirects (including
 * http:// ones) safely, so every cross-origin call lives here.
 */

import { UpstreamError } from './upstreamError.ts';

/**
 * Timeouts stay below the client-side ones (8s for a word, 20s for a search),
 * so the browser receives a real error response instead of aborting itself.
 */
const DEFAULT_TIMEOUT_MS = 15000;
const DICTIONARY_TIMEOUT_MS = 6000;
const SEARCH_TIMEOUT_MS = 15000;
const BOOK_TEXT_TIMEOUT_MS = 30000;
const AI_TIMEOUT_MS = 60000;
const MAX_BOOK_TEXT_BYTES = 12 * 1024 * 1024;

export const DEFAULT_USER_AGENT = 'LanguageReaderMVP/1.0 (self-hosted reading proxy)';

/** Only these hosts may be downloaded through /api/books/text. */
const BOOK_TEXT_HOSTS = new Set([
  'www.gutenberg.org',
  'gutenberg.org',
  'aleph.gutenberg.org',
  'www.gutenberg.net.au',
]);

export { UpstreamError };

/** Guard against SSRF: the text download route accepts only Gutenberg hosts. */
export function assertBookTextUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new UpstreamError(400, 'invalid url parameter');
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new UpstreamError(400, 'only http(s) urls are allowed');
  }
  if (!BOOK_TEXT_HOSTS.has(url.hostname)) {
    throw new UpstreamError(400, `host not allowed: ${url.hostname}`);
  }
  return url;
}

async function request(url, options = {}) {
  const { method = 'GET', body, headers = {}, timeoutMs = DEFAULT_TIMEOUT_MS } = options;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, {
      method,
      body,
      headers,
      signal: controller.signal,
      redirect: 'follow',
    });
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    throw new UpstreamError(502, `upstream request failed: ${reason}`);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Wikimedia and Project Gutenberg both throttle clients that do not send a
 * proper User-Agent, so the caller's browser UA is forwarded when we have one.
 */
function jsonHeaders(userAgent) {
  return { Accept: 'application/json', 'User-Agent': userAgent || DEFAULT_USER_AGENT };
}

/** GET a JSON endpoint and hand back its status + raw body for pass-through. */
export async function getJson(url, userAgent, timeoutMs = DEFAULT_TIMEOUT_MS) {
  const response = await request(url, { headers: jsonHeaders(userAgent), timeoutMs });
  return { status: response.status, body: await response.text() };
}

function decodeBody(buffer, contentType) {
  if (/iso-8859-1|latin1|windows-1252/i.test(contentType)) {
    return buffer.toString('latin1');
  }
  return buffer.toString('utf8');
}

/**
 * STEP 2 of the book import: download the text behind one of the "text/plain"
 * links Gutendex advertises in its `formats` object.
 */
export async function getBookText(targetUrl, userAgent) {
  const url = assertBookTextUrl(targetUrl);
  const response = await request(url, {
    timeoutMs: BOOK_TEXT_TIMEOUT_MS,
    headers: {
      // Gutenberg throttles generic clients, so keep the caller's browser UA.
      'User-Agent': userAgent || DEFAULT_USER_AGENT,
      Accept: 'text/plain,*/*;q=0.8',
    },
  });

  if (!response.ok) {
    throw new UpstreamError(response.status, `upstream returned HTTP ${response.status}`);
  }

  const buffer = Buffer.from(await response.arrayBuffer());
  if (buffer.byteLength > MAX_BOOK_TEXT_BYTES) {
    throw new UpstreamError(413, 'book text exceeds the size limit');
  }

  const text = decodeBody(buffer, response.headers.get('content-type') || '');
  if (/^\s*<(?:!doctype|html)/i.test(text.slice(0, 200))) {
    throw new UpstreamError(502, 'upstream returned an HTML page instead of plain text');
  }

  return { status: 200, body: text, bytes: buffer.byteLength };
}

/* --------------------------------- Gutendex -------------------------------- */

export function searchGutendex(query, userAgent) {
  const url = `https://gutendex.com/books?search=${encodeURIComponent(query)}&languages=en`;
  return getJson(url, userAgent, SEARCH_TIMEOUT_MS);
}

/* ------------------------------- Dictionaries ------------------------------ */

export function lookupDictionaryApi(word, userAgent) {
  const url = `https://api.dictionaryapi.dev/api/v2/entries/en/${encodeURIComponent(word)}`;
  return getJson(url, userAgent, DICTIONARY_TIMEOUT_MS);
}

export function lookupWiktionary(word, userAgent) {
  const url = `https://en.wiktionary.org/api/rest_v1/page/definition/${encodeURIComponent(word)}`;
  return getJson(url, userAgent, DICTIONARY_TIMEOUT_MS);
}

export function lookupDatamuse(word, userAgent) {
  const url = `https://api.datamuse.com/words?sp=${encodeURIComponent(word)}&md=d&max=1`;
  return getJson(url, userAgent, DICTIONARY_TIMEOUT_MS);
}

/* --------------------------------- DeepSeek -------------------------------- */

/**
 * Proxy for the chat completions endpoint. The key comes from the request's
 * Authorization header, or from the DEEPSEEK_API_KEY environment variable so a
 * self-hosted deployment can keep it on the server.
 */
export async function deepseekChat(payload, clientAuthorization) {
  const apiKey = (clientAuthorization || process.env.DEEPSEEK_API_KEY || '')
    .replace(/^Bearer\s+/i, '')
    .trim();

  if (!apiKey) {
    throw new UpstreamError(
      401,
      'missing DeepSeek API key: send an Authorization header or set DEEPSEEK_API_KEY',
    );
  }

  const response = await request('https://api.deepseek.com/chat/completions', {
    method: 'POST',
    timeoutMs: AI_TIMEOUT_MS,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify(payload ?? {}),
  });

  return { status: response.status, body: await response.text() };
}
