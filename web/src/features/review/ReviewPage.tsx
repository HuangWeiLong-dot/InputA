import { useState } from 'react'
import { ArrowRight, RotateCcw, Volume2 } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../app/AuthContext'
import { useLessonAudio } from '../../app/useLessonAudio'
import { useWords } from '../../app/useWords'
import { EmptyState, LoadingState } from '../../components/Feedback'
import { reviewAnswer, reviewQueue } from '../../domain/words'
import { useI18n } from '../../i18n/useI18n'

export function ReviewPage() {
  const { t } = useI18n()
  const { words, loading } = useWords()
  if (loading) return <LoadingState />
  return words.length > 0 ? <ReviewSession words={words} /> : <EmptyState title={t('emptyVocabulary')} />
}

function ReviewSession({ words }: { words: ReturnType<typeof useWords>['words'] }) {
  const { t } = useI18n()
  const { api, language, token } = useAuth()
  const { setStatus } = useWords()
  const audio = useLessonAudio(api, language)
  const [queue, setQueue] = useState(() => reviewQueue(words).map((word) => word.term))
  const [index, setIndex] = useState(0)
  const [revealed, setRevealed] = useState(false)
  const [reviewed, setReviewed] = useState(0)
  const [complete, setComplete] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(false)
  const term = queue[index]
  const meanings = useQuery({
    queryKey: ['review-meanings', language, term],
    queryFn: () => api.getMeanings(language ?? '', term),
    enabled: Boolean(revealed && term && language && token),
  })

  async function answer(remembered: boolean) {
    if (!term || saving) return
    setSaving(true)
    setError(false)
    try {
      const word = words.find((item) => item.term === term)
      if (word) await setStatus(term, reviewAnswer(word.status, remembered))
      setReviewed((count) => count + 1)
      setRevealed(false)
      if (index + 1 >= queue.length) setComplete(true)
      else setIndex((current) => current + 1)
    } catch {
      setError(true)
    } finally {
      setSaving(false)
    }
  }

  function restart() {
    setQueue(reviewQueue(words).map((word) => word.term))
    setIndex(0)
    setReviewed(0)
    setRevealed(false)
    setComplete(false)
  }

  if (queue.length === 0) return <EmptyState title={t('reviewEmpty')} />
  if (complete) return (
    <div className="review-complete">
      <span className="finish-icon"><RotateCcw size={28} /></span>
      <h1>{t('reviewComplete')}</h1>
      <p>{t('reviewCompleteBody', { count: reviewed })}</p>
      <div className="button-row"><button className="button primary" onClick={restart}><RotateCcw size={17} />{t('reviewNow')}</button></div>
    </div>
  )

  return (
    <div className="review-page">
      <div className="page-heading"><div><span className="eyebrow">{t('localProgress')}</span><h1>{t('reviewTitle')}</h1><p>{t('reviewSubtitle')}</p></div><span className="review-counter">{index + 1} / {queue.length}</span></div>
      <div className="review-progress"><span style={{ width: `${index / queue.length * 100}%` }} /></div>
      <section className={`flashcard${revealed ? ' revealed' : ''}`} aria-live="polite">
        <span className="eyebrow">{t('learning')}</span>
        <h2>{term}</h2>
        <button className="icon-button pronounce-button" onClick={() => void audio.pronounce(term)} aria-label={t('play')}><Volume2 size={20} /></button>
        <div className="flashcard-meaning">
          {revealed ? (
            meanings.isLoading ? <p>{t('loadingMeanings')}</p> : meanings.data?.length ? (
              <ul>{meanings.data.map((meaning, meaningIndex) => <li key={`${meaning.text}-${meaningIndex}`}>{meaning.text}</li>)}</ul>
            ) : <p>{meanings.isError ? meanings.error.message : t('noMeanings')}</p>
          ) : <button className="button secondary" onClick={() => setRevealed(true)}>{t('showMeaning')}<ArrowRight size={16} /></button>}
        </div>
      </section>
      {error && <p className="form-error" role="alert">{t('retry')}</p>}
      {revealed && <div className="review-actions"><button className="button secondary large" disabled={saving} onClick={() => void answer(false)}>{t('again')}</button><button className="button primary large" disabled={saving} onClick={() => void answer(true)}>{t('remembered')}</button></div>}
    </div>
  )
}
