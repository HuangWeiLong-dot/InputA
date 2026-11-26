import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveUpstream } from './proxy.mjs'

const prefix = '/api/lingq/'

test('allows only the core read-only LingQ routes', () => {
  assert.equal(resolveUpstream(`${prefix}api/v3/profiles/`, 'GET')?.href, 'https://www.lingq.com/api/v3/profiles/')
  assert.equal(resolveUpstream(`${prefix}api/v3/en/search/?q=hello&page_size=30`, 'GET')?.searchParams.get('q'), 'hello')
  assert.equal(resolveUpstream(`${prefix}api/v3/ja/lessons/42/sentences/`, 'GET')?.pathname, '/api/v3/ja/lessons/42/sentences/')
  assert.equal(resolveUpstream(`${prefix}api/v2/es/hints/search/?term=libro&locale=en`, 'GET')?.pathname, '/api/v2/es/hints/search/')
  assert.equal(resolveUpstream(`${prefix}api/v2/api-token-auth/`, 'POST')?.method, undefined)
})

test('rejects writes, unknown routes, unknown query keys and invalid values', () => {
  assert.equal(resolveUpstream(`${prefix}api/v2/en/cards/`, 'GET'), null)
  assert.equal(resolveUpstream(`${prefix}api/v2/en/cards/1/`, 'PATCH'), null)
  assert.equal(resolveUpstream(`${prefix}api/v3/en/lessons/1/complete/`, 'POST'), null)
  assert.equal(resolveUpstream(`${prefix}api/v3/profiles/?username=other`, 'GET'), null)
  assert.equal(resolveUpstream(`${prefix}api/v3/en/search/?q=hello&callback=alert`, 'GET'), null)
  assert.equal(resolveUpstream(`${prefix}api/v3/en/search/?page_size=1000`, 'GET'), null)
  assert.equal(resolveUpstream(`${prefix}api/v3/en/lessons/not-an-id/`, 'GET'), null)
  assert.equal(resolveUpstream('/api/other', 'GET'), null)
})

test('uses a fixed LingQ origin and rejects malformed language tags', () => {
  const target = resolveUpstream(`${prefix}api/v3/zh-cn/shelves/?suppress=true`, 'GET')
  assert.equal(target?.origin, 'https://www.lingq.com')
  assert.equal(target?.pathname, '/api/v3/zh-cn/shelves/')
  assert.equal(resolveUpstream(`${prefix}api/v3/../admin/`, 'GET'), null)
  assert.equal(resolveUpstream(`${prefix}api/v3/en%2F..%2Fadmin/`, 'GET'), null)
})
