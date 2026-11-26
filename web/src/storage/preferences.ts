import { z } from 'zod'

export const preferenceSchema = z.object({
  appLanguage: z.enum(['en', 'zh']),
  autoCreateLingq: z.boolean(),
  pronounceOnTap: z.boolean(),
  readerMode: z.enum(['page', 'sentence', 'scroll']),
  fontScale: z.number().min(0.85).max(1.35),
})

export type Preferences = z.infer<typeof preferenceSchema>

export const defaultPreferences: Preferences = {
  appLanguage: 'en',
  autoCreateLingq: true,
  pronounceOnTap: false,
  readerMode: 'page',
  fontScale: 1,
}

export function loadPreferences(): Preferences {
  try {
    const raw = localStorage.getItem('inputa.preferences')
    if (!raw) return defaultPreferences
    const parsed = preferenceSchema.partial().safeParse(JSON.parse(raw))
    return parsed.success ? { ...defaultPreferences, ...parsed.data } : defaultPreferences
  } catch {
    return defaultPreferences
  }
}

export function savePreferences(preferences: Preferences) {
  localStorage.setItem('inputa.preferences', JSON.stringify(preferences))
}

export function loadLearningLanguage(): string | null {
  return localStorage.getItem('inputa.language')
}

export function saveLearningLanguage(language: string) {
  localStorage.setItem('inputa.language', language)
}
