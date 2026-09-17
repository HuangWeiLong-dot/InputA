import React from 'react';
import { BookOpen, BookMarked, Sun, Moon, Coffee, Sparkles } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import type { ReaderTheme } from '../store/useReaderStore';
import { useVocabularyStore } from '../store/useVocabularyStore';

/** Shared look of the square header controls. */
const CONTROL =
  'flex h-8 items-center gap-1.5 border border-[var(--border-color)] px-2.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--text-main)] transition-colors hover:bg-[var(--bg-hover)]';

const THEME_OPTIONS: Array<{ id: ReaderTheme; label: string; icon: React.ReactNode }> = [
  { id: 'light', label: '明亮模式', icon: <Sun className="h-3.5 w-3.5" /> },
  { id: 'sepia', label: '羊皮纸模式', icon: <Coffee className="h-3.5 w-3.5" /> },
  { id: 'dark', label: '夜间模式', icon: <Moon className="h-3.5 w-3.5" /> },
];

export const Header: React.FC = () => {
  const {
    currentBook,
    currentChapterIndex,
    theme,
    setTheme,
    fontSize,
    setFontSize,
    setBookCatalogOpen,
    setVocabularyOpen,
    setSentenceAnalysisOpen,
  } = useReaderStore();

  const words = useVocabularyStore((state) => state.words);
  const learningCount = Object.values(words).filter((status) => status === 'learning').length;
  const knownCount = Object.values(words).filter((status) => status === 'known').length;

  const currentChapterTitle = currentBook?.chapters[currentChapterIndex]?.title || '—';

  return (
    <header className="sticky top-0 z-30 border-b border-[var(--border-color)] bg-[var(--bg-surface)]">
      <div className="mx-auto flex h-14 max-w-[56rem] items-center justify-between gap-4 px-4 sm:px-6">
        {/* Left: catalog trigger + current book */}
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={() => setBookCatalogOpen(true)}
            className={CONTROL}
            title="更换读物或搜索 Gutendex"
          >
            <BookOpen className="h-3.5 w-3.5 text-[var(--accent)]" />
            <span className="hidden sm:inline">读物库</span>
          </button>

          <div className="min-w-0 leading-tight">
            <h1
              className="truncate text-[13px] font-semibold text-[var(--text-strong)]"
              title={currentBook?.title}
            >
              {currentBook?.title || '正在载入读物…'}
            </h1>
            <p className="truncate text-[11px] text-[var(--text-muted)]">{currentChapterTitle}</p>
          </div>
        </div>

        {/* Right: actions */}
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={() => setSentenceAnalysisOpen(true)}
            className="hidden h-8 items-center gap-1.5 border border-[var(--accent-border)] bg-[var(--accent-soft)] px-2.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--accent)] transition-colors hover:bg-[var(--bg-hover)] sm:flex"
            title="AI 长难句语法拆解"
          >
            <Sparkles className="h-3.5 w-3.5" />
            <span>AI 分析</span>
          </button>

          <button
            type="button"
            onClick={() => setVocabularyOpen(true)}
            className={CONTROL}
            title="生词本与词汇库"
          >
            <BookMarked className="h-3.5 w-3.5 text-[var(--highlight-border)]" />
            <span className="font-mono tabular-nums">{learningCount}</span>
            <span className="hidden md:inline">生词</span>
            <span className="text-[var(--border-strong)]">/</span>
            <span className="font-mono tabular-nums">{knownCount}</span>
            <span className="hidden md:inline">已会</span>
          </button>

          {/* Theme switch: the active option is inverted for maximum contrast */}
          <div className="flex border border-[var(--border-color)]">
            {THEME_OPTIONS.map((option) => (
              <button
                key={option.id}
                type="button"
                onClick={() => setTheme(option.id)}
                aria-label={option.label}
                title={option.label}
                className={`flex h-8 w-8 items-center justify-center transition-colors ${
                  theme === option.id
                    ? 'bg-[var(--text-strong)] text-[var(--bg-surface)]'
                    : 'text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-strong)]'
                }`}
              >
                {option.icon}
              </button>
            ))}
          </div>

          {/* Font size */}
          <div className="hidden items-center border border-[var(--border-color)] lg:flex">
            <button
              type="button"
              onClick={() => setFontSize(Math.max(14, fontSize - 2))}
              className="h-8 w-8 text-[12px] font-semibold text-[var(--text-main)] transition-colors hover:bg-[var(--bg-hover)]"
              title="缩小字号"
            >
              A−
            </button>
            <span className="h-8 w-px bg-[var(--border-color)]" />
            <span className="flex h-8 items-center px-2 font-mono text-[11px] tabular-nums text-[var(--text-muted)]">
              {fontSize}
            </span>
            <span className="h-8 w-px bg-[var(--border-color)]" />
            <button
              type="button"
              onClick={() => setFontSize(Math.min(26, fontSize + 2))}
              className="h-8 w-8 text-[13px] font-semibold text-[var(--text-main)] transition-colors hover:bg-[var(--bg-hover)]"
              title="放大字号"
            >
              A+
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};
