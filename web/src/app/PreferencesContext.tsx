import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { defaultPreferences, loadPreferences, savePreferences, type Preferences } from '../storage/preferences'

interface PreferencesValue {
  preferences: Preferences
  update: (patch: Partial<Preferences>) => void
  reset: () => void
}

const PreferencesContext = createContext<PreferencesValue | null>(null)

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [preferences, setPreferences] = useState(loadPreferences)
  const value = useMemo<PreferencesValue>(() => ({
    preferences,
    update(patch) {
      setPreferences((current) => {
        const next = { ...current, ...patch }
        savePreferences(next)
        return next
      })
    },
    reset() {
      setPreferences(defaultPreferences)
      savePreferences(defaultPreferences)
    },
  }), [preferences])

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>
}

export function usePreferences() {
  const value = useContext(PreferencesContext)
  if (!value) throw new Error('usePreferences must be used inside PreferencesProvider')
  return value
}
