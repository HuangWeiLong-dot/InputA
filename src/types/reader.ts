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
  /**
   * ISO 639-1（可带地区），正文的主要语言。
   *
   * 来源优先级：书源元数据（Gutendex 会在响应里给 languages，过去被丢掉了）
   * 优先于本地检测。用来选朗读音色、显示语言标签。
   */
  language?: string;
}

export interface ReadingProgress {
  bookId: string;
  bookTitle: string;
  chapterIndex: number;
  pageIndex: number;
  updatedAt: number;
}

/**
 * 一条保存下来的例句。`sentence` 是去重键 —— 同一句只留一条。
 *
 * 移植自 LingKuma 的 `sentences: [{sentence, translation, url}]`
 * （见其 background.js 的 addSentenceToDB）。原文里的 `url` 是网页地址，
 * 对阅读器没有意义，这里换成书内位置，回看时能定位到出处。
 */
export interface SavedSentence {
  sentence: string;
  /** AI 整句翻译，目标词以 `**粗体**` 标出；还没翻译时为空。 */
  translation?: string;
  bookId?: string;
  bookTitle?: string;
  chapterIndex?: number;
  pageIndex?: number;
  /** 导入的旧备份可能没有这个字段，所以可选。 */
  createdAt?: number;
}

/**
 * 一个词的笔记与例句。
 *
 * 刻意与熟练度分开存放：熟练度在 `language_reader_vocabulary` 里，值的类型是
 * 标量（1-5 或 'mastered'）。把笔记塞进那个映射会让所有现存用户的词库被清空 ——
 * 原因见 src/utils/annotations.ts 的说明。
 */
export interface AnnotationsData {
  /** 单词 → 多条笔记。手写笔记与采纳的 AI 建议同构，无法事后区分。 */
  notes: Record<string, string[]>;
  sentences: Record<string, SavedSentence[]>;
}
