import { Database, Globe2, LogOut, RotateCcw, Type } from 'lucide-react'
import { useAuth } from '../../app/AuthContext'
import { usePreferences } from '../../app/PreferencesContext'
import { useI18n } from '../../i18n/useI18n'
import type { Preferences } from '../../storage/preferences'

export function SettingsPage() {
  const { t } = useI18n()
  const { preferences, update } = usePreferences()
  const { profile, signOut } = useAuth()
  return (
    <div className="page-stack settings-page">
      <div className="page-heading"><div><span className="eyebrow">InputA</span><h1>{t('settingsTitle')}</h1></div></div>
      <section className="settings-section">
        <div className="settings-section-title"><Type size={19} /><h2>{t('readingPreferences')}</h2></div>
        <div className="setting-row">
          <label htmlFor="reader-mode"><strong>{t('readerMode')}</strong></label>
          <select id="reader-mode" value={preferences.readerMode} onChange={(event) => update({ readerMode: event.target.value as Preferences['readerMode'] })}>
            <option value="page">{t('pageMode')}</option>
            <option value="sentence">{t('sentenceMode')}</option>
            <option value="scroll">{t('scrollMode')}</option>
          </select>
        </div>
        <div className="setting-row">
          <label htmlFor="font-scale"><strong>{t('fontSize')}</strong><small>{Math.round(preferences.fontScale * 100)}%</small></label>
          <input id="font-scale" type="range" min="0.85" max="1.35" step="0.05" value={preferences.fontScale} onChange={(event) => update({ fontScale: Number(event.target.value) })} />
        </div>
        <Toggle label={t('autoCreate')} checked={preferences.autoCreateLingq} onChange={(checked) => update({ autoCreateLingq: checked })} />
        <Toggle label={t('pronounceOnTap')} checked={preferences.pronounceOnTap} onChange={(checked) => update({ pronounceOnTap: checked })} />
        <button className="button secondary" onClick={() => update({ readerMode: 'page', fontScale: 1, autoCreateLingq: true, pronounceOnTap: false })}><RotateCcw size={16} />{t('resetSettings')}</button>
      </section>
      <section className="settings-section">
        <div className="settings-section-title"><Globe2 size={19} /><h2>{t('appLanguage')}</h2></div>
        <div className="segmented-control language-switch">
          <button className={preferences.appLanguage === 'en' ? 'active' : ''} onClick={() => update({ appLanguage: 'en' })}>English</button>
          <button className={preferences.appLanguage === 'zh' ? 'active' : ''} onClick={() => update({ appLanguage: 'zh' })}>中文</button>
        </div>
      </section>
      <section className="settings-section privacy-section">
        <div className="settings-section-title"><Database size={19} /><h2>{t('privacyTitle')}</h2></div>
        <p>{t('privacyBody')}</p>
        <div className="account-settings"><div className="avatar">{profile?.displayName.slice(0, 1).toUpperCase() || 'A'}</div><div><strong>{profile?.displayName || profile?.username}</strong><span>LingQ</span></div><button className="button secondary" onClick={signOut}><LogOut size={16} />{t('signOut')}</button></div>
      </section>
    </div>
  )
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return (
    <label className="setting-row toggle-row">
      <strong>{label}</strong>
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
      <span className="toggle-track" aria-hidden="true"><span /></span>
    </label>
  )
}
