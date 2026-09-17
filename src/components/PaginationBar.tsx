import React, { useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, CheckCircle } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { tokenizeText, extractWordsFromTokens } from '../utils/tokenizer';

const NAV_BUTTON =
  'flex h-9 items-center gap-1.5 border px-3 text-[11px] font-semibold uppercase tracking-[0.08em] transition-colors disabled:cursor-not-allowed disabled:opacity-35';

export const PaginationBar: React.FC = () => {
  const { currentPageIndex, pages, currentChapterIndex, currentBook, nextPage, prevPage } =
    useReaderStore();
  const { markPageWordsAsKnown } = useVocabularyStore();

  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimer = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (toastTimer.current !== null) {
        window.clearTimeout(toastTimer.current);
      }
    };
  }, []);

  const totalPages = Math.max(1, pages.length);
  const isFirstPage = currentPageIndex === 0 && currentChapterIndex === 0;
  const isLastPage =
    currentPageIndex === pages.length - 1 &&
    (!currentBook || currentChapterIndex === currentBook.chapters.length - 1);

  const handleNextPage = () => {
    // Auto-file every word of this page that the reader did not click on.
    const wordsOnPage = extractWordsFromTokens(tokenizeText(pages[currentPageIndex] || ''));
    const newlyKnown = markPageWordsAsKnown(wordsOnPage);

    if (newlyKnown > 0) {
      setToastMessage(`本页 ${newlyKnown} 个未点击的词已自动记为「已会」`);
      if (toastTimer.current !== null) {
        window.clearTimeout(toastTimer.current);
      }
      toastTimer.current = window.setTimeout(() => setToastMessage(null), 2600);
    }

    nextPage();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handlePrevPage = () => {
    prevPage();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <footer className="sticky bottom-0 z-20 border-t border-[var(--border-color)] bg-[var(--bg-surface)]">
      <div className="relative mx-auto max-w-[42rem] px-5 py-3 sm:px-6">
        {toastMessage && (
          <div
            role="status"
            aria-live="polite"
            className="absolute bottom-full left-1/2 mb-2 flex -translate-x-1/2 items-center gap-1.5 border border-[var(--accent-border)] bg-[var(--accent-soft)] px-3 py-1.5 text-[11px] font-semibold whitespace-nowrap text-[var(--accent)] animate-toast-in"
          >
            <CheckCircle className="h-3.5 w-3.5" />
            <span>{toastMessage}</span>
          </div>
        )}

        <div className="flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={handlePrevPage}
            disabled={isFirstPage}
            className={`${NAV_BUTTON} border-[var(--border-color)] text-[var(--text-main)] enabled:hover:bg-[var(--bg-hover)]`}
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            <span>上一页</span>
          </button>

          <div className="text-center leading-tight">
            <p className="font-mono text-[11px] tracking-wide tabular-nums text-[var(--text-main)]">
              {currentPageIndex + 1}
              <span className="mx-1 text-[var(--border-strong)]">/</span>
              {totalPages}
              <span className="ml-1.5 text-[var(--text-muted)]">页</span>
            </p>
            {currentBook && currentBook.chapters.length > 1 && (
              <p className="text-[10px] tracking-[0.1em] text-[var(--text-muted)]">
                第 {currentChapterIndex + 1} / {currentBook.chapters.length} 章
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={handleNextPage}
            disabled={isLastPage}
            className={`${NAV_BUTTON} border-[var(--text-strong)] bg-[var(--text-strong)] text-[var(--bg-surface)] enabled:hover:opacity-85`}
            title="翻到下一页（本页所有未点击的词将自动记为已会）"
          >
            <span>下一页</span>
            <ChevronRight className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </footer>
  );
};
