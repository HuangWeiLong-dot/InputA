import { BookOpen, Compass, House, LibraryBig, LogOut, Settings, Sparkles } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../app/AuthContext'
import { useI18n } from '../i18n/useI18n'

const items = [
  { to: '/', label: 'navHome', icon: House, end: true },
  { to: '/explore', label: 'navExplore', icon: Compass, end: false },
  { to: '/vocabulary', label: 'navVocabulary', icon: LibraryBig, end: false },
  { to: '/review', label: 'navReview', icon: Sparkles, end: false },
] as const

export function AppShell() {
  const { t } = useI18n()
  const { profile, languages, language, selectLanguage, signOut } = useAuth()
  const navigation = (
    <>
      {items.map(({ to, label, icon: Icon, end }) => (
        <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
          <Icon size={19} /><span>{t(label)}</span>
        </NavLink>
      ))}
    </>
  )
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink to="/" className="brand"><span className="brand-mark">A</span><span>InputA</span></NavLink>
        <nav className="side-nav" aria-label="Primary">{navigation}</nav>
        <div className="sidebar-bottom">
          <NavLink to="/settings" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
            <Settings size={19} /><span>{t('navSettings')}</span>
          </NavLink>
          <div className="account-card">
            <div className="avatar">{profile?.displayName.slice(0, 1).toUpperCase() || 'A'}</div>
            <div><strong>{profile?.displayName || profile?.username}</strong><span>LingQ</span></div>
            <button className="icon-button" onClick={signOut} aria-label={t('signOut')} title={t('signOut')}><LogOut size={17} /></button>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <NavLink to="/" className="brand mobile-brand"><span className="brand-mark">A</span><span>InputA</span></NavLink>
          {languages.length > 1 ? (
            <select className="language-select" value={language ?? ''} onChange={(event) => selectLanguage(event.target.value)} aria-label={t('language')}>
              {languages.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}
            </select>
          ) : <span className="language-label"><BookOpen size={16} />{languages[0]?.name ?? language}</span>}
          <NavLink className="icon-button mobile-settings" to="/settings" aria-label={t('navSettings')} title={t('navSettings')}><Settings size={19} /></NavLink>
        </header>
        <main className="page-content"><Outlet /></main>
      </div>
      <nav className="bottom-nav" aria-label="Primary">{navigation}</nav>
    </div>
  )
}
