import { create } from 'zustand';
import type { WordStatus } from '../types/reader';

const STORAGE_KEY = 'language_reader_vocabulary';

interface VocabularyStorageData {
  words: Record<string, WordStatus>;
}

interface VocabularyState {
  words: Record<string, WordStatus>;
  
  // Actions
  markLearning: (word: string) => void;
  markKnown: (word: string) => void;
  removeWord: (word: string) => void;
  markPageWordsAsKnown: (pageWords: string[]) => number; // returns count of newly marked known words
  getWordStatus: (word: string) => WordStatus | 'unknown';
  clearVocabulary: () => void;
  importVocabulary: (data: Record<string, WordStatus>) => void;
}

// Helper to safely load from localStorage
const loadInitialWords = (): Record<string, WordStatus> => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as VocabularyStorageData;
    return parsed?.words || {};
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

  markLearning: (word: string) => {
    const clean = word.trim().toLowerCase();
    if (!clean) return;

    set((state) => {
      const nextWords = {
        ...state.words,
        [clean]: 'learning' as WordStatus,
      };
      persistWords(nextWords);
      return { words: nextWords };
    });
  },

  markKnown: (word: string) => {
    const clean = word.trim().toLowerCase();
    if (!clean) return;

    set((state) => {
      const nextWords = {
        ...state.words,
        [clean]: 'known' as WordStatus,
      };
      persistWords(nextWords);
      return { words: nextWords };
    });
  },

  removeWord: (word: string) => {
    const clean = word.trim().toLowerCase();
    if (!clean) return;

    set((state) => {
      const nextWords = { ...state.words };
      delete nextWords[clean];
      persistWords(nextWords);
      return { words: nextWords };
    });
  },

  markPageWordsAsKnown: (pageWords: string[]) => {
    const currentWords = get().words;
    const nextWords = { ...currentWords };
    let newlyMarkedCount = 0;

    for (const raw of pageWords) {
      const clean = raw.trim().toLowerCase();
      // Skip empty or already marked as learning or already known
      if (!clean || clean.length < 2) continue;
      
      // If it's already in learning state, do NOT override
      if (nextWords[clean] === 'learning') {
        continue;
      }

      // If it is unknown (not marked at all)
      if (!nextWords[clean]) {
        nextWords[clean] = 'known';
        newlyMarkedCount++;
      }
    }

    if (newlyMarkedCount > 0) {
      persistWords(nextWords);
      set({ words: nextWords });
    }

    return newlyMarkedCount;
  },

  getWordStatus: (word: string) => {
    const clean = word.trim().toLowerCase();
    return get().words[clean] || 'unknown';
  },

  clearVocabulary: () => {
    persistWords({});
    set({ words: {} });
  },

  importVocabulary: (data: Record<string, WordStatus>) => {
    persistWords(data);
    set({ words: data });
  },
}));
