import { ALLOWED_QUERY, matchUpstream } from './routes.mjs'

const LINGQ_ORIGIN = 'https://www.lingq.com'
const MAX_BODY_BYTES = 16 * 1024

function json(res, status, message) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  })
  res.end(JSON.stringify({ error: message }))
}

async function readBody(req) {
  const chunks = []
  let size = 0
  for await (const chunk of req) {
    size += chunk.length
    if (size > MAX_BODY_BYTES) throw new Error('body_too_large')
    chunks.push(chunk)
  }
  return Buffer.concat(chunks)
}

export function resolveUpstream(requestUrl, method) {
  const url = new URL(requestUrl, 'http://localhost')
  if (!url.pathname.startsWith('/api/lingq/')) return null
  const path = url.pathname.slice('/api/lingq/'.length)
  const route = matchUpstream(path, method)
  if (!route) return null

  for (const key of url.searchParams.keys()) {
    if (!ALLOWED_QUERY[route.id]?.includes(key)) return null
  }

  const target = new URL(path, `${LINGQ_ORIGIN}/`)
  for (const [key, value] of url.searchParams) {
    if (!route.query[key]?.(value)) return null
    target.searchParams.set(key, value)
  }
  return target
}

export function createLingqProxy() {
  return async function lingqProxy(req, res, next) {
    if (!req.url?.startsWith('/api/lingq/')) {
      next?.()
      return
    }

    const method = req.method ?? 'GET'
    let target
    try {
      target = resolveUpstream(req.url, method)
    } catch {
      json(res, 400, 'Invalid API request')
      return
    }
    if (!target) {
      json(res, 404, 'API route not allowed')
      return
    }

    const headers = { Accept: 'application/json' }
    const authorization = req.headers.authorization
    if (authorization) {
      if (!/^Token [A-Za-z0-9._~-]{8,512}$/.test(authorization)) {
        json(res, 400, 'Invalid authorization header')
        return
      }
      headers.Authorization = authorization
    }

    let body
    if (method === 'POST') {
      if (!/^application\/x-www-form-urlencoded(?:;|$)/i.test(req.headers['content-type'] ?? '')) {
        json(res, 415, 'Unsupported content type')
        return
      }
      try {
        body = await readBody(req)
      } catch {
        json(res, 413, 'Request body is too large')
        return
      }
      headers['Content-Type'] = 'application/x-www-form-urlencoded'
    }

    try {
      const upstream = await fetch(target, {
        method,
        headers,
        body,
        redirect: 'error',
        credentials: 'omit',
        signal: AbortSignal.timeout(15_000),
      })
      const responseBody = Buffer.from(await upstream.arrayBuffer())
      const responseHeaders = {
        'Content-Type': upstream.headers.get('content-type') ?? 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff',
        'Referrer-Policy': 'no-referrer',
      }
      res.writeHead(upstream.status, responseHeaders)
      res.end(responseBody)
    } catch {
      json(res, 502, 'LingQ request failed')
    }
  }
}
