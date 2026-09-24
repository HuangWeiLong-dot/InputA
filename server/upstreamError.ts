/**
 * The error the server turns into an HTTP status.
 *
 * It lives in its own TypeScript module rather than inside upstreams.js so the
 * TypeScript half of the backend (edgeTts.ts, dictLookup.ts) can import it with
 * types. upstreams.js and index.js are plain JavaScript and load it through
 * Node's type stripping, the same way they already load dictLookup.ts.
 */
export class UpstreamError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'UpstreamError';
    this.status = status;
  }
}
