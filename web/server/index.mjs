import { createServer } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { extname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createLingqProxy } from './proxy.mjs'

const root = resolve(fileURLToPath(new URL('../dist', import.meta.url)))
const proxy = createLingqProxy()
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.woff2': 'font/woff2',
  '.json': 'application/json; charset=utf-8',
}

function securityHeaders() {
  return {
    'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; media-src 'self' https:; connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  }
}

async function sendFile(res, path, status = 200) {
  const data = await readFile(path)
  const extension = extname(path)
  const headers = {
    ...securityHeaders(),
    'Content-Type': mime[extension] ?? 'application/octet-stream',
    'Cache-Control': extension === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable',
  }
  if (status !== 200) headers['X-Robots-Tag'] = 'noindex'
  res.writeHead(status, headers)
  res.end(data)
}

const server = createServer(async (req, res) => {
  if (req.url?.startsWith('/api/lingq/')) {
    await proxy(req, res)
    return
  }
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { Allow: 'GET, HEAD', ...securityHeaders() })
    res.end()
    return
  }

  try {
    const pathname = decodeURIComponent(new URL(req.url ?? '/', 'http://localhost').pathname)
    const candidate = resolve(root, `.${pathname}`)
    if (candidate !== root && !candidate.startsWith(`${root}${sep}`)) {
      res.writeHead(400, securityHeaders())
      res.end()
      return
    }
    const info = await stat(candidate).catch(() => null)
    if (info?.isFile()) {
      await sendFile(res, candidate)
      return
    }
    if (extname(pathname)) {
      res.writeHead(404, securityHeaders())
      res.end()
      return
    }
    await sendFile(res, resolve(root, 'index.html'))
  } catch {
    res.writeHead(500, securityHeaders())
    res.end()
  }
})

const port = Number(process.env.PORT ?? 4173)
const host = process.env.HOST ?? '127.0.0.1'
server.listen(port, host, () => {
  console.log(`InputA Web listening on http://${host}:${port}`)
})
