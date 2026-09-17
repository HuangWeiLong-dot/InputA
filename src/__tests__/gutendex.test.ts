import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { GutendexBookResult } from '../services/gutendexApi';

/**
 * Importing a book from Gutendex is a two step flow:
 *   1. /books answers with bibliographic data + a `formats` map of download links
 *   2. one of the "text/plain" links is fetched separately and returns the body
 *
 * Gutenberg answers step 2 without any CORS header (and redirects to a plain
 * HTTP cache URL), so it must go through the local backend.
 */

const FRANKENSTEIN: GutendexBookResult = {
  id: 84,
  title: 'Frankenstein; Or, The Modern Prometheus',
  authors: [{ name: 'Shelley, Mary Wollstonecraft' }],
  formats: {
    'text/html; charset=utf-8': 'https://www.gutenberg.org/ebooks/84.html.utf-8',
    'application/epub+zip': 'https://www.gutenberg.org/ebooks/84.epub.images',
    'text/plain; charset=us-ascii': 'https://www.gutenberg.org/files/84/84-0.txt',
    'text/plain; charset=utf-8': 'https://www.gutenberg.org/ebooks/84.txt.utf-8',
    'image/jpeg': 'https://www.gutenberg.org/cache/epub/84/pg84.cover.medium.jpg',
  },
  download_count: 4321,
};

const GUTENBERG_TEXT = [
  'The Project Gutenberg eBook of Frankenstein',
  '',
  'This eBook is for the use of anyone anywhere in the United States.',
  '',
  '*** START OF THE PROJECT GUTENBERG EBOOK FRANKENSTEIN ***',
  '',
  'CHAPTER I.',
  '',
  'Letter one text. '.repeat(30).trim(),
  '',
  'CHAPTER II.',
  '',
  'Letter two text. '.repeat(30).trim(),
  '',
  '*** END OF THE PROJECT GUTENBERG EBOOK FRANKENSTEIN ***',
  '',
  'Updated editions will replace the previous one.',
].join('\n');

function stubResponse(body: string, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
    json: async () => JSON.parse(body),
  } as unknown as Response;
}

/** Mock that answers the backend health probe deterministically. */
function stubFetch(health: 'ok' | 'down', handle: (url: string) => Response | Promise<Response>) {
  const seen: string[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const target = String(url);
      seen.push(target);
      if (target === '/api/health') {
        return health === 'ok' ? stubResponse('{"ok":true}') : stubResponse('{"ok":false}', 404);
      }
      return handle(target);
    }),
  );
  return seen;
}

async function loadService() {
  vi.resetModules();
  return import('../services/gutendexApi');
}

describe('Gutendex book import', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('STEP 1: collects only the plain-text download links, UTF-8 first', async () => {
    const { collectPlainTextUrls } = await loadService();

    expect(collectPlainTextUrls(FRANKENSTEIN)).toEqual([
      'https://www.gutenberg.org/ebooks/84.txt.utf-8',
      'https://www.gutenberg.org/files/84/84-0.txt',
    ]);
  });

  it('reports clearly when a book has no plain-text format', async () => {
    const { collectPlainTextUrls } = await loadService();
    expect(
      collectPlainTextUrls({
        ...FRANKENSTEIN,
        formats: { 'application/epub+zip': 'https://www.gutenberg.org/ebooks/84.epub.images' },
      }),
    ).toEqual([]);
  });

  it('strips the Gutenberg header/footer and splits on chapter headings', async () => {
    const { processGutenbergText } = await loadService();
    const chapters = processGutenbergText(GUTENBERG_TEXT, 'Frankenstein');

    expect(chapters.map((chapter) => chapter.title)).toEqual(['CHAPTER I.', 'CHAPTER II.']);
    expect(chapters[0].content).toContain('Letter one text.');
    expect(chapters[0].content).not.toContain('START OF THE PROJECT GUTENBERG');
    expect(chapters[1].content).toContain('Letter two text.');
    expect(chapters[1].content).not.toContain('END OF THE PROJECT GUTENBERG');
    expect(chapters[1].content).not.toContain('Updated editions will replace');
  });

  it('keeps a long title page as a front-matter chapter', async () => {
    const { processGutenbergText } = await loadService();
    const titlePage = 'A very long dedication. '.repeat(40).trim();
    const chapters = processGutenbergText(
      `${titlePage}\n\nCHAPTER I.\n\n${'Body text. '.repeat(40).trim()}\n\nCHAPTER II.\n\n${'More text. '.repeat(40).trim()}`,
      'Book',
    );

    expect(chapters[0].title).toBe('前言 / Front Matter');
    expect(chapters[0].content).toContain('A very long dedication.');
    expect(chapters.map((chapter) => chapter.title)).toContain('CHAPTER II.');
  });

  it('falls back to size based sections for books without headings', async () => {
    const { processGutenbergText } = await loadService();
    const chapters = processGutenbergText('word '.repeat(2200), 'Plain Book');

    expect(chapters.length).toBeGreaterThan(1);
    expect(chapters[0].title).toBe('Section 1');
  });

  it('keeps a short text as a single chapter named after the book', async () => {
    const { processGutenbergText } = await loadService();
    expect(processGutenbergText('A short public domain poem.', 'My Book')).toEqual([
      { title: 'My Book', content: 'A short public domain poem.' },
    ]);
  });
});

describe('Gutendex text download (step 2)', () => {
  const TXT_URL = 'https://www.gutenberg.org/ebooks/84.txt.utf-8';

  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('downloads through the same-origin backend when it is running', async () => {
    const seen = stubFetch('ok', (url) => {
      if (url.startsWith('/api/books/text')) return stubResponse(GUTENBERG_TEXT);
      throw new Error(`unexpected request: ${url}`);
    });

    const { fetchGutenbergText } = await loadService();
    const text = await fetchGutenbergText(TXT_URL);

    expect(text).toBe(GUTENBERG_TEXT);
    expect(seen[1]).toBe(`/api/books/text?url=${encodeURIComponent(TXT_URL)}`);
  });

  it('rejects an HTML error page instead of treating it as book text', async () => {
    stubFetch('ok', () => stubResponse('<!DOCTYPE html><html><body>522</body></html>'));

    const { fetchGutenbergText } = await loadService();
    await expect(fetchGutenbergText(TXT_URL)).rejects.toThrow(/HTML/);
  });

  it('explains how to start the backend when every attempt fails', async () => {
    stubFetch('down', () => {
      throw new TypeError('Failed to fetch');
    });

    const { fetchGutenbergText } = await loadService();
    await expect(fetchGutenbergText(TXT_URL)).rejects.toThrow(/npm run server/);
  });

  it('routes the search request through the backend as well', async () => {
    const seen = stubFetch('ok', () =>
      stubResponse(JSON.stringify({ count: 1, results: [FRANKENSTEIN] })),
    );

    const { searchGutendexBooks } = await loadService();
    const results = await searchGutendexBooks('frankenstein');

    expect(results).toHaveLength(1);
    expect(seen[1]).toBe('/api/books/search?query=frankenstein');
  });
});

describe('loadBookFromGutendex', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('assembles a readable book from the downloaded text', async () => {
    stubFetch('ok', (url) => {
      if (url.startsWith('/api/books/text')) return stubResponse(GUTENBERG_TEXT);
      return stubResponse('{"count":0,"results":[]}');
    });

    const { loadBookFromGutendex } = await loadService();
    const book = await loadBookFromGutendex(FRANKENSTEIN);

    expect(book.id).toBe('gutendex-84');
    expect(book.source).toBe('gutenberg');
    expect(book.author).toBe('Shelley, Mary Wollstonecraft');
    expect(book.coverUrl).toBe('https://www.gutenberg.org/cache/epub/84/pg84.cover.medium.jpg');
    expect(book.chapters.length).toBe(2);
    expect(book.chapters[0].content).toContain('Letter one text.');
  });

  it('fails with a readable message when the book has no plain text', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => stubResponse('{"ok":true}')));

    const { loadBookFromGutendex } = await loadService();

    await expect(
      loadBookFromGutendex({
        ...FRANKENSTEIN,
        formats: { 'text/html': 'https://www.gutenberg.org/ebooks/84.html.utf-8' },
      }),
    ).rejects.toThrow(/纯文本/);
  });
});
