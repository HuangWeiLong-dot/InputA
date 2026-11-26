import type { WordStatus } from '../api/types'
import { normalizeTerm, wordTerms } from './tokenize'

export function lessonStats(text: string, language: string, words: Map<string, WordStatus>) {
  const terms = wordTerms(text, language)
  const unique = new Set(terms)
  let known = 0
  let learning = 0
  let ignored = 0
  for (const term of unique) {
    const status = words.get(term)
    if (status === 0) known += 1
    else if (status === -1) ignored += 1
    else if (status !== undefined) learning += 1
  }
  return {
    total: terms.length,
    unique: unique.size,
    newWords: unique.size - known - ignored,
    known,
    learning,
    ignored,
  }
}

export function reviewAnswer(status: WordStatus, remembered: boolean): WordStatus {
  if (status === -1 || status === 0) return status
  if (!remembered) return 1
  if (status >= 4) return 0
  return (status + 1) as WordStatus
}

export function isDueForReview(status: WordStatus) {
  return status >= 1 && status <= 4
}

export function reviewQueue(words: { term: string; status: WordStatus; updatedAt: string }[]) {
  return words
    .filter((word) => isDueForReview(word.status))
    .sort((a, b) => a.status - b.status || a.updatedAt.localeCompare(b.updatedAt) || a.term.localeCompare(b.term))
}

export { normalizeTerm }
