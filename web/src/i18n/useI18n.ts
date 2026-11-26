import { useCallback, useEffect } from 'react'
import en from './en.json'
import zh from './zh.json'
import { usePreferences } from '../app/PreferencesContext'

const messages = { en, zh }

export function useI18n() {
  const { preferences } = usePreferences()
  const language = preferences.appLanguage
  const t = useCallback((key: keyof typeof en, values?: Record<string, string | number>) => {
    let text: string = messages[language][key] ?? en[key] ?? key
    if (values) {
      for (const [name, value] of Object.entries(values)) {
        text = text.replaceAll(`{${name}}`, String(value))
      }
    }
    return text
  }, [language])
  useEffect(() => {
    document.documentElement.lang = language === 'zh' ? 'zh-CN' : 'en'
  }, [language])
  return { t, language }
}
