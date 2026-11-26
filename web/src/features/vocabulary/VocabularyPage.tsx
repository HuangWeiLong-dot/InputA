import { useEffect, useRef, useState } from 'react'
import { Bookmark, Check, Search, Trash2, X } from 'lucide-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../../app/AuthContext'
import { useWords } from '../../app/useWords'
import { LessonCard } from '../../components/LessonCard'
import { EmptyState, LoadingState } from '../../components/Feedback'
import { deleteBookmark, listBookmarks } from '../../storage/db'
import type { WordStatus } from '../../api/types'
import { useI18n } from '../../i18n/useI18n'

export function VocabularyPage() {
  const { t } = useI18n()
  const { api, language, token } = useAuth()
  const { words, loading, setStatus, removeWord } = useWords()
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'saved' ? 'saved' : 'words'
  const rawStatus = params.get('status')
  const statusParam = rawStatus === null ? Number.NaN : Number(rawStatus)
  const filter: WordStatus | 'all' = [-1, 0, 1, 2, 3, 4].includes(statusParam) ? statusParam as WordStatus : 'all'
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<string | null>(null)
  const selectedCloseRef = useRef<HTMLButtonElement>(null)
  const queryClient = useQueryClient()
  const bookmarks = useQuery({
    queryKey: ['bookmarks', language],
    queryFn: () => listBookmarks(language ?? ''),
    enabled: Boolean(language && token),
  })
  const meanings = useQuery({
    queryKey: ['meanings', language, selected],
    queryFn: () => api.getMeanings(language ?? '', selected ?? ''),
    enabled: Boolean(language && token && selected),
  })
  const filtered = words.filter((word) => {
    const matchesStatus = filter === 'all' || (filter >= 1 ? word.status >= 1 : word.status === filter)
    return matchesStatus && word.term.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())
  }).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  const selectedWord = words.find((word) => word.term === selected)

  useEffect(() => {
    if (selected) selectedCloseRef.current?.focus()
  }, [selected])

  function selectTab(next: 'words' | 'saved') {
    const nextParams = new URLSearchParams(params)
    if (next === 'saved') nextParams.set('tab', 'saved')
    else nextParams.delete('tab')
    nextParams.delete('status')
    setParams(nextParams)
    setSelected(null)
  }

  function selectFilter(next: WordStatus | 'all') {
    const nextParams = new URLSearchParams(params)
    if (next === 'all') nextParams.delete('status')
    else nextParams.set('status', String(next))
    setParams(nextParams)
  }

  async function updateStatus(status: WordStatus) {
    if (!selected) return
    await setStatus(selected, status)
    setSelected(null)
  }

  return (
    <div className="page-stack vocabulary-page">
      <div className="page-heading"><div><span className="eyebrow">{t('localProgress')}</span><h1>{t('vocabularyTitle')}</h1><p>{t('vocabularySubtitle')}</p></div></div>
      <div className="segmented-control" role="tablist">
        <button role="tab" aria-selected={tab === 'words'} className={tab === 'words' ? 'active' : ''} onClick={() => selectTab('words')}>{t('navVocabulary')} <span>{words.length}</span></button>
        <button role="tab" aria-selected={tab === 'saved'} className={tab === 'saved' ? 'active' : ''} onClick={() => selectTab('saved')}><Bookmark size={15} />{t('savedLessons')} <span>{bookmarks.data?.length ?? 0}</span></button>
      </div>
      {tab === 'words' ? (
        <>
          <div className="search-form compact-search"><Search size={18} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('searchWords')} aria-label={t('searchWords')} />{query && <button className="icon-button" onClick={() => setQuery('')} aria-label={t('dismiss')}><X size={17} /></button>}</div>
          <div className="filter-chips">
            <button className={filter === 'all' ? 'chip active' : 'chip'} onClick={() => selectFilter('all')}>{t('all')} <span>{words.length}</span></button>
            <button className={filter !== 'all' && filter >= 1 ? 'chip active' : 'chip'} onClick={() => selectFilter(1)}>{t('learning')} <span>{words.filter((word) => word.status >= 1).length}</span></button>
            <button className={filter === 0 ? 'chip active' : 'chip'} onClick={() => selectFilter(0)}>{t('known')} <span>{words.filter((word) => word.status === 0).length}</span></button>
            <button className={filter === -1 ? 'chip active' : 'chip'} onClick={() => selectFilter(-1)}>{t('ignored')} <span>{words.filter((word) => word.status === -1).length}</span></button>
          </div>
          {loading ? <LoadingState /> : filtered.length ? (
            <div className="word-list">
              {filtered.map((word) => (
                <button className="word-row" key={word.term} onClick={() => setSelected(word.term)}>
                  <span className="word-row-term">{word.term}</span>
                  <span className={`status-chip status-${word.status}`}>{word.status === 0 ? t('known') : word.status === -1 ? t('ignored') : t('level', { level: word.status })}</span>
                </button>
              ))}
            </div>
          ) : <EmptyState title={t('emptyVocabulary')} />}
        </>
      ) : bookmarks.isLoading ? <LoadingState /> : bookmarks.data?.length ? (
        <div className="lesson-grid">{bookmarks.data.map((bookmark) => <div className="bookmark-card" key={bookmark.lesson.id}><LessonCard lesson={bookmark.lesson} saved /><button className="icon-button bookmark-remove" aria-label={t('delete')} onClick={async () => {
          await deleteBookmark(language ?? '', bookmark.lessonId)
          await queryClient.invalidateQueries({ queryKey: ['bookmarks', language] })
          await queryClient.invalidateQueries({ queryKey: ['home-local', language] })
        }}><Trash2 size={16} /></button></div>)}</div>
      ) : <EmptyState title={t('savedLessons')} body={t('noResults')} />}
      {selected && selectedWord && (
        <div className="word-panel-backdrop">
          <button className="panel-dismiss" type="button" onClick={() => setSelected(null)} aria-label={t('close')} />
          <section className="word-panel" role="dialog" aria-modal="true" aria-labelledby="vocab-word-title">
            <div className="word-panel-heading"><div><span className="eyebrow">{t('wordStatus')}</span><h2 id="vocab-word-title">{selectedWord.term}</h2></div><button ref={selectedCloseRef} className="icon-button" onClick={() => setSelected(null)} aria-label={t('close')}><X size={19} /></button></div>
            <div className="word-status-actions">
              <button className="status-button known" onClick={() => void updateStatus(0)}><Check size={15} />{t('known')}</button>
              <button className="status-button ignored" onClick={() => void updateStatus(-1)}><X size={15} />{t('ignored')}</button>
            </div>
            <div className="level-buttons">{[1, 2, 3, 4].map((level) => <button key={level} className={selectedWord.status === level ? 'active' : ''} onClick={() => void updateStatus(level as WordStatus)}>{level}</button>)}</div>
            <div className="word-panel-section"><h3>{t('meanings')}</h3>{meanings.isLoading ? <p className="muted">{t('loadingMeanings')}</p> : meanings.data?.length ? <ul className="meaning-list">{meanings.data.map((meaning, index) => <li key={`${meaning.text}-${index}`}>{meaning.text}</li>)}</ul> : <p className="muted">{meanings.isError ? meanings.error.message : t('noMeanings')}</p>}</div>
            <div className="word-panel-footer"><button className="text-button danger" onClick={async () => { await removeWord(selected); setSelected(null) }}><Trash2 size={16} />{t('removeWord')}</button></div>
          </section>
        </div>
      )}
    </div>
  )
}
