import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, CheckCircle } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { tokenizeText, extractWordsFromTokens } from '../utils/tokenizer';
import { BTN_NAV, SCHEME_NAV_NEXT, SCHEME_NAV_PREV } from './ui';

/**
 * How long a toast that offers an undo stays up.
 *
 * The plain page-turn line gets 2.6s and carries no action; an undo needs the
 * reader to finish reading, understand and then decide, which 2.6s does not
 * cover. Android settled on 6s for the same reason (ReaderScreen.kt), so the
 * two stay the same length.
 */
const UNDO_TOAST_MS = 6000;
const PLAIN_TOAST_MS = 2600;

interface Toast {
  text: string;
  /** Words this toast offers to undo, or null for a toast with no action. */
  undo: string[] | null;
}

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
  const { markPageWordsAsMastered, undoMasteredWords } = useVocabularyStore();

  const [toast, setToast] = useState<Toast | null>(null);
  const toastTimer = useRef<number | null>(null);

  /**
   * Which book this session has been read to the end. Held here rather than in a
   * store: it is a plain "we already did this" flag with no state to persist,
   * and keying it by book id keeps it from leaking into the next book. A second
   * click would be harmless anyway (nothing left to file), but the disabled
   * 「已读完」 is what tells the reader the button did anything.
   */
  const [finishedBookId, setFinishedBookId] = useState<string | null>(null);

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
  const isFinished = currentBook !== null && finishedBookId === currentBook.id;

  const showToast = useCallback((next: Toast) => {
    setToast(next);
    if (toastTimer.current !== null) {
      window.clearTimeout(toastTimer.current);
    }
    toastTimer.current = window.setTimeout(
      () => setToast(null),
      next.undo ? UNDO_TOAST_MS : PLAIN_TOAST_MS,
    );
  }, []);

  /**
   * The words this page would file. Both handlers below share it so the
   * page-turn rule and the finish button cannot drift apart - they are the same
   * rule (see markPageWordsAsMastered), reached by a different gesture.
   */
  const pageWords = useCallback(
    () => extractWordsFromTokens(tokenizeText(pages[currentPageIndex] || '')),
    [pages, currentPageIndex],
  );

  const handleNextPage = useCallback(() => {
    // Turning the page past the end would otherwise still file this page's
    // words as known without anything having moved.
    if (isLastPage) return;

    // Auto-file every word of this page that the reader did not click on.
    // Turn the page without reading a word and we take it as known: it becomes
    // 掌握, i.e. no colour in the text. Words the reader already gave a level
    // keep it.
    const newlyMastered = markPageWordsAsMastered(pageWords());

    if (newlyMastered.length > 0) {
      showToast({
        text: `本页 ${newlyMastered.length} 个未点击的词已自动记为「已掌握」`,
        undo: null,
      });
    }

    nextPage();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [isLastPage, pageWords, markPageWordsAsMastered, nextPage, showToast]);

  /**
   * 「完成阅读」 - the last page's version of turning the page.
   *
   * There is no page after the last one, so without this the book's final page
   * could never be processed: its unclicked words would stay unfiled forever.
   * The rule applied is exactly the page-turn rule, so the two entry points
   * share `pageWords()` and `markPageWordsAsMastered`; the only difference is
   * that nothing moves afterwards.
   */
  const handleFinishReading = useCallback(() => {
    const newlyMastered = markPageWordsAsMastered(pageWords());
    setFinishedBookId(currentBook?.id ?? null);

    if (newlyMastered.length > 0) {
      showToast({
        text: `本页 ${newlyMastered.length} 个未点击的词已记为「已掌握」`,
        undo: newlyMastered,
      });
    }
  }, [currentBook, pageWords, markPageWordsAsMastered, showToast]);

  const handleUndoMastery = useCallback(() => {
    if (toast?.undo) {
      undoMasteredWords(toast.undo);
    }
    // Undoing the finish has to un-finish it too. Otherwise the button stays a
    // disabled 「已读完」 while the page is dashed again — and a dead button is
    // no way to file those words a second time. The undo only ever appears on
    // the finish toast, so this is exactly the action being reversed.
    setFinishedBookId(null);
    if (toastTimer.current !== null) {
      window.clearTimeout(toastTimer.current);
    }
    setToast(null);
  }, [toast, undoMasteredWords]);

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
        {toast && (
          <div
            role="status"
            aria-live="polite"
            className="absolute bottom-full left-1/2 mb-2 flex -translate-x-1/2 items-center gap-2 border border-[var(--accent-border)] bg-[var(--accent-soft)] px-4 py-2 text-[13px] font-semibold whitespace-nowrap text-[var(--accent)] animate-toast-in"
          >
            <CheckCircle className="h-4 w-4" />
            <span>{toast.text}</span>
            {toast.undo && (
              <button
                type="button"
                onClick={handleUndoMastery}
                className="ml-1 border-l border-[var(--accent-border)] pl-2.5 underline underline-offset-2"
              >
                撤销
              </button>
            )}
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

          {/* The last page has nothing after it, so 「下一页」 would be a dead
              button and the page's unclicked words would never be filed. It
              becomes the one forward action that page can actually take. */}
          {isLastPage ? (
            <button
              type="button"
              onClick={handleFinishReading}
              disabled={isFinished}
              className={`${BTN_NAV} ${SCHEME_NAV_NEXT}`}
              title="读完了这一页（本页所有未点击的词将记为已掌握）"
            >
              <CheckCircle className="h-5 w-5" />
              <span>{isFinished ? '已读完' : '完成阅读'}</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={handleNextPage}
              className={`${BTN_NAV} ${SCHEME_NAV_NEXT}`}
              title="翻到下一页（本页所有未点击的词将自动记为已掌握）· 快捷键 → 或 PageDown"
            >
              <span>下一页</span>
              <ChevronRight className="h-5 w-5" />
            </button>
          )}
        </div>
      </div>
    </footer>
  );
};
