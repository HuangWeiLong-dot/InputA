import { describe, expect, it } from 'vitest';
import { isOriginAllowed, parseAllowedOrigins } from './cors.ts';

/**
 * The allowlist exists for the split deployment — the SPA on GitHub Pages
 * calling this API at another hostname. Two properties of it are easy to get
 * wrong and are pinned here:
 *
 *   - matching is exact, never by prefix, so a lookalike host is refused
 *   - localhost stays allowed with no configuration, which is what keeps
 *     `npm run dev` working when CORS_ALLOWED_ORIGINS is unset
 */
describe('parseAllowedOrigins', () => {
  it('returns nothing for an unset or blank value', () => {
    expect(parseAllowedOrigins(undefined)).toEqual([]);
    expect(parseAllowedOrigins('')).toEqual([]);
    expect(parseAllowedOrigins('  ,  ,')).toEqual([]);
  });

  it('splits on commas and normalises case, padding and trailing slashes', () => {
    expect(parseAllowedOrigins(' HTTPS://Example.COM/, http://a.test ')).toEqual([
      'https://example.com',
      'http://a.test',
    ]);
  });

  it('drops the empty entry a trailing comma leaves behind', () => {
    expect(parseAllowedOrigins('https://a.test,,https://b.test,')).toEqual([
      'https://a.test',
      'https://b.test',
    ]);
  });
});

describe('isOriginAllowed', () => {
  const allowed = parseAllowedOrigins('https://huangweilong-dot.github.io');

  it('refuses a request that carries no Origin at all', () => {
    expect(isOriginAllowed(undefined, allowed)).toBe(false);
    expect(isOriginAllowed('', allowed)).toBe(false);
  });

  it('always allows localhost, however it is spelled', () => {
    expect(isOriginAllowed('http://localhost:5173', [])).toBe(true);
    expect(isOriginAllowed('http://127.0.0.1:8787', [])).toBe(true);
    expect(isOriginAllowed('http://localhost', [])).toBe(true);
  });

  it('allows a configured origin, ignoring case', () => {
    expect(isOriginAllowed('https://huangweilong-dot.github.io', allowed)).toBe(true);
    expect(isOriginAllowed('HTTPS://HuangWeiLong-Dot.GitHub.io', allowed)).toBe(true);
  });

  it('refuses a lookalike host that merely starts with an allowed one', () => {
    // This is the reason matching is exact: a prefix test would accept these.
    expect(isOriginAllowed('https://huangweilong-dot.github.io.evil.test', allowed)).toBe(false);
    expect(isOriginAllowed('https://evil-huangweilong-dot.github.io', allowed)).toBe(false);
  });

  it('treats a different scheme or port as a different origin', () => {
    expect(isOriginAllowed('http://huangweilong-dot.github.io', allowed)).toBe(false);
    expect(isOriginAllowed('https://huangweilong-dot.github.io:8443', allowed)).toBe(false);
  });

  it('refuses an unrelated origin', () => {
    expect(isOriginAllowed('https://evil.example', allowed)).toBe(false);
    // A sandboxed iframe or a file:// page sends the literal string "null".
    expect(isOriginAllowed('null', allowed)).toBe(false);
  });
});
