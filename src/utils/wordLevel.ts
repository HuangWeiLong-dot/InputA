import type { WordLevel, WordStatus } from '../types/reader';

/**
 * 熟练度色阶的元数据与状态转换规则。
 *
 * 规则（也是整个应用的词汇逻辑，改动前先看这里）：
 *   - 在正文里点击一个词  → 尚未收录时记为 5 级「生词」（最深）
 *   - 翻到下一页时，本页未被点击的词 → 记为「掌握」（正文不再高亮）
 *   - 释义面板里的 5 段选择器可以手动指定任意一级
 *   - 状态只由用户改写：点击与翻页都不会覆盖已有状态（1-5 级或已掌握），
 *     要改回 5 级请用「标为生词」按钮 —— 用户的判断优先
 *
 * 另有一条**只关于显示**的规则：没收录过的词在正文里按 5 级「生词」着色
 * （见 highlightLevel）。它不写词库、不改变上面任何一条 —— 未收录的词翻页后
 * 照样被记为「掌握」、高亮照样消失。别把它"顺手"改成真写入：那样会让点击
 * 永远进不了「用户判断过」的名单（markAsNewWord 早退在写入之前），翻页随即
 * 清掉读者刚点过的词；词库也会从几百条涨到几万条。
 */

/** 由浅至深，1 = 最浅。 */
export const WORD_LEVELS: readonly WordLevel[] = [1, 2, 3, 4, 5];

/** 1 = 最浅（熟知），5 = 最深（生词）。 */
export const WORD_LEVEL_LABELS: Record<WordLevel, string> = {
  1: '熟知',
  2: '熟悉',
  3: '一般',
  4: '模糊',
  5: '生词',
};

/**
 * 每一级的正文高亮底色。文字颜色不在这里指定，沿用主题的 --text-main：
 * 色阶的每一级都由主题墨色与页面底色混合而成（见 index.css），因此
 * 在明亮 / 羊皮纸 / 夜间三个主题下都自动与正文保持足够对比度。
 */
export const WORD_LEVEL_BG: Record<WordLevel, string> = {
  1: 'bg-[var(--level-1-bg)]',
  2: 'bg-[var(--level-2-bg)]',
  3: 'bg-[var(--level-3-bg)]',
  4: 'bg-[var(--level-4-bg)]',
  5: 'bg-[var(--level-5-bg)]',
};

/**
 * 完整标签，如「3 一般」。只用于 title / aria-label 等提示文字 —— 界面上呈现的
 * 状态一律用 WORD_LEVEL_LABELS（只有档位名，不带数字）。
 */
export function levelLabel(level: WordLevel): string {
  return `${level} ${WORD_LEVEL_LABELS[level]}`;
}

/**
 * 是否为熟练度等级（1-5）。'mastered' 与 'unknown' 都返回 false，但含义不同：
 * 前者是已掌握，后者是没收录。**注意它只回答"状态是什么"，不回答"该用什么颜色"**
 * —— 未收录的词要按 5 级着色，那是 highlightLevel 的事。
 */
export function isLevel(status: WordStatus | 'unknown'): status is WordLevel {
  return typeof status === 'number';
}

/**
 * 是否从未收录。与 isLevel 对称，也是给调用点用的：`'unknown'` 不是词库里的
 * 一个值，而是「这个词没有任何状态」的哨兵。
 *
 * 之所以是普通谓词而不是 `status is 'unknown'`：调用点往往已经把它和
 * 'mastered' 分开处理了（那片分支里没有可收窄的东西）。另外，直接在 JSX 里写
 * `status === 'unknown'` 过不了 tsc —— 见 ReaderArea 里那段注释。
 */
export function isUnknown(status: WordStatus | 'unknown'): boolean {
  return status === 'unknown';
}

/**
 * 正文里该用哪一档底色。
 *
 * 未收录（'unknown'）按 5「生词」着色：读者一眼就能看到这一页还有哪些词没处理过。
 * 它和真正标为 5 级的词看起来一样，但词库里没有任何记录；'mastered' 返回 null，
 * 与今天一样彻底不高亮。
 *
 * 之所以单独成一个函数而不是写在 JSX 的三元里：本仓库的测试跑在纯 node 环境、
 * 没有 jsdom，逻辑落在 utils 里才有单测可言（同 bionic.ts / tokenizer.ts）。
 */
export function highlightLevel(status: WordStatus | 'unknown'): WordLevel | null {
  if (status === 'unknown') return 5;
  return isLevel(status) ? status : null;
}

/**
 * 把 localStorage 或旧导出文件里的值规范化为当前表示。
 *
 * 旧版本只有两态：'learning'（点击收录）与 'known'（翻页自动收录）。
 * 迁移时按语义对应到今天的结果：
 *   learning → 5「生词」   —— 用户明确点过的词，仍然是最不熟的一档
 *   known    → 'mastered'  —— 翻页自动收录的词，今天翻页给的就是「掌握」
 * 无法识别的值返回 null，由调用方丢弃，避免脏数据继续传播。
 */
export function normalizeWordStatus(value: unknown): WordStatus | null {
  if (value === 'mastered') return 'mastered';
  if (value === 'learning') return 5;
  if (value === 'known') return 'mastered';
  if (typeof value === 'number' && Number.isInteger(value) && value >= 1 && value <= 5) {
    return value as WordLevel;
  }
  return null;
}

/** 逐条规范化一个 { 单词: 状态 } 映射，丢弃无法识别的条目。 */
export function normalizeWordMap(input: unknown): Record<string, WordStatus> {
  const result: Record<string, WordStatus> = {};
  if (!input || typeof input !== 'object') return result;

  for (const [word, value] of Object.entries(input as Record<string, unknown>)) {
    const status = normalizeWordStatus(value);
    if (status !== null) result[word] = status;
  }
  return result;
}
