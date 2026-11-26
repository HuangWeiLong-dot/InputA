import { describe, expect, it } from 'vitest'
import { normalizeTerm, tokenize, wordTerms } from './tokenize'

describe('tokenize', () => {
  it('keeps punctuation and whitespace while separating spaced words', () => {
    expect(tokenize("Don't stop!", 'en')).toEqual([
      { text: "Don't", kind: 'word' },
      { text: ' ', kind: 'space' },
      { text: 'stop', kind: 'word' },
      { text: '!', kind: 'punctuation' },
    ])
  })

  it('normalizes dictionary words and excludes punctuation from stats', () => {
    expect(wordTerms('阅读, 阅读.', 'zh')).toEqual(['阅读', '阅读'])
    expect(normalizeTerm('  Hello ')).toBe('hello')
  })

  it('preserves line breaks', () => {
    expect(tokenize('one\ntwo', 'en').some((token) => token.kind === 'linebreak')).toBe(true)
  })
})
