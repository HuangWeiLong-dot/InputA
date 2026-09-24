/**
 * 一点点 Markdown 的行内解析：`**粗体**` 与 `` `代码` ``。
 *
 * 存在的理由：AI 提示词（例 `sentenceTranslationPrompt`）要求模型用 Markdown
 * 加粗标出目标词。LingKuma 那边是拿 `innerHTML +=` 拼接这些东西的
 * （其 src/content.js 的 formatContent），这里改成先解析成 token、再交给 React
 * 渲染 —— 模型输出是不可信输入，不经过 innerHTML 就没有注入面。
 *
 * 只认这两个标记，其余一律当纯文本：宁可少渲染，也不要让不认识的语法把
 * 正文吃掉。
 */

export type InlineToken =
  | { kind: 'text'; text: string }
  | { kind: 'bold'; text: string }
  | { kind: 'code'; text: string };

const INLINE_PATTERN = /\*\*([^*]+)\*\*|`([^`]+)`/g;

export function parseInlineMarkdown(input: string): InlineToken[] {
  if (!input) return [];

  const tokens: InlineToken[] = [];
  let lastIndex = 0;

  for (const match of input.matchAll(INLINE_PATTERN)) {
    const index = match.index ?? 0;
    if (index > lastIndex) {
      tokens.push({ kind: 'text', text: input.slice(lastIndex, index) });
    }

    const bold = match[1];
    const code = match[2];
    if (bold !== undefined) {
      tokens.push({ kind: 'bold', text: bold });
    } else if (code !== undefined) {
      tokens.push({ kind: 'code', text: code });
    }

    lastIndex = index + match[0].length;
  }

  if (lastIndex < input.length) {
    tokens.push({ kind: 'text', text: input.slice(lastIndex) });
  }
  return tokens;
}

export interface WordSplit {
  text: string;
  /** true = 这一段是被查的那个词本身，渲染时加粗。 */
  match: boolean;
}

/**
 * 按整词切分文本，用来在例句里标出被查的那个词。
 *
 * 不用 `\b`：它对带撇号（don't）或连字符（well-known）的词会在中间断开。
 * 这里用「前后都不能是字母或数字」来界定词的边界，并用 Unicode 属性类覆盖
 * 变音符号（café、über），否则非英文例句里根本标不中。
 */
export function splitOnWord(text: string, word: string): WordSplit[] {
  const target = word.trim();
  if (!target) return [{ text, match: false }];

  const escaped = target
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    // 撇号在正文里可能是直引号也可能是弯引号（tokenizer 两种都收），
    // 所以让它在模式里匹配任意一种，免得 did'nt / didn’t 互相标不中。
    .replace(/['’]/g, "['’]");
  const pattern = new RegExp(`(?<![\\p{L}\\p{N}])(${escaped})(?![\\p{L}\\p{N}])`, 'giu');

  const parts: WordSplit[] = [];
  let lastIndex = 0;

  for (const match of text.matchAll(pattern)) {
    const index = match.index ?? 0;
    if (index > lastIndex) parts.push({ text: text.slice(lastIndex, index), match: false });
    parts.push({ text: match[0], match: true });
    lastIndex = index + match[0].length;
  }

  if (lastIndex < text.length) parts.push({ text: text.slice(lastIndex), match: false });
  return parts;
}
