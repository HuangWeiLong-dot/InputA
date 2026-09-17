/**
 * Detects whether the local CORS bridge (server/index.js) is reachable.
 *
 * Everything cross-origin goes through that backend: Gutendex search, the
 * Gutenberg plain-text download, the free dictionaries and DeepSeek. When it is
 * not running we fall back to direct upstream calls so `npm run dev` alone still
 * works for the CORS-enabled sources.
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

const PROBE_TIMEOUT_MS = 2500;

let healthProbe: Promise<BackendHealth> | null = null;

async function probe(): Promise<BackendHealth> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS);
  try {
    const res = await fetch('/api/health', {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) return { ok: false };
    const data = (await res.json()) as BackendHealth;
    return data?.ok === true ? data : { ok: false };
  } catch {
    return { ok: false };
  } finally {
    clearTimeout(timer);
  }
}

/** Cached backend health probe (one request per page load). */
export function getBackendHealth(): Promise<BackendHealth> {
  if (!healthProbe) {
    healthProbe = probe();
  }
  return healthProbe;
}

export async function hasBackend(): Promise<boolean> {
  return (await getBackendHealth()).ok;
}

/** Mostly for tests and for manual retry after starting the server. */
export function resetBackendProbe(): void {
  healthProbe = null;
}
