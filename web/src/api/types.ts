export type WordStatus = -1 | 0 | 1 | 2 | 3 | 4

export interface Profile {
  username: string
  displayName: string
  avatarUrl: string | null
  knownWords: number | null
  lingqCount: number | null
  languageCode: string | null
  languageName: string | null
}

export interface LearningLanguage {
  code: string
  name: string
  knownWords: number | null
  lingqCount: number | null
  wordsRead: number | null
  level: number | null
  isActive: boolean
}

export interface LessonSummary {
  id: number
  libraryId: number
  title: string
  description: string | null
  imageUrl: string | null
  audioUrl: string | null
  durationSec: number | null
  level: number | null
  newWordsCount: number | null
  collectionId: number | null
  collectionTitle: string | null
  isCourse: boolean
  lessonCount: number | null
  isCompleted: boolean
  isTaken: boolean
}

export interface ShelfSection {
  title: string
  lessons: LessonSummary[]
}

export interface ExploreProvider {
  id: string
  title: string
  imageUrl: string | null
}

export interface LessonDetail extends LessonSummary {
  text: string | null
}

export interface CourseDetail {
  id: number
  title: string
  description: string | null
  imageUrl: string | null
  level: number | null
  lessons: LessonSummary[]
}

export interface ReaderSentence {
  index: number
  text: string
  paragraphStart: boolean
}

export interface Meaning {
  text: string
  locale: string | null
}

export interface TtsVoice {
  appName: string
  voice: string
  isDefault: boolean
}

export interface WordRecord {
  language: string
  term: string
  status: WordStatus
  updatedAt: string
}

export interface BookmarkRecord {
  language: string
  lessonId: number
  lesson: LessonSummary
  savedAt: string
}

export interface LastLessonRecord {
  language: string
  lesson: LessonSummary
  sentenceIndex: number
  updatedAt: string
}
