import { useCallback, useEffect, useRef, useState } from 'react'
import type { LingqClient } from '../api/client'
import type { TtsVoice } from '../api/types'
import { useI18n } from '../i18n/useI18n'

export function useLessonAudio(api: LingqClient, language: string | null) {
  const { t } = useI18n()
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const voicesRef = useRef(new Map<string, TtsVoice[]>())
  const preferredVoiceRef = useRef(new Map<string, TtsVoice>())
  const [playing, setPlaying] = useState(false)
  const [duration, setDuration] = useState(0)
  const [position, setPosition] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)

  const stop = useCallback(() => {
    audioRef.current?.pause()
    audioRef.current = null
    setPlaying(false)
    setDuration(0)
    setPosition(0)
  }, [])

  const playUrl = useCallback((url: string) => {
    stop()
    const audio = new Audio(url)
    audio.preload = 'metadata'
    audio.onloadedmetadata = () => setDuration(Number.isFinite(audio.duration) ? audio.duration : 0)
    audio.ontimeupdate = () => setPosition(audio.currentTime)
    audio.onended = () => { setPlaying(false); setPosition(0) }
    audio.onerror = () => { setPlaying(false); setNotice(t('audioError')) }
    audioRef.current = audio
    void audio.play().then(() => setPlaying(true)).catch(() => setNotice(t('audioError')))
  }, [stop, t])

  const toggle = useCallback((url: string | null) => {
    if (audioRef.current) {
      if (audioRef.current.paused) void audioRef.current.play().then(() => setPlaying(true)).catch(() => undefined)
      else audioRef.current.pause()
      return
    }
    if (url) playUrl(url)
    else setNotice(t('noAudio'))
  }, [playUrl, t])

  const seek = useCallback((value: number) => {
    const audio = audioRef.current
    if (!audio || !Number.isFinite(audio.duration)) return
    audio.currentTime = Math.max(0, Math.min(1, value)) * audio.duration
    setPosition(audio.currentTime)
  }, [])

  const pronounce = useCallback(async (text: string) => {
    if (!language || !text.trim()) return
    let voices = voicesRef.current.get(language)
    if (!voices) {
      voices = await api.getTtsVoices(language)
      voicesRef.current.set(language, voices)
    }
    const preferred = preferredVoiceRef.current.get(language)
    const options = [...new Map([preferred, ...voices]
      .filter((voice): voice is TtsVoice => voice !== undefined)
      .map((voice) => [`${voice.appName}:${voice.voice}`, voice])).values()].slice(0, 3)
    for (const voice of options.length > 0 ? options : [undefined]) {
      try {
        const url = await api.getTtsUrl(language, text, voice)
        const audio = new Audio(url)
        await audio.play()
        if (voice) preferredVoiceRef.current.set(language, voice)
        return
      } catch {
        // 服务端音色都失败后再回退系统发音，避免点词没有反馈。
      }
    }
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel()
      const utterance = new SpeechSynthesisUtterance(text)
      utterance.lang = language
      window.speechSynthesis.speak(utterance)
    }
  }, [api, language])

  useEffect(() => () => {
    audioRef.current?.pause()
    window.speechSynthesis?.cancel()
  }, [])
  return { playing, duration, position, notice, toggle, seek, pronounce, stop, clearNotice: () => setNotice(null) }
}

export function formatTime(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  return `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`
}
