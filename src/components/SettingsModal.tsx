import React, { useEffect, useState } from 'react';
import { X, Settings, Play, Loader2 } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import type { TtsProvider } from '../store/useReaderStore';
import { apiUrl, getBackendHealth } from '../services/apiBase';
import { AUTO_VOICE_HINT, speakWord } from '../services/ttsService';
import {
  BTN_GHOST,
  BTN_SM,
  FIELD,
  SECTION_LABEL,
  SEGMENT,
  SEGMENT_BOX,
  SEGMENT_ON,
  SEGMENT_OFF,
} from './ui';

const PROVIDERS: Array<{ id: TtsProvider; label: string; hint: string }> = [
  { id: 'edge', label: 'Edge TTS', hint: '后端代理微软 Edge 朗读，音质最好。需要 npm run server。' },
  { id: 'custom', label: '自定义服务', hint: '填自己的 TTS 地址，约定为 GET 返回可播放的音频。' },
  { id: 'browser', label: '浏览器合成', hint: '词典真人音源优先，否则用系统语音合成。离线可用。' },
];

const RATES = [
  { id: 'default', label: '原速' },
  { id: '-20%', label: '慢' },
  { id: '+20%', label: '快' },
  { id: '+40%', label: '更快' },
];

const SAMPLE_TEXT = 'Reading is the best way to grow your vocabulary.';

interface VoiceOption {
  name: string;
  locale: string;
}

export const SettingsModal: React.FC = () => {
  const {
    isSettingsOpen,
    setSettingsOpen,
    ttsProvider,
    ttsVoice,
    ttsRate,
    ttsCustomUrlTemplate,
    bionicEnabled,
    readingRulerEnabled,
    currentBook,
    updateSettings,
  } = useReaderStore();

  // null = 还没查过，"加载中"是推导出来的，不在 effect 里同步 setState。
  const [voices, setVoices] = useState<VoiceOption[] | null>(null);

  // Load the voice catalogue once the panel is open and Edge is selected.
  // It is proxied through the backend because the upstream sends no CORS headers.
  useEffect(() => {
    if (!isSettingsOpen || ttsProvider !== 'edge' || voices !== null) return;

    let cancelled = false;

    void (async () => {
      try {
        const backend = await getBackendHealth();
        if (cancelled) return;
        if (!backend.ok) {
          setVoices([]);
          return;
        }

        const response = await fetch(apiUrl('/api/tts/voices'));
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data: unknown = await response.json();
        if (cancelled || !Array.isArray(data)) return;

        setVoices(
          data
            .map((item) => {
              const entry = item as { ShortName?: unknown; Locale?: unknown };
              return {
                name: typeof entry.ShortName === 'string' ? entry.ShortName : '',
                locale: typeof entry.Locale === 'string' ? entry.Locale : '',
              };
            })
            .filter((voice) => voice.name),
        );
      } catch (error) {
        // 记成空列表：否则这个 effect 会一直重试。
        console.warn('Could not load the TTS voice list:', error);
        if (!cancelled) setVoices([]);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [isSettingsOpen, ttsProvider, voices]);

  if (!isSettingsOpen) return null;

  const activeHint = PROVIDERS.find((provider) => provider.id === ttsProvider)?.hint ?? '';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      {/* dvh 而不是 vh：手机上 vh 不随键盘收缩，面板底部会被键盘顶出可视区。 */}
      <div className="flex max-h-[88dvh] w-full max-w-2xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
        <div className="flex items-center justify-between border-b border-[var(--border-color)] px-5 py-4">
          <div className="flex items-center gap-2.5">
            <Settings className="h-5 w-5 text-[var(--accent)]" />
            <h2 className="text-[16px] font-semibold text-[var(--text-strong)]">设置</h2>
          </div>
          <button
            type="button"
            onClick={() => setSettingsOpen(false)}
            className={BTN_GHOST}
            aria-label="关闭"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 space-y-6 overflow-y-auto px-5 py-5">
          <section className="space-y-3">
            <span className={SECTION_LABEL}>朗读引擎</span>

            <div className={`${SEGMENT_BOX} w-full`}>
              {PROVIDERS.map((provider) => (
                <button
                  key={provider.id}
                  type="button"
                  onClick={() => updateSettings({ ttsProvider: provider.id })}
                  className={`${SEGMENT} h-10 flex-1 px-3 whitespace-nowrap ${
                    ttsProvider === provider.id ? SEGMENT_ON : SEGMENT_OFF
                  }`}
                >
                  {provider.label}
                </button>
              ))}
            </div>
            <p className="text-[12px] leading-relaxed text-[var(--text-muted)]">{activeHint}</p>
          </section>

          {ttsProvider === 'edge' && (
            <>
              <section className="space-y-2">
                <span className={SECTION_LABEL}>音色</span>
                <input
                  type="text"
                  list="tts-voice-options"
                  value={ttsVoice}
                  onChange={(event) => updateSettings({ ttsVoice: event.target.value })}
                  placeholder={`留空按正文语言自动选择（${AUTO_VOICE_HINT}）`}
                  className={FIELD}
                />
                {/*
                  A datalist rather than a select: there are ~320 voices, and a
                  select that long is worse to use than typing into a filtered list.
                */}
                <datalist id="tts-voice-options">
                  {(voices ?? []).map((voice) => (
                    <option key={voice.name} value={voice.name}>
                      {voice.locale}
                    </option>
                  ))}
                </datalist>
                <p className="text-[12px] text-[var(--text-muted)]">
                  {voices === null
                    ? '正在加载音色列表…'
                    : voices.length > 0
                      ? `可用音色 ${voices.length} 个，输入可筛选。`
                      : '后端未运行，暂时无法列出音色；仍可手填音色名。'}
                </p>
              </section>

              <section className="space-y-2">
                <span className={SECTION_LABEL}>语速</span>
                <div className={SEGMENT_BOX}>
                  {RATES.map((rate) => (
                    <button
                      key={rate.id}
                      type="button"
                      onClick={() => updateSettings({ ttsRate: rate.id })}
                      className={`${SEGMENT} h-10 px-3.5 ${ttsRate === rate.id ? SEGMENT_ON : SEGMENT_OFF}`}
                    >
                      {rate.label}
                    </button>
                  ))}
                </div>
              </section>
            </>
          )}

          {ttsProvider === 'custom' && (
            <section className="space-y-2">
              <span className={SECTION_LABEL}>服务地址模板</span>
              <input
                type="text"
                value={ttsCustomUrlTemplate}
                onChange={(event) => updateSettings({ ttsCustomUrlTemplate: event.target.value })}
                placeholder="https://example.com/tts?text={text}&lang={lang}"
                className={FIELD}
              />
              <p className="text-[12px] leading-relaxed text-[var(--text-muted)]">
                用 <code className="font-mono">{'{text}'}</code> 与{' '}
                <code className="font-mono">{'{lang}'}</code> 作占位符，会被 URL 编码后替换。
                这个地址需要直接返回可播放的音频字节。
              </p>
            </section>
          )}

          <button
            type="button"
            onClick={() => speakWord(SAMPLE_TEXT)}
            className={`${BTN_SM} w-full`}
            title="用当前设置朗读一句示例"
          >
            {voices === null && ttsProvider === 'edge' ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Play className="h-4 w-4" />
            )}
            <span>试听</span>
          </button>

          <section className="space-y-3 border-t border-[var(--border-color)] pt-5">
            <span className={SECTION_LABEL}>阅读辅助</span>

            {(
              [
                {
                  key: 'bionicEnabled' as const,
                  on: bionicEnabled,
                  label: '仿生阅读',
                  hint: '加粗每个词的前 40%，给眼睛一个锚点。中日韩文字会整段跳过。',
                },
                {
                  key: 'readingRulerEnabled' as const,
                  on: readingRulerEnabled,
                  label: '段落聚焦标尺',
                  hint: '鼠标所在段落保持清晰，其余变暗（跟随滚动）。',
                },
              ]
            ).map((toggle) => (
              <label
                key={toggle.key}
                className="flex cursor-pointer items-start gap-3 border border-[var(--border-color)] px-3.5 py-3 transition-colors hover:bg-[var(--bg-hover)]"
              >
                <input
                  type="checkbox"
                  checked={toggle.on}
                  onChange={(event) => updateSettings({ [toggle.key]: event.target.checked })}
                  className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--accent)]"
                />
                <span className="min-w-0">
                  <span className="block text-[14px] font-semibold text-[var(--text-strong)]">
                    {toggle.label}
                  </span>
                  <span className="mt-0.5 block text-[12px] leading-relaxed text-[var(--text-muted)]">
                    {toggle.hint}
                  </span>
                </span>
              </label>
            ))}
          </section>

          {currentBook && (
            <section className="space-y-1.5 border-t border-[var(--border-color)] pt-5">
              <span className={SECTION_LABEL}>当前读物</span>
              <p className="text-[13px] text-[var(--text-main)]">
                {currentBook.title}
                {currentBook.language ? ` · 语言 ${currentBook.language}` : ''}
              </p>
              <p className="text-[12px] leading-relaxed text-[var(--text-muted)]">
                朗读音色按这个语言自动选择；留空音色时就用它。
              </p>
            </section>
          )}
        </div>
      </div>
    </div>
  );
};
