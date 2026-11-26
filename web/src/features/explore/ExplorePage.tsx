import { useState, type FormEvent } from 'react'
import { Search, X } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../app/AuthContext'
import { LessonCard } from '../../components/LessonCard'
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback'
import { useI18n } from '../../i18n/useI18n'

export function ExplorePage() {
  const { t } = useI18n()
  const { api, language, token } = useAuth()
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [provider, setProvider] = useState<string | null>(null)
  const providers = useQuery({
    queryKey: ['providers', language],
    queryFn: () => api.getProviders(language ?? ''),
    enabled: Boolean(language && token),
  })
  const results = useQuery({
    queryKey: ['search', language, query, provider],
    queryFn: async () => {
      if (query || provider) return api.search(language ?? '', query || null, provider)
      return (await api.getShelf(language ?? '')).flatMap((section) => section.lessons)
    },
    enabled: Boolean(language && token),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    setQuery(input.trim())
  }
  function clear() {
    setInput('')
    setQuery('')
    setProvider(null)
  }

  return (
    <div className="page-stack explore-page">
      <div className="page-heading"><div><span className="eyebrow">LingQ</span><h1>{t('navExplore')}</h1></div></div>
      <form className="search-form" onSubmit={submit} role="search">
        <Search size={20} />
        <input value={input} onChange={(event) => setInput(event.target.value)} placeholder={t('searchLessons')} aria-label={t('search')} />
        {(input || query) && <button type="button" className="icon-button" onClick={clear} aria-label={t('dismiss')}><X size={18} /></button>}
        <button className="button primary" type="submit">{t('search')}</button>
      </form>
      <p className="muted">{t('searchHint')}</p>
      <div className="filter-chips" aria-label={t('providers')}>
        <button className={provider === null ? 'chip active' : 'chip'} onClick={() => setProvider(null)}>{t('allProviders')}</button>
        {(providers.data ?? []).map((item) => (
          <button key={item.id} className={provider === item.id ? 'chip active' : 'chip'} onClick={() => setProvider(item.id)}>{item.title}</button>
        ))}
      </div>
      <div className="section-heading"><h2>{query ? t('results') : t('browseLessons')}</h2></div>
      {results.isLoading ? <LoadingState /> : results.isError ? <ErrorState message={results.error.message} onRetry={() => results.refetch()} /> : results.data?.length ? (
        <div className="lesson-grid">{(results.data ?? []).map((lesson) => <LessonCard key={lesson.id} lesson={lesson} />)}</div>
      ) : <EmptyState title={t('noResults')} body={query} />}
    </div>
  )
}
