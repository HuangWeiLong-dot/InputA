/// <reference types="vite/client" />

/**
 * Build-time environment variables this app reads. Declared so
 * `import.meta.env.VITE_API_BASE_ENC` is `string | undefined` rather than falling
 * through to vite/client's index signature, which types it as `any`.
 */
interface ImportMetaEnv {
  /**
   * Backend origin for the split deployment, **base64-encoded**, e.g.
   * `aHR0cHM6Ly9leGFtcGxlLnRlc3Q=`. Unset means "same origin", which is what
   * `npm start` and `npm run dev` both rely on; see services/apiBase.ts.
   *
   * Encoded rather than plain so the address does not appear literally in the
   * published bundle (the CI job encodes it at build time). That is obfuscation, not
   * encryption — see `decodeApiBase` for why it must not be mistaken for security.
   */
  readonly VITE_API_BASE_ENC?: string;
}
