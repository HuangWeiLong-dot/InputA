import { create } from 'zustand';
import type { Book, ReadingProgress } from '../types/reader';
import { paginateText, WORDS_PER_PAGE } from '../utils/tokenizer';

const PROGRESS_STORAGE_KEY = 'language_reader_progress';
const SETTINGS_STORAGE_KEY = 'language_reader_settings';

export type ReaderTheme = 'light' | 'sepia' | 'dark';

interface ReaderSettings {
  fontSize: number; // in pixels, e.g. 18
  theme: ReaderTheme;
  lineHeight: number; // e.g. 1.8
}

interface ReaderState {
  currentBook: Book | null;
  currentChapterIndex: number;
  currentPageIndex: number;
  pages: string[]; // paginated text slices of the current chapter
  
  // Settings
  fontSize: number;
  theme: ReaderTheme;
  lineHeight: number;

  // Modals / Drawers
  isBookCatalogOpen: boolean;
  isVocabularyOpen: boolean;
  isSettingsOpen: boolean;
  isSentenceAnalysisOpen: boolean;
  selectedSentence: string;

  // Actions
  setCurrentBook: (book: Book, startChapter?: number, startPage?: number) => void;
  setChapterIndex: (index: number) => void;
  setPageIndex: (index: number) => void;
  setPages: (pages: string[]) => void;
  nextPage: () => boolean; // returns true if page changed
  prevPage: () => boolean;
  setFontSize: (size: number) => void;
  setTheme: (theme: ReaderTheme) => void;
  setLineHeight: (lh: number) => void;
  setBookCatalogOpen: (open: boolean) => void;
  setVocabularyOpen: (open: boolean) => void;
  setSentenceAnalysisOpen: (open: boolean, sentence?: string) => void;
}

const loadSavedProgress = (): Record<string, ReadingProgress> => {
  try {
    const raw = localStorage.getItem(PROGRESS_STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
};

const persistProgress = (progress: ReadingProgress) => {
  try {
    const all = loadSavedProgress();
    all[progress.bookId] = progress;
    localStorage.setItem(PROGRESS_STORAGE_KEY, JSON.stringify(all));
  } catch (err) {
    console.error('Failed to save reading progress:', err);
  }
};

const loadSettings = (): ReaderSettings => {
  try {
    const raw = localStorage.getItem(SETTINGS_STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }
  // 20px is the new baseline: comfortable for long-form reading on the wide
  // screens this app targets. Existing readers keep whatever they set, since
  // the saved value wins over this default.
  return { fontSize: 20, theme: 'sepia', lineHeight: 1.8 };
};

const persistSettings = (settings: ReaderSettings) => {
  try {
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings));
  } catch (err) {
    console.error('Failed to save reader settings:', err);
  }
};

export const useReaderStore = create<ReaderState>((set, get) => {
  const initialSettings = loadSettings();

  return {
    currentBook: null,
    currentChapterIndex: 0,
    currentPageIndex: 0,
    pages: [],

    fontSize: initialSettings.fontSize,
    theme: initialSettings.theme,
    lineHeight: initialSettings.lineHeight,

    isBookCatalogOpen: false,
    isVocabularyOpen: false,
    isSettingsOpen: false,
    isSentenceAnalysisOpen: false,
    selectedSentence: '',

    setCurrentBook: (book: Book, startChapter = 0, startPage = 0) => {
      // Check if progress is saved for this book
      const saved = loadSavedProgress()[book.id];
      const chapterIdx = saved ? saved.chapterIndex : startChapter;
      const pageIdx = saved ? saved.pageIndex : startPage;

      set({
        currentBook: book,
        currentChapterIndex: Math.min(chapterIdx, Math.max(0, book.chapters.length - 1)),
        currentPageIndex: pageIdx,
      });

      persistProgress({
        bookId: book.id,
        bookTitle: book.title,
        chapterIndex: chapterIdx,
        pageIndex: pageIdx,
        updatedAt: Date.now(),
      });
    },

    setChapterIndex: (index: number) => {
      const { currentBook } = get();
      if (!currentBook) return;
      const validIndex = Math.max(0, Math.min(index, currentBook.chapters.length - 1));
      
      set({
        currentChapterIndex: validIndex,
        currentPageIndex: 0,
      });

      persistProgress({
        bookId: currentBook.id,
        bookTitle: currentBook.title,
        chapterIndex: validIndex,
        pageIndex: 0,
        updatedAt: Date.now(),
      });
    },

    setPageIndex: (index: number) => {
      const { currentBook, currentChapterIndex, pages } = get();
      const validIndex = Math.max(0, Math.min(index, Math.max(0, pages.length - 1)));
      
      set({ currentPageIndex: validIndex });

      if (currentBook) {
        persistProgress({
          bookId: currentBook.id,
          bookTitle: currentBook.title,
          chapterIndex: currentChapterIndex,
          pageIndex: validIndex,
          updatedAt: Date.now(),
        });
      }
    },

    setPages: (pages: string[]) => {
      const { currentPageIndex } = get();
      const safePageIndex = Math.min(currentPageIndex, Math.max(0, pages.length - 1));
      set({ pages, currentPageIndex: safePageIndex });
    },

    nextPage: () => {
      const { currentPageIndex, pages, currentChapterIndex, currentBook } = get();
      if (currentPageIndex < pages.length - 1) {
        get().setPageIndex(currentPageIndex + 1);
        return true;
      } else if (currentBook && currentChapterIndex < currentBook.chapters.length - 1) {
        // Go to next chapter
        get().setChapterIndex(currentChapterIndex + 1);
        return true;
      }
      return false;
    },

    prevPage: () => {
      const { currentPageIndex, currentChapterIndex, currentBook } = get();
      if (currentPageIndex > 0) {
        get().setPageIndex(currentPageIndex - 1);
        return true;
      } else if (currentBook && currentChapterIndex > 0) {
        // Land on the last page of the previous chapter so no page is skipped.
        const previousChapterIndex = currentChapterIndex - 1;
        const previousChapter = currentBook.chapters[previousChapterIndex];
        const previousPages = paginateText(previousChapter?.content ?? '', WORDS_PER_PAGE);
        const lastPageIndex = Math.max(0, previousPages.length - 1);

        set({
          currentChapterIndex: previousChapterIndex,
          pages: previousPages,
          currentPageIndex: lastPageIndex,
        });

        persistProgress({
          bookId: currentBook.id,
          bookTitle: currentBook.title,
          chapterIndex: previousChapterIndex,
          pageIndex: lastPageIndex,
          updatedAt: Date.now(),
        });
        return true;
      }
      return false;
    },

    setFontSize: (fontSize: number) => {
      set({ fontSize });
      persistSettings({ fontSize, theme: get().theme, lineHeight: get().lineHeight });
    },

    setTheme: (theme: ReaderTheme) => {
      // App.tsx mirrors the theme onto <html data-theme="..."> in one effect.
      set({ theme });
      persistSettings({ fontSize: get().fontSize, theme, lineHeight: get().lineHeight });
    },

    setLineHeight: (lineHeight: number) => {
      set({ lineHeight });
      persistSettings({ fontSize: get().fontSize, theme: get().theme, lineHeight });
    },

    setBookCatalogOpen: (isBookCatalogOpen: boolean) => set({ isBookCatalogOpen }),
    setVocabularyOpen: (isVocabularyOpen: boolean) => set({ isVocabularyOpen }),
    setSentenceAnalysisOpen: (isSentenceAnalysisOpen: boolean, sentence = '') =>
      set({ isSentenceAnalysisOpen, selectedSentence: sentence }),
  };
});
