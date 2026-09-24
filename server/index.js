/**
 * Language reader backend: CORS bridge + static host for the built SPA.
 *
 * Run it next to Vite during development (`npm run server`, Vite proxies /api),
 * or standalone in production (`npm start`), where it also serves ./dist so the
 * whole app is one origin and no request is cross-origin any more.
 */
import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  UpstreamError,
  deepseekChat,
  getBookText,
  lookupDatamuse,
  lookupDictionaryApi,
  lookupWiktionary,
  searchGutendex,
} from './upstreams.js';
import { listVoices, synthesize } from './edgeTts.ts';
// TypeScript module, loaded through Node's built-in type stripping (Node
// 22.18+/23.6+/24). It keeps the read-only SQLite dictionary behind /api/dict.
import { isDictionaryAvailable, lookupWord } from './dictLookup.ts';

const PORT = Number(process.env.API_PORT || process.env.PORT || 8787);
const PROJECT_ROOT = fileURLToPath(new URL('..', import.meta.url));
const DIST_DIR = path.join(PROJECT_ROOT, 'dist');
const MAX_BODY_BYTES = 1024 * 1024;

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
};

const API_ROUTES = [
  'GET  /api/health',
  'GET  /api/books/search?query=',
  'GET  /api/books/text?url=',
  'GET  /api/dict?word=',
  'GET  /api/dictionary/word/:word',
  'GET  /api/dictionary/wiktionary/:word',
  'GET  /api/dictionary/datamuse/:word',
  'GET  /api/tts?text=&voice=&rate=&pitch=',
  'GET  /api/tts/voices',
  'POST /api/ai/chat',
];

/**
 * Synthesis is a WebSocket round trip and the service rate limits, while the same
 * word gets replayed constantly during reading. A small LRU pays for itself
 * immediately. Insertion order doubles as recency, so a hit re-inserts.
 */
const TTS_CACHE_LIMIT = 200;
const ttsCache = new Map();
/** The catalogue changes rarely; fetch it once per process. */
let voicesCache = null;

function ttsCacheGet(key) {
  if (!ttsCache.has(key)) return null;
  const value = ttsCache.get(key);
  ttsCache.delete(key);
  ttsCache.set(key, value);
  return value;
}

function ttsCacheSet(key, value) {
  if (ttsCache.size >= TTS_CACHE_LIMIT) {
    const oldest = ttsCache.keys().next().value;
    ttsCache.delete(oldest);
  }
  ttsCache.set(key, value);
}

const FALLBACK_HTML = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>Language Reader API</title>
<style>body{font-family:system-ui,sans-serif;max-width:38rem;margin:4rem auto;padding:0 1.5rem;line-height:1.7;color:#18181b}
code{background:#f4f4f5;padding:.15rem .35rem;border:1px solid #d4d4d8}h1{font-size:1.25rem}</style></head>
<body><h1>语言学习阅读器 · 后端已运行</h1>
<p><code>./dist</code> 不存在，因此没有可托管的静态页面。请任选一种方式启动前端：</p>
<ul>
<li>开发模式：<code>npm run dev</code>（Vite 会把 <code>/api</code> 转发到本服务）</li>
<li>生产模式：先 <code>npm run build</code>，再刷新本页即可单源访问</li>
</ul>
<p>可用接口：</p><pre>${API_ROUTES.join('\n')}</pre>
</body></html>`;

/** Development convenience: the SPA may run on localhost with another port. */
function setCorsHeaders(req, res) {
  const origin = req.headers.origin;
  if (origin && /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  }
}

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
  });
  res.end(body);
}

function sendText(res, status, text, contentType = 'text/plain; charset=utf-8') {
  res.writeHead(status, {
    'Content-Type': contentType,
    'Content-Length': Buffer.byteLength(text),
    'Cache-Control': 'no-store',
  });
  res.end(text);
}

/** Forward an upstream status + body untouched so client logic stays in charge. */
function passThrough(res, upstream) {
  res.writeHead(upstream.status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(upstream.body),
    'Cache-Control': 'no-store',
  });
  res.end(upstream.body);
}

/**
 * Audio is the one response worth letting the browser cache: it is immutable for
 * a given (text, voice, rate, pitch) and replayed while reading. `hit` in the
 * header makes the server-side cache observable when testing by hand.
 */
function sendAudio(res, buffer, cacheState) {
  res.writeHead(200, {
    'Content-Type': 'audio/mpeg',
    'Content-Length': String(buffer.length),
    'Cache-Control': 'private, max-age=86400',
    'X-TTS-Cache': cacheState,
  });
  res.end(buffer);
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new UpstreamError(413, 'request body is too large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8').trim();
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new UpstreamError(400, 'invalid JSON body'));
      }
    });
    req.on('error', reject);
  });
}

/* ---------------------------------- API ----------------------------------- */

const DICTIONARY_HANDLERS = {
  word: lookupDictionaryApi,
  wiktionary: lookupWiktionary,
  datamuse: lookupDatamuse,
};

async function handleApi(req, res, url) {
  const { pathname } = url;

  if (pathname === '/api/health') {
    sendJson(res, 200, {
      ok: true,
      service: 'language-reader-api',
      uptimeSeconds: Math.round(process.uptime()),
      // Lets the UI know it may call /api/ai/chat without a browser-side key.
      deepseekKeyConfigured: Boolean(process.env.DEEPSEEK_API_KEY),
      // Lets the UI know whether GET /api/dict can answer (data/stardict.db).
      dictionaryAvailable: isDictionaryAvailable(),
    });
    return;
  }

  if (pathname === '/api/books/search') {
    const query = (url.searchParams.get('query') || '').trim();
    if (!query) {
      sendJson(res, 400, { error: 'query parameter is required' });
      return;
    }
    passThrough(res, await searchGutendex(query, req.headers['user-agent']));
    return;
  }

  // STEP 2: download the plain-text body behind a Gutendex "text/plain" link.
  if (pathname === '/api/books/text') {
    const target = (url.searchParams.get('url') || '').trim();
    if (!target) {
      sendJson(res, 400, { error: 'url parameter is required' });
      return;
    }
    const result = await getBookText(target, req.headers['user-agent']);
    res.setHeader('X-Book-Bytes', String(result.bytes));
    sendText(res, 200, result.body);
    return;
  }

  // STEP 0 for a word: the offline ECDICT file behind /api/dict. Everything
  // below is the online fallback chain.
  if (pathname === '/api/dict') {
    if (req.method !== 'GET') {
      sendJson(res, 405, { error: 'use GET' });
      return;
    }
    const word = (url.searchParams.get('word') || '').trim();
    if (!word) {
      sendJson(res, 400, { error: 'word parameter is required' });
      return;
    }
    // Missing file => the feature is off, but the rest of the API still works.
    if (!isDictionaryAvailable()) {
      sendJson(res, 503, { error: 'dictionary database is unavailable (expected data/stardict.db)' });
      return;
    }
    // null means "not in the dictionary" - answered as 404, never as an error.
    const entry = lookupWord(word);
    if (!entry) {
      sendJson(res, 404, { error: `no dictionary entry for "${word}"`, word });
      return;
    }
    sendJson(res, 200, entry);
    return;
  }

  const dictionaryRoute = /^\/api\/dictionary\/(word|wiktionary|datamuse)\/(.+)$/.exec(pathname);
  if (dictionaryRoute) {
    const kind = dictionaryRoute[1];
    const word = decodeURIComponent(dictionaryRoute[2]).trim();
    if (!word) {
      sendJson(res, 400, { error: 'word is required' });
      return;
    }
    passThrough(res, await DICTIONARY_HANDLERS[kind](word, req.headers['user-agent']));
    return;
  }

  if (pathname === '/api/tts') {
    if (req.method !== 'GET') {
      sendJson(res, 405, { error: 'use GET' });
      return;
    }
    const text = (url.searchParams.get('text') || '').trim();
    if (!text) {
      sendJson(res, 400, { error: 'text parameter is required' });
      return;
    }

    const voice = (url.searchParams.get('voice') || '').trim();
    const rate = (url.searchParams.get('rate') || '').trim();
    const pitch = (url.searchParams.get('pitch') || '').trim();

    // NUL cannot appear in a URL query value, so it is a safe field separator.
    const cacheKey = [text, voice, rate, pitch].join('\u0000');
    const cached = ttsCacheGet(cacheKey);
    if (cached) {
      sendAudio(res, cached, 'hit');
      return;
    }

    const audio = await synthesize(text, { voice, rate, pitch });
    ttsCacheSet(cacheKey, audio);
    sendAudio(res, audio, 'miss');
    return;
  }

  if (pathname === '/api/tts/voices') {
    if (req.method !== 'GET') {
      sendJson(res, 405, { error: 'use GET' });
      return;
    }
    // Only a success is worth remembering; a failure should be retried.
    if (!voicesCache) voicesCache = await listVoices();
    sendJson(res, 200, voicesCache);
    return;
  }

  if (pathname === '/api/ai/chat') {
    if (req.method !== 'POST') {
      sendJson(res, 405, { error: 'use POST' });
      return;
    }
    const payload = await readJsonBody(req);
    passThrough(res, await deepseekChat(payload, req.headers.authorization));
    return;
  }

  sendJson(res, 404, { error: `unknown api route: ${req.method} ${pathname}` });
}

/* --------------------------------- Static --------------------------------- */

async function fileExists(target) {
  try {
    const info = await stat(target);
    return info.isFile();
  } catch {
    return false;
  }
}

/** Serve ./dist with an SPA fallback; explain the alternatives when missing. */
async function serveStatic(req, res, url) {
  const indexFile = path.join(DIST_DIR, 'index.html');
  if (!(await fileExists(indexFile))) {
    sendText(res, 200, FALLBACK_HTML, 'text/html; charset=utf-8');
    return;
  }

  const requested = decodeURIComponent(url.pathname);
  const resolved = path.resolve(DIST_DIR, `.${requested}`);
  const insideDist = resolved === DIST_DIR || resolved.startsWith(DIST_DIR + path.sep);

  if (insideDist && requested !== '/' && (await fileExists(resolved))) {
    const type = MIME_TYPES[path.extname(resolved).toLowerCase()] || 'application/octet-stream';
    const info = await stat(resolved);
    res.writeHead(200, {
      'Content-Type': type,
      'Content-Length': String(info.size),
      'Cache-Control': requested.startsWith('/assets/')
        ? 'public, max-age=31536000, immutable'
        : 'no-cache',
    });
    if (req.method === 'HEAD') {
      res.end();
      return;
    }
    createReadStream(resolved).pipe(res);
    return;
  }

  sendText(res, 200, await readFile(indexFile, 'utf8'), 'text/html; charset=utf-8');
}

/* -------------------------------- Bootstrap ------------------------------- */

const server = createServer(async (req, res) => {
  const startedAt = Date.now();

  try {
    setCorsHeaders(req, res);

    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

    if (url.pathname.startsWith('/api/')) {
      await handleApi(req, res, url);
      console.log(
        `[api] ${req.method} ${url.pathname}${url.search} -> ${res.statusCode} (${Date.now() - startedAt}ms)`,
      );
      return;
    }

    if (req.method === 'GET' || req.method === 'HEAD') {
      await serveStatic(req, res, url);
      return;
    }

    sendJson(res, 405, { error: 'method not allowed' });
  } catch (error) {
    const status = error instanceof UpstreamError ? error.status : 500;
    const message = error instanceof Error ? error.message : 'unexpected server error';
    if (res.headersSent) {
      res.end();
    } else {
      sendJson(res, status, { error: message });
    }
    console.error(`[api] ${req.method} ${req.url} -> ${status}: ${message}`);
  }
});

const distAvailable = await fileExists(path.join(DIST_DIR, 'index.html'));

server.listen(PORT, () => {
  console.log('');
  console.log(`  language-reader backend   http://localhost:${PORT}`);
  console.log('  routes:');
  for (const route of API_ROUTES) {
    console.log(`    ${route}`);
  }
  console.log('');
  console.log(
    distAvailable
      ? '  ./dist found - serving the built SPA on the same origin (no CORS at all)'
      : '  ./dist not found - run "npm run build", or "npm run dev" (Vite proxies /api here)',
  );
  console.log('');
});

