import { create } from 'zustand';
import type { WordLevel, WordStatus } from '../types/reader';
import { normalizeWordMap } from '../utils/wordLevel';

const STORAGE_KEY = 'language_reader_vocabulary';

interface VocabularyStorageData {
  words: Record<string, WordStatus>;
}

interface VocabularyState {
  words: Record<string, WordStatus>;

  // Actions
  /** 手动指定熟练度（释义面板的 5 段选择器）。 */
  setWordLevel: (word: string, level: WordLevel) => void;
  /**
   * 正文里点击一个词：只在尚未收录时记为 5 级「生词」。
   * 已经有状态的词（1-5 级或已掌握）原样保留 —— 与翻页同一条「用户的判断优先」。
   * 想改回 5 级要用「标为生词」按钮，那才是明确的意图。
   */
  markAsNewWord: (word: string) => void;
  /** 记为「掌握」：正文不再高亮。 */
  markMastered: (word: string) => void;
  removeWord: (word: string) => void;
  /**
   * 翻页时把本页没被点击的词记为「掌握」，返回**本次真正写入的那批词**。
   * 已经收录的词（1-5 级或已掌握）不会被覆盖，也就不在返回值里。
   * 返回词表而不是条数：撤销要知道是哪几个词，条数取 .length 即可。
   */
  markPageWordsAsMastered: (pageWords: string[]) => string[];
  /**
   * 撤销一次自动收录（翻页或「完成阅读」）。
   *
   * 只删当前状态仍是 'mastered' 的词 —— 读者在这之后手动改成 1-5 级的词不能删，
   * 那是明确的新意图。
   *
   * 这是对 Android 版的**有意简化**：那边 `undoMastery` 靠 `pageTurnStamp` +
   * `updatedAt` 双重校验（见 `VocabularyDao.deletePageTurned`），能认出"这一行
   * 自那次翻页后是否被改过"。Web 侧的存储是一整块 JSON、没有行时间戳，用
   * 「现在还是不是 mastered」这个状态守卫达到同等效果，不必为此新增字段。
   */
  undoMasteredWords: (words: string[]) => void;
  getWordStatus: (word: string) => WordStatus | 'unknown';
  clearVocabulary: () => void;
  importVocabulary: (data: unknown) => void;
}

const normalizeWord = (word: string): string => word.trim().toLowerCase();

// Helper to safely load from localStorage. Values written by older versions
// ('learning' / 'known') are migrated here, so the rest of the app only ever
// sees level numbers and 'mastered'.
const loadInitialWords = (): Record<string, WordStatus> => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as VocabularyStorageData;
    return normalizeWordMap(parsed?.words);
  } catch (err) {
    console.warn('Failed to load vocabulary from localStorage:', err);
    return {};
  }
};

// Helper to persist to localStorage
const persistWords = (words: Record<string, WordStatus>) => {
  try {
    const data: VocabularyStorageData = { words };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch (err) {
    console.error('Failed to save vocabulary to localStorage:', err);
  }
};

export const useVocabularyStore = create<VocabularyState>((set, get) => ({
  words: loadInitialWords(),

  setWordLevel: (word: string, level: WordLevel) => {
    const clean = normalizeWord(word);
    if (!clean) return;
    // A single status update, shared by every mutation below.
    set((state) => {
      const nextWords = { ...state.words, [clean]: level as WordStatus };
      persistWords(nextWords);
      return { words: nextWords };
    });
  },

  markAsNewWord: (word: string) => {
    const clean = normalizeWord(word);
    if (!clean) return;
    // Already filed - by hand, by a page turn, or by an earlier click. Clicking
    // is how a word enters the vocabulary, never how it gets rewritten.
    if (get().words[clean] !== undefined) return;

    set((state) => {
      const nextWords = { ...state.words, [clean]: 5 as WordStatus };
      persistWords(nextWords);
      return { words: nextWords };
    });
  },

  markMastered: (word: string) => {
    const clean = normalizeWord(word);
    if (!clean) return;

    set((state) => {
      const nextWords = { ...state.words, [clean]: 'mastered' as WordStatus };
      persistWords(nextWords);
      return { words: nextWords };
    });
  },

  removeWord: (word: string) => {
    const clean = normalizeWord(word);
    if (!clean) return;

    set((state) => {
      const nextWords = { ...state.words };
      delete nextWords[clean];
      persistWords(nextWords);
      return { words: nextWords };
    });
  },

  markPageWordsAsMastered: (pageWords: string[]) => {
    const currentWords = get().words;
    const nextWords = { ...currentWords };
    const newlyMastered: string[] = [];

    for (const raw of pageWords) {
      const clean = normalizeWord(raw);
      // Single letters are noise (articles, initials), not vocabulary.
      if (!clean || clean.length < 2) continue;
      // Anything already in the map - a level the reader picked by hand, or a
      // word already mastered - is left alone. Only never-seen words are filed.
      if (nextWords[clean] !== undefined) continue;

      nextWords[clean] = 'mastered';
      newlyMastered.push(clean);
    }

    if (newlyMastered.length > 0) {
      persistWords(nextWords);
      set({ words: nextWords });
    }

    return newlyMastered;
  },

  undoMasteredWords: (words: string[]) => {
    const nextWords = { ...get().words };
    let removed = 0;

    for (const raw of words) {
      const clean = normalizeWord(raw);
      // Still 'mastered' means nothing has touched it since the turn; anything
      // else is the reader's own later judgement and stays.
      if (nextWords[clean] !== 'mastered') continue;

      delete nextWords[clean];
      removed++;
    }

    if (removed > 0) {
      persistWords(nextWords);
      set({ words: nextWords });
    }
  },

  getWordStatus: (word: string) => {
    const clean = normalizeWord(word);
    return get().words[clean] ?? 'unknown';
  },

  clearVocabulary: () => {
    persistWords({});
    set({ words: {} });
  },

  /** Also normalizes: an imported backup from an older version still loads. */
  importVocabulary: (data: unknown) => {
    const words = normalizeWordMap(data);
    persistWords(words);
    set({ words });
  },
}));
