import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { LingqClient } from '../api/client'
import type { LearningLanguage, Profile } from '../api/types'
import { loadLearningLanguage, saveLearningLanguage } from '../storage/preferences'

interface AuthValue {
  token: string | null
  profile: Profile | null
  languages: LearningLanguage[]
  language: string | null
  accountLoading: boolean
  api: LingqClient
  signIn: (username: string, password: string) => Promise<void>
  signInWithToken: (token: string) => Promise<void>
  selectLanguage: (language: string) => void
  signOut: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState<string | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [languages, setLanguages] = useState<LearningLanguage[]>([])
  const [language, setLanguage] = useState<string | null>(null)
  const [accountLoading, setAccountLoading] = useState(false)

  const signOut = useCallback(() => {
    queryClient.clear()
    setToken(null)
    setProfile(null)
    setLanguages([])
    setLanguage(null)
    setAccountLoading(false)
  }, [queryClient])

  const api = useMemo(() => new LingqClient({
    getToken: () => token,
    onUnauthorized: signOut,
  }), [signOut, token])

  const activate = useCallback(async (newToken: string) => {
    queryClient.clear()
    const authClient = new LingqClient({ getToken: () => newToken, onUnauthorized: signOut })
    setToken(newToken)
    setAccountLoading(true)
    try {
      const nextProfile = await authClient.getProfile()
      const nextLanguages = await authClient.getLanguages(nextProfile.username).catch(() => [])
      const saved = loadLearningLanguage()
      const profileLanguage = nextProfile.languageCode?.trim().toLowerCase().split(/[-_]/)[0]
      const preferred = nextLanguages.find((item) => item.isActive)?.code
        ?? profileLanguage
        ?? saved
        ?? nextLanguages[0]?.code
        ?? 'en'
      const resolved = nextLanguages.some((item) => item.code === preferred) || nextLanguages.length === 0
        ? preferred
        : nextLanguages[0]?.code ?? preferred
      setProfile(nextProfile)
      setLanguages(nextLanguages)
      setLanguage(resolved)
      saveLearningLanguage(resolved)
    } catch (error) {
      signOut()
      throw error
    } finally {
      setAccountLoading(false)
    }
  }, [queryClient, signOut])

  const signIn = useCallback(async (username: string, password: string) => {
    const newToken = await api.login(username, password)
    await activate(newToken)
  }, [activate, api])

  const signInWithToken = useCallback(async (newToken: string) => {
    await activate(newToken.trim())
  }, [activate])

  const selectLanguage = useCallback((next: string) => {
    queryClient.clear()
    setLanguage(next)
    saveLearningLanguage(next)
  }, [queryClient])

  const value = useMemo<AuthValue>(() => ({
    token,
    profile,
    languages,
    language,
    accountLoading,
    api,
    signIn,
    signInWithToken,
    selectLanguage,
    signOut,
  }), [accountLoading, api, language, languages, profile, selectLanguage, signIn, signInWithToken, signOut, token])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
