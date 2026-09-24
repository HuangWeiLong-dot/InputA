export interface Token {
  id: string;
  raw: string;
  isWord: boolean;
  cleanWord: string; // for dictionary lookup and state matching (lowercase)
}

// Regex to split text into words and non-words (punctuation, spaces, line breaks)
// Matches contractions like don't, it's, and hyphenated words like well-known
//
// 词元用 Unicode 字母/数字类，而不是 [a-zA-Z0-9]：后者会把 café 切成 `caf` + `é`、
// 把 über 切成 `ber`，于是非英文书里几乎没有能整词点击的词。`u` 标志是 \p{...} 的前提。
const WORD_REGEX = /([\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*(?:-[\p{L}\p{N}]+)*)/gu;

/**
 * 中日韩等无空格文字。
 *
 * 它们没有「点一下这个词」的天然边界，真要分词得引入 kuromoji / jieba 这类专门的
 * 分词库；而更糟的是翻页自动收录规则会把整段中文当成一个「词」塞进词库。所以这里
 * 不把它们算作可点击的词 —— 与 LingKuma 的 shouldHighlightText 是同一个取舍。
 */
const CJK_PATTERN = /[ᄀ-ᇿ぀-ヿ㄰-㆏一-鿿가-힯]/;

/**
 * 这段文字里有没有中日韩文字。分词器用它决定「算不算一个可点击的词」，
 * 仿生阅读用它决定「要不要加粗」—— 两处必须用同一套区间，所以只留一份定义。
 */
export function containsCjk(text: string): boolean {
  return CJK_PATTERN.test(text);
}

/**
 * 规范化一个词元：去掉首尾的撇号与连字符，然后小写。
 *
 * 词库的状态键、字典查询、以及单词爆炸面板挑出来的词都用它，所以这个词元清洗
 * 只有一份实现 —— 两处不一致会让词查不到自己的状态。
 */
export function cleanToken(raw: string): string {
  return raw.replace(/^['’-]+|['’-]+$/g, '').toLowerCase();
}

/**
 * Tokenize a paragraph or text block into an array of words and non-words
 */
export function tokenizeText(text: string, paragraphIndex = 0): Token[] {
  if (!text) return [];

  const tokens: Token[] = [];
  let lastIndex = 0;
  let tokenCounter = 0;

  // Find matches of words
  const matches = [...text.matchAll(WORD_REGEX)];

  for (const match of matches) {
    const matchIndex = match.index ?? 0;
    const word = match[0];

    // Check if there is non-word text before this word (whitespace, punctuation)
    if (matchIndex > lastIndex) {
      const nonWordText = text.slice(lastIndex, matchIndex);
      tokens.push({
        id: `p${paragraphIndex}-t${tokenCounter++}`,
        raw: nonWordText,
        isWord: false,
        cleanWord: '',
      });
    }

    // Clean word: strip surrounding hyphens/apostrophes, convert to lowercase
    const clean = cleanToken(word);

    tokens.push({
      id: `p${paragraphIndex}-t${tokenCounter++}`,
      raw: word,
      // 必须含字母（纯数字不算词），且不能是中日韩 —— 见 CJK_PATTERN。
      isWord: clean.length > 0 && /\p{L}/u.test(clean) && !CJK_PATTERN.test(clean),
      cleanWord: clean,
    });

    lastIndex = matchIndex + word.length;
  }

  // Any trailing non-word text
  if (lastIndex < text.length) {
    tokens.push({
      id: `p${paragraphIndex}-t${tokenCounter++}`,
      raw: text.slice(lastIndex),
      isWord: false,
      cleanWord: '',
    });
  }

  return tokens;
}

/**
 * Extract all unique clean words from tokens
 */
export function extractWordsFromTokens(tokens: Token[]): string[] {
  const set = new Set<string>();
  for (const token of tokens) {
    if (token.isWord && token.cleanWord) {
      set.add(token.cleanWord);
    }
  }
  return Array.from(set);
}

/** Default reading chunk size, shared by the reader, the store and the footer. */
export const WORDS_PER_PAGE = 220;

/**
 * Paginate full chapter text into comfortable reading chunks (~200-260 words per page)
 * Keeps paragraphs intact whenever possible so sentences are not cut abruptly.
 */
export function paginateText(chapterContent: string, targetWordsPerPage = WORDS_PER_PAGE): string[] {
  if (!chapterContent.trim()) {
    return [''];
  }

  // Normalize newlines
  const normalized = chapterContent.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  
  // Split into paragraphs
  const paragraphs = normalized.split(/\n\s*\n/).filter((p) => p.trim().length > 0);
  if (paragraphs.length === 0) {
    return [normalized];
  }

  const pages: string[] = [];
  let currentPageParagraphs: string[] = [];
  let currentWordCount = 0;

  for (const para of paragraphs) {
    // Count words in paragraph
    const paraWords = para.trim().split(/\s+/).length;

    // If paragraph alone is larger than targetWordsPerPage * 1.5, break into sentences
    if (paraWords > targetWordsPerPage * 1.5) {
      // Flush previous page if exists
      if (currentPageParagraphs.length > 0) {
        pages.push(currentPageParagraphs.join('\n\n'));
        currentPageParagraphs = [];
        currentWordCount = 0;
      }

      // Break long paragraph by sentences
      const sentences = para.match(/[^.!?]+[.!?]+(\s+|$)|[^.!?]+$/g) || [para];
      let sentenceChunk: string[] = [];
      let sentenceChunkWords = 0;

      for (const sent of sentences) {
        const sentWords = sent.trim().split(/\s+/).length;
        if (sentenceChunkWords + sentWords > targetWordsPerPage && sentenceChunk.length > 0) {
          pages.push(sentenceChunk.join(' ').trim());
          sentenceChunk = [sent];
          sentenceChunkWords = sentWords;
        } else {
          sentenceChunk.push(sent);
          sentenceChunkWords += sentWords;
        }
      }
      if (sentenceChunk.length > 0) {
        pages.push(sentenceChunk.join(' ').trim());
      }
      continue;
    }

    // Standard paragraph accumulation
    if (currentWordCount + paraWords > targetWordsPerPage && currentPageParagraphs.length > 0) {
      pages.push(currentPageParagraphs.join('\n\n'));
      currentPageParagraphs = [para];
      currentWordCount = paraWords;
    } else {
      currentPageParagraphs.push(para);
      currentWordCount += paraWords;
    }
  }

  if (currentPageParagraphs.length > 0) {
    pages.push(currentPageParagraphs.join('\n\n'));
  }

  return pages.length > 0 ? pages : [''];
}
