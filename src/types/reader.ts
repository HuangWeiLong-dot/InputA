/**
 * 熟练度：1 最浅（熟知）→ 5 最深（生词）。颜色越深表示这个词越不熟。
 */
export type WordLevel = 1 | 2 | 3 | 4 | 5;

/**
 * 一个词的收录状态。
 *
 *   1-5       熟练度，正文里按色阶高亮（见 src/utils/wordLevel.ts）
 *   'mastered' 独立的第 6 个状态「掌握」：正文不再高亮
 *
 * 从未被收录的词不在这里表示，由 getWordStatus 返回 'unknown'。
 */
export type WordStatus = WordLevel | 'mastered';

export interface Phonetic {
  text?: string;
  audio?: string;
}

export interface DefinitionItem {
  definition: string;
  example?: string;
  synonyms?: string[];
}

export interface Meaning {
  partOfSpeech: string;
  definitions: DefinitionItem[];
}

/**
 * 只有本地词库（GET /api/dict，ECDICT）才会带上的补充信息：中文释义、考试标签、
 * 词性分布、词形变化与词频。在线来源没有这些字段，因此整体可选。
 */
export interface DictionaryExtra {
  /** 中文释义原文，多行（每行一段，含词性前缀）。 */
  translation?: string;
  /** 考试标签，如 ['中考', '高考', '四级']。 */
  examTags?: string[];
  /** 词性分布（中文标签 + 英文缩写 + 占比），按占比降序。 */
  partsOfSpeech?: Array<{ label: string; abbr: string; percent: number }>;
  /** 原形：本词是某个变形时指向它的原形，如 running → run。 */
  lemma?: string;
  /** 本词条是原形的哪种变形（中文），如「现在分词」。 */
  inflection?: string;
  /** 其他词形变化，如 [{ label: '过去式', words: ['ran'] }]。 */
  forms?: Array<{ label: string; words: string[] }>;
  /** 柯林斯星级 1-5、牛津核心词标记、BNC 与 COCA 词频排名（越小越常用）。 */
  frequency?: { collins?: number; oxford?: boolean; bnc?: number; frq?: number };
}

export interface DictionaryEntry {
  word: string;
  phonetic?: string;
  phonetics?: Phonetic[];
  meanings: Meaning[];
  audioUrl?: string;
  /** 离线词库附带的补充信息，见 DictionaryExtra。 */
  extra?: DictionaryExtra;
}

export interface BookChapter {
  title: string;
  content: string;
}

export interface Book {
  id: string;
  title: string;
  author: string;
  coverUrl?: string;
  chapters: BookChapter[];
  source?: 'gutenberg' | 'custom' | 'builtin';
}

export interface ReadingProgress {
  bookId: string;
  bookTitle: string;
  chapterIndex: number;
  pageIndex: number;
  updatedAt: number;
}
