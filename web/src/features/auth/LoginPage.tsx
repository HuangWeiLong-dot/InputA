import { useState, type FormEvent } from 'react'
import { ArrowRight, BookOpen, Database, Languages, LoaderCircle, LockKeyhole } from 'lucide-react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../../app/AuthContext'
import { useI18n } from '../../i18n/useI18n'

export function LoginPage() {
  const { t } = useI18n()
  const { token, signIn, signInWithToken, accountLoading } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pastedToken, setPastedToken] = useState('')
  const [tokenMode, setTokenMode] = useState(false)
  const [error, setError] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  if (token) return <Navigate to="/" replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(false)
    setSubmitting(true)
    try {
      if (tokenMode) await signInWithToken(pastedToken)
      else await signIn(username, password)
      navigate('/', { replace: true })
    } catch {
      setError(true)
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-story">
        <div className="brand login-brand"><span className="brand-mark">A</span><span>InputA</span></div>
        <div className="login-hero-copy">
          <span className="eyebrow"><BookOpen size={15} /> {t('loginEyebrow')}</span>
          <h1>{t('tagline')}</h1>
          <p>{t('loginStory')}</p>
        </div>
        <div className="login-features">
          <div><Languages size={21} /><strong>{t('immersiveReading')}</strong><span>{t('immersiveReadingBody')}</span></div>
          <div><Database size={21} /><strong>{t('localByDesign')}</strong><span>{t('localByDesignBody')}</span></div>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-form-wrap">
          <span className="login-lock"><LockKeyhole size={22} /></span>
          <h2>{t('welcomeBack')}</h2>
          <p>{t('loginSubtitle')}</p>
          <form onSubmit={submit}>
            {tokenMode ? (
              <label className="field">
                <span>{t('token')}</span>
                <textarea
                  value={pastedToken}
                  onChange={(event) => setPastedToken(event.target.value)}
                  rows={4}
                  required
                  autoComplete="off"
                  spellCheck={false}
                  placeholder="Token…"
                />
                <small>{t('tokenHint')}</small>
              </label>
            ) : (
              <>
                <label className="field">
                  <span>{t('username')}</span>
                  <input value={username} onChange={(event) => setUsername(event.target.value)} required autoComplete="username" />
                </label>
                <label className="field">
                  <span>{t('password')}</span>
                  <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required autoComplete="current-password" />
                </label>
              </>
            )}
            {error && <p className="form-error" role="alert">{t('loginError')}</p>}
            <button className="button primary login-submit" type="submit" disabled={submitting || accountLoading}>
              {submitting || accountLoading ? <LoaderCircle size={18} className="spin" /> : <ArrowRight size={18} />}
              {submitting || accountLoading ? t('signingIn') : t('signIn')}
            </button>
          </form>
          <button className="text-button token-toggle" type="button" onClick={() => { setTokenMode((value) => !value); setError(false) }}>
            {tokenMode ? t('username') + ' + ' + t('password') : t('orToken')}
          </button>
        </div>
      </section>
    </main>
  )
}
