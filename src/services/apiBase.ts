/**
 * Where the backend is, and whether it is reachable.
 *
 * Everything the browser cannot fetch itself goes through server/: Gutendex
 * search, the Gutenberg plain-text download, the free dictionaries and
 * DeepSeek. When it is not reachable we fall back to direct upstream calls so
 * `npm run dev` alone still works for the CORS-enabled sources.
 *
 * The base is *build-time* configuration rather than a reader setting, because
 * the TTS endpoint is a module-level constant resolved at import time — making
 * it dynamic would mean turning every module-level URL in the app into a lazy
 * function. Both `npm start` (single-origin: this same server hosts ./dist) and
 * `npm run dev` leave the variable unset, so `''` reproduces the pre-split
 * behaviour exactly; the GitHub Pages build sets VITE_API_BASE to the deployed
 * backend origin instead.
 */

export interface BackendHealth {
  ok: boolean;
  service?: string;
  /** true when the server itself holds a DEEPSEEK_API_KEY (self-hosted key mode). */
  deepseekKeyConfigured?: boolean;
  /**
   * true when GET /api/dict can answer (data/stardict.db is readable on the
   * server). Older servers omit the field; treat undefined as "try anyway".
   */
  dictionaryAvailable?: boolean;
}

/**
 * Normalises the configured backend origin. `''` means "same origin" and is the
 * default, so an unset variable reproduces the single-origin deployment exactly.
 *
 * A value without an http(s) scheme is refused rather than repaired: guessing
 * https for `inputa-api.duckdns.org` happens to be right and guessing for a bare
 * LAN host happens to be wrong, and a wrong guess surfaces in the browser as a
 * mixed-content block that is indistinguishable from a CORS failure — so the
 * mistake would be debugged in the wrong place. Trailing slashes are stripped so
 * apiUrl() cannot emit `https://host//api/...`.
 */
export function normalizeApiBase(raw: string | undefined): string {
  const trimmed = (raw ?? '').trim().replace(/\/+$/, '');
  if (!trimmed) return '';
  if (!/^https?:\/\//i.test(trimmed)) {
    console.warn(
      `[backend] VITE_API_BASE="${trimmed}" is not an http(s) origin — ignoring it and using same-origin requests.`,
    );
    return '';
  }
  return trimmed;
}

export const API_BASE = normalizeApiBase(import.meta.env.VITE_API_BASE);

/**
 * Absolute URL for a backend route.
 *
 * With the base empty this returns `path` unchanged, which is exactly what every
 * call site passed literally before the split; that identity is what keeps the
 * single-origin build untouched. Pure concatenation on purpose — callers encode
 * their own query values with encodeURIComponent, so encoding here would
 * double-escape `ain't` and `air bed`.
 */
export function apiUrl(path: string): string {
  return `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`;
}

/**
 * True when a request URL points at our own backend rather than a third-party
 * upstream. Only used for diagnostics, but two call sites used to branch on the
 * literal '/api/' prefix and would have silently mislabelled every backend
 * request as a direct one once apiUrl() began returning absolute URLs.
 *
 * The prefix is segment-bounded, so `https://host.evil.test/api/x` is not ours.
 */
export function isBackendUrl(url: string): boolean {
  if (!API_BASE) return url.startsWith('/api/');
  return url.startsWith(`${API_BASE}/api/`);
}

/**
 * 2.5s is generous for a backend on localhost (it answers in milliseconds or is
 * not running at all). A remote one pays DNS + TLS + a possibly cold reverse
 * proxy on the first request of a page load, and aborting there would report a
 * healthy backend as offline — the failure the cooldown below can only soften.
 */
const PROBE_TIMEOUT_MS = API_BASE ? 5000 : 2500;

/**
 * How long a *failed* probe is remembered.
 *
 * A success is a fact about the server and is kept for the page load, which is
 * the documented one-request-per-page-load behaviour. A failure is only a fact
 * about that moment: while the probe was a localhost request that hardly
 * mattered, but once the backend lives at another hostname a single dropped
 * packet would disable the local dictionary, the book download, Edge TTS and the
 * AI path for the entire page load, with resetBackendProbe() as the only — and
 * unused — escape hatch. A cooldown, rather than "never cache a failure", is
 * what stops a genuinely absent backend from adding seconds to every lookup.
 */
const FAILED_PROBE_COOLDOWN_MS = 30_000;

async function probe(): Promise<BackendHealth> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS);
  const url = apiUrl('/api/health');
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) {
      // A cross-origin failure is opaque — "Failed to fetch" covers DNS, TLS and
      // a missing Access-Control-Allow-Origin alike — so name what was tried.
      // Silent unless a remote base is configured: in the single-origin build
      // this is just `npm run server` not being started yet, which is normal.
      if (API_BASE) console.warn(`[backend] health probe got HTTP ${res.status} from ${url}`);
      return { ok: false };
    }
    const data = (await res.json()) as BackendHealth;
    return data?.ok === true ? data : { ok: false };
  } catch (error) {
    if (API_BASE) console.warn(`[backend] health probe failed: ${url}`, error);
    return { ok: false };
  } finally {
    clearTimeout(timer);
  }
}

let healthProbe: Promise<BackendHealth> | null = null;

/** When a cached *failure* may be re-probed; 0 while the cached answer is a success. */
let retryAfter = 0;

/** Cached backend health probe (one request per page load, while it succeeds). */
export function getBackendHealth(): Promise<BackendHealth> {
  if (healthProbe && retryAfter > 0 && Date.now() >= retryAfter) {
    healthProbe = null;
    retryAfter = 0;
  }
  if (!healthProbe) {
    healthProbe = probe().then((health) => {
      if (!health.ok) retryAfter = Date.now() + FAILED_PROBE_COOLDOWN_MS;
      return health;
    });
  }
  return healthProbe;
}

export async function hasBackend(): Promise<boolean> {
  return (await getBackendHealth()).ok;
}

/** Manual retry after starting the server; also the cooldown's escape hatch. */
export function resetBackendProbe(): void {
  healthProbe = null;
  retryAfter = 0;
}
