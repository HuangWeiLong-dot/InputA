import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { normalizeApiBase } from '../services/apiBase';

/**
 * The backend origin is build-time configuration, and `''` means same-origin —
 * the single-origin build that `npm start` and `npm run dev` both run as. These
 * tests pin the two halves that matter:
 *
 *   - with the variable unset, apiUrl()/isBackendUrl() are byte-for-byte what
 *     every call site did literally before the split. That identity is the
 *     no-regression promise for the self-hosted build.
 *   - with it set, backend URLs become absolute and the predicate still tells
 *     our backend apart from the third-party upstreams.
 *
 * The second half needs a fresh module graph: API_BASE is a module constant read
 * at import time.
 */

/** Loads apiBase.ts with VITE_API_BASE set to `base`; `undefined` means unset. */
async function loadApiBase(base?: string) {
  // Explicit '' rather than "leave it alone", so a machine-local .env.local
  // cannot change what these tests assert.
  vi.stubEnv('VITE_API_BASE', base ?? '');
  vi.resetModules();
  return import('../services/apiBase');
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('normalizeApiBase', () => {
  it('treats unset and blank values as same-origin', () => {
    expect(normalizeApiBase(undefined)).toBe('');
    expect(normalizeApiBase('')).toBe('');
    expect(normalizeApiBase('   ')).toBe('');
  });

  it('strips trailing slashes so apiUrl cannot emit a doubled slash', () => {
    expect(normalizeApiBase('https://a.test')).toBe('https://a.test');
    expect(normalizeApiBase('https://a.test/')).toBe('https://a.test');
    expect(normalizeApiBase('https://a.test///')).toBe('https://a.test');
    expect(normalizeApiBase('https://a.test/reader/')).toBe('https://a.test/reader');
  });

  it('refuses a value with no http(s) scheme instead of guessing one', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    expect(normalizeApiBase('inputa-api.duckdns.org')).toBe('');
    expect(warn).toHaveBeenCalledOnce();
    warn.mockRestore();
  });
});

describe('same-origin build (VITE_API_BASE unset)', () => {
  it('leaves backend paths exactly as the call sites wrote them', async () => {
    const { API_BASE, apiUrl, isBackendUrl } = await loadApiBase();
    expect(API_BASE).toBe('');
    expect(apiUrl('/api/health')).toBe('/api/health');
    // Nothing is re-encoded — the callers do their own encodeURIComponent, and
    // encoding here would double-escape "air bed" and "ain't".
    expect(apiUrl('/api/dict?word=air%20bed')).toBe('/api/dict?word=air%20bed');
    expect(isBackendUrl('/api/dict?word=very')).toBe(true);
    expect(isBackendUrl('https://api.datamuse.com/words?sp=very')).toBe(false);
    expect(isBackendUrl('https://api.allorigins.win/raw?url=x')).toBe(false);
  });
});

describe('split build (VITE_API_BASE set)', () => {
  it('prefixes backend paths with the configured origin', async () => {
    const { API_BASE, apiUrl } = await loadApiBase('https://inputa-api.duckdns.org/');
    expect(API_BASE).toBe('https://inputa-api.duckdns.org');
    expect(apiUrl('/api/health')).toBe('https://inputa-api.duckdns.org/api/health');
    expect(apiUrl('/api/dict?word=air%20bed')).toBe(
      'https://inputa-api.duckdns.org/api/dict?word=air%20bed',
    );
  });

  it('keeps third-party upstreams out of isBackendUrl', async () => {
    const { isBackendUrl } = await loadApiBase('https://inputa-api.duckdns.org');
    expect(isBackendUrl('https://inputa-api.duckdns.org/api/dict?word=very')).toBe(true);
    // Segment-bounded, so a lookalike host is not mistaken for ours.
    expect(isBackendUrl('https://inputa-api.duckdns.org.evil.test/api/dict')).toBe(false);
    // And a same-origin-looking path is no longer ours once a base is set.
    expect(isBackendUrl('/api/dict?word=very')).toBe(false);
    expect(isBackendUrl('https://api.dictionaryapi.dev/api/v2/entries/en/very')).toBe(false);
  });
});

describe('health probe caching', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'));
  });

  /** Stubs fetch to answer /api/health with `ok`; returns a call counter. */
  function stubHealthFetch(ok: boolean) {
    let calls = 0;
    vi.stubGlobal('fetch', async (url: string) => {
      if (!String(url).endsWith('/api/health')) throw new Error(`unexpected fetch: ${url}`);
      calls += 1;
      return {
        ok,
        status: ok ? 200 : 503,
        json: async () => ({ ok }),
      } as unknown as Response;
    });
    return () => calls;
  }

  it('probes once per page load while the answer is a success', async () => {
    const calls = stubHealthFetch(true);
    const { getBackendHealth } = await loadApiBase();

    expect((await getBackendHealth()).ok).toBe(true);
    await getBackendHealth();
    vi.setSystemTime(new Date('2026-01-01T01:00:00Z'));
    await getBackendHealth();
    expect(calls()).toBe(1);
  });

  it('re-probes a failure only after the cooldown', async () => {
    const calls = stubHealthFetch(false);
    const { getBackendHealth } = await loadApiBase();

    expect((await getBackendHealth()).ok).toBe(false);
    expect(calls()).toBe(1);

    // Inside the cooldown the cached failure is reused rather than re-requested,
    // which is what keeps an absent backend from adding a probe to every lookup.
    vi.setSystemTime(new Date('2026-01-01T00:00:29Z'));
    await getBackendHealth();
    expect(calls()).toBe(1);

    // Past it the next caller gets a fresh answer — the fix for a transient
    // failure that used to disable every backend feature until a full reload.
    vi.setSystemTime(new Date('2026-01-01T00:00:31Z'));
    await getBackendHealth();
    expect(calls()).toBe(2);
  });

  it('resetBackendProbe clears both the memo and the cooldown', async () => {
    const calls = stubHealthFetch(false);
    const { getBackendHealth, resetBackendProbe } = await loadApiBase();

    await getBackendHealth();
    expect(calls()).toBe(1);

    resetBackendProbe();
    await getBackendHealth();
    expect(calls()).toBe(2);
  });
});
