const language = '[a-z]{2,3}(?:-[a-z0-9]{2,8})?'
const id = '\\d{1,12}'

const bounded = (max) => (value) => value.length > 0 && value.length <= max
const integer = (min, max) => (value) => /^\d+$/.test(value) && Number(value) >= min && Number(value) <= max
const oneOf = (...values) => (value) => values.includes(value)

const query = {
  username: bounded(100),
  q: bounded(200),
  term: bounded(200),
  level: integer(0, 6),
  page: integer(1, 100_000),
  page_size: integer(1, 100),
  collection: integer(1, Number.MAX_SAFE_INTEGER),
  suppress: oneOf('true', 'false'),
  sortBy: oneOf('recommended', 'lastOpenTime', 'newest', 'oldest', 'level'),
  type: oneOf('continue', 'lesson', 'collection'),
  provider: bounded(100),
  shelf: bounded(100),
  locale: (value) => /^[a-z]{2,3}(?:-[a-z0-9]{2,8})?$/i.test(value),
  all: oneOf('true', 'false'),
  language: (value) => new RegExp(`^${language}$`).test(value),
  text: bounded(300),
  ttsType: oneOf('word', 'phrase', 'sentence'),
  app_name: bounded(80),
  voice: bounded(120),
}

const definitions = [
  {
    id: 'login',
    method: 'POST',
    path: /^api\/v2\/api-token-auth\/?$/,
    query: {},
  },
  {
    id: 'profile',
    method: 'GET',
    path: /^api\/v3\/profiles\/?$/,
    query: {},
  },
  {
    id: 'languages',
    method: 'GET',
    path: /^api\/v2\/languages\/?$/,
    query: { username: query.username },
  },
  {
    id: 'shelves',
    method: 'GET',
    path: new RegExp(`^api/v3/(${language})/shelves/?$`),
    query: { suppress: query.suppress, level: query.level, page: query.page, page_size: query.page_size },
  },
  {
    id: 'search',
    method: 'GET',
    path: new RegExp(`^api/v3/(${language})/search/?$`),
    query: {
      q: query.q,
      level: query.level,
      sortBy: query.sortBy,
      type: query.type,
      provider: query.provider,
      shelf: query.shelf,
      collection: query.collection,
      page: query.page,
      page_size: query.page_size,
      suppress: query.suppress,
    },
  },
  {
    id: 'providers',
    method: 'GET',
    path: new RegExp(`^api/v3/(${language})/providers/(?:featured/)?$`),
    query: {},
  },
  {
    id: 'lesson',
    method: 'GET',
    path: new RegExp(`^api/v3/(${language})/lessons/${id}(?:/(?:info|text|sentences))?/?$`),
    query: {},
  },
  {
    id: 'collection',
    method: 'GET',
    path: new RegExp(`^api/v3/(${language})/collections/${id}/?$`),
    query: {},
  },
  {
    id: 'meanings',
    method: 'GET',
    path: new RegExp(`^api/v2/(${language})/hints/search/?$`),
    query: { term: query.term, all: query.all, locale: query.locale },
  },
  {
    id: 'tts-voices',
    method: 'GET',
    path: /^api\/v2\/tts\/supported-voices\/?$/,
    query: { language: query.language },
  },
  {
    id: 'tts',
    method: 'GET',
    path: new RegExp(`^api/v3/(${language})/tts/?$`),
    query: { text: query.text, type: query.ttsType, app_name: query.app_name, voice: query.voice },
  },
]

export const ALLOWED_QUERY = Object.fromEntries(definitions.map((route) => [route.id, Object.keys(route.query)]))

export function matchUpstream(path, method) {
  return definitions.find((route) => route.method === method && route.path.test(path)) ?? null
}
