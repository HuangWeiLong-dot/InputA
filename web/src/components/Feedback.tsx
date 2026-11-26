import { AlertCircle, LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'
import { useI18n } from '../i18n/useI18n'

export function LoadingState({ label }: { label?: string }) {
  const { t } = useI18n()
  return <div className="state-state" role="status"><LoaderCircle className="spin" size={24} /><p>{label ?? t('loading')}</p></div>
}

export function ErrorState({ message, onRetry }: { message?: string | null; onRetry?: () => void }) {
  const { t } = useI18n()
  return (
    <div className="state-state error-state" role="alert">
      <AlertCircle size={24} />
      <p>{message || t('serverUnavailable')}</p>
      {onRetry && <button className="button secondary" onClick={onRetry}>{t('retry')}</button>}
    </div>
  )
}

export function EmptyState({ title, body, action }: { title: string; body?: string; action?: ReactNode }) {
  return <div className="state-state empty-state"><h3>{title}</h3>{body && <p>{body}</p>}{action}</div>
}
