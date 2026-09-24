import React from 'react';
import { BookOpen, BookMarked, Sun, Moon, Coffee, Sparkles, Settings } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import type { ReaderTheme } from '../store/useReaderStore';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { isLevel } from '../utils/wordLevel';
import { BTN, BTN_ACCENT_SOFT, SEGMENT, SEGMENT_BOX, SEGMENT_OFF, SEGMENT_ON } from './ui';

const THEME_OPTIONS: Array<{ id: ReaderTheme; label: string; icon: React.ReactNode }> = [
  { id: 'light', label: '明亮模式', icon: <Sun className="h-5 w-5" /> },
  { id: 'sepia', label: '羊皮纸模式', icon: <Coffee className="h-5 w-5" /> },
  { id: 'dark', label: '夜间模式', icon: <Moon className="h-5 w-5" /> },
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
    setSettingsOpen,
  } = useReaderStore();

  const words = useVocabularyStore((state) => state.words);
  // 未掌握 = 仍带熟练度色阶（1-5 级）的词；已掌握的词正文里不再高亮。
  const activeCount = Object.values(words).filter((status) => isLevel(status)).length;
  const masteredCount = Object.values(words).filter((status) => status === 'mastered').length;

  const currentChapterTitle = currentBook?.chapters[currentChapterIndex]?.title || '—';

  return (
    <header className="sticky top-0 z-30 border-b border-[var(--border-color)] bg-[var(--bg-surface)]">
      <div className="mx-auto flex h-16 max-w-[62rem] items-center justify-between gap-3 px-4 sm:gap-4 sm:px-6">
        {/* Left: catalog trigger + current book */}
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={() => setBookCatalogOpen(true)}
            className={`${BTN} shrink-0`}
            title="更换或搜索 Gutendex"
          >
            <BookOpen className="h-5 w-5 text-[var(--accent)]" />
            <span className="hidden sm:inline">书本</span>
          </button>

          <div className="min-w-0 leading-tight">
            <h1
              className="truncate text-[15px] font-semibold text-[var(--text-strong)]"
              title={currentBook?.title}
            >
              {currentBook?.title || '正在加载…'}
            </h1>
            <p className="hidden truncate text-[13px] text-[var(--text-muted)] sm:block">
              {currentChapterTitle}
            </p>
          </div>
        </div>

        {/* Right: actions */}
        <div className="flex shrink-0 items-center gap-2">
          {/*
            Hidden below md to keep the header uncrowded. The responsive display
            lives on this wrapper rather than on the button: BTN_ACCENT_SOFT
            already sets `inline-flex`, and an unprefixed display utility beats
            `hidden` (Tailwind emits .hidden before .inline-flex), so putting
            both on the button would leave it visible at every width.
          */}
          <span className="max-md:hidden">
            <button
              type="button"
              onClick={() => setSentenceAnalysisOpen(true)}
              className={BTN_ACCENT_SOFT}
              title="AI 语法拆解"
            >
              <Sparkles className="h-5 w-5" />
              <span>AI 分析</span>
            </button>
          </span>

          <button
            type="button"
            onClick={() => setVocabularyOpen(true)}
            className={BTN}
            title="生词本与词汇库"
          >
            <BookMarked className="h-5 w-5 text-[var(--highlight-border)]" />
            <span className="font-mono tabular-nums">{activeCount}</span>
            <span className="hidden md:inline">未掌握</span>
            <span className="hidden text-[var(--border-strong)] sm:inline">/</span>
            <span className="hidden font-mono tabular-nums sm:inline">{masteredCount}</span>
            <span className="hidden md:inline">掌握</span>
          </button>

          <button
            type="button"
            onClick={() => setSettingsOpen(true)}
            className={BTN}
            title="设置（朗读引擎、阅读辅助）"
            aria-label="设置"
          >
            <Settings className="h-5 w-5 text-[var(--text-muted)]" />
          </button>

          {/* Theme switch: the active option is inverted for maximum contrast */}
          <div className={SEGMENT_BOX}>
            {THEME_OPTIONS.map((option) => (
              <button
                key={option.id}
                type="button"
                onClick={() => setTheme(option.id)}
                aria-label={option.label}
                title={option.label}
                className={`${SEGMENT} h-11 w-11 ${theme === option.id ? SEGMENT_ON : SEGMENT_OFF}`}
              >
                {option.icon}
              </button>
            ))}
          </div>

          {/* Font size */}
          <div className={`${SEGMENT_BOX} hidden lg:flex`}>
            <button
              type="button"
              onClick={() => setFontSize(Math.max(16, fontSize - 2))}
              className="flex h-11 w-11 items-center justify-center text-[15px] font-semibold text-[var(--text-main)] transition-colors hover:bg-[var(--bg-hover)] active:bg-[var(--bg-active)]"
              title="缩小字号"
            >
              A−
            </button>
            <span className="h-11 w-px bg-[var(--border-color)]" />
            <span className="flex h-11 w-11 items-center justify-center font-mono text-[13px] tabular-nums text-[var(--text-muted)]">
              {fontSize}
            </span>
            <span className="h-11 w-px bg-[var(--border-color)]" />
            <button
              type="button"
              onClick={() => setFontSize(Math.min(30, fontSize + 2))}
              className="flex h-11 w-11 items-center justify-center text-[17px] font-semibold text-[var(--text-main)] transition-colors hover:bg-[var(--bg-hover)] active:bg-[var(--bg-active)]"
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
