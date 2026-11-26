import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import type { BookmarkRecord, LastLessonRecord, LessonDetail, WordRecord, WordStatus } from '../api/types'

interface CachedLesson extends LessonDetail {
  language: string
}

interface InputADatabase extends DBSchema {
  words: {
    key: [string, string]
    value: WordRecord
    indexes: { language: string }
  }
  bookmarks: {
    key: [string, number]
    value: BookmarkRecord
    indexes: { language: string }
  }
  lessons: {
    key: [string, number]
    value: CachedLesson
    indexes: { language: string }
  }
  lastLesson: {
    key: string
    value: LastLessonRecord
  }
}

let database: Promise<IDBPDatabase<InputADatabase>> | null = null

function db() {
  database ??= openDB<InputADatabase>('inputa-web', 1, {
    upgrade(database) {
      const words = database.createObjectStore('words', { keyPath: ['language', 'term'] })
      words.createIndex('language', 'language')
      const bookmarks = database.createObjectStore('bookmarks', { keyPath: ['language', 'lessonId'] })
      bookmarks.createIndex('language', 'language')
      const lessons = database.createObjectStore('lessons', { keyPath: ['language', 'id'] })
      lessons.createIndex('language', 'language')
      database.createObjectStore('lastLesson', { keyPath: 'language' })
    },
  })
  return database
}

function isStatus(value: number): value is WordStatus {
  return Number.isInteger(value) && value >= -1 && value <= 4
}

export async function listWords(language: string): Promise<WordRecord[]> {
  const records = await (await db()).getAllFromIndex('words', 'language', language)
  return records.filter((record) => isStatus(record.status))
}

export async function getWord(language: string, term: string): Promise<WordRecord | null> {
  const record = await (await db()).get('words', [language, term]) ?? null
  return record && isStatus(record.status) ? record : null
}

export async function putWord(language: string, term: string, status: WordStatus) {
  const normalized = term.trim().toLowerCase()
  if (!normalized || !isStatus(status)) return
  await (await db()).put('words', {
    language,
    term: normalized,
    status,
    updatedAt: new Date().toISOString(),
  })
}

export async function deleteWord(language: string, term: string) {
  await (await db()).delete('words', [language, term])
}

export async function listBookmarks(language: string): Promise<BookmarkRecord[]> {
  const records = await (await db()).getAllFromIndex('bookmarks', 'language', language)
  return records.sort((a, b) => b.savedAt.localeCompare(a.savedAt))
}

export async function putBookmark(record: BookmarkRecord) {
  await (await db()).put('bookmarks', record)
}

export async function deleteBookmark(language: string, lessonId: number) {
  await (await db()).delete('bookmarks', [language, lessonId])
}

export async function cacheLesson(language: string, lesson: LessonDetail) {
  await (await db()).put('lessons', { ...lesson, language })
}

export async function getCachedLesson(language: string, id: number): Promise<LessonDetail | null> {
  return await (await db()).get('lessons', [language, id]) ?? null
}

export async function getLastLesson(language: string): Promise<LastLessonRecord | null> {
  return await (await db()).get('lastLesson', language) ?? null
}

export async function putLastLesson(language: string, lesson: BookmarkRecord['lesson'], sentenceIndex: number) {
  await (await db()).put('lastLesson', {
    language,
    lesson,
    sentenceIndex,
    updatedAt: new Date().toISOString(),
  })
}
