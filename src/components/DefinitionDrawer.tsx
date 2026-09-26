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
import { UNKNOWN_PART_OF_SPEECH } from '../services/dictionaryApi';
import type { DictionarySource } from '../services/dictionaryApi';
import { speakWord } from '../services/ttsService';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { useReaderStore } from '../store/useReaderStore';
import { WORD_LEVELS, WORD_LEVEL_BG, WORD_LEVEL_LABELS, isLevel } from '../utils/wordLevel';
import { NotesSection } from './NotesSection';
import { ExampleSentencesSection } from './ExampleSentencesSection';
import {
  BADGE,
  BTN,
  BTN_DANGER_ICON,
  BTN_GHOST,
  BTN_ON_HIGHLIGHT,
  BTN_SUCCESS,
  SECTION_LABEL,
  SEGMENT,
  SEGMENT_BOX,
} from './ui';

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

/** Metalinguistic detail (phonetic, frequency ranks, word forms): readable but
 *  deliberately quieter than the definitions themselves. */
const META = 'font-mono text-[12px] leading-relaxed text-[var(--text-muted)]';
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
  const { words, setWordLevel, markMastered, removeWord } = useVocabularyStore();
  const { setSentenceAnalysisOpen, setWordExplosionOpen } = useReaderStore();

  if (!isOpen || !word) return null;

  const currentStatus: WordStatus | 'unknown' = words[word.toLowerCase()] || 'unknown';
  // 界面上只显示档位名（「生词」），不显示档位数字；数字留在 title / aria-label 提示里。
  const statusText = isLevel(currentStatus)
    ? WORD_LEVEL_LABELS[currentStatus]
    : currentStatus === 'mastered'
      ? '已掌握'
      : '未标记';
  const statusClass = isLevel(currentStatus)
    ? `${WORD_LEVEL_BG[currentStatus]} border-[var(--border-color)] text-[var(--text-main)]`
    : currentStatus === 'mastered'
      ? 'border-[var(--success)] text-[var(--success)]'
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

  const renderWordLink = (target: string) =>
    onLookupWord ? (
      <button
        type="button"
        onClick={() => onLookupWord(target)}
        className="mx-1 font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline"
      >
        {target}
      </button>
    ) : (
      <span className="mx-1 text-[var(--text-main)]">{target}</span>
    );

  return (
    <>
      {/*
        Below lg the panel is a bottom sheet over a dimmed page, because a side
        panel at those widths would squeeze the text column down to ~25
        characters per line. From lg up it docks right and the column narrows.
      */}
      <div onClick={onClose} className="fixed inset-0 z-30 bg-black/40 lg:hidden animate-overlay-in" />

      {/* dvh 而不是 vh：抽屉贴底，手机键盘弹起时 vh 不收缩，底部内容会被顶出可视区。 */}
      <aside className="fixed left-0 right-0 bottom-0 z-40 flex max-h-[86dvh] flex-col border-t border-[var(--border-color)] bg-[var(--bg-surface)] animate-drawer-bottom lg:top-16 lg:left-auto lg:right-0 lg:bottom-0 lg:max-h-none lg:w-[var(--drawer-width)] lg:border-t-0 lg:border-l lg:animate-drawer-right">
        {/* Status bar */}
        <div className="flex shrink-0 items-center justify-between gap-2 border-b border-[var(--border-color)] px-5 py-2.5">
          <div className="flex min-w-0 items-center gap-2.5">
            <span className={`${BADGE} uppercase ${statusClass}`}>
              {statusText}
            </span>
            {source && (
              <span className={META}>来源 {SOURCE_LABELS[source] ?? source}</span>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className={BTN_GHOST}
            aria-label="关闭释义面板"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Headword */}
        <div className="shrink-0 border-b border-[var(--border-color)] bg-[var(--bg-subtle)] px-5 py-5">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="truncate font-serif text-3xl leading-tight font-bold text-[var(--text-strong)]">
                {word}
              </h3>
              {/*
                Phonetic and the proficiency scale share one row: the scale is
                the control for what the reader thinks of this word, and it sits
                with the word itself rather than down in the footer.
              */}
              <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-2">
                {entry?.phonetic && (
                  <p className="font-mono text-[14px] text-[var(--text-muted)]">
                    /{entry.phonetic}/
                  </p>
                )}

                <div className="flex items-center gap-2.5">
                  <div
                    className={SEGMENT_BOX}
                    role="group"
                    aria-label="熟练度（1 最浅，5 最深）"
                  >
                    {WORD_LEVELS.map((level) => {
                      const selected = currentStatus === level;
                      return (
                        <button
                          key={level}
                          type="button"
                          onClick={() => setWordLevel(word, level)}
                          aria-pressed={selected}
                          aria-label={`熟练度 ${level}：${WORD_LEVEL_LABELS[level]}`}
                          title={`${level} · ${WORD_LEVEL_LABELS[level]}`}
                          className={`${SEGMENT} h-8 w-9 ${WORD_LEVEL_BG[level]} ${
                            selected
                              ? // 反白描边画在按钮内侧：outline-offset 为正时描边会溢出
                                // 到相邻色块和外框上，看着像整组错位。
                                'outline-2 outline-offset-[-2px] outline-[var(--text-strong)]'
                              : 'hover:opacity-75 active:opacity-60'
                          }`}
                        >
                          <span className="sr-only">{level}</span>
                        </button>
                      );
                    })}
                  </div>

                  <span className="text-[13px] font-semibold whitespace-nowrap text-[var(--text-muted)]">
                    {statusText}
                  </span>
                </div>
              </div>
              {headword && (
                <p className="mt-1.5 text-[13px] text-[var(--text-muted)]">
                  词库词条{renderWordLink(headword)}
                </p>
              )}
              {lemma && (
                <p className="mt-1.5 text-[13px] text-[var(--text-muted)]">
                  原形{renderWordLink(lemma)}
                  {extra?.inflection ? `（${extra.inflection}）` : ''}
                </p>
              )}
            </div>

            <button
              type="button"
              onClick={() => speakWord(word, entry?.audioUrl)}
              className={`flex h-11 w-11 shrink-0 items-center justify-center border transition-colors ${
                entry?.audioUrl
                  ? 'border-[var(--accent-border)] bg-[var(--accent-soft)] text-[var(--accent)]'
                  : 'border-[var(--border-color)] text-[var(--text-main)] hover:bg-[var(--bg-hover)]'
              }`}
              title="朗读（优先词典音源，其次系统 TTS）"
              aria-label="朗读单词"
            >
              <Volume2 className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Meanings / states */}
        <div className="flex-1 space-y-6 overflow-y-auto px-5 py-5">
          {isLoading && (
            <div className="flex flex-col items-center justify-center gap-2.5 py-12 text-[var(--text-muted)]">
              <Loader2 className="h-6 w-6 animate-spin text-[var(--accent)]" />
              <p className="text-[14px]">正在查询词典…</p>
              <p className="font-mono text-[12px]">
                本地词库 → dictionaryapi.dev → Wiktionary → Datamuse
              </p>
            </div>
          )}

          {!isLoading && isUnavailable && !entry && (
            <div className="border border-[var(--highlight-border)] bg-[var(--highlight-bg)] p-5 text-[var(--highlight-text)]">
              <p className="flex items-center gap-2 text-[14px] font-semibold">
                <AlertTriangle className="h-4 w-4" />
                词典服务暂时无法访问
              </p>
              <p className="mt-2 text-[13px] leading-relaxed">
                已依次尝试本地词库（需要后端与 data/stardict.db）以及 dictionaryapi.dev、Wiktionary、
                Datamuse 三个在线来源，均未取得响应（可能是网络或跨域限制）。该词已记入生词本，可稍后重试。
              </p>
              <button
                type="button"
                onClick={onRetry}
                className={`${BTN_ON_HIGHLIGHT} mt-3`}
              >
                <RefreshCw className="h-4 w-4" />
                <span>重新查询</span>
              </button>
            </div>
          )}

          {!isLoading && !isUnavailable && !entry && (
            <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] p-5">
              <p className="text-[14px] font-semibold text-[var(--text-strong)]">词典均未收录该词</p>
              <p className="mt-2 text-[13px] leading-relaxed text-[var(--text-muted)]">
                可能是专有名词、罕见词或词形变化。它已记入生词本，之后在文章中遇到仍会保持高亮。
              </p>
            </div>
          )}

          {/* Offline ECDICT extras: exam tags + Chinese definitions. */}
          {!isLoading && examTags.length > 0 && (
            <div className="flex flex-wrap items-center gap-2">
              {examTags.map((tag) => (
                <span
                  key={tag}
                  className="border border-[var(--accent-border)] bg-[var(--accent-soft)] px-2 py-1 text-[12px] font-semibold text-[var(--accent)]"
                >
                  {tag}
                </span>
              ))}
            </div>
          )}

          {!isLoading && translationLines.length > 0 && (
            <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] px-4 py-3.5">
              <span className={SECTION_LABEL}>中文释义</span>
              <div className="mt-2 space-y-1.5">
                {translationLines.map((line, lineIdx) => (
                  <p key={lineIdx} className="text-[15px] leading-relaxed text-[var(--text-main)]">
                    {line}
                  </p>
                ))}
              </div>
              {(posLine || metaBits) && (
                <p className={`mt-2.5 ${META}`}>
                  {[posLine && `词性分布 ${posLine}`, metaBits].filter(Boolean).join('　|　')}
                </p>
              )}
            </div>
          )}

          {!isLoading && !lemma && formLine && (
            <p className="text-[13px] leading-relaxed text-[var(--text-muted)]">
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
              <div key={meaningIdx} className="space-y-3">
                {/*
                  A group whose source stated no part of speech gets the divider
                  but no badge: "UNKNOWN" told the reader nothing and read like
                  a defect, whereas the definitions themselves are still useful.
                */}
                <div className="flex items-center gap-2.5">
                  {meaning.partOfSpeech !== UNKNOWN_PART_OF_SPEECH && (
                    <span className="border border-[var(--accent-border)] bg-[var(--accent-soft)] px-2 py-1 text-[12px] font-semibold uppercase tracking-[0.14em] text-[var(--accent)]">
                      {meaning.partOfSpeech}
                    </span>
                  )}
                  <span className="h-px flex-1 bg-[var(--border-color)]" />
                </div>

                <ol className="space-y-4">
                  {meaning.definitions.slice(0, 4).map((definition, definitionIdx) => (
                    <li key={definitionIdx} className="flex gap-2.5">
                      <span className="pt-0.5 font-mono text-[13px] tabular-nums text-[var(--border-strong)]">
                        {definitionIdx + 1}.
                      </span>
                      <div className="min-w-0">
                        <p className="text-[15px] leading-relaxed text-[var(--text-main)]">
                          {definition.definition}
                        </p>
                        {definition.example && (
                          <p className="mt-1.5 border-l-2 border-[var(--accent-border)] pl-2.5 text-[14px] leading-relaxed text-[var(--text-muted)] italic">
                            “{definition.example}”
                          </p>
                        )}
                      </div>
                    </li>
                  ))}
                </ol>
              </div>
            ))}

          {/*
            Notes and saved sentences live inside the scrolling body, not in a
            third shrink-0 band at the bottom: on a phone the drawer is capped at
            max-h-[86dvh], and another fixed band would squeeze this scroll area
            down to almost nothing.
          */}
          <NotesSection word={word} sentence={surroundingSentence} />
          <ExampleSentencesSection word={word} sentence={surroundingSentence} />
        </div>

        {/* Sentence in context */}
        {surroundingSentence && (
          <div className="shrink-0 border-t border-[var(--border-color)] px-5 py-4">
            <div className="flex items-center justify-between gap-2">
              <span className={`${SECTION_LABEL} flex items-center gap-1.5`}>
                <Sparkles className="h-3.5 w-3.5" />
                当前所在句子
              </span>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => setWordExplosionOpen(true, surroundingSentence)}
                  className="text-[13px] font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline"
                  title="列出这句里所有还没收录的词"
                >
                  本句生词 →
                </button>
                <button
                  type="button"
                  onClick={() => setSentenceAnalysisOpen(true, surroundingSentence)}
                  className="text-[13px] font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline"
                >
                  AI 拆解语法 →
                </button>
              </div>
            </div>
            <p className="mt-2 line-clamp-3 font-serif text-[14px] leading-relaxed text-[var(--text-muted)] italic">
              “{surroundingSentence}”
            </p>
          </div>
        )}

        {/*
          Mastery is reached by this button (green: it is an achievement, and the
          yellow highlight already means "生词" everywhere else in the app).
          Once mastered, the same slot offers the way back, to level 5.
        */}
        {/*
          flex-wrap: the buttons are flex-1 and their labels never wrap (BTN_BASE),
          so on a narrow bottom sheet the second one drops to its own line instead
          of the pair overflowing the panel.
        */}
        <div className="flex shrink-0 flex-wrap items-center gap-2.5 border-t border-[var(--border-color)] bg-[var(--bg-subtle)] px-5 py-4">
          {currentStatus === 'mastered' ? (
            <button
              type="button"
              onClick={() => setWordLevel(word, 5)}
              className={`${BTN} flex-1`}
              title="重新记为 5 级「生词」"
            >
              <BookOpen className="h-5 w-5" />
              <span>标为生词</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => markMastered(word)}
              className={`${BTN_SUCCESS} flex-1`}
            >
              <Check className="h-5 w-5" />
              <span>标记为已掌握</span>
            </button>
          )}

          <button
            type="button"
            onClick={() => removeWord(word)}
            className={BTN_DANGER_ICON}
            title="移出词汇库（恢复未标记状态）"
            aria-label="移出词汇库"
          >
            <Trash2 className="h-5 w-5" />
          </button>
        </div>
      </aside>
    </>
  );
};
