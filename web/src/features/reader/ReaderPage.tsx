import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { ArrowLeft, Check, ChevronLeft, ChevronRight, Pause, Play, Trash2, Volume2, X } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../app/AuthContext'
import { usePreferences } from '../../app/PreferencesContext'
import { useLessonAudio, formatTime } from '../../app/useLessonAudio'
import { useWords } from '../../app/useWords'
import { ErrorState, LoadingState } from '../../components/Feedback'
import { tokenize, type Token } from '../../domain/tokenize'
import type { Meaning, ReaderSentence, WordStatus } from '../../api/types'
import type { Preferences } from '../../storage/preferences'
import { cacheLesson, getCachedLesson, getLastLesson, putLastLesson } from '../../storage/db'
import { useI18n } from '../../i18n/useI18n'

type ReaderModeName = Preferences['readerMode']
interface WordSelection {
  sentenceIndex: number
  tokenIndex: number
}

interface WordPanelState {
  selection: WordSelection
  term: string
  context: string
  meanings: Meaning[]
  loading: boolean
}

export function ReaderPage() {
  const { id = '' } = useParams()
  const { language } = useAuth()
  return <ReaderSession key={`${language ?? ''}:${id}`} lessonId={Number(id)} />
}

function ReaderSession({ lessonId }: { lessonId: number }) {
  const { t } = useI18n()
  const { api, language, token } = useAuth()
  const { preferences, update } = usePreferences()
  const { wordMap, setStatus, removeWord } = useWords()
  const audio = useLessonAudio(api, language)
  const [mode, setMode] = useState<ReaderModeName>(preferences.readerMode)
  const [page, setPage] = useState(0)
  const [sentenceIndex, setSentenceIndex] = useState(0)
  const [pageBreaks, setPageBreaks] = useState<{ first: number; last: number }[]>([])
  const [panel, setPanel] = useState<WordPanelState | null>(null)
  const [finished, setFinished] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const viewportRef = useRef<HTMLDivElement>(null)
  const measureRef = useRef<HTMLDivElement>(null)
  const generationRef = useRef(0)
  const meaningCache = useRef(new Map<string, Meaning[]>())
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const finishButtonRef = useRef<HTMLButtonElement>(null)

  const query = useQuery({
    queryKey: ['reader', language, lessonId],
    queryFn: async () => {
      const cached = await getCachedLesson(language ?? '', lessonId).catch(() => null)
      try {
        const lesson = await api.getLesson(language ?? '', lessonId)
        const sentences = await api.getSentences(language ?? '', lessonId, lesson.text)
        if (sentences.length === 0) throw new Error(t('emptyLesson'))
        const withText = { ...lesson, text: sentences.map((sentence) => sentence.text).join('\n') }
        void cacheLesson(language ?? '', withText).catch(() => undefined)
        return { lesson: withText, sentences, cached: false }
      } catch (error) {
        if (cached?.text) {
          const sentences = await api.getSentences(language ?? '', lessonId, cached.text)
          return { lesson: cached, sentences, cached: true }
        }
        throw error
      }
    },
    enabled: Boolean(language && token && Number.isInteger(lessonId)),
  })

  const lesson = query.data?.lesson
  const sentences = useMemo(() => query.data?.sentences ?? [], [query.data])
  const pageCount = pageBreaks.length || Math.max(1, Math.ceil(sentences.length / 8))
  const visibleSentences = mode === 'scroll'
    ? sentences
    : mode === 'sentence'
      ? sentences.slice(sentenceIndex, sentenceIndex + 1)
      : (() => {
        const range = pageBreaks[page]
        return range ? sentences.slice(range.first, range.last + 1) : sentences.slice(page * 8, page * 8 + 8)
      })()

  useEffect(() => {
    if (!language || !Number.isInteger(lessonId)) return
    void getLastLesson(language).then((last) => {
      if (last?.lesson.id === lessonId) setSentenceIndex(last.sentenceIndex)
    })
  }, [language, lessonId])

  useEffect(() => {
    if (!lesson || !language) return
    const range = pageBreaks[page]
    const currentIndex = mode === 'page' ? range?.first ?? page * 8 : sentenceIndex
    void putLastLesson(language, lesson, currentIndex)
  }, [language, lesson, mode, page, pageBreaks, sentenceIndex])

  useLayoutEffect(() => {
    if (mode !== 'page' || !viewportRef.current || !measureRef.current || sentences.length === 0) return
    const measure = () => {
      const viewport = viewportRef.current
      const layer = measureRef.current
      if (!viewport || !layer) return
      const maxHeight = viewport.clientHeight
      const gap = Number.parseFloat(getComputedStyle(layer).rowGap) || 0
      const breaks: { first: number; last: number }[] = []
      let first = 0
      let height = 0
      Array.from(layer.children).forEach((element, index) => {
        const itemHeight = (element as HTMLElement).offsetHeight
        if (index > first && height + gap + itemHeight > maxHeight) {
          breaks.push({ first, last: index - 1 })
          first = index
          height = itemHeight
        } else {
          height += itemHeight + (index > first ? gap : 0)
        }
      })
      if (first < sentences.length) breaks.push({ first, last: sentences.length - 1 })
      setPageBreaks((current) => JSON.stringify(current) === JSON.stringify(breaks) ? current : breaks)
    }
    measure()
    const observer = new ResizeObserver(measure)
    if (viewportRef.current) observer.observe(viewportRef.current)
    window.addEventListener('resize', measure)
    return () => { observer.disconnect(); window.removeEventListener('resize', measure) }
  }, [mode, preferences.fontScale, sentences])

  useEffect(() => {
    if (pageBreaks.length === 0) return
    const frame = requestAnimationFrame(() => {
      const target = pageBreaks.findIndex((range) => sentenceIndex >= range.first && sentenceIndex <= range.last)
      setPage(target >= 0 ? target : 0)
    })
    return () => cancelAnimationFrame(frame)
  }, [pageBreaks, sentenceIndex])

  function closePanel() {
    generationRef.current += 1
    setPanel(null)
  }

  useEffect(() => {
    if (panel) closeButtonRef.current?.focus()
  }, [panel])

  useEffect(() => {
    if (finished) finishButtonRef.current?.focus()
  }, [finished])

  useEffect(() => {
    function keydown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      if (target?.matches('input, textarea, select')) return
      if (event.key === 'Escape') { closePanel(); setFinished(false); return }
      if (panel || finished) return
      if (event.key === 'ArrowRight') navigate(1)
      if (event.key === 'ArrowLeft') navigate(-1)
    }
    window.addEventListener('keydown', keydown)
    return () => window.removeEventListener('keydown', keydown)
  })

  function navigate(direction: number) {
    if (mode === 'scroll') return
    if (mode === 'page') setPage((current) => Math.max(0, Math.min(pageCount - 1, current + direction)))
    else setSentenceIndex((current) => Math.max(0, Math.min(sentences.length - 1, current + direction)))
    closePanel()
  }

  function changeMode(next: ReaderModeName) {
    update({ readerMode: next })
    setMode(next)
    closePanel()
  }

  async function selectWord(selection: WordSelection) {
    const sentence = sentences[selection.sentenceIndex]
    if (!sentence || !language) return
    const segments = tokenize(sentence.text, language)
    const selected = segments[selection.tokenIndex]?.text ?? ''
    const term = selected.trim()
    if (!term) return
    const key = term.toLocaleLowerCase()
    const requestGeneration = ++generationRef.current
    const cached = meaningCache.current.get(key)
    setPanel({ selection, term, context: sentence.text, meanings: cached ?? [], loading: !cached })
    if (preferences.autoCreateLingq && wordMap.get(key) === undefined) void setStatus(term, 1)
    if (preferences.pronounceOnTap) void audio.pronounce(term)
    if (cached) return
    try {
      const meanings = await api.getMeanings(language, term)
      if (meanings.length > 0) meaningCache.current.set(key, meanings)
      if (generationRef.current === requestGeneration) {
        setPanel((current) => current?.selection === selection ? { ...current, meanings, loading: false } : current)
      }
    } catch {
      if (generationRef.current === requestGeneration) {
        setPanel((current) => current?.selection === selection ? { ...current, loading: false } : current)
      }
    }
  }

  async function updateStatus(status: WordStatus) {
    if (!panel) return
    await setStatus(panel.term, status)
    closePanel()
  }

  if (query.isLoading) return <LoadingState />
  if (query.isError || !query.data) return <ErrorState message={query.error?.message} onRetry={() => query.refetch()} />

  const readerStyle = { '--reader-scale': preferences.fontScale } as CSSProperties
  const position = mode === 'page' ? page : sentenceIndex
  const total = mode === 'page' ? pageCount : sentences.length

  return (
    <div className="reader-page" style={readerStyle}>
      <header className="reader-header">
        <Link className="icon-button" to={`/lesson/${lessonId}`} aria-label={t('back')}><ArrowLeft size={19} /></Link>
        <div className="reader-title"><strong>{lesson?.title}</strong><span>{lesson?.collectionTitle}</span></div>
        <div className="reader-modes" aria-label={t('readerMode')}>
          {(['page', 'sentence', 'scroll'] as const).map((item) => (
            <button key={item} className={mode === item ? 'active' : ''} onClick={() => changeMode(item)}>{t(item === 'page' ? 'pageMode' : item === 'sentence' ? 'sentenceMode' : 'scrollMode')}</button>
          ))}
        </div>
      </header>
      <div className="reader-toolbar">
        <div className="audio-controls">
          <button className="icon-button" onClick={() => audio.toggle(lesson?.audioUrl ?? null)} aria-label={audio.playing ? t('pause') : t('play')}>
            {audio.playing ? <Pause size={17} /> : <Play size={17} />}
          </button>
          <span>{formatTime(audio.position)}</span>
          <input type="range" min="0" max="1" step="0.01" value={audio.duration ? audio.position / audio.duration : 0} onChange={(event) => audio.seek(Number(event.target.value))} aria-label={t('audio')} />
          <span>{formatTime(audio.duration)}</span>
        </div>
        <button className="button secondary small" onClick={() => setFinished(true)}><Check size={15} />{t('finishLesson')}</button>
      </div>
      {notice && <div className="reader-notice" role="status">{notice}<button className="icon-button" onClick={() => setNotice(null)} aria-label={t('dismiss')}><X size={15} /></button></div>}
      <div className={`reader-body mode-${mode}`} ref={viewportRef}>
        {mode === 'page' && (
          <div className="measure-layer" ref={measureRef} aria-hidden="true">
            {sentences.map((sentence, index) => <SentenceContent key={sentence.index} sentence={sentence} sentenceIndex={index} language={language ?? 'en'} wordMap={wordMap} onSelect={() => undefined} measure />)}
          </div>
        )}
        <div className="sentence-layer">
          {visibleSentences.map((sentence) => (
            <SentenceContent key={sentence.index} sentence={sentence} sentenceIndex={sentence.index} language={language ?? 'en'} wordMap={wordMap} onSelect={selectWord} />
          ))}
        </div>
      </div>
      {mode !== 'scroll' && (
        <footer className="reader-footer">
          <button className="button secondary" onClick={() => navigate(-1)} disabled={position <= 0}><ChevronLeft size={17} />{t('previous')}</button>
          <span>{mode === 'page' ? t('pageOf', { current: page + 1, total: pageCount }) : t('sentenceOf', { current: sentenceIndex + 1, total: sentences.length })}</span>
          <button className="button secondary" onClick={() => navigate(1)} disabled={position >= total - 1}>{t('next')}<ChevronRight size={17} /></button>
        </footer>
      )}
      {panel && (
        <div className="word-panel-backdrop">
          <button className="panel-dismiss" type="button" onClick={closePanel} aria-label={t('close')} />
          <section className="word-panel" role="dialog" aria-modal="true" aria-labelledby="word-panel-title">
            <div className="word-panel-heading">
              <div><span className="eyebrow">{t('wordStatus')}</span><h2 id="word-panel-title">{panel.term}</h2></div>
              <button ref={closeButtonRef} className="icon-button" onClick={closePanel} aria-label={t('close')}><X size={19} /></button>
            </div>
            <p className="word-context">{panel.context}</p>
            <div className="word-status-actions">
              <button className={wordMap.get(panel.term.toLocaleLowerCase()) === 0 ? 'status-button known active' : 'status-button known'} onClick={() => void updateStatus(0)}><Check size={15} />{t('known')}</button>
              <button className={wordMap.get(panel.term.toLocaleLowerCase()) === -1 ? 'status-button ignored active' : 'status-button ignored'} onClick={() => void updateStatus(-1)}><X size={15} />{t('ignored')}</button>
            </div>
            <div className="level-buttons" aria-label={t('learning')}>
              {[1, 2, 3, 4].map((level) => <button key={level} className={wordMap.get(panel.term.toLocaleLowerCase()) === level ? 'active' : ''} onClick={() => void updateStatus(level as WordStatus)}>{level}</button>)}
            </div>
            <div className="word-panel-section">
              <h3>{t('meanings')}</h3>
              {panel.loading ? <p className="muted">{t('loadingMeanings')}</p> : panel.meanings.length ? (
                <ul className="meaning-list">{panel.meanings.map((meaning, index) => <li key={`${meaning.text}-${index}`}>{meaning.text}{meaning.locale && <span>{meaning.locale}</span>}</li>)}</ul>
              ) : <p className="muted">{t('noMeanings')}</p>}
            </div>
            <div className="word-panel-footer">
              <button className="text-button" onClick={() => void audio.pronounce(panel.term)}><Volume2 size={16} />{t('play')}</button>
              <button className="text-button danger" onClick={() => { void removeWord(panel.term); closePanel() }}><Trash2 size={16} />{t('removeWord')}</button>
            </div>
          </section>
        </div>
      )}
      {finished && (
        <div className="word-panel-backdrop">
          <section className="word-panel finish-panel" role="dialog" aria-modal="true" aria-labelledby="finish-title">
            <span className="finish-icon"><Check size={28} /></span>
            <h2 id="finish-title">{t('finishedTitle')}</h2>
            <p>{t('finishedBody')}</p>
            <button ref={finishButtonRef} className="button primary" onClick={() => setFinished(false)}>{t('keepReading')}</button>
            <Link className="button secondary" to="/">{t('navHome')}</Link>
          </section>
        </div>
      )}
    </div>
  )
}

function SentenceContent({ sentence, sentenceIndex, language, wordMap, onSelect, measure = false }: {
  sentence: ReaderSentence
  sentenceIndex: number
  language: string
  wordMap: Map<string, WordStatus>
  onSelect: (selection: WordSelection) => void
  measure?: boolean
}) {
  const tokens = useMemo(() => tokenize(sentence.text, language), [language, sentence.text])
  let tokenIndex = 0
  return (
    <p className="reader-sentence">
      {tokens.map((token: Token, index: number) => {
        const current = tokenIndex++
        if (token.kind !== 'word') return <span key={index} className={`token-${token.kind}`}>{token.text}</span>
        const status = wordMap.get(token.text.toLocaleLowerCase())
        return (
          <button
            key={index}
            type="button"
            tabIndex={measure ? -1 : 0}
            className={`word-token${status === 0 ? ' known' : status === -1 ? ' ignored' : status ? ' learning' : ''}`}
            onClick={() => onSelect({ sentenceIndex, tokenIndex: current })}
            aria-label={token.text}
          >{token.text}</button>
        )
      })}
    </p>
  )
}
