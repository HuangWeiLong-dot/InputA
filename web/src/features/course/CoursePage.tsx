import { ArrowLeft, Layers } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../app/AuthContext'
import type { LessonSummary } from '../../api/types'
import { LessonCard, LessonImage } from '../../components/LessonCard'
import { ErrorState, LoadingState } from '../../components/Feedback'
import { useI18n } from '../../i18n/useI18n'

export function CoursePage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const fallback = (location.state as { lesson?: LessonSummary } | null)?.lesson
  const { t } = useI18n()
  const { api, language, token } = useAuth()
  const courseId = Number(id)
  const query = useQuery({
    queryKey: ['course', language, courseId],
    queryFn: () => api.getCourse(language ?? '', courseId, fallback),
    enabled: Boolean(language && token && Number.isInteger(courseId)),
  })
  if (query.isLoading) return <LoadingState />
  if (query.isError || !query.data) return <ErrorState message={query.error?.message} onRetry={() => query.refetch()} />
  const course = query.data
  return (
    <div className="page-stack course-page">
      <Link className="back-link" to="/explore"><ArrowLeft size={16} />{t('back')}</Link>
      <section className="detail-hero course-hero">
        <LessonImage src={course.imageUrl} />
        <div className="detail-hero-content">
          <span className="pill"><Layers size={13} />{t('course')}</span>
          <h1>{course.title}</h1>
          <p>{course.description}</p>
          {course.level !== null && <span className="pill">{t('level', { level: course.level })}</span>}
        </div>
      </section>
      <section>
        <div className="section-heading"><h2>{t('courseContents')}</h2><span className="muted">{course.lessons.length} {t('lessons')}</span></div>
        <div className="lesson-grid">{course.lessons.map((lesson, index) => (
          <div className="course-lesson" key={lesson.id}>
            <span className="course-index">{String(index + 1).padStart(2, '0')}</span>
            <LessonCard lesson={lesson} />
          </div>
        ))}</div>
      </section>
    </div>
  )
}
