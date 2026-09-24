import { cleanToken, tokenizeText } from './tokenizer';

/**
 * 从一句话里挑出「值得查」的词，供单词爆炸（批量查词）面板使用。
 *
 * 移植自 LingKuma 的 `extractUnknownWords`（其 src/service/a7_words_boom.js），
 * 但**分词改用本仓库自己的 tokenizer，而不是 Intl.Segmenter** —— 这是与计划里
 * 说法不同的一处有意偏离，理由是正确性：
 *
 *   Segmenter 会按 Unicode 词边界把 `well-known` 切成 `well` + `known`，
 *   而阅读器收录词时用的是 tokenizeText，把 `well-known` 当成**一个**词、
 *   以它为键存进词库。两者不一致的直接后果是：面板会把这些词报成「未收录」
 *   （查 `well` 查不到 `well-known` 的状态），而用户点「已掌握」又会往词库里
 *   塞进 `well`、`known` 这些永远不会在正文里出现的键。
 *
 * 面板显示的词必须和阅读器收录的词是同一套单位，所以这里复用同一个分词器。
 * 代价是中日韩等无空格语言切不出来 —— 但那本来就超出「只做检测与适配」的范围。
 *
 * 另一点与 LingKuma 的分歧：那边的状态码 0-4 都算「未知」。InputA 的「未收录」
 * 就是没进过词库，这一步由调用方按自己的词库过滤，这里只负责分词、去重与去噪。
 */

export interface ExtractedWord {
  /** 首次出现的原大小写形式，用于显示与朗读。 */
  raw: string;
  /** 小写并去首尾标点的形式，用于查词库状态与查词典。 */
  clean: string;
}

/**
 * 单个字母算不上生词（冠词 a、代词 I），纯数字与纯符号同理。
 * 与翻页自动收录的规则保持一致（见 useVocabularyStore.markPageWordsAsMastered）。
 */
function isNoise(clean: string): boolean {
  if (clean.length < 2) return true;
  // `\p{L}` 覆盖带变音符号的字母，所以 café、über 不会被当成符号丢掉。
  return !/\p{L}/u.test(clean);
}

export function extractWords(text: string): ExtractedWord[] {
  if (!text.trim()) return [];

  const seen = new Set<string>();
  const result: ExtractedWord[] = [];

  for (const token of tokenizeText(text)) {
    if (!token.isWord) continue;

    const clean = cleanToken(token.raw);
    if (!clean || isNoise(clean) || seen.has(clean)) continue;

    seen.add(clean);
    result.push({ raw: token.raw.replace(/^['’-]+|['’-]+$/g, ''), clean });
  }

  return result;
}
