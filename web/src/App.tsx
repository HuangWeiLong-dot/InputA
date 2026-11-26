import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './app/AuthContext'
import { AppShell } from './app/AppShell'
import { LoadingState } from './components/Feedback'
import { LoginPage } from './features/auth/LoginPage'
import { HomePage } from './features/home/HomePage'
import { ExplorePage } from './features/explore/ExplorePage'
import { LessonPage } from './features/lesson/LessonPage'
import { CoursePage } from './features/course/CoursePage'
import { ReaderPage } from './features/reader/ReaderPage'
import { VocabularyPage } from './features/vocabulary/VocabularyPage'
import { ReviewPage } from './features/review/ReviewPage'
import { SettingsPage } from './features/settings/SettingsPage'
import { useI18n } from './i18n/useI18n'

function ProtectedRoute() {
  const { token, language, accountLoading } = useAuth()
  const location = useLocation()
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (accountLoading || !language) return <LoadingState />
  return <Outlet />
}

function NotFoundPage() {
  const { t } = useI18n()
  return <div className="state-state"><h1>{t('notFound')}</h1><a className="button primary" href="/">{t('goHome')}</a></div>
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<HomePage />} />
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/lesson/:id" element={<LessonPage />} />
          <Route path="/course/:id" element={<CoursePage />} />
          <Route path="/vocabulary" element={<VocabularyPage />} />
          <Route path="/review" element={<ReviewPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="/reader/:id" element={<ReaderPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
