import type { Book, BookChapter } from '../types/reader';
import { apiUrl, hasBackend, isBackendUrl } from './apiBase';

export interface GutendexBookResult {
  id: number;
  title: string;
  authors: Array<{ name: string }>;
  formats: Record<string, string>;
  download_count: number;
  /**
   * 书源自己声明的语言（如 ['en']）。Gutendex 一直在返回它，只是过去没接。
   * 它比本地检测可靠，所以优先用作 Book.language。
   */
  languages?: string[];
}

export interface GutendexSearchResponse {
  count: number;
  results: GutendexBookResult[];
}

/**
 * Importing a book is a two step flow:
 *
 *   1. Gutendex answers with bibliographic data (title, authors, cover) plus a
 *      `formats` object that holds download links — including the plain-text
 *      ones (`text/plain; charset=utf-8`).
 *   2. That link is requested separately and returns the actual book body.
 *
 * Gutenberg sends no Access-Control-Allow-Origin header and redirects
 * `ebooks/<id>.txt.utf-8` to a plain HTTP cache URL, so step 2 is routed through
 * the local backend (server/index.js), which follows redirects server-side.
 */
const BACKEND_HINT =
  '无法下载 Gutenberg 正文：请在项目根目录运行 "npm run server" 启动本地代理后端后重试（Gutenberg 不返回 CORS 头，浏览器无法直连）。';

const PLAIN_TEXT_PREFIX = 'text/plain';

const PREFERRED_TEXT_KEYS = [
  'text/plain; charset=utf-8',
  'text/plain; charset=UTF-8',
  'text/plain; charset=us-ascii',
  'text/plain',
];

const SEARCH_TIMEOUT_MS = 20000;
const DOWNLOAD_TIMEOUT_MS = 90000;

async function fetchWithTimeout(url: string, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/* --------------------- STEP 1: find a book and its links ------------------- */

export async function searchGutendexBooks(query: string): Promise<GutendexBookResult[]> {
  const trimmed = query.trim();
  if (!trimmed) return [];

  // 不再把 languages=en 写死在 URL 里：多语言支持的目标是「检测与适配」，而
  // 写死这个参数等于连非英文书都搜不出来。语言交给结果自己声明（languages 字段）。
  const url = (await hasBackend())
    ? apiUrl(`/api/books/search?query=${encodeURIComponent(trimmed)}`)
    : `https://gutendex.com/books?search=${encodeURIComponent(trimmed)}`;

  const res = await fetchWithTimeout(url, SEARCH_TIMEOUT_MS);
  if (!res.ok) {
    throw new Error(`Gutendex 搜索失败（HTTP ${res.status}）`);
  }

  const data = (await res.json()) as GutendexSearchResponse;
  return data.results ?? [];
}

/**
 * Every plain-text download link advertised by a Gutendex result, best first
 * (UTF-8 editions before US-ASCII ones) and de-duplicated.
 */
export function collectPlainTextUrls(item: GutendexBookResult): string[] {
  const formats = item.formats ?? {};
  const urls: string[] = [];

  const add = (value?: string) => {
    if (!value || !/^https?:\/\//i.test(value)) return;
    if (!urls.includes(value)) urls.push(value);
  };

  for (const key of PREFERRED_TEXT_KEYS) {
    add(formats[key]);
  }
  for (const [key, value] of Object.entries(formats)) {
    if (key.toLowerCase().startsWith(PLAIN_TEXT_PREFIX)) add(value);
  }

  return urls;
}

/* --------------------------- STEP 2: download it --------------------------- */

function labelFor(candidate: string): string {
  if (isBackendUrl(candidate)) return '本地后端';
  if (candidate.startsWith('https://api.allorigins.win')) return '公共代理 allorigins';
  return '浏览器直连';
}

/** STEP 2: download the book body behind one of the plain-text links. */
export async function fetchGutenbergText(txtUrl: string): Promise<string> {
  const useBackend = await hasBackend();

  const candidates: string[] = [];
  if (useBackend) {
    candidates.push(apiUrl(`/api/books/text?url=${encodeURIComponent(txtUrl)}`));
  }
  candidates.push(txtUrl);
  candidates.push(`https://api.allorigins.win/raw?url=${encodeURIComponent(txtUrl)}`);

  const failures: string[] = [];

  for (const candidate of candidates) {
    try {
      const res = await fetchWithTimeout(candidate, DOWNLOAD_TIMEOUT_MS);
      if (!res.ok) {
        failures.push(`${labelFor(candidate)} HTTP ${res.status}`);
        continue;
      }

      const text = await res.text();
      if (text.trim().length === 0) {
        failures.push(`${labelFor(candidate)} 返回空内容`);
        continue;
      }
      if (/^\s*<(?:!doctype|html)/i.test(text.slice(0, 200))) {
        failures.push(`${labelFor(candidate)} 返回的是 HTML 页面`);
        continue;
      }

      return text;
    } catch (error) {
      const reason = error instanceof Error ? error.message : '请求失败';
      failures.push(`${labelFor(candidate)} ${reason}`);
    }
  }

  const detail = failures.length > 0 ? `（尝试记录：${failures.join('；')}）` : '';
  throw new Error(
    useBackend ? `后端未能取回该书的正文${detail}` : `${BACKEND_HINT}${detail}`,
  );
}

/* ------------------------------ Text clean-up ------------------------------ */

const BOILERPLATE_START = /\*{3}\s*START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK[^*]*\*{3}/i;
const BOILERPLATE_END = /\*{3}\s*END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK[^*]*\*{3}/i;

/** Chapter headings such as "CHAPTER I.", "Chapter 12", "LETTER 2", "BOOK III". */
const CHAPTER_HEADING =
  /^[ \t]{0,3}(?:CHAPTER|Chapter|LETTER|Letter|STAVE|Stave|BOOK|Book|PART|Part|SECTION|Section)[ \t]+(?:[0-9]{1,3}|[IVXLCDMivxlcdm]{1,7})(?:[^\n]{0,70})?$/gm;

/** Drop the Project Gutenberg legal header/footer. */
function stripGutenbergBoilerplate(rawText: string): string {
  let text = rawText.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

  const startMatch = text.match(BOILERPLATE_START);
  if (startMatch && startMatch.index !== undefined) {
    text = text.slice(startMatch.index + startMatch[0].length);
  }

  const endMatch = text.match(BOILERPLATE_END);
  if (endMatch && endMatch.index !== undefined) {
    text = text.slice(0, endMatch.index);
  }

  return text.trim();
}

/**
 * Turn one downloaded book into chapters: split on chapter headings when the
 * text has them, otherwise fall back to fixed-size sections.
 */
export function processGutenbergText(rawText: string, defaultTitle: string): BookChapter[] {
  const cleanText = stripGutenbergBoilerplate(rawText);
  if (!cleanText) {
    return [{ title: defaultTitle || 'Chapter 1', content: '' }];
  }

  const headings: Array<{ index: number; title: string }> = [];
  for (const match of cleanText.matchAll(CHAPTER_HEADING)) {
    const index = match.index ?? 0;
    const previous = headings[headings.length - 1];
    // Ignore a "heading" glued to the previous one (table of contents).
    if (previous && index - previous.index < 40) continue;
    headings.push({ index, title: match[0].trim() });
  }

  // A single heading is only trusted in a long text, which keeps narrative
  // sentences starting with "Chapter 3 ..." from splitting a story.
  const canSplitByHeading =
    headings.length >= 2 || (headings.length === 1 && cleanText.length > 3000);

  if (canSplitByHeading) {
    const chapters: BookChapter[] = [];

    const frontMatter = cleanText.slice(0, headings[0].index).trim();
    if (frontMatter.length > 600) {
      chapters.push({ title: '前言 / Front Matter', content: frontMatter });
    }

    headings.forEach((heading, position) => {
      const end = position + 1 < headings.length ? headings[position + 1].index : cleanText.length;
      const content = cleanText.slice(heading.index, end).trim();
      if (!content) return;

      const previous = chapters[chapters.length - 1];
      if (previous && content.length < 200) {
        // Fragments too small to read on their own stay with the chapter above.
        previous.content = `${previous.content}\n\n${content}`;
        return;
      }

      chapters.push({ title: heading.title.slice(0, 80), content });
    });

    if (chapters.length > 0) {
      return chapters;
    }
  }

  const words = cleanText.split(/\s+/).filter(Boolean);
  if (words.length > 1800) {
    const wordsPerSection = 1500;
    const chapters: BookChapter[] = [];
    for (let start = 0; start < words.length; start += wordsPerSection) {
      chapters.push({
        title: `Section ${chapters.length + 1}`,
        content: words.slice(start, start + wordsPerSection).join(' '),
      });
    }
    return chapters;
  }

  return [{ title: defaultTitle || 'Chapter 1', content: cleanText }];
}

/* ------------------------------ Book assembly ----------------------------- */

export async function loadBookFromGutendex(item: GutendexBookResult): Promise<Book> {
  const txtUrls = collectPlainTextUrls(item);
  if (txtUrls.length === 0) {
    throw new Error('该书未提供纯文本（text/plain）格式，无法在阅读器中打开。');
  }

  const failures: string[] = [];

  for (const txtUrl of txtUrls) {
    try {
      const rawText = await fetchGutenbergText(txtUrl);
      const chapters = processGutenbergText(rawText, item.title);

      if (chapters.every((chapter) => chapter.content.trim().length === 0)) {
        failures.push(`${txtUrl} 正文为空`);
        continue;
      }

      return {
        id: `gutendex-${item.id}`,
        title: item.title,
        author: item.authors?.map((author) => author.name).join(', ') || 'Unknown Author',
        coverUrl: item.formats?.['image/jpeg'],
        chapters,
        source: 'gutenberg',
        // 书源元数据优先；缺失时由导入方对正文跑一次检测。
        language: item.languages?.[0],
      };
    } catch (error) {
      failures.push(error instanceof Error ? error.message : '下载失败');
    }
  }

  throw new Error(`加载读物失败：${failures.join('；')}`);
}
