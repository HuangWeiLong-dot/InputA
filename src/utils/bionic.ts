import { containsCjk } from './tokenizer';

/**
 * 仿生阅读：把一个词的前几个字符加粗，给眼睛一个锚点，据说能提高阅读速度。
 *
 * 算法直接取自 LingKuma 的 createHighlightData（其 src/plugin/bionic.js）：
 * 取词长的 40%、**向上取整**作为加粗位数 ——
 *
 *   长度 1 → 1   长度 3 → 2   长度 5 → 2   长度 6 → 3
 *
 * 它没有任何按词长分档、短词例外或元音边界的修饰，这里也不加：换一套规则会让
 * 效果和参考实现不一样，而对不对本来就是个偏好问题。
 *
 * 一处与 LingKuma 的实现分歧：它用绝对定位的渐变 `::before` 覆盖层**模拟**加粗
 * （`font-weight: inherit`，并非真的加粗），还带一条 `data-first-three` 的死 CSS。
 * 这里直接渲染真正的 `<strong>` —— 阅读区本来就是一个词一个 span，原生加粗更简单
 * 也更可访问。
 */

/** 加粗位数：词长的 40%，向上取整。 */
export function bionicBoldLength(word: string): number {
  return Math.ceil(word.length * 0.4);
}

export interface BionicParts {
  /** 加粗的词首。 */
  bold: string;
  /** 其余部分。 */
  rest: string;
}

/**
 * 把词切成「加粗的前段」与「其余」。
 *
 * 中日韩文字整段跳过（返回空 bold）：它们的「词首」没有意义，而且仿生阅读的
 * 效果本来就来自拉丁字母的形状。
 */
export function bionicSplit(word: string): BionicParts {
  if (!word || containsCjk(word)) return { bold: '', rest: word };

  const boldLength = bionicBoldLength(word);
  return { bold: word.slice(0, boldLength), rest: word.slice(boldLength) };
}

/**
 * 这段文字该不该套仿生阅读。
 *
 * 同 LingKuma 的 shouldHighlightText：含中日韩就整段跳过。它的另一条分支
 * （长度为 1 且落在全角标点区间时跳过）在这里不需要 —— 分词器已经不会把标点
 * 当作词了，传进来的都是词。
 */
export function isBionicEligible(word: string): boolean {
  return Boolean(word) && !containsCjk(word);
}
