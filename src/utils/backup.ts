import type { SavedSentence, WordStatus } from '../types/reader';
import { normalizeWordMap } from './wordLevel';
import { normalizeNotesMap, normalizeSentencesMap } from './annotations';

/**
 * 词汇备份文件的读写。
 *
 * v1（旧版「导出」）写的是**裸的** `{ 单词: 状态 }` 映射 —— 注意它和 localStorage
 * 里存的形状并不一致（那里是 `{words: {...}}`），所以历史上「导出」出来的文件
 * 直接拿去当 localStorage 的值是用不了的。
 *
 * v2 把三份数据装进一个带版本号的对象，并记下导出时间。
 */

export const EXPORT_VERSION = 2;

export interface BackupData {
  words: Record<string, WordStatus>;
  notes: Record<string, string[]>;
  sentences: Record<string, SavedSentence[]>;
}

export interface BackupFile extends BackupData {
  version: number;
  exportedAt: string;
}

/** `exportedAt` 由调用方传入，好让测试拿到确定的输出。 */
export function buildBackup(data: BackupData, exportedAt: string): BackupFile {
  return {
    version: EXPORT_VERSION,
    exportedAt,
    words: data.words,
    notes: data.notes,
    sentences: data.sentences,
  };
}

/**
 * 解析一份备份。认得三种形状：
 *   - v2：`{version, exportedAt, words, notes, sentences}`
 *   - v1 包裹形：`{words: {...}}`（localStorage 里就是长这样，用户可能直接拷出来）
 *   - v1 裸映射：`{ 单词: 状态 }`（旧版「导出」真正写出的形状）
 *
 * 判别包裹形的依据是 `words` 的值是不是对象：裸映射里任何键的值都只能是标量
 * （1-5 或 'mastered'），所以那个位置出现对象只可能是包裹形。
 *
 * 返回 null 表示「这不是本应用的备份」；数据本身不合法（脏条目）不算 null，
 * 交给 normalize* 逐条丢弃。
 */
export function parseBackup(raw: unknown): BackupData | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;

  const record = raw as Record<string, unknown>;
  const wrapper = record.words;

  if (wrapper && typeof wrapper === 'object' && !Array.isArray(wrapper)) {
    return {
      words: normalizeWordMap(wrapper),
      // v1 没有这两项；normalize* 对 undefined 返回空集，正是替换式导入要的语义。
      notes: normalizeNotesMap(record.notes),
      sentences: normalizeSentencesMap(record.sentences),
    };
  }

  // 声称自己是 v2 却没有 words 对象 —— 文件是坏的。没有这道闸，下面的裸映射
  // 分支会把 `version: 2` 读成一个名为 "version"、熟练度 2 的单词，于是导入
  // 就用这一个假词覆盖掉用户的整个词库。
  if (typeof record.version === 'number') return null;

  return { words: normalizeWordMap(record), notes: {}, sentences: {} };
}

/**
 * 这份备份是不是空的。空备份等同于「清空词库」，而那更可能是选错了文件 ——
 * 调用方据此拒绝导入，比默默抹掉用户的数据安全。
 */
export function isEmptyBackup(data: BackupData): boolean {
  return (
    Object.keys(data.words).length === 0 &&
    Object.keys(data.notes).length === 0 &&
    Object.keys(data.sentences).length === 0
  );
}
