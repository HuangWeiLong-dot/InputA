import type { Book, ReadingProgress } from '../types/reader';

/**
 * 书的持久化。
 *
 * 用 IndexedDB 而不是 localStorage：一本导入的书正文是 MB 级的，而 localStorage 只有
 * 约 5MB、还按 UTF-16 存（每个字符两个字节）。词库、笔记、进度、设置继续留在
 * localStorage —— 那四张表都小，两边的取舍互不影响。
 *
 * 抽一层接口是为了可测：IndexedDB 在 vitest 的 node 环境里不存在，而这里刻意**不**引入
 * fake-indexeddb（与 Android 侧用内存 Room 测试同一个思路）。于是「该恢复哪本书」这类
 * 逻辑可以对着内存实现单测，只有下面那个 IndexedDB 实现需要手工在浏览器里验。
 *
 * 已知的取舍：**只能新增、不能删除**。每次导入都会生成一个新的 `custom-<时间戳>` id，
 * 所以重复导入同一个文件会留下两份。目前没有删书的界面，也还没有清理策略 —— 真到占用
 * 明显的那天再补（那是产品决定，不是这里的疏漏）。
 */
export interface BookStorage {
  put(book: Book): Promise<void>;
  get(id: string): Promise<Book | null>;
}

const DB_NAME = 'inputa-books';
const DB_VERSION = 1;
const STORE_NAME = 'books';

export function createIndexedDbBookStorage(): BookStorage {
  let connection: Promise<IDBDatabase> | null = null;

  const open = (): Promise<IDBDatabase> => {
    if (connection) return connection;
    connection = new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error ?? new Error('打开 IndexedDB 失败'));
    });
    return connection;
  };

  /**
   * 一个操作一个事务。
   *
   * **不要在事务里 await 无关的东西**：IndexedDB 的事务会在微任务间隙自动提交，提交之后
   * 再往它上面发请求就抛 TransactionInactiveError —— 这是这套 API 最容易踩的坑。
   * 这里每次操作都开一个新事务，且中间不 await，所以不会遇到。
   */
  const run = <T>(
    mode: IDBTransactionMode,
    action: (store: IDBObjectStore) => IDBRequest<T>,
  ): Promise<T> =>
    open().then(
      (db) =>
        new Promise<T>((resolve, reject) => {
          const transaction = db.transaction(STORE_NAME, mode);
          const request = action(transaction.objectStore(STORE_NAME));
          request.onsuccess = () => resolve(request.result);
          request.onerror = () => reject(request.error ?? new Error('IndexedDB 操作失败'));
        }),
    );

  return {
    put: (book) => run('readwrite', (store) => store.put(book)).then(() => undefined),
    get: (id) =>
      run<Book | undefined>('readonly', (store) => store.get(id)).then((book) => book ?? null),
  };
}

/** 测试与降级用。 */
export function createMemoryBookStorage(initial: Book[] = []): BookStorage {
  const books = new Map(initial.map((book) => [book.id, book]));
  return {
    put: async (book) => {
      books.set(book.id, book);
    },
    get: async (id) => books.get(id) ?? null,
  };
}

/**
 * 应用用的那一份。惰性：`createIndexedDbBookStorage()` 只是关掉一个空 promise，
 * 真正碰 `indexedDB` 是在第一次 put/get —— 所以即使测试里 import 到这个模块也不会炸。
 */
export const bookStorage: BookStorage = createIndexedDbBookStorage();

/**
 * `updatedAt` 最新的那本书，也就是「上次读到哪」。
 *
 * 复用既有的 `language_reader_progress`（`Record<bookId, ReadingProgress>`）而不新加一个
 * 「当前书」键：那份数据本来就在（每次翻页都写），语义也正是这个，再加一个键只会多一处
 * 需要同步的状态。
 */
export function lastReadBookId(progress: Record<string, ReadingProgress>): string | null {
  let bestId: string | null = null;
  let bestAt = Number.NEGATIVE_INFINITY;
  for (const [id, entry] of Object.entries(progress)) {
    const at = entry?.updatedAt;
    if (typeof at === 'number' && at > bestAt) {
      bestAt = at;
      bestId = id;
    }
  }
  return bestId;
}

/**
 * 保存一本非内置的书。返回是否成功 —— **调用方负责让用户看到失败**。
 *
 * 内置样书的正文就在 bundle 里（`src/data/sampleBooks.ts`），不必再存一份。
 *
 * 之所以不把这件事放进 `useReaderStore`：这个 store 是 UI 无关的，而失败必须让用户看见
 * （不存的话刷新后书就没了，而界面会假装一切正常）。提示交给调用它的组件去做，那里也
 * 正好是 `VocabularyModal` 处理导入失败的同一种做法。
 */
export async function saveBook(book: Book, storage: BookStorage = bookStorage): Promise<boolean> {
  if (book.source === 'builtin') return true;
  try {
    await storage.put(book);
    return true;
  } catch (error) {
    console.error('Failed to persist the book:', error);
    return false;
  }
}

/**
 * 「上次读的那本书」的正文。
 *
 * 与 storage 解耦是为了可测：`useReaderStore.restoreLastBook` 只剩接线（把进度表读出来、
 * 把结果塞回 state），而「该取哪本、取不到怎么办」这段逻辑可以对着内存实现单测。
 *
 * 读不到就返回 null，**不抛异常**：恢复失败与「第一次打开」没有区别，启动流程不该因此中断。
 */
export async function loadLastReadBook(
  progress: Record<string, ReadingProgress>,
  storage: BookStorage = bookStorage,
): Promise<Book | null> {
  const id = lastReadBookId(progress);
  if (!id) return null;
  return storage.get(id).catch(() => null);
}
