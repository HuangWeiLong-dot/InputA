import { z } from 'zod'
import {
  extractText,
  parseAudioUrl,
  parseCourse,
  parseLanguages,
  parseLessonDetail,
  parseLessons,
  parseMeanings,
  parseProfile,
  parseProviders,
  parseSentences,
  parseShelves,
  parseTtsVoices,
} from './parsers'
import type {
  CourseDetail,
  LessonDetail,
  LessonSummary,
  Meaning,
  ReaderSentence,
  TtsVoice,
} from './types'

const loginSchema = z.object({ token: z.string().min(1) }).passthrough()

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

interface ClientOptions {
  getToken: () => string | null
  onUnauthorized: () => void
}

export class LingqClient {
  private readonly prefix = '/api/lingq'

  private readonly options: ClientOptions

  constructor(options: ClientOptions) {
    this.options = options
  }

  private async request<T>(
    path: string,
    parse: (value: unknown) => T,
    init: RequestInit = {},
    timeoutMs?: number,
  ): Promise<T> {
    const token = this.options.getToken()
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Token ${token}`)

    let response: Response
    try {
      response = await fetch(`${this.prefix}${path}`, {
        ...init,
        headers,
        credentials: 'omit',
        signal: timeoutMs ? AbortSignal.timeout(timeoutMs) : init.signal,
      })
    } catch {
      throw new ApiError(0, 'Network error. Please try again.')
    }
    if (response.status === 401) {
      this.options.onUnauthorized()
      throw new ApiError(401, 'Your session has expired. Please sign in again.')
    }
    if (!response.ok) {
      throw new ApiError(response.status, `Request failed (${response.status})`)
    }
    if (response.status === 204) return undefined as T
    const text = await response.text()
    let value: unknown = null
    if (text) {
      try {
        value = JSON.parse(text)
      } catch {
        throw new ApiError(response.status, 'The server returned an invalid response')
      }
    }
    return parse(value)
  }

  private get<T>(path: string, parse: (value: unknown) => T, timeoutMs?: number) {
    return this.request(path, parse, {}, timeoutMs)
  }

  async login(username: string, password: string): Promise<string> {
    const value = await this.request(
      '/api/v2/api-token-auth/',
      (body) => loginSchema.parse(body).token,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ username, password }).toString(),
      },
    )
    return value
  }

  getProfile() {
    return this.get('/api/v3/profiles/', parseProfile)
  }

  getLanguages(username?: string | null) {
    const params = new URLSearchParams()
    if (username) params.set('username', username)
    const query = params.size > 0 ? `?${params.toString()}` : ''
    return this.get(`/api/v2/languages/${query}`, parseLanguages)
  }

  getShelf(language: string) {
    return this.get(`/api/v3/${encodeURIComponent(language)}/shelves/?suppress=true`, parseShelves)
  }

  async search(language: string, query: string | null, provider?: string | null): Promise<LessonSummary[]> {
    const params = new URLSearchParams({ page: '1', page_size: '30', suppress: 'true' })
    if (query?.trim()) params.set('q', query.trim())
    if (provider) params.set('provider', provider)
    return this.get(
      `/api/v3/${encodeURIComponent(language)}/search/?${params.toString()}`,
      parseLessons,
    )
  }

  getProviders(language: string) {
    return this.get(`/api/v3/${encodeURIComponent(language)}/providers/`, parseProviders)
  }

  async getLesson(language: string, id: number): Promise<LessonDetail> {
    const [detail, textResult] = await Promise.all([
      this.get(`/api/v3/${encodeURIComponent(language)}/lessons/${id}/`, parseLessonDetail),
      this.get(`/api/v3/${encodeURIComponent(language)}/lessons/${id}/text/`, extractText).catch(() => null),
    ])
    if (!detail) throw new ApiError(404, 'Lesson not found')
    return { ...detail, text: textResult ?? detail.text }
  }

  async getCourse(language: string, id: number, fallback?: LessonSummary): Promise<CourseDetail> {
    const fromCollection = await this.get(
      `/api/v3/${encodeURIComponent(language)}/collections/${id}/`,
      (value) => parseCourse(value, fallback),
    ).catch(() => null)
    let course = fromCollection ?? await this.get(
      `/api/v3/${encodeURIComponent(language)}/lessons/${id}/`,
      (value) => parseCourse(value, fallback),
    ).catch(() => null)
    if (!course && fallback) course = parseCourse(null, fallback)
    if (!course) throw new ApiError(404, 'Course not found')
    if (course.lessons.length === 0) {
      const params = new URLSearchParams({ collection: String(id), page: '1', page_size: '100', suppress: 'true' })
      const lessons = await this.get(
        `/api/v3/${encodeURIComponent(language)}/search/?${params.toString()}`,
        parseLessons,
      ).catch(() => [])
      course = { ...course, lessons }
    }
    return course
  }

  getCollection(language: string, id: number) {
    return this.get(`/api/v3/${encodeURIComponent(language)}/collections/${id}/`, parseCourse)
  }

  async getSentences(language: string, id: number, fallbackText: string | null): Promise<ReaderSentence[]> {
    let failure: unknown = null
    const sentences = await this.get(
      `/api/v3/${encodeURIComponent(language)}/lessons/${id}/sentences/`,
      (value) => parseSentences(value, fallbackText, language),
    ).catch((error: unknown) => {
      failure = error
      return []
    })
    if (sentences.length > 0) return sentences
    const fallback = parseSentences(null, fallbackText, language)
    if (fallback.length > 0) return fallback
    if (failure) throw failure
    return []
  }

  async getMeanings(language: string, term: string): Promise<Meaning[]> {
    const params = new URLSearchParams({ term, all: 'true', locale: 'en' })
    try {
      return await this.get(
        `/api/v2/${encodeURIComponent(language)}/hints/search/?${params.toString()}`,
        parseMeanings,
      )
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) throw error
      params.delete('all')
      params.delete('locale')
      return this.get(
        `/api/v2/${encodeURIComponent(language)}/hints/search/?${params.toString()}`,
        parseMeanings,
      )
    }
  }

  getTtsVoices(language: string): Promise<TtsVoice[]> {
    return this.get(
      `/api/v2/tts/supported-voices?language=${encodeURIComponent(language)}`,
      parseTtsVoices,
      4_000,
    ).catch(() => [])
  }

  async getTtsUrl(language: string, text: string, voice?: TtsVoice): Promise<string> {
    const trimmed = text.trim()
    const wordCount = trimmed.split(/\s+/).filter(Boolean).length
    const type = wordCount <= 1 ? 'word' : wordCount <= 4 ? 'phrase' : 'sentence'
    const params = new URLSearchParams({ text: trimmed, type })
    if (voice) {
      params.set('app_name', voice.appName)
      params.set('voice', voice.voice)
    }
    const value = await this.request<unknown>(
      `/api/v3/${encodeURIComponent(language)}/tts/?${params.toString()}`,
      (body) => body,
      {},
      3_500,
    )
    const url = parseAudioUrl(value)
    if (!url) throw new ApiError(502, 'Audio is unavailable')
    return url
  }
}
