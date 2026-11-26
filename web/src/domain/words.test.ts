import { describe, expect, it } from 'vitest'
import type { WordStatus } from '../api/types'
import { isDueForReview, lessonStats, reviewAnswer, reviewQueue } from './words'

describe('local-first word rules', () => {
  it('computes lesson totals from text and local status', () => {
    const words = new Map<string, WordStatus>([['hello', 0], ['monde', 2], ['skip', -1]])
    expect(lessonStats('Hello world, hello monde skip.', 'en', words)).toEqual({
      total: 5,
      unique: 4,
      newWords: 2,
      known: 1,
      learning: 1,
      ignored: 1,
    })
  })

  it('advances learning levels and graduates level four to known', () => {
    expect(reviewAnswer(1, true)).toBe(2)
    expect(reviewAnswer(3, true)).toBe(4)
    expect(reviewAnswer(4, true)).toBe(0)
    expect(reviewAnswer(3, false)).toBe(1)
    expect(reviewAnswer(0, false)).toBe(0)
  })

  it('queues only learning words in a stable order', () => {
    const words = [
      { term: 'later', status: 3 as WordStatus, updatedAt: '2026-01-02' },
      { term: 'known', status: 0 as WordStatus, updatedAt: '2026-01-01' },
      { term: 'first', status: 1 as WordStatus, updatedAt: '2026-01-01' },
      { term: 'ignored', status: -1 as WordStatus, updatedAt: '2026-01-01' },
    ]
    expect(reviewQueue(words).map((word) => word.term)).toEqual(['first', 'later'])
    expect(isDueForReview(4)).toBe(true)
    expect(isDueForReview(0)).toBe(false)
  })
})
