import { create } from 'zustand';
import type { AnnotationsData, SavedSentence } from '../types/reader';
import {
  normalizeAnnotations,
  normalizeSavedSentence,
} from '../utils/annotations';

/**
 * 词的笔记与例句。
 *
 * 独立于 `useVocabularyStore` 存放，理由见 src/utils/annotations.ts 的说明：
 * 熟练度的值必须是标量，否则 `normalizeWordMap` 会把整个词库判为无效。
 *
 * 数据留在浏览器 localStorage，没有服务端。换浏览器/清缓存前请先用词汇库的
 * 「导出」备份（导出文件 v2 已包含这三份数据）。
 */
const STORAGE_KEY = 'language_reader_annotations';

interface AnnotationStore extends AnnotationsData {
  /**
   * 添加一条笔记。空白会被忽略；与已有笔记精确重复时不重复添加。
   * AI 推荐的笔记也只有被「采纳」时才走到这里——采纳后与手写笔记完全同构。
   */
  addNote: (word: string, note: string) => void;
  /** 删除某一条笔记；这是该词最后一条时会连键一起删掉。 */
  removeNote: (word: string, note: string) => void;
  /** 保存一条例句。同一句只留一条；已存过则只在补上译文时更新。 */
  addSentence: (word: string, sentence: SavedSentence) => void;
  removeSentence: (word: string, sentence: string) => void;
  /**
   * 连同例句清掉该词的全部标注。删词时调用，否则会留下永远查不到的孤儿数据。
   * 刻意不在两个 store 之间互相 import —— 由调用处同时触发，依赖方向保持单向。
   */
  removeWordAnnotations: (word: string) => void;
  getNotes: (word: string) => string[];
  getSentences: (word: string) => SavedSentence[];
  clearAnnotations: () => void;
  /** 替换式导入（导出文件 v2 的 notes/sentences 部分）。 */
  importAnnotations: (data: unknown) => void;
}

const normalizeWord = (word: string): string => word.trim().toLowerCase();

const loadInitial = (): AnnotationsData => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { notes: {}, sentences: {} };
    return normalizeAnnotations(JSON.parse(raw));
  } catch (err) {
    console.warn('Failed to load annotations from localStorage:', err);
    return { notes: {}, sentences: {} };
  }
};

const persist = (data: AnnotationsData) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch (err) {
    console.error('Failed to save annotations to localStorage:', err);
  }
};

/** 掉到空集时要删键，而不是留一个空数组——否则导出文件里全是空壳。 */
function withoutKey<T>(map: Record<string, T[]>, key: string): Record<string, T[]> {
  const next = { ...map };
  delete next[key];
  return next;
}

export const useAnnotationStore = create<AnnotationStore>((set, get) => ({
  ...loadInitial(),

  addNote: (word: string, note: string) => {
    const clean = normalizeWord(word);
    const text = note.trim();
    if (!clean || !text) return;

    set((state) => {
      const existing = state.notes[clean] ?? [];
      // 精确去重，与 LingKuma 的 addTranslationToDB（includes 后 push）一致。
      if (existing.includes(text)) return state;

      const nextNotes = { ...state.notes, [clean]: [...existing, text] };
      persist({ notes: nextNotes, sentences: state.sentences });
      return { notes: nextNotes };
    });
  },

  removeNote: (word: string, note: string) => {
    const clean = normalizeWord(word);
    const text = note.trim();
    if (!clean || !text) return;

    set((state) => {
      const existing = state.notes[clean];
      if (!existing) return state;

      const remaining = existing.filter((item) => item !== text);
      const nextNotes =
        remaining.length > 0 ? { ...state.notes, [clean]: remaining } : withoutKey(state.notes, clean);

      persist({ notes: nextNotes, sentences: state.sentences });
      return { notes: nextNotes };
    });
  },

  addSentence: (word: string, sentence: SavedSentence) => {
    const clean = normalizeWord(word);
    const normalized = normalizeSavedSentence(sentence);
    if (!clean || !normalized) return;

    set((state) => {
      const existing = state.sentences[clean] ?? [];
      const index = existing.findIndex((item) => item.sentence === normalized.sentence);

      let nextList: SavedSentence[];
      if (index >= 0) {
        // 这句已经存过了。保存与取译文是并行的，译文可能后到——
        // 那种情况下要把译文补进去，而不是整条丢掉让它再也落不了库。
        // 除此之外不覆盖已有内容：用户可以自己改过译文。
        if (!normalized.translation || existing[index].translation) return state;
        nextList = [...existing];
        nextList[index] = { ...existing[index], translation: normalized.translation };
      } else {
        nextList = [...existing, normalized];
      }

      const nextSentences = { ...state.sentences, [clean]: nextList };
      persist({ notes: state.notes, sentences: nextSentences });
      return { sentences: nextSentences };
    });
  },

  removeSentence: (word: string, sentence: string) => {
    const clean = normalizeWord(word);
    const text = sentence.trim();
    if (!clean || !text) return;

    set((state) => {
      const existing = state.sentences[clean];
      if (!existing) return state;

      const remaining = existing.filter((item) => item.sentence !== text);
      const nextSentences =
        remaining.length > 0
          ? { ...state.sentences, [clean]: remaining }
          : withoutKey(state.sentences, clean);

      persist({ notes: state.notes, sentences: nextSentences });
      return { sentences: nextSentences };
    });
  },

  removeWordAnnotations: (word: string) => {
    const clean = normalizeWord(word);
    if (!clean) return;

    set((state) => {
      if (state.notes[clean] === undefined && state.sentences[clean] === undefined) return state;

      const nextNotes = withoutKey(state.notes, clean);
      const nextSentences = withoutKey(state.sentences, clean);
      persist({ notes: nextNotes, sentences: nextSentences });
      return { notes: nextNotes, sentences: nextSentences };
    });
  },

  getNotes: (word: string) => get().notes[normalizeWord(word)] ?? [],

  getSentences: (word: string) => get().sentences[normalizeWord(word)] ?? [],

  clearAnnotations: () => {
    persist({ notes: {}, sentences: {} });
    set({ notes: {}, sentences: {} });
  },

  /** 也做规范化：旧版本或手改过的备份都能安全读入。 */
  importAnnotations: (data: unknown) => {
    const next = normalizeAnnotations(data);
    persist(next);
    set(next);
  },
}));
