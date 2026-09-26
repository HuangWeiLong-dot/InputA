import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createMemoryBookStorage,
  lastReadBookId,
  loadLastReadBook,
  saveBook,
} from '../services/bookStorage';
import type { BookStorage } from '../services/bookStorage';
import { useReaderStore } from '../store/useReaderStore';
import type { Book, ReadingProgress } from '../types/reader';

/**
 * 书的持久化。
 *
 * 这里能覆盖的是**逻辑**：该恢复哪本、存不存、失败怎么办。IndexedDB 那一层接线覆盖不到
 * —— vitest 跑在 node 环境、没有 indexedDB，而这里刻意不引 fake-indexeddb（与 Android
 * 侧用内存 Room 测试同一个思路）。所以那一层必须在浏览器里手工验，不是可选项。
 */

function makeBook(id: string, source: Book['source'] = 'custom'): Book {
  return {
    id,
    title: `Book ${id}`,
    author: 'User Imported',
    source,
    chapters: [{ title: 'Chapter 1', content: 'Hello world.' }],
  };
}

function progress(entries: Array<[string, number]>): Record<string, ReadingProgress> {
  return Object.fromEntries(
    entries.map(([id, updatedAt]) => [
      id,
      { bookId: id, bookTitle: id, chapterIndex: 0, pageIndex: 0, updatedAt },
    ]),
  );
}

describe('lastReadBookId', () => {
  it('picks the most recently updated entry, not the first one listed', () => {
    expect(lastReadBookId(progress([['old', 100], ['new', 900], ['middle', 500]]))).toBe('new');
  });

  it('returns null when nothing has been read', () => {
    expect(lastReadBookId({})).toBeNull();
  });

  it('ignores an entry whose updatedAt is missing or not a number', () => {
    // 老版本写下的进度、或手工改坏的 localStorage，都不该让恢复流程崩掉。
    const broken = {
      ...progress([['good', 100]]),
      broken: { bookId: 'broken' } as unknown as ReadingProgress,
      alsoBroken: null as unknown as ReadingProgress,
    };
    expect(lastReadBookId(broken)).toBe('good');
  });
});

describe('loadLastReadBook', () => {
  it('reads back the body of the most recently read book', async () => {
    const storage = createMemoryBookStorage([makeBook('a'), makeBook('b')]);

    const book = await loadLastReadBook(progress([['a', 1], ['b', 2]]), storage);

    expect(book?.id).toBe('b');
    expect(book?.chapters[0].content).toBe('Hello world.');
  });

  it('returns null when there is no progress to go on', async () => {
    await expect(loadLastReadBook({}, createMemoryBookStorage())).resolves.toBeNull();
  });

  it('returns null when the progress names a book the storage no longer has', async () => {
    // 清了浏览器数据、或进度表比书活得久时会这样。启动必须退回内置样书，而不是报错。
    await expect(loadLastReadBook(progress([['gone', 1]]), createMemoryBookStorage())).resolves.toBeNull();
  });

  it('returns null instead of throwing when the storage itself fails', async () => {
    const failing: BookStorage = {
      put: async () => undefined,
      get: async () => {
        throw new Error('IndexedDB is unavailable');
      },
    };
    await expect(loadLastReadBook(progress([['a', 1]]), failing)).resolves.toBeNull();
  });
});

describe('saveBook', () => {
  it('stores a book and reads it back', async () => {
    const storage = createMemoryBookStorage();
    await expect(saveBook(makeBook('imported'), storage)).resolves.toBe(true);
    await expect(storage.get('imported')).resolves.toMatchObject({ title: 'Book imported' });
  });

  it('does not store built-in samples, whose text already ships in the bundle', async () => {
    const storage = createMemoryBookStorage();

    await expect(saveBook(makeBook('sample', 'builtin'), storage)).resolves.toBe(true);
    await expect(storage.get('sample')).resolves.toBeNull();
  });

  it('reports failure rather than throwing when the storage rejects', async () => {
    // 配额写满、或浏览器禁用了本地数据库。返回值让调用方去提示用户 ——
    // 这里同时钉住「失败会被记录下来」，而不是悄无声息。
    const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    const failing: BookStorage = {
      put: async () => {
        throw new Error('QuotaExceededError');
      },
      get: async () => null,
    };

    await expect(saveBook(makeBook('big'), failing)).resolves.toBe(false);
    expect(error).toHaveBeenCalled();
    error.mockRestore();
  });
});

describe('useReaderStore.restoreLastBook', () => {
  beforeEach(() => {
    localStorage.clear();
    useReaderStore.setState({ currentBook: null, currentChapterIndex: 0, currentPageIndex: 0 });
  });

  it('reports nothing to restore when no progress was ever saved', async () => {
    await expect(useReaderStore.getState().restoreLastBook()).resolves.toBe(false);
    expect(useReaderStore.getState().currentBook).toBeNull();
  });

  it('leaves an already-open book alone', async () => {
    useReaderStore.setState({ currentBook: makeBook('already-open') });

    await expect(useReaderStore.getState().restoreLastBook()).resolves.toBe(false);
    expect(useReaderStore.getState().currentBook?.id).toBe('already-open');
  });

  it('degrades to "nothing to restore" when the saved body cannot be read back', async () => {
    // 用正常的选书路径写一条进度，再把内存里的书清掉 —— 于是「有进度、读不回正文」
    // 这个情形就复现了（node 里连 IndexedDB 都没有，正好走的是同一条降级分支）。
    useReaderStore.getState().setCurrentBook(makeBook('custom-1'));
    useReaderStore.setState({ currentBook: null });

    await expect(useReaderStore.getState().restoreLastBook()).resolves.toBe(false);
    expect(useReaderStore.getState().currentBook).toBeNull();
  });

  it('can name the last read book even when its body was never stored', () => {
    // 内置样书就属于这种：正文在 bundle 里，`saveBook` 会跳过入库，所以 App 只能靠 id
    // 把它们找回来（否则读到第 3 本样书、刷新后会跳回第 1 本）。
    useReaderStore.getState().setCurrentBook(makeBook('sherlock-holmes', 'builtin'));

    expect(useReaderStore.getState().lastReadBookIdFromProgress()).toBe('sherlock-holmes');
  });

  it('survives a progress key that holds valid JSON of the wrong shape', async () => {
    // `JSON.parse('null')` 是**合法**的。若照原样当对象用，启动时会抛异常、阅读器白屏。
    // 键名是公开契约（README 与 CLAUDE.md 都列了这几个 key），所以这里直接写上。
    localStorage.setItem('language_reader_progress', 'null');

    expect(useReaderStore.getState().lastReadBookIdFromProgress()).toBeNull();
    await expect(useReaderStore.getState().restoreLastBook()).resolves.toBe(false);
  });
});
