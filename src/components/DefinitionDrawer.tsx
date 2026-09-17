import React from 'react';
import {
  AlertTriangle,
  BookOpen,
  Check,
  Loader2,
  RefreshCw,
  Sparkles,
  Trash2,
  Volume2,
  X,
} from 'lucide-react';
import type { DictionaryEntry, WordStatus } from '../types/reader';
import type { DictionarySource } from '../services/dictionaryApi';
import { speakWord } from '../services/speechService';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { useReaderStore } from '../store/useReaderStore';

interface DefinitionDrawerProps {
  word: string | null;
  entry: DictionaryEntry | null;
  source: DictionarySource | null;
  isLoading: boolean;
  isUnavailable: boolean;
  isOpen: boolean;
  onClose: () => void;
  onRetry: () => void;
  /** Clicking the headword of the local dictionary re-looks-up that spelling. */
  onLookupWord?: (word: string) => void;
  surroundingSentence?: string;
}

const SECTION_LABEL = 'text-[10px] font-semibold uppercase tracking-[0.16em] text-[var(--text-muted)]';
const FOOTER_BUTTON =
  'flex h-9 flex-1 items-center justify-center gap-1.5 border px-3 text-[11px] font-semibold uppercase tracking-[0.08em] transition-colors';
/** Only the offline dictionary needs a friendly name; the rest are hostnames. */
const SOURCE_LABELS: Partial<Record<DictionarySource, string>> = {
  'local-ecdict': '本地词库',
};

export const DefinitionDrawer: React.FC<DefinitionDrawerProps> = ({
  word,
  entry,
  source,
  isLoading,
  isUnavailable,
  isOpen,
  onClose,
  onRetry,
  onLookupWord,
  surroundingSentence,
}) => {
  const { words, markKnown, markLearning, removeWord } = useVocabularyStore();
  const { setSentenceAnalysisOpen } = useReaderStore();

  if (!isOpen || !word) return null;

  const currentStatus: WordStatus | 'unknown' = words[word.toLowerCase()] || 'unknown';
  const statusLabel =
    currentStatus === 'learning' ? '生词' : currentStatus === 'known' ? '已会' : '未标记';
  const statusClass =
    currentStatus === 'learning'
      ? 'border-[var(--highlight-border)] bg-[var(--highlight-bg)] text-[var(--highlight-text)]'
      : currentStatus === 'known'
        ? 'border-[var(--accent-border)] bg-[var(--accent-soft)] text-[var(--accent)]'
        : 'border-[var(--border-color)] bg-[var(--bg-subtle)] text-[var(--text-muted)]';

  // Everything below only exists for the offline ECDICT entry (entry.extra).
  const extra = entry?.extra;
  const translationLines =
    extra?.translation
      ?.split('\n')
      .map((line) => line.trim())
      .filter(Boolean) ?? [];
  const examTags = extra?.examTags ?? [];
  const headword =
    entry?.word && entry.word.toLowerCase() !== word.toLowerCase() ? entry.word : '';
  const lemma = extra?.lemma && extra.lemma.toLowerCase() !== word.toLowerCase() ? extra.lemma : '';
  const metaBits = [
    extra?.frequency?.collins ? `柯林斯 ${'★'.repeat(extra.frequency.collins)}` : '',
    extra?.frequency?.oxford ? '牛津核心词' : '',
    extra?.frequency?.bnc ? `BNC #${extra.frequency.bnc}` : '',
    extra?.frequency?.frq ? `COCA #${extra.frequency.frq}` : '',
  ]
    .filter(Boolean)
    .join(' · ');
  const posLine = (extra?.partsOfSpeech ?? [])
    .filter((part) => part.percent > 0)
    .map((part) => `${part.label} ${part.percent}%`)
    .join(' · ');
  const formLine = (extra?.forms ?? [])
    .map((form) => `${form.label} ${form.words.slice(0, 3).join(' / ')}`)
    .slice(0, 4)
    .join(' · ');

  return (
    <>
      {/* Mobile backdrop; on wide screens the reading column simply narrows. */}
      <div onClick={onClose} className="fixed inset-0 z-30 bg-black/40 md:hidden animate-overlay-in" />

      <aside className="fixed left-0 right-0 bottom-0 z-40 flex max-h-[82vh] flex-col border-t border-[var(--border-color)] bg-[var(--bg-surface)] animate-drawer-bottom md:top-14 md:left-auto md:right-0 md:bottom-0 md:max-h-none md:w-96 md:border-t-0 md:border-l md:animate-drawer-right">
        {/* Status bar */}
        <div className="flex shrink-0 items-center justify-between gap-2 border-b border-[var(--border-color)] px-4 py-2.5">
          <div className="flex items-center gap-2">
            <span className={`border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-[0.14em] ${statusClass}`}>
              {statusLabel}
            </span>
            {source && (
              <span className="font-mono text-[10px] text-[var(--text-muted)]">
                来源 {SOURCE_LABELS[source] ?? source}
              </span>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="flex h-7 w-7 items-center justify-center text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-hover)] hover:text-[var(--text-strong)]"
            aria-label="关闭释义面板"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Headword */}
        <div className="shrink-0 border-b border-[var(--border-color)] bg-[var(--bg-subtle)] px-4 py-4">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="truncate font-serif text-2xl leading-tight font-bold text-[var(--text-strong)]">
                {word}
              </h3>
              {entry?.phonetic && (
                <p className="mt-1 font-mono text-xs text-[var(--text-muted)]">{entry.phonetic}</p>
              )}
              {headword && (
                <p className="mt-1 text-[11px] text-[var(--text-muted)]">
                  词库词条
                  {onLookupWord ? (
                    <button
                      type="button"
                      onClick={() => onLookupWord(headword)}
                      className="ml-1 font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline"
                    >
                      {headword}
                    </button>
                  ) : (
                    <span className="ml-1 text-[var(--text-main)]">{headword}</span>
                  )}
                </p>
              )}
              {lemma && (
                <p className="mt-1 text-[11px] text-[var(--text-muted)]">
                  原形
                  {onLookupWord ? (
                    <button
                      type="button"
                      onClick={() => onLookupWord(lemma)}
                      className="mx-1 font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline"
                    >
                      {lemma}
                    </button>
                  ) : (
                    <span className="mx-1 text-[var(--text-main)]">{lemma}</span>
                  )}
                  {extra?.inflection ? `（${extra.inflection}）` : ''}
                </p>
              )}
            </div>

            <button
              type="button"
              onClick={() => speakWord(word, entry?.audioUrl)}
              className={`flex h-9 w-9 shrink-0 items-center justify-center border transition-colors ${
                entry?.audioUrl
                  ? 'border-[var(--accent-border)] bg-[var(--accent-soft)] text-[var(--accent)]'
                  : 'border-[var(--border-color)] text-[var(--text-main)] hover:bg-[var(--bg-hover)]'
              }`}
              title="朗读（优先词典音源，其次系统 TTS）"
              aria-label="朗读单词"
            >
              <Volume2 className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Meanings / states */}
        <div className="flex-1 space-y-5 overflow-y-auto px-4 py-4">
          {isLoading && (
            <div className="flex flex-col items-center justify-center gap-2 py-12 text-[var(--text-muted)]">
              <Loader2 className="h-5 w-5 animate-spin text-[var(--accent)]" />
              <p className="text-xs">正在查询词典…</p>
              <p className="font-mono text-[10px]">
                本地词库 → dictionaryapi.dev → Wiktionary → Datamuse
              </p>
            </div>
          )}

          {!isLoading && isUnavailable && !entry && (
            <div className="border border-[var(--highlight-border)] bg-[var(--highlight-bg)] p-4 text-[var(--highlight-text)]">
              <p className="flex items-center gap-1.5 text-xs font-semibold">
                <AlertTriangle className="h-3.5 w-3.5" />
                词典服务暂时无法访问
              </p>
              <p className="mt-1.5 text-[11px] leading-relaxed">
                已依次尝试本地词库（需要后端与 data/stardict.db）以及 dictionaryapi.dev、Wiktionary、
                Datamuse 三个在线来源，均未取得响应（可能是网络或跨域限制）。该词已记入生词本，可稍后重试。
              </p>
              <button
                type="button"
                onClick={onRetry}
                className="mt-3 flex h-8 items-center gap-1.5 border border-[var(--highlight-border)] bg-[var(--bg-surface)] px-2.5 text-[11px] font-semibold uppercase tracking-[0.08em] transition-colors hover:bg-[var(--bg-hover)]"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                <span>重新查询</span>
              </button>
            </div>
          )}

          {!isLoading && !isUnavailable && !entry && (
            <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] p-4">
              <p className="text-xs font-semibold text-[var(--text-strong)]">词典均未收录该词</p>
              <p className="mt-1.5 text-[11px] leading-relaxed text-[var(--text-muted)]">
                可能是专有名词、罕见词或词形变化。它已记入生词本，之后在文章中遇到仍会保持高亮。
              </p>
            </div>
          )}

          {/* Offline ECDICT extras: exam tags + Chinese definitions. */}
          {!isLoading && examTags.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5">
              {examTags.map((tag) => (
                <span
                  key={tag}
                  className="border border-[var(--accent-border)] bg-[var(--accent-soft)] px-1.5 py-0.5 text-[10px] font-semibold text-[var(--accent)]"
                >
                  {tag}
                </span>
              ))}
            </div>
          )}

          {!isLoading && translationLines.length > 0 && (
            <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] px-3 py-2.5">
              <span className={SECTION_LABEL}>中文释义</span>
              <div className="mt-1.5 space-y-1">
                {translationLines.map((line, lineIdx) => (
                  <p key={lineIdx} className="text-[13px] leading-relaxed text-[var(--text-main)]">
                    {line}
                  </p>
                ))}
              </div>
              {(posLine || metaBits) && (
                <p className="mt-2 font-mono text-[10px] leading-relaxed text-[var(--text-muted)]">
                  {[posLine && `词性分布 ${posLine}`, metaBits].filter(Boolean).join('　|　')}
                </p>
              )}
            </div>
          )}

          {!isLoading && !lemma && formLine && (
            <p className="text-[11px] leading-relaxed text-[var(--text-muted)]">
              <span className={SECTION_LABEL}>词形变化</span>
              <span className="ml-2">{formLine}</span>
            </p>
          )}

          {!isLoading && entry && translationLines.length > 0 && (
            <span className={`${SECTION_LABEL} block`}>英文释义</span>
          )}

          {!isLoading &&
            entry &&
            entry.meanings.map((meaning, meaningIdx) => (
              <div key={meaningIdx} className="space-y-2.5">
                <div className="flex items-center gap-2">
                  <span className="border border-[var(--accent-border)] bg-[var(--accent-soft)] px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-[var(--accent)]">
                    {meaning.partOfSpeech}
                  </span>
                  <span className="h-px flex-1 bg-[var(--border-color)]" />
                </div>

                <ol className="space-y-3">
                  {meaning.definitions.slice(0, 4).map((definition, definitionIdx) => (
                    <li key={definitionIdx} className="flex gap-2">
                      <span className="pt-0.5 font-mono text-[11px] tabular-nums text-[var(--border-strong)]">
                        {definitionIdx + 1}.
                      </span>
                      <div className="min-w-0">
                        <p className="text-[13px] leading-relaxed text-[var(--text-main)]">
                          {definition.definition}
                        </p>
                        {definition.example && (
                          <p className="mt-1 border-l-2 border-[var(--accent-border)] pl-2 text-[12px] leading-relaxed text-[var(--text-muted)] italic">
                            “{definition.example}”
                          </p>
                        )}
                      </div>
                    </li>
                  ))}
                </ol>
              </div>
            ))}
        </div>

        {/* Sentence in context */}
        {surroundingSentence && (
          <div className="shrink-0 border-t border-[var(--border-color)] px-4 py-3">
            <div className="flex items-center justify-between gap-2">
              <span className={`${SECTION_LABEL} flex items-center gap-1`}>
                <Sparkles className="h-3 w-3" />
                当前所在句子
              </span>
              <button
                type="button"
                onClick={() => setSentenceAnalysisOpen(true, surroundingSentence)}
                className="text-[11px] font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline"
              >
                AI 拆解语法 →
              </button>
            </div>
            <p className="mt-2 line-clamp-3 font-serif text-[12px] leading-relaxed text-[var(--text-muted)] italic">
              “{surroundingSentence}”
            </p>
          </div>
        )}

        {/* Actions */}
        <div className="flex shrink-0 items-center gap-2 border-t border-[var(--border-color)] bg-[var(--bg-subtle)] px-4 py-3">
          {currentStatus === 'learning' ? (
            <button
              type="button"
              onClick={() => markKnown(word)}
              className={`${FOOTER_BUTTON} border-[var(--success)] text-[var(--success)] hover:bg-[var(--bg-hover)]`}
            >
              <Check className="h-3.5 w-3.5" />
              <span>标记为已会</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => markLearning(word)}
              className={`${FOOTER_BUTTON} border-[var(--highlight-border)] bg-[var(--highlight-bg)] text-[var(--highlight-text)] hover:opacity-90`}
            >
              <BookOpen className="h-3.5 w-3.5" />
              <span>标为生词</span>
            </button>
          )}

          <button
            type="button"
            onClick={() => removeWord(word)}
            className="flex h-9 w-9 items-center justify-center border border-[var(--border-color)] text-[var(--text-muted)] transition-colors hover:border-[var(--danger)] hover:text-[var(--danger)]"
            title="移出词汇库（恢复未标记状态）"
            aria-label="移出词汇库"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </aside>
    </>
  );
};


