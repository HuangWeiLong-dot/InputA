/// <reference types="vite/client" />

/**
 * Build-time environment variables this app reads. Declared so
 * `import.meta.env.VITE_API_BASE` is `string | undefined` rather than falling
 * through to vite/client's index signature, which types it as `any`.
 */
interface ImportMetaEnv {
  /**
   * Backend origin for the split deployment, e.g.
   * `https://inputa-api.duckdns.org`. Unset means "same origin", which is what
   * `npm start` and `npm run dev` both rely on; see services/apiBase.ts.
   */
  readonly VITE_API_BASE?: string;
}
