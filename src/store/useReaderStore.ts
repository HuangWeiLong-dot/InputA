import { create } from 'zustand';
import type { Book, ReadingProgress } from '../types/reader';
import { lastReadBookId, loadLastReadBook } from '../services/bookStorage';
import { paginateText, WORDS_PER_PAGE } from '../utils/tokenizer';

const PROGRESS_STORAGE_KEY = 'language_reader_progress';
const SETTINGS_STORAGE_KEY = 'language_reader_settings';

export type ReaderTheme = 'light' | 'sepia' | 'dark';

/**
 * 朗读引擎。
 *   edge    —— 后端代理 Edge TTS（/api/tts），音质最好，需要后端在跑
 *   custom  —— 用户自己的 TTS 服务地址，约定是「GET 返回可播放的音频字节」
 *   browser —— 词典音源，退而求其次用浏览器语音合成
 */
export type TtsProvider = 'edge' | 'custom' | 'browser';

interface ReaderSettings {
  fontSize: number; // in pixels, e.g. 18
  theme: ReaderTheme;
  lineHeight: number; // e.g. 1.8
  ttsProvider: TtsProvider;
  /** Edge 音色名；留空表示按正文语言自动选。 */
  ttsVoice: string;
  /** Edge 语速，形如 '+10%'；'default' 为原速。 */
  ttsRate: string;
  /** 自定义 TTS 地址模板，支持 {text} 与 {lang} 占位符。 */
  ttsCustomUrlTemplate: string;
  /** 仿生阅读：加粗每个词的前 40%，给眼睛一个锚点。 */
  bionicEnabled: boolean;
  /** 阅读标尺：指针所在段落保持清晰，其余变暗。 */
  readingRulerEnabled: boolean;
}

/**
 * 新增字段必须在这里给出默认值，并由 loadSettings 逐字段兜底 ——
 * 老用户 localStorage 里的设置没有这些键。
 */
const DEFAULT_SETTINGS: ReaderSettings = {
  // 20px is the new baseline: comfortable for long-form reading on the wide
  // screens this app targets. Existing readers keep whatever they set, since
  // the saved value wins over this default.
  fontSize: 20,
  theme: 'sepia',
  lineHeight: 1.8,
  ttsProvider: 'edge',
  ttsVoice: '',
  ttsRate: 'default',
  ttsCustomUrlTemplate: '',
  bionicEnabled: false,
  readingRulerEnabled: false,
};

interface ReaderState {
  currentBook: Book | null;
  currentChapterIndex: number;
  currentPageIndex: number;
  pages: string[]; // paginated text slices of the current chapter
  
  // Settings
  fontSize: number;
  theme: ReaderTheme;
  lineHeight: number;
  ttsProvider: TtsProvider;
  ttsVoice: string;
  ttsRate: string;
  ttsCustomUrlTemplate: string;
  bionicEnabled: boolean;
  readingRulerEnabled: boolean;

  // Modals / Drawers
  isBookCatalogOpen: boolean;
  isVocabularyOpen: boolean;
  isSettingsOpen: boolean;
  isSentenceAnalysisOpen: boolean;
  selectedSentence: string;
  /** 单词爆炸面板：列出某一句里所有还没收录的词。 */
  isWordExplosionOpen: boolean;
  explosionSentence: string;

  // Actions
  setCurrentBook: (book: Book, startChapter?: number, startPage?: number) => void;
  /**
   * 尝试恢复上次读的那本书（正文存在 IndexedDB 里）。返回是否成功 ——
   * 调用方据此决定要不要退回内置样书。
   */
  restoreLastBook: () => Promise<boolean>;
  /**
   * 进度表里最后读的那本书的 id —— **与正文是否还在存储里无关**。
   * 内置样书的正文在 bundle 里、从不入库，所以恢复它们时只能靠这个 id 找回来。
   */
  lastReadBookIdFromProgress: () => string | null;
  setChapterIndex: (index: number) => void;
  setPageIndex: (index: number) => void;
  setPages: (pages: string[]) => void;
  nextPage: () => boolean; // returns true if page changed
  prevPage: () => boolean;
  setFontSize: (size: number) => void;
  setTheme: (theme: ReaderTheme) => void;
  setLineHeight: (lh: number) => void;
  /** 一次改多个设置项并落盘（TTS、阅读辅助等新增项走这条）。 */
  updateSettings: (patch: Partial<ReaderSettings>) => void;
  setBookCatalogOpen: (open: boolean) => void;
  setVocabularyOpen: (open: boolean) => void;
  setSettingsOpen: (open: boolean) => void;
  setSentenceAnalysisOpen: (open: boolean, sentence?: string) => void;
  setWordExplosionOpen: (open: boolean, sentence?: string) => void;
}

const loadSavedProgress = (): Record<string, ReadingProgress> => {
  try {
    const raw = localStorage.getItem(PROGRESS_STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    // `JSON.parse` 可以返回 null、数字或字符串，而所有调用方都当对象用 ——
    // 一份被改坏的 localStorage 会让 `Object.entries(null)` 抛异常、阅读器白屏。
    // 边界处挡住它，和 normalizeWordMap 校验词库值是同一个道理。
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, ReadingProgress>) : {};
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

/**
 * 与默认值逐字段合并，而不是直接返回解析结果。
 *
 * 这里原本把 JSON.parse 的结果原样返回、不做任何校验，于是每一次新增设置项，
 * 老用户读到的都是 `undefined` —— 一路流进渲染（字号变成 `undefinedpx`）。
 * 合并之后，旧数据缺哪个键就用默认值补上。
 */
const loadSettings = (): ReaderSettings => {
  try {
    const raw = localStorage.getItem(SETTINGS_STORAGE_KEY);
    if (raw) {
      const parsed: unknown = JSON.parse(raw);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return { ...DEFAULT_SETTINGS, ...(parsed as Partial<ReaderSettings>) };
      }
    }
  } catch {
    // 读不出来就用默认值。
  }
  return { ...DEFAULT_SETTINGS };
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

  /**
   * 从当前 state 取全部设置项写盘。每次新增设置项只需改这里，而不必再去
   * 每个 setter 里补一遍字段 —— 之前正是那种写法让新增项容易漏掉。
   */
  const persistCurrentSettings = () => {
    const {
      fontSize,
      theme,
      lineHeight,
      ttsProvider,
      ttsVoice,
      ttsRate,
      ttsCustomUrlTemplate,
      bionicEnabled,
      readingRulerEnabled,
    } = get();
    persistSettings({
      fontSize,
      theme,
      lineHeight,
      ttsProvider,
      ttsVoice,
      ttsRate,
      ttsCustomUrlTemplate,
      bionicEnabled,
      readingRulerEnabled,
    });
  };

  return {
    currentBook: null,
    currentChapterIndex: 0,
    currentPageIndex: 0,
    pages: [],

    fontSize: initialSettings.fontSize,
    theme: initialSettings.theme,
    lineHeight: initialSettings.lineHeight,
    ttsProvider: initialSettings.ttsProvider,
    ttsVoice: initialSettings.ttsVoice,
    ttsRate: initialSettings.ttsRate,
    ttsCustomUrlTemplate: initialSettings.ttsCustomUrlTemplate,
    bionicEnabled: initialSettings.bionicEnabled,
    readingRulerEnabled: initialSettings.readingRulerEnabled,

    isBookCatalogOpen: false,
    isVocabularyOpen: false,
    isSettingsOpen: false,
    isSentenceAnalysisOpen: false,
    selectedSentence: '',
    isWordExplosionOpen: false,
    explosionSentence: '',

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

    /**
     * 恢复上次那本书。**只在这里读**，写（保存正文）由选书的地方负责 ——
     * 见 `bookStorage.saveBook` 的注释：写失败必须让用户看见，而那个提示属于有 UI 的那一层。
     *
     * 读失败不算故障：拿不到就退回内置样书，与「第一次打开」没有区别。
     */
    restoreLastBook: async () => {
      if (get().currentBook) return false;

      const book = await loadLastReadBook(loadSavedProgress());
      // 再查一次：await 期间用户完全可能已经从书架里打开了另一本。
      if (!book || get().currentBook) return false;

      get().setCurrentBook(book);
      return true;
    },

    lastReadBookIdFromProgress: () => lastReadBookId(loadSavedProgress()),

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
      persistCurrentSettings();
    },

    setTheme: (theme: ReaderTheme) => {
      // App.tsx mirrors the theme onto <html data-theme="..."> in one effect.
      set({ theme });
      persistCurrentSettings();
    },

    setLineHeight: (lineHeight: number) => {
      set({ lineHeight });
      persistCurrentSettings();
    },

    updateSettings: (patch: Partial<ReaderSettings>) => {
      set(patch);
      persistCurrentSettings();
    },

    setBookCatalogOpen: (isBookCatalogOpen: boolean) => set({ isBookCatalogOpen }),
    setVocabularyOpen: (isVocabularyOpen: boolean) => set({ isVocabularyOpen }),
    setSettingsOpen: (isSettingsOpen: boolean) => set({ isSettingsOpen }),
    setSentenceAnalysisOpen: (isSentenceAnalysisOpen: boolean, sentence = '') =>
      set({ isSentenceAnalysisOpen, selectedSentence: sentence }),

    setWordExplosionOpen: (isWordExplosionOpen: boolean, sentence = '') =>
      set({ isWordExplosionOpen, explosionSentence: sentence }),
  };
});
