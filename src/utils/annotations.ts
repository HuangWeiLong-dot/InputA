import type { AnnotationsData, SavedSentence } from '../types/reader';

/**
 * 笔记与例句的规范化。
 *
 * 为什么单独一个键、单独一套规范化：熟练度存在 `language_reader_vocabulary`
 * 里，值的类型是**标量**（1-5 或 'mastered'），而 `normalizeWordMap`
 * （同目录 wordLevel.ts）会把任何非标量的值判为无效丢弃 —— 它同时是加载和
 * 导入的唯一闸门。把笔记塞进同一个映射（比如把值改成 `{level, note}`）会让
 * 所有现存用户的词库在下一次加载时被**静默清空**，而且有测试钉住了这个行为。
 *
 * 所以标注存到自己的键 `language_reader_annotations`，单词的值继续是标量，
 * 所有读词库的地方都不用改。
 *
 * 这里的函数一律「丢弃脏数据、保留能救的」，与 `normalizeWordMap` 同风格：
 * 导入一份旧备份或手改过的 JSON 不能抛异常。
 */

export const EMPTY_ANNOTATIONS: AnnotationsData = { notes: {}, sentences: {} };

/**
 * 词的键一律去空白 + 小写，与 `useVocabularyStore` 的 normalizeWord 一致。
 *
 * 读取时永远用规范化后的词查表，所以导入时若不规范化，带大写或前后空白的键
 * 会**永远查不到**——那是静默丢数据，比报错更糟。
 */
function normalizeKey(raw: string): string {
  return raw.trim().toLowerCase();
}

function optionalString(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const text = value.trim();
  return text || undefined;
}

/**
 * 章节号 / 页码 / 时间戳都只取整数：`3.5` 页或 `'yesterday'` 这种值说明数据
 * 已经被改坏或来自别的格式，丢掉比原样带着走进渲染更安全。
 */
function optionalInteger(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) ? value : undefined;
}

/**
 * 规范化一个词的笔记数组：丢掉非字符串与空白项，按精确匹配去重。
 *
 * 精确匹配（而不是大小写无关）是有意的：LingKuma 的 addTranslationToDB 就是
 * `includes()` 后 push，用户可能刻意保留两条只差大小写的笔记。
 */
export function normalizeNoteList(input: unknown): string[] {
  if (!Array.isArray(input)) return [];

  const seen = new Set<string>();
  const result: string[] = [];
  for (const item of input) {
    if (typeof item !== 'string') continue;
    const text = item.trim();
    if (!text || seen.has(text)) continue;
    seen.add(text);
    result.push(text);
  }
  return result;
}

/** 规范化一条例句。没有 `sentence` 就直接丢弃——它是去重键，缺了没有意义。 */
export function normalizeSavedSentence(input: unknown): SavedSentence | null {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return null;

  const raw = input as Record<string, unknown>;
  const sentence = optionalString(raw.sentence);
  if (!sentence) return null;

  const result: SavedSentence = { sentence };
  const translation = optionalString(raw.translation);
  if (translation) result.translation = translation;

  const bookId = optionalString(raw.bookId);
  if (bookId) result.bookId = bookId;
  const bookTitle = optionalString(raw.bookTitle);
  if (bookTitle) result.bookTitle = bookTitle;

  const chapterIndex = optionalInteger(raw.chapterIndex);
  if (chapterIndex !== undefined) result.chapterIndex = chapterIndex;
  const pageIndex = optionalInteger(raw.pageIndex);
  if (pageIndex !== undefined) result.pageIndex = pageIndex;

  const createdAt = optionalInteger(raw.createdAt);
  if (createdAt !== undefined) result.createdAt = createdAt;

  return result;
}

/** 规范化一个词的例句数组，按 `sentence` 去重（先出现的那条胜出）。 */
export function normalizeSentenceList(input: unknown): SavedSentence[] {
  if (!Array.isArray(input)) return [];

  const seen = new Set<string>();
  const result: SavedSentence[] = [];
  for (const item of input) {
    const sentence = normalizeSavedSentence(item);
    if (!sentence || seen.has(sentence.sentence)) continue;
    seen.add(sentence.sentence);
    result.push(sentence);
  }
  return result;
}

/** 规范化一张 { 单词 → 笔记[] } 表，丢掉空表，避免导出文件里满是空壳。 */
export function normalizeNotesMap(input: unknown): Record<string, string[]> {
  const result: Record<string, string[]> = {};
  if (!input || typeof input !== 'object' || Array.isArray(input)) return result;

  for (const [rawKey, value] of Object.entries(input as Record<string, unknown>)) {
    const key = normalizeKey(rawKey);
    if (!key) continue;
    const notes = normalizeNoteList(value);
    if (notes.length === 0) continue;
    result[key] = notes;
  }
  return result;
}

/** 规范化一张 { 单词 → 例句[] } 表。 */
export function normalizeSentencesMap(input: unknown): Record<string, SavedSentence[]> {
  const result: Record<string, SavedSentence[]> = {};
  if (!input || typeof input !== 'object' || Array.isArray(input)) return result;

  for (const [rawKey, value] of Object.entries(input as Record<string, unknown>)) {
    const key = normalizeKey(rawKey);
    if (!key) continue;
    const sentences = normalizeSentenceList(value);
    if (sentences.length === 0) continue;
    result[key] = sentences;
  }
  return result;
}

/**
 * 把任意来源的数据规范化成 `AnnotationsData`。
 *
 * 接受的形状：`{notes, sentences}`（本应用写出的两份映射）、
 * 或者任何缺字段/类型不对的输入（按空处理）。导入 v1 备份时没有标注数据，
 * 传 undefined 进来即得到空集——这正是「替换式导入」想要的语义。
 */
export function normalizeAnnotations(input: unknown): AnnotationsData {
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    return { notes: {}, sentences: {} };
  }
  const raw = input as Record<string, unknown>;
  return {
    notes: normalizeNotesMap(raw.notes),
    sentences: normalizeSentencesMap(raw.sentences),
  };
}
