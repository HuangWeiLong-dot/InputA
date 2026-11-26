import type { IncomingMessage, ServerResponse } from 'node:http'

export function resolveUpstream(requestUrl: string, method: string): URL | null
export function createLingqProxy(): (
  req: IncomingMessage,
  res: ServerResponse,
  next?: () => void,
) => Promise<void>
