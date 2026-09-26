/**
 * Browser CORS policy — for the split deployment only.
 *
 * None of this matters while `npm start` serves ./dist and /api from the same
 * origin: a same-origin GET carries no Origin header at all, and nothing is ever
 * preflighted. The allowlist exists for the other deployment — the SPA on GitHub
 * Pages calling this API at another hostname — and is env-driven so deployment
 * details stay out of the source tree.
 *
 * It lives in a .ts module rather than inline in index.js for the same reason
 * dictLookup/edgeTts do: tsconfig.node.json typechecks the .ts files under
 * server/, and a .ts test cannot import from a .js module (allowJs is off) — so
 * this is the only shape in which the logic is testable at all.
 *
 * Origins are compared exactly (scheme + host + port, case-insensitively) and
 * never by prefix: Access-Control-Allow-Origin names one origin, and a prefix
 * match would accept `https://evil-github.io` for `https://github.io`.
 *
 * One consequence worth knowing: a GitHub Pages origin is the *account*, not the
 * repository. Allowing https://huangweilong-dot.github.io also allows every other
 * Pages site on that account. That is unavoidable (the origin carries no path)
 * and acceptable, since those pages are controlled by the same account. Moving to
 * a custom domain means editing the environment variable, not this file.
 */

/** Parses `CORS_ALLOWED_ORIGINS`: comma-separated, tolerating case, spaces, slashes and gaps. */
export function parseAllowedOrigins(raw: string | undefined): string[] {
  return (raw ?? '')
    .split(',')
    .map((origin) => origin.trim().toLowerCase().replace(/\/+$/, ''))
    .filter((origin) => origin.length > 0);
}

/** `npm run dev` on :5173 — and a LAN IP during phone testing — stay allowed unconfigured. */
const LOCALHOST_ORIGIN = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/;

export function isOriginAllowed(origin: string | undefined, allowed: readonly string[]): boolean {
  if (!origin) return false;
  const normalized = origin.trim().toLowerCase();
  return LOCALHOST_ORIGIN.test(normalized) || allowed.includes(normalized);
}
