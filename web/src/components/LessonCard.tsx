import { Bookmark, BookOpen, Check, Layers } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { LessonSummary } from '../api/types'
import { useI18n } from '../i18n/useI18n'

export function LessonImage({ src, alt = '' }: { src: string | null; alt?: string }) {
  return src
    ? <img className="lesson-image" src={src} alt={alt} loading="lazy" onError={(event) => { event.currentTarget.hidden = true }} />
    : <div className="lesson-image image-fallback" aria-hidden="true"><BookOpen size={30} /></div>
}

export function formatDuration(seconds: number | null) {
  if (!seconds || seconds < 0) return null
  const minutes = Math.round(seconds / 60)
  return minutes < 60 ? `${minutes}m` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`
}

export function LessonCard({ lesson, saved = false }: { lesson: LessonSummary; saved?: boolean }) {
  const { t } = useI18n()
  const duration = formatDuration(lesson.durationSec)
  const path = lesson.isCourse ? `/course/${lesson.libraryId}` : `/lesson/${lesson.id}`
  return (
    <Link className="lesson-card" to={path} state={{ lesson }}>
      <div className="lesson-card-media">
        <LessonImage src={lesson.imageUrl} />
        <span className="lesson-kind">{lesson.isCourse ? <Layers size={13} /> : <BookOpen size={13} />}{lesson.isCourse ? t('course') : t('lesson')}</span>
        {lesson.isCompleted && <span className="lesson-complete"><Check size={13} />{t('completed')}</span>}
      </div>
      <div className="lesson-card-body">
        <div className="lesson-card-title-row">
          <h3>{lesson.title}</h3>
          {saved && <Bookmark size={15} fill="currentColor" aria-label={t('saved')} />}
        </div>
        <p>{lesson.collectionTitle ?? lesson.description}</p>
        <div className="lesson-meta">
          {lesson.level !== null && <span>{t('level', { level: lesson.level })}</span>}
          {duration && <span>{duration}</span>}
          {lesson.isCourse && lesson.lessonCount !== null && <span>{lesson.lessonCount} {t('lessons')}</span>}
        </div>
      </div>
    </Link>
  )
}
