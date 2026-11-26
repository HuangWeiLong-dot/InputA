import { ArrowLeft, Bookmark, BookmarkCheck, Clock, Play } from 'lucide-react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../app/AuthContext'
import { useWords } from '../../app/useWords'
import { LessonImage, formatDuration } from '../../components/LessonCard'
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback'
import { lessonStats } from '../../domain/words'
import { cacheLesson, deleteBookmark, getCachedLesson, listBookmarks, putBookmark } from '../../storage/db'
import { useI18n } from '../../i18n/useI18n'

export function LessonPage() {
  const { id = '' } = useParams()
  const { t } = useI18n()
  const { api, language, token } = useAuth()
  const { wordMap } = useWords()
  const queryClient = useQueryClient()
  const lessonId = Number(id)
  const query = useQuery({
    queryKey: ['lesson', language, lessonId],
    queryFn: async () => {
      const cached = await getCachedLesson(language ?? '', lessonId).catch(() => null)
      try {
        const lesson = await api.getLesson(language ?? '', lessonId)
        void cacheLesson(language ?? '', lesson).catch(() => undefined)
        return { lesson, cached: false }
      } catch (error) {
        if (cached) return { lesson: cached, cached: true }
        throw error
      }
    },
    enabled: Boolean(language && token && Number.isInteger(lessonId)),
  })
  const bookmarks = useQuery({
    queryKey: ['bookmarks', language],
    queryFn: () => listBookmarks(language ?? ''),
    enabled: Boolean(language && token),
  })
  if (query.isLoading) return <LoadingState />
  if (query.isError || !query.data) return <ErrorState message={query.error?.message} onRetry={() => query.refetch()} />
  const { lesson, cached } = query.data
  if (lesson.isCourse) return <Navigate to={`/course/${lesson.libraryId}`} replace state={{ lesson }} />
  const stats = lessonStats(lesson.text ?? '', language ?? 'en', wordMap)
  const saved = bookmarks.data?.some((record) => record.lessonId === lesson.id) ?? false
  const duration = formatDuration(lesson.durationSec)

  async function toggleBookmark() {
    if (!lesson || !language) return
    if (saved) await deleteBookmark(language, lesson.id)
    else await putBookmark({ language, lessonId: lesson.id, lesson, savedAt: new Date().toISOString() })
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['bookmarks', language] }),
      queryClient.invalidateQueries({ queryKey: ['home-local', language] }),
    ])
  }

  return (
    <div className="page-stack lesson-detail-page">
      <Link className="back-link" to="/explore"><ArrowLeft size={16} />{t('back')}</Link>
      {cached && <p className="offline-banner">{t('serverUnavailable')}</p>}
      <section className="detail-hero">
        <LessonImage src={lesson.imageUrl} />
        <div className="detail-hero-content">
          <div className="detail-labels">
            <span className="pill">{t('lesson')}</span>
            {lesson.level !== null && <span className="pill">{t('level', { level: lesson.level })}</span>}
            {duration && <span className="pill"><Clock size={13} />{duration}</span>}
          </div>
          <h1>{lesson.title}</h1>
          <p>{lesson.description}</p>
          <div className="button-row">
            <Link className="button primary" to={`/reader/${lesson.id}`}><Play size={17} />{t('startReading')}</Link>
            <button className={saved ? 'button secondary saved' : 'button secondary'} onClick={toggleBookmark}>
              {saved ? <BookmarkCheck size={17} /> : <Bookmark size={17} />}{saved ? t('saved') : t('save')}
            </button>
          </div>
        </div>
      </section>
      <section className="detail-stats">
        <div><strong>{stats.total}</strong><span>{t('totalWords')}</span></div>
        <div><strong>{stats.unique}</strong><span>{t('uniqueWords')}</span></div>
        <div className="accent"><strong>{stats.newWords}</strong><span>{t('newWords')}</span></div>
      </section>
      {lesson.description && <section><h2>{t('description')}</h2><p className="detail-description">{lesson.description}</p></section>}
      {!lesson.text && <EmptyState title={t('noResults')} />}
    </div>
  )
}
