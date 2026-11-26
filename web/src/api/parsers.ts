import type {
  CourseDetail,
  ExploreProvider,
  LearningLanguage,
  LessonDetail,
  LessonSummary,
  Meaning,
  Profile,
  ReaderSentence,
  ShelfSection,
  TtsVoice,
} from './types'

type Json = Record<string, unknown>

function record(value: unknown): Json {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Json
    : {}
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function string(...keys: string[]): (value: Json) => string | null {
  return (value) => {
    for (const key of keys) {
      const candidate = value[key]
      if (typeof candidate === 'string' && candidate.length > 0) return candidate
      if (typeof candidate === 'number' && Number.isFinite(candidate)) return String(candidate)
    }
    return null
  }
}

function text(...keys: string[]): (value: Json) => string | null {
  return (value) => {
    for (const key of keys) {
      const candidate = value[key]
      if (typeof candidate === 'string' && candidate.trim().length > 0) return candidate
    }
    return null
  }
}

function number(...keys: string[]): (value: Json) => number | null {
  return (value) => {
    for (const key of keys) {
      const candidate = value[key]
      if (typeof candidate !== 'number' && typeof candidate !== 'string') continue
      if (typeof candidate === 'string' && candidate.trim() === '') continue
      const parsed = Number(candidate)
      if (Number.isFinite(parsed)) return parsed
    }
    return null
  }
}

function boolean(...keys: string[]): (value: Json) => boolean {
  return (value) => keys.some((key) => {
    const candidate = value[key]
    return candidate === true || candidate === 1 || candidate === 'true'
  })
}

function booleanValue(...keys: string[]): (value: Json) => boolean | null {
  return (value) => {
    for (const key of keys) {
      const candidate = value[key]
      if (candidate === true || candidate === 1 || candidate === 'true') return true
      if (candidate === false || candidate === 0 || candidate === 'false') return false
    }
    return null
  }
}

function id(value: Json): number | null {
  const parsed = number('id', 'pk')(value)
  return parsed === null ? null : Math.trunc(parsed)
}

function lessonId(value: Json): number | null {
  const parsed = number('contentId', 'content_id', 'lessonId', 'lesson_id', 'id', 'pk')(value)
  return parsed === null ? null : Math.trunc(parsed)
}

function absoluteUrl(value: string | null): string | null {
  const candidate = value?.trim()
  if (!candidate) return null
  if (/^https?:\/\//i.test(candidate)) return candidate
  if (candidate.startsWith('//')) return `https:${candidate}`
  if (candidate.startsWith('/')) return `https://www.lingq.com${candidate}`
  if (candidate.includes('/') && !candidate.includes(' ')) return `https://www.lingq.com/${candidate}`
  return null
}

function image(value: Json): string | null {
  return absoluteUrl(string(
    'imageUrl', 'image_url', 'originalImageUrl', 'original_image_url',
    'mediaImageUrl', 'media_image_url', 'image', 'thumbnail', 'thumbnail_url',
  )(value) ?? string('url', 'imageUrl', 'image_url')(record(value.image)))
}

function audioUrl(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const candidate = value.trim()
  if (!candidate || /^data:/i.test(candidate)) return null
  return absoluteUrl(candidate)
}

function audio(value: Json): string | null {
  return audioUrl(string('audioUrl', 'audio_url', 'audio')(value)) ??
    audioUrl(string('url', 'audioUrl', 'audio_url')(record(value.audio)))
}

function objects(value: unknown): Json[] {
  if (Array.isArray(value)) return value.map(record)
  const wrapped = record(value)
  const inner = wrapped.results ?? wrapped.data ?? wrapped.objects
  if (Array.isArray(inner)) return inner.map(record)
  return []
}

function collectObjects(value: unknown, predicate: (item: Json) => boolean, depth = 0): Json[] {
  if (depth > 8) return []
  if (Array.isArray(value)) return value.flatMap((item) => collectObjects(item, predicate, depth + 1))
  const item = record(value)
  if (Object.keys(item).length === 0) return []
  const matched = predicate(item) ? [item] : []
  return matched.length > 0
    ? matched
    : Object.values(item).flatMap((child) => collectObjects(child, predicate, depth + 1))
}

function unwrapSingle(value: unknown): Json {
  if (Array.isArray(value)) return record(value[0])
  const item = record(value)
  for (const key of ['results', 'data', 'object', 'profile', 'result']) {
    const inner = item[key]
    if (Array.isArray(inner)) return record(inner[0])
    if (inner && typeof inner === 'object' && Object.keys(inner).length > 0) return record(inner)
  }
  return item
}

function cleanText(raw: string): string {
  return raw
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p\s*>/gi, '\n\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/\u00a0/g, ' ')
    .trim()
}

export function extractText(value: unknown, depth = 0): string | null {
  if (depth > 8) return null
  if (typeof value === 'string') return cleanText(value) || null
  if (Array.isArray(value)) {
    const parts = value.map((item) => {
      if (typeof item === 'string') return cleanText(item)
      const entry = record(item)
      return cleanText(text('text', 'content', 'sentence')(entry) ?? '')
    }).filter(Boolean)
    return parts.length > 0 ? cleanText(parts.join('\n\n')) : null
  }
  const item = record(value)
  const nestedText = item.text
  if (Array.isArray(nestedText) || (nestedText && typeof nestedText === 'object')) {
    const nestedResult = extractText(nestedText, depth + 1)
    if (nestedResult) return nestedResult
  }
  const direct = cleanText(text('text', 'content')(item) ?? '')
  if (direct) return direct
  for (const key of ['results', 'data', 'sentences']) {
    if (item[key] !== undefined) {
      const result = extractText(item[key], depth + 1)
      if (result) return result
    }
  }
  const candidates = collectObjects(item, (entry) => text('text', 'content')(entry) !== null, depth)
    .map((entry) => cleanText(text('text', 'content')(entry) ?? ''))
    .filter(Boolean)
  return candidates.sort((a, b) => b.length - a.length)[0] ?? null
}

function looksLikeLesson(item: Json): boolean {
  const hasId = lessonId(item) !== null
  const hasTitle = text('title', 'name')(item) !== null ||
    text('title', 'name')(record(item.lessonPreview ?? item.lesson_preview)) !== null
  const markerKeys = new Set([
    'audioUrl', 'audio_url', 'imageUrl', 'image_url', 'newWordsCount', 'new_words_count',
    'duration', 'collectionTitle', 'collection_title', 'isCompleted', 'is_completed',
    'isTaken', 'is_taken', 'originalImageUrl', 'lessonPreview', 'lesson_preview',
    'lessonsCount', 'lessons_count', 'lessons', 'type',
  ])
  return hasId && hasTitle && Object.keys(item).some((key) => markerKeys.has(key))
}

function lesson(value: unknown): LessonSummary | null {
  const item = record(value)
  const directId = lessonId(item)
  if (directId === null) return null
  const title = text('title', 'name')(item)?.trim() ||
    text('title', 'name')(record(item.lessonPreview ?? item.lesson_preview))?.trim()
  if (!title) return null

  const previewElement = item.lessonPreview ?? item.lesson_preview
  const preview = record(previewElement)
  const previewId = lessonId(preview) ??
    (typeof previewElement === 'number' || typeof previewElement === 'string'
      ? Number(previewElement) || null
      : null)
  const lessonsValue = item.lessons
  const firstLesson = Array.isArray(lessonsValue)
    ? lessonsValue[0]
    : lessonsValue && typeof lessonsValue === 'object'
      ? Object.values(record(lessonsValue))[0]
      : null
  const firstLessonId = lessonId(record(firstLesson))
  const lessonCount = number('lessonsCount', 'lessons_count', 'child_count', 'num_lessons')(item) ??
    (Array.isArray(lessonsValue) ? lessonsValue.length : lessonsValue && typeof lessonsValue === 'object' ? Object.keys(lessonsValue).length : 0)
  const hasPreview = previewElement !== undefined && previewElement !== null
  const type = string('type')(item)?.toLowerCase()
  const isCourse = boolean('isCourse', 'is_course')(item) || type === 'collection' || type === 'folder' ||
    (!type && (hasPreview || (lessonCount ?? 0) > 0 || lessonsValue !== undefined))
  const resolvedId = isCourse ? previewId ?? firstLessonId ?? directId : directId
  const stats = Object.keys(preview).length > 0 ? preview : item
  const collection = record(item.collection)
  const collectionId = isCourse ? null : number('collectionId', 'collection_id')(item) ?? id(collection)
  const durationRaw = number('duration', 'durationSec', 'duration_sec', 'duration_seconds', 'audioDuration')(stats) ??
    number('duration')(item)
  const duration = durationRaw === null ? null : Math.trunc(durationRaw > 21_600 ? durationRaw / 1000 : durationRaw)
  return {
    id: resolvedId,
    libraryId: directId,
    title,
    description: text('description', 'description_html', 'summary')(item),
    imageUrl: image(item) ?? image(stats) ?? image(collection),
    audioUrl: audio(stats) ?? audio(item),
    durationSec: duration,
    level: number('level', 'difficulty', 'currentLevel', 'current_level')(stats) ?? number('level')(item) ?? number('level')(collection),
    newWordsCount: number('newWordsCount', 'new_words_count', 'newWords', 'new_words')(stats),
    collectionId: collectionId === null ? null : Math.trunc(collectionId),
    collectionTitle: isCourse ? null : text('collectionTitle', 'collection_title')(item) ?? text('title', 'name')(collection),
    isCourse,
    lessonCount: lessonCount !== null && lessonCount > 0 ? lessonCount : null,
    isCompleted: boolean('isCompleted', 'is_completed', 'completed')(stats),
    isTaken: boolean('isTaken', 'is_taken')(item) || boolean('isTaken', 'is_taken')(stats) || (number('status')(item) ?? 0) >= 1,
  }
}

export function parseProfile(value: unknown): Profile {
  const item = unwrapSingle(value)
  const language = record(item.learnLanguage ?? item.learningLanguage ?? item.activeLanguage ?? item.language)
  const languages = array(item.languages).map(record)
  const source = languages.find((entry) => boolean('isActive', 'is_active', 'active')(entry)) ?? languages[0] ?? item
  const first = text('first_name', 'firstName')(item) ?? ''
  const last = text('last_name', 'lastName')(item) ?? ''
  return {
    username: text('username', 'email')(item) ?? 'LingQ',
    displayName: text('displayName', 'display_name', 'fullName', 'full_name', 'name')(item) ??
      ([first, last].filter(Boolean).join(' ') || text('username')(item)) ?? 'LingQ',
    avatarUrl: image(item),
    knownWords: number('knownWords', 'known_words', 'knownWordsCount', 'known_words_count')(source) ??
      number('knownWords', 'known_words', 'knownWordsCount')(item),
    lingqCount: number('lingqsCount', 'lingqs_count', 'lingqCount', 'lingq_count')(source) ??
      number('lingqsCount', 'lingqs_count', 'lingqCount')(item),
    languageCode: text('code', 'languageCode', 'language_code', 'isoCode')(language) ??
      text('languageCode', 'language_code', 'code')(source) ?? text('learnLanguageCode', 'learn_language_code')(item),
    languageName: text('name', 'title', 'languageName')(language) ?? text('languageName', 'language_name')(source),
  }
}

export function parseLanguages(value: unknown): LearningLanguage[] {
  const candidates = Array.isArray(value)
    ? value.map(record)
    : collectObjects(value, (item) => string('code', 'languageCode', 'language_code')(item) !== null)
  const mapped = candidates.flatMap((item) => {
    const rawCode = text('code', 'languageCode', 'language_code')(item)
    if (!rawCode) return []
    const code = rawCode.trim().toLowerCase().split(/[-_]/)[0] || 'en'
    return [{
      code,
      name: text('name', 'title', 'languageName', 'language_name')(item) ?? code.toUpperCase(),
      knownWords: number('knownWords', 'known_words', 'knownWordsCount', 'known_words_count')(item),
      lingqCount: number('lingqsCount', 'lingqs_count', 'lingqCount', 'lingq_count')(item),
      wordsRead: number('wordsRead', 'words_read')(item),
      level: number('level', 'currentLevel', 'current_level')(item),
      isActive: boolean('isActive', 'is_active', 'active', 'selected', 'is_current')(item) || text('status')(item) === 'active',
    }]
  })
  const distinct = [...new Map(mapped.map((item) => [item.code, item])).values()]
  const learning = distinct.filter((item) => item.knownWords !== null || item.level !== null || item.isActive)
  return (learning.length > 0 ? learning : distinct).sort((a, b) =>
    Number(b.isActive) - Number(a.isActive) || a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
}

export function parseLessons(value: unknown): LessonSummary[] {
  return collectObjects(value, looksLikeLesson)
    .map(lesson)
    .filter((item): item is LessonSummary => item !== null)
    .filter((item, index, all) => all.findIndex((other) => other.id === item.id) === index)
}

export function parseShelf(value: unknown, fallbackTitle: string): ShelfSection {
  const item = record(value)
  const lessons = parseLessons(item.lessons ?? item.items ?? item.contents ?? item.results ?? item.data ?? item)
  return { title: text('title', 'name')(item) ?? fallbackTitle, lessons }
}

export function parseShelves(value: unknown): ShelfSection[] {
  const item = record(value)
  const sections = array(item.shelves ?? item.sections)
  const seen = new Set<number>()
  const parsed = sections.length > 0
    ? sections.map((entry, index) => parseShelf(entry, `Shelf ${index + 1}`))
    : [{
      title: 'Continue learning',
      lessons: parseLessons(item.continue_learning ?? item.continueLearning),
    }, {
      title: 'Your lessons',
      lessons: parseLessons(item.lessons ?? item.results ?? item),
    }, {
      title: 'Recently added',
      lessons: parseLessons(item.recent ?? item.recently_added),
    }]
  return parsed.map((section) => ({
    ...section,
    lessons: section.lessons.filter((entry) => {
      if (seen.has(entry.id)) return false
      seen.add(entry.id)
      return true
    }),
  })).filter((section) => section.lessons.length > 0)
}

export function parseProviders(value: unknown): ExploreProvider[] {
  return collectObjects(value, (item) =>
    string('id', 'slug', 'code')(item) !== null &&
    (string('imageUrl', 'image_url', 'image')(item) !== null || text('title', 'name', 'label')(item) !== null))
    .flatMap((item) => {
      const providerId = string('id', 'slug', 'code')(item)
      if (!providerId) return []
      return [{
        id: providerId,
        title: text('title', 'name', 'label')(item) ?? providerId,
        imageUrl: image(item),
      }]
    })
}

export function parseLessonDetail(value: unknown): LessonDetail | null {
  const item = unwrapSingle(value)
  const base = lesson(item.lesson ?? item) ?? lesson(item)
  if (!base) return null
  const text = extractText(item) ?? extractText(item.lesson)
  return { ...base, text }
}

export function parseCourse(value: unknown, fallback?: LessonSummary): CourseDetail | null {
  const item = unwrapSingle(value)
  const collection = record(item.collection)
  const courseId = lessonId(item) ?? id(collection) ?? fallback?.libraryId ?? fallback?.id ?? null
  if (courseId === null) return null
  const lessonsValue = item.lessons
  let lessons = parseLessons(Array.isArray(lessonsValue)
    ? lessonsValue
    : lessonsValue && typeof lessonsValue === 'object'
      ? Object.values(record(lessonsValue))
      : [])
  if (lessons.length === 0) {
    const preview = record(item.lessonPreview ?? item.lesson_preview)
    const previewLesson = lesson(preview)
    const previewId = lessonId(preview) ?? (typeof item.lessonPreview === 'number' ? item.lessonPreview : null)
    if (previewLesson) lessons = [previewLesson]
    else if (previewId !== null && fallback) lessons = [{ ...fallback, id: previewId, isCourse: false, libraryId: courseId }]
  }
  return {
    id: courseId,
    title: text('title', 'name')(item) ?? fallback?.title ?? 'Course',
    description: text('description', 'summary')(item) ?? fallback?.description ?? null,
    imageUrl: image(item) ?? image(collection) ?? fallback?.imageUrl ?? null,
    level: number('level', 'difficulty')(item) ?? number('level')(collection) ?? fallback?.level ?? null,
    lessons,
  }
}

function sentenceItems(value: unknown): unknown[] {
  if (Array.isArray(value)) return value
  const item = record(value)
  for (const key of ['results', 'sentences', 'data']) {
    if (Array.isArray(item[key])) return array(item[key])
  }
  const values = Object.values(item)
  if (values.length > 0 && values.every((entry) =>
    typeof entry === 'string' || text('text', 'sentence', 'content')(record(entry)) !== null)) return values
  let best: unknown[] = []
  const walk = (entry: unknown, depth: number) => {
    if (depth > 8) return
    if (Array.isArray(entry)) {
      if (entry.some((child) => typeof child === 'string' ||
        text('text', 'sentence', 'content')(record(child)) !== null ||
        record(child).tokens !== undefined || record(child).words !== undefined)) {
        if (entry.length > best.length) best = entry
      }
      entry.forEach((child) => walk(child, depth + 1))
    } else if (entry && typeof entry === 'object') {
      Object.entries(record(entry))
        .filter(([key]) => !['tokens', 'words', 'lessonWords'].includes(key))
        .forEach(([, child]) => walk(child, depth + 1))
    }
  }
  walk(value, 0)
  return best
}

function assembledSentence(item: Json, language: string): string {
  const tokensValue = item.tokens ?? item.words ?? item.lessonWords
  const tokens = Array.isArray(tokensValue)
    ? tokensValue.map(record)
    : tokensValue && typeof tokensValue === 'object'
      ? Object.values(record(tokensValue)).map(record)
      : []
  const dictionary = new Set(['zh', 'ja', 'th', 'ko', 'lo', 'km', 'my', 'yue'])
  let result = ''
  for (const token of tokens) {
    const word = text('text', 'word', 'term')(token) ?? ''
    const punctuation = text('punct', 'punctuation')(token) ?? ''
    const separator = result && word && !dictionary.has(language) && !/^\p{P}/u.test(word) ? ' ' : ''
    result += `${separator}${word}${punctuation}`
  }
  return cleanText(result)
}

export function parseSentences(value: unknown, fallbackText: string | null, language = 'en'): ReaderSentence[] {
  const parsed = sentenceItems(value).flatMap((entry, position) => {
    if (typeof entry === 'string') {
      const sentence = cleanText(entry)
      return sentence ? [{ index: position, text: sentence, paragraphStart: false }] : []
    }
    const item = record(entry)
    const sentence = cleanText(text('text', 'sentence', 'content')(item) ?? '') || assembledSentence(item, language)
    if (!sentence) return []
    return [{
      index: position,
      text: sentence,
      paragraphStart: boolean('startParagraph', 'start_paragraph', 'isParagraphStart')(item),
    }]
  })
  if (parsed.length > 0) return parsed.map((sentence, index) => ({ ...sentence, index }))
  const normalized = cleanText(fallbackText ?? '')
  if (!normalized) return []
  const result: ReaderSentence[] = []
  for (const paragraph of normalized.split(/\n\s*\n/)) {
    let first = true
    const sentences = paragraph.match(/[^.!?。！？…]+[.!?。！？…]*/g) ?? [paragraph]
    sentences.map((sentence) => sentence.trim()).filter(Boolean).forEach((sentence) => {
      result.push({ index: result.length, text: sentence, paragraphStart: first })
      first = false
    })
  }
  return result
}

export function parseMeanings(value: unknown): Meaning[] {
  const parsed = objects(value).flatMap((item) => {
    const meaning = text('text', 'translation', 'definition', 'meaning')(item)?.trim()
    if (!meaning) return []
    return [{
      text: meaning,
      locale: text('locale', 'language', 'language_code', 'detected_locale', 'dictionary_locale')(item),
    }]
  })
  if (parsed.length > 0) return [...new Map(parsed.map((item) => [item.text, item])).values()].slice(0, 6)
  const item = record(value)
  const single = text('text', 'translation', 'meaning')(item)?.trim()
  return single ? [{ text: single, locale: text('locale')(item) }] : []
}

export function parseTtsVoices(value: unknown): TtsVoice[] {
  const entries = Array.isArray(value)
    ? value.map(record)
    : collectObjects(value, (item) => array(item.voicesByApp ?? item.voices_by_app).length > 0)
  const options = entries.flatMap((item) => {
    if (boolean('premium')(item) || booleanValue('isSelectable', 'selectable')(item) === false) return []
    const isDefault = array(item.tags).some((tag) => tag === 'default') || boolean('isDefault', 'is_default', 'default')(item)
    return array(item.voicesByApp ?? item.voices_by_app).flatMap((entry) => {
      const voice = record(entry)
      const appName = text('appName', 'app_name', 'app')(voice)
      const name = text('name', 'voice', 'voice_name')(voice)
      return appName && name ? [{ appName, voice: name, isDefault }] : []
    })
  })
  const distinct = [...new Map(options.map((item) => [`${item.appName}:${item.voice}`, item])).values()]
  return distinct.sort((a, b) => Number(b.isDefault) - Number(a.isDefault))
}

export function parseAudioUrl(value: unknown, depth = 0): string | null {
  if (depth > 6) return null
  if (typeof value === 'string') return audioUrl(value)
  if (Array.isArray(value)) {
    for (const child of value) {
      const result = parseAudioUrl(child, depth + 1)
      if (result) return result
    }
    return null
  }
  const item = record(value)
  const direct = audioUrl(text('audio', 'audioUrl', 'audio_url', 'mp3', 'file', 'fileUrl', 'path', 'url')(item))
  if (direct) return direct
  for (const key of ['generated', 'requested', 'data', 'result', 'utterance', 'audio']) {
    const result = parseAudioUrl(item[key], depth + 1)
    if (result) return result
  }
  return null
}
