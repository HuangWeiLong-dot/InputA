export interface Token {
  text: string
  kind: 'word' | 'punctuation' | 'space' | 'linebreak'
}

const dictionaryLanguages = new Set(['zh', 'ja', 'th', 'ko', 'lo', 'km', 'my', 'yue'])
const segmenter = typeof Intl.Segmenter === 'function'
  ? new Intl.Segmenter(undefined, { granularity: 'word' })
  : null
const spacedPattern = /[\p{L}\p{M}\p{N}]+(?:['’-][\p{L}\p{M}\p{N}]+)*|[\p{S}\p{P}]+|[^\S\r\n]+|\r?\n/gu

export function isDictionaryLanguage(language: string) {
  return dictionaryLanguages.has(language.toLowerCase().split('-')[0])
}

function classify(text: string): Token['kind'] {
  if (/^\s+$/u.test(text)) return text.includes('\n') ? 'linebreak' : 'space'
  if (/^[\p{L}\p{M}\p{N}]/u.test(text)) return 'word'
  return 'punctuation'
}

export function tokenize(text: string, language: string): Token[] {
  if (isDictionaryLanguage(language) && segmenter) {
    return Array.from(segmenter.segment(text), (segment) => ({
      text: segment.segment,
      kind: classify(segment.segment),
    }))
  }
  return Array.from(text.matchAll(spacedPattern), (match) => ({
    text: match[0],
    kind: classify(match[0]),
  }))
}

export function normalizeTerm(term: string) {
  return term.trim().toLocaleLowerCase()
}

export function wordTerms(text: string, language: string): string[] {
  return tokenize(text, language)
    .filter((token) => token.kind === 'word')
    .map((token) => normalizeTerm(token.text))
    .filter(Boolean)
}
