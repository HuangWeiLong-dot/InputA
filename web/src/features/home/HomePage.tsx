import { ArrowRight, Bookmark, BookOpen, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../app/AuthContext'
import { useWords } from '../../app/useWords'
import { LessonCard, LessonImage } from '../../components/LessonCard'
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback'
import { listBookmarks, getLastLesson } from '../../storage/db'
import { useI18n } from '../../i18n/useI18n'

export function HomePage() {
  const { t } = useI18n()
  const { api, language, token } = useAuth()
  const { words, loading: wordsLoading } = useWords()
  const shelfQuery = useQuery({
    queryKey: ['shelf', language],
    queryFn: () => api.getShelf(language ?? ''),
    enabled: Boolean(language && token),
  })
  const localQuery = useQuery({
    queryKey: ['home-local', language],
    queryFn: async () => {
      const [bookmarks, last] = await Promise.all([listBookmarks(language ?? ''), getLastLesson(language ?? '')])
      return { bookmarks, last }
    },
    enabled: Boolean(language && token),
  })
  const sections = shelfQuery.data ?? []
  const recent = sections[0]?.lessons[0]
  const continueLesson = localQuery.data?.last?.lesson ?? recent
  const learning = words.filter((word) => word.status >= 1).length
  const known = words.filter((word) => word.status === 0).length
  const ignored = words.filter((word) => word.status === -1).length

  if (shelfQuery.isLoading) return <LoadingState />

  return (
    <div className="home-page page-stack">
      <div className="page-heading">
        <div><span className="eyebrow">{t('appName')}</span><h1>{t('continueLearning')}</h1></div>
        <Link className="button secondary" to="/review"><Sparkles size={17} />{learning} {t('todayReview')}</Link>
      </div>
      {shelfQuery.isError && <ErrorState message={shelfQuery.error.message} onRetry={() => shelfQuery.refetch()} />}
      {continueLesson ? (
        <section className="continue-card">
          <LessonImage src={continueLesson.imageUrl} />
          <div className="continue-overlay" />
          <div className="continue-content">
            <span className="continue-label"><BookOpen size={15} />{localQuery.data?.last ? t('continueLearning') : t('startLearning')}</span>
            <h2>{continueLesson.title}</h2>
            <p>{continueLesson.collectionTitle ?? continueLesson.description}</p>
            <Link className="button light" to={continueLesson.isCourse ? `/course/${continueLesson.libraryId}` : `/lesson/${continueLesson.id}`}>
              {t('openLesson')}<ArrowRight size={17} />
            </Link>
          </div>
        </section>
      ) : (
        <EmptyState title={t('noRecentLesson')} action={<Link className="button primary" to="/explore">{t('browseLessons')}</Link>} />
      )}

      <section>
        <div className="section-heading"><h2>{t('localProgress')}</h2><Link to="/vocabulary">{t('viewAll')}<ArrowRight size={15} /></Link></div>
        {wordsLoading ? <LoadingState /> : (
          <div className="stat-grid">
            <Link className="stat-card" to="/vocabulary"><strong>{known}</strong><span>{t('knownWords')}</span></Link>
            <Link className="stat-card accent" to="/review"><strong>{learning}</strong><span>{t('learningWords')}</span></Link>
            <Link className="stat-card" to="/vocabulary?status=-1"><strong>{ignored}</strong><span>{t('ignoredWords')}</span></Link>
          </div>
        )}
      </section>

      {localQuery.data?.bookmarks.length ? (
        <section>
          <div className="section-heading"><h2>{t('savedLessons')}</h2><Link to="/vocabulary?tab=saved"><Bookmark size={15} />{t('viewAll')}</Link></div>
          <div className="lesson-grid compact">
            {localQuery.data.bookmarks.slice(0, 3).map((bookmark) => <LessonCard key={bookmark.lesson.id} lesson={bookmark.lesson} saved />)}
          </div>
        </section>
      ) : null}

      {sections.slice(recent ? 1 : 0).map((section) => (
        <section key={section.title}>
          <div className="section-heading"><h2>{section.title}</h2><Link to="/explore">{t('viewAll')}<ArrowRight size={15} /></Link></div>
          <div className="lesson-grid compact">
            {section.lessons.slice(0, 4).map((lesson) => <LessonCard key={lesson.id} lesson={lesson} />)}
          </div>
        </section>
      ))}
    </div>
  )
}
