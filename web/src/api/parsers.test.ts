import { describe, expect, it } from 'vitest'
import {
  extractText,
  parseAudioUrl,
  parseCourse,
  parseLanguages,
  parseLessonDetail,
  parseLessons,
  parseMeanings,
  parseProfile,
  parseSentences,
  parseShelves,
  parseTtsVoices,
} from '../api/parsers'
import type { LessonSummary } from '../api/types'

describe('LingQ parsers', () => {
  it('accepts mixed aliases and DRF wrappers', () => {
    const profile = parseProfile({ results: [{ username: 'reader', display_name: 'Reader', language: { code: 'fr' } }] })
    expect(profile).toMatchObject({ username: 'reader', displayName: 'Reader', languageCode: 'fr' })
    const languages = parseLanguages({ results: [{ code: 'fr', title: 'French', is_active: true }] })
    expect(languages[0]).toMatchObject({ code: 'fr', name: 'French', isActive: true })
  })

  it('parses lesson aliases and rejects records without ids', () => {
    const lessons = parseLessons({ results: [
      { id: '12', title: 'One', audio_url: 'https://example.com/audio.mp3', type: 'lesson' },
      { title: 'Missing id' },
    ] })
    expect(lessons).toHaveLength(1)
    expect(lessons[0]).toMatchObject({ id: 12, audioUrl: 'https://example.com/audio.mp3', isCourse: false })
    const detail = parseLessonDetail({ id: 12, title: 'One', text: 'Hello world.' })
    expect(detail?.text).toBe('Hello world.')
  })

  it('uses preview lesson ids for courses and preserves collection ids', () => {
    const lessons = parseLessons({ results: [{
      id: 80,
      type: 'folder',
      title: 'Starter course',
      lessonsCount: 2,
      lessonPreview: { content_id: 91, title: 'First', audio: { url: '//example.com/audio.mp3' } },
    }, {
      contentId: 92,
      title: 'Second',
      duration: 7_200_000,
      collection: { id: 80, title: 'Starter course' },
    }] })
    expect(lessons[0]).toMatchObject({
      id: 91,
      libraryId: 80,
      isCourse: true,
      lessonCount: 2,
      audioUrl: 'https://example.com/audio.mp3',
    })
    expect(lessons[1]).toMatchObject({
      id: 92,
      libraryId: 92,
      collectionId: 80,
      collectionTitle: 'Starter course',
      durationSec: 7_200,
    })
  })

  it('reads shelf contents and removes duplicate lessons globally', () => {
    const shelf = parseShelves({ shelves: [
      { title: 'Featured', contents: [{ contentId: 1, title: 'One', duration: 30 }] },
      { title: 'More', items: [{ contentId: 1, title: 'Duplicate', duration: 30 }, { id: 2, title: 'Two', type: 'lesson' }] },
    ] })
    expect(shelf.map((section) => section.title)).toEqual(['Featured', 'More'])
    expect(shelf.flatMap((section) => section.lessons).map((entry) => entry.id)).toEqual([1, 2])
  })

  it('finds nested learning languages and normalizes regional codes', () => {
    const languages = parseLanguages({ data: { languages: [
      { code: 'en-US', title: 'English', known_words: 12, words_read: 100, is_active: true },
      { code: 'fr', title: 'French' },
    ] } })
    expect(languages).toEqual([{
      code: 'en',
      name: 'English',
      knownWords: 12,
      lingqCount: null,
      wordsRead: 100,
      level: null,
      isActive: true,
    }])
  })

  it('renumbers sentence indices and falls back to cached text', () => {
    expect(parseSentences({ results: [{ index: 8, text: 'First.' }, { index: 2, text: 'Second.' }] }, null))
      .toEqual([
        { index: 0, text: 'First.', paragraphStart: false },
        { index: 1, text: 'Second.', paragraphStart: false },
      ])
    expect(parseSentences(null, 'One.\nTwo.')).toEqual([
      { index: 0, text: 'One.', paragraphStart: true },
      { index: 1, text: 'Two.', paragraphStart: false },
    ])
  })

  it('accepts primitive, keyed and token-only sentence responses', () => {
    expect(parseSentences({ 4: 'First.', 9: 'Second.' }, null).map((sentence) => sentence.text))
      .toEqual(['First.', 'Second.'])
    expect(parseSentences({ data: { sentences: [
      { tokens: [{ text: 'Hello' }, { text: 'world', punct: '.' }] },
    ] } }, null)[0].text).toBe('Hello world.')
  })

  it('extracts nested lesson text and strips HTML and entities', () => {
    expect(extractText({ data: { text: ['<p>Hello&nbsp;world.</p>', { content: '<p>Second.</p>' }] } }))
      .toBe('Hello world.\n\nSecond.')
    expect(extractText({ text: 42 })).toBeNull()
  })

  it('unwraps courses with keyed lessons and preview fallbacks', () => {
    const fallback: LessonSummary = {
      id: 91,
      libraryId: 80,
      title: 'Starter course',
      description: null,
      imageUrl: null,
      audioUrl: null,
      durationSec: null,
      level: 1,
      newWordsCount: null,
      collectionId: null,
      collectionTitle: null,
      isCourse: true,
      lessonCount: 1,
      isCompleted: false,
      isTaken: false,
    }
    const course = parseCourse({ results: [{
      id: 80,
      title: 'Starter course',
      lessons: { first: { contentId: 91, title: 'First', type: 'lesson' } },
    }] })
    expect(course).toMatchObject({ id: 80, title: 'Starter course' })
    expect(course?.lessons[0]).toMatchObject({ id: 91, libraryId: 91 })
    const preview = parseCourse({ id: 80, title: 'Starter course', lessonPreview: 91 }, fallback)
    expect(preview?.lessons[0]).toMatchObject({ id: 91, isCourse: false, libraryId: 80 })
  })

  it('parses single meanings, translations and removes duplicates', () => {
    expect(parseMeanings([{ definition: 'book', language: 'en' }, { translation: 'book', locale: 'en' }]))
      .toEqual([{ text: 'book', locale: 'en' }])
    expect(parseMeanings({ translation: 'livre', locale: 'fr' }))
      .toEqual([{ text: 'livre', locale: 'fr' }])
  })

  it('parses selectable TTS voices and nested audio urls', () => {
    const voices = parseTtsVoices([{
      premium: false,
      isSelectable: true,
      tags: ['default'],
      voicesByApp: [{ appName: 'android', name: 'voice-a' }, { app_name: 'web', voice: 'voice-b' }],
    }, {
      premium: true,
      voicesByApp: [{ appName: 'premium', name: 'voice-c' }],
    }, {
      isSelectable: false,
      voicesByApp: [{ appName: 'locked', name: 'voice-d' }],
    }])
    expect(voices).toEqual([
      { appName: 'android', voice: 'voice-a', isDefault: true },
      { appName: 'web', voice: 'voice-b', isDefault: true },
    ])
    expect(parseAudioUrl({ generated: { audio: { url: '/media/word.mp3' } } }))
      .toBe('https://www.lingq.com/media/word.mp3')
    expect(parseAudioUrl({ audio: 'data:audio/mp3;base64,abc' })).toBeNull()
  })
})
