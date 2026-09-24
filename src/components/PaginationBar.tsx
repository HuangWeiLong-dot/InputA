import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, CheckCircle } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { tokenizeText, extractWordsFromTokens } from '../utils/tokenizer';
import { BTN_NAV, SCHEME_NAV_NEXT, SCHEME_NAV_PREV } from './ui';

export const PaginationBar: React.FC = () => {
  const {
    currentPageIndex,
    pages,
    currentChapterIndex,
    currentBook,
    nextPage,
    prevPage,
    isBookCatalogOpen,
    isVocabularyOpen,
    isSentenceAnalysisOpen,
    isWordExplosionOpen,
  } = useReaderStore();
  const { markPageWordsAsMastered } = useVocabularyStore();

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

  const handleNextPage = useCallback(() => {
    // Turning the page past the end would otherwise still file this page's
    // words as known without anything having moved.
    if (isLastPage) return;

    // Auto-file every word of this page that the reader did not click on.
    // Turn the page without reading a word and we take it as known: it becomes
    // 掌握, i.e. no colour in the text. Words the reader already gave a level
    // keep it.
    const wordsOnPage = extractWordsFromTokens(tokenizeText(pages[currentPageIndex] || ''));
    const newlyMastered = markPageWordsAsMastered(wordsOnPage);

    if (newlyMastered > 0) {
      setToastMessage(`本页 ${newlyMastered} 个未点击的词已自动记为「已掌握」`);
      if (toastTimer.current !== null) {
        window.clearTimeout(toastTimer.current);
      }
      toastTimer.current = window.setTimeout(() => setToastMessage(null), 2600);
    }

    nextPage();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [isLastPage, pages, currentPageIndex, markPageWordsAsMastered, nextPage]);

  const handlePrevPage = useCallback(() => {
    if (isFirstPage) return;
    prevPage();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [isFirstPage, prevPage]);

  /**
   * Arrow keys turn pages so the reader never has to leave the text. Skipped
   * while a modal is open or a field has focus, where the keys belong to that
   * widget (the vocabulary search box, the paste-your-own-article textarea).
   */
  const isAnyModalOpen =
    isBookCatalogOpen || isVocabularyOpen || isSentenceAnalysisOpen || isWordExplosionOpen;

  useEffect(() => {
    if (isAnyModalOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.metaKey || event.ctrlKey || event.altKey) return;

      const target = event.target as HTMLElement | null;
      if (
        target?.isContentEditable ||
        ['INPUT', 'TEXTAREA', 'SELECT'].includes(target?.tagName ?? '')
      ) {
        return;
      }

      if (event.key === 'ArrowRight' || event.key === 'PageDown') {
        event.preventDefault();
        handleNextPage();
      } else if (event.key === 'ArrowLeft' || event.key === 'PageUp') {
        event.preventDefault();
        handlePrevPage();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isAnyModalOpen, handleNextPage, handlePrevPage]);

  return (
    <footer className="sticky bottom-0 z-20 border-t border-[var(--border-color)] bg-[var(--bg-surface)]">
      <div className="relative mx-auto max-w-[46rem] px-5 py-3.5 sm:px-6">
        {toastMessage && (
          <div
            role="status"
            aria-live="polite"
            className="absolute bottom-full left-1/2 mb-2 flex -translate-x-1/2 items-center gap-2 border border-[var(--accent-border)] bg-[var(--accent-soft)] px-4 py-2 text-[13px] font-semibold whitespace-nowrap text-[var(--accent)] animate-toast-in"
          >
            <CheckCircle className="h-4 w-4" />
            <span>{toastMessage}</span>
          </div>
        )}

        <div className="flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={handlePrevPage}
            disabled={isFirstPage}
            className={`${BTN_NAV} ${SCHEME_NAV_PREV}`}
            title="上一页（快捷键 ← 或 PageUp）"
          >
            <ChevronLeft className="h-5 w-5" />
            <span>上一页</span>
          </button>

          <div className="text-center leading-tight">
            <p className="font-mono text-[15px] tracking-wide tabular-nums text-[var(--text-main)]">
              {currentPageIndex + 1}
              <span className="mx-1.5 text-[var(--border-strong)]">/</span>
              {totalPages}
              <span className="ml-1.5 text-[var(--text-muted)]">页</span>
            </p>
            {currentBook && currentBook.chapters.length > 1 && (
              <p className="mt-0.5 text-[12px] tracking-[0.1em] text-[var(--text-muted)]">
                第 {currentChapterIndex + 1} / {currentBook.chapters.length} 章
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={handleNextPage}
            disabled={isLastPage}
            className={`${BTN_NAV} ${SCHEME_NAV_NEXT}`}
            title="翻到下一页（本页所有未点击的词将自动记为已掌握）· 快捷键 → 或 PageDown"
          >
            <span>下一页</span>
            <ChevronRight className="h-5 w-5" />
          </button>
        </div>
      </div>
    </footer>
  );
};
