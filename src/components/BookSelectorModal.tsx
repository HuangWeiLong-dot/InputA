import React, { useRef, useState } from 'react';
import { X, Search, BookOpen, FileText, Loader2, AlertCircle, Library, Upload } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { SAMPLE_BOOKS } from '../data/sampleBooks';
import {
  searchGutendexBooks,
  loadBookFromGutendex,
  processGutenbergText,
} from '../services/gutendexApi';
import type { GutendexBookResult } from '../services/gutendexApi';
import { readBookFile } from '../services/bookImport';
import { saveBook } from '../services/bookStorage';
import { detectLanguage } from '../services/languageDetect';
import type { Book } from '../types/reader';
import { BTN_GHOST, BTN_PRIMARY, FIELD } from './ui';

/** 文件选择器能选的东西。PDF 不在其中，理由见 bookImport.ts 的开头。 */
const IMPORT_ACCEPT = '.txt,.text,.md,.markdown,.html,.htm,.xhtml,.epub';

const TAB_BUTTON =
  '-mb-px border-b-2 px-4 py-2 text-[13px] font-semibold uppercase tracking-[0.08em] transition-colors';

/** One clickable book row, used by the built-in shelf and the search results. */
const BOOK_ROW =
  'flex w-full items-center justify-between gap-4 border border-[var(--border-color)] p-4 text-left transition-colors hover:border-[var(--accent)] hover:bg-[var(--bg-hover)]';

/** 就地显示一条错误。搜索失败与文件导入失败都用它，省得两处样式各写一遍、日后漂移。 */
const ErrorNote: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div className="flex items-start gap-2.5 border border-[var(--highlight-border)] bg-[var(--highlight-bg)] p-4 text-[13px] leading-relaxed text-[var(--highlight-text)]">
    <AlertCircle className="mt-px h-4 w-4 shrink-0" />
    <span>{children}</span>
  </div>
);

export const BookSelectorModal: React.FC = () => {
  const { isBookCatalogOpen, setBookCatalogOpen, setCurrentBook } = useReaderStore();

  const [activeTab, setActiveTab] = useState<'builtin' | 'gutendex' | 'paste' | 'file'>('builtin');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<GutendexBookResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isLoadingBook, setIsLoadingBook] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  const [customTitle, setCustomTitle] = useState('');
  /**
   * 粘贴的正文**刻意不进 React state**，只用一个布尔驱动按钮的禁用态。
   *
   * 放进 state 意味着每次按键都要重新渲染整个字符串；粘贴几 MB 之后，光是把那个值
   * 交给 React 就会让输入明显卡顿。正文只在点「载入阅读器」时从 DOM 读一次。
   */
  const [hasCustomContent, setHasCustomContent] = useState(false);
  const customContentRef = useRef<HTMLTextAreaElement>(null);

  const [isImporting, setIsImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isBookCatalogOpen) return null;

  /**
   * 选定一本书的**唯一**出口：进阅读器 + 存到本地。
   *
   * 保存失败必须说出来。不存的话刷新后这本书就没了，而界面会假装一切正常 —— 与
   * `VocabularyModal` 处理导入失败一样用 alert。内置样书的正文就在 bundle 里，
   * `saveBook` 会直接跳过它。
   */
  const openBook = async (book: Book) => {
    setCurrentBook(book);
    setBookCatalogOpen(false);
    if (!(await saveBook(book))) {
      window.alert(
        '这本书没能保存到本地，刷新页面后需要重新导入。\n（正文太大、或浏览器禁用了本地数据库时会出现。）',
      );
    }
  };

  const handleSelectBuiltin = (book: Book) => {
    void openBook(book);
  };

  const handleImportFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // 先把 input 的值清掉：否则再选同一个文件不会触发 change，看起来像没反应。
    event.target.value = '';
    if (!file) return;

    setIsImporting(true);
    setImportError(null);
    try {
      await openBook(await readBookFile(file));
    } catch (error) {
      // 解析失败时书架保持打开，错误就地显示 —— 这样能直接换一个文件重试。
      setImportError(error instanceof Error ? error.message : '这个文件读不出来。');
    } finally {
      setIsImporting(false);
    }
  };

  const handleSearchGutendex = async (event?: React.FormEvent) => {
    if (event) event.preventDefault();
    if (!searchQuery.trim()) return;

    setIsSearching(true);
    setSearchError(null);
    try {
      const results = await searchGutendexBooks(searchQuery.trim());
      setSearchResults(results);
      if (results.length === 0) {
        setSearchError('未找到相关英文书籍，可尝试 "pride"、"time"、"alice" 等常用词。');
      }
    } catch (err) {
      setSearchError(err instanceof Error ? err.message : '搜索 Gutendex 失败，请检查网络连接。');
    } finally {
      setIsSearching(false);
    }
  };

  const handleSelectGutendexBook = async (item: GutendexBookResult) => {
    setIsLoadingBook(true);
    setSearchError(null);
    try {
      const book = await loadBookFromGutendex(item);
      await openBook(book);
    } catch (err) {
      const reason = err instanceof Error ? err.message : '网络或跨域限制';
      setSearchError(`加载读物失败：${reason}`);
    } finally {
      setIsLoadingBook(false);
    }
  };

  const handleSaveCustomArticle = () => {
    const content = (customContentRef.current?.value ?? '').trim();
    if (!content) return;

    const title = customTitle.trim() || '自定义导入';

    // 先切章，而不是塞成单个 "Section 1"：否则一本长文在翻页条上就是一个巨大章节，
    // 分页也只能把段落一路堆下去。复用 Gutenberg 那条切分器 —— 它与书源无关，
    // 认的是 CHAPTER I. / Letter 2 这类标题，认不出来时按 1500 词切成 Section N。
    const chapters = processGutenbergText(content, title);

    // 粘贴的正文没有任何书源元数据，所以这里跑一次本地检测 —— 语言标签与朗读
    // 音色都靠它。置信度低也照存：检测出来的是「最可能」，比没有强。
    const detected = detectLanguage(content);

    const customBook: Book = {
      id: `custom-${Date.now()}`,
      title,
      author: 'User Imported',
      source: 'custom',
      language: detected.language,
      chapters,
    };

    void openBook(customBook);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      {/*
        `dvh` 而不是 `vh`：手机上 `vh` 指的是「浏览器工具栏收起时」的高度，不随键盘
        收缩，于是面板底部会被键盘顶出可视区 —— 表现就是「粘贴一大段文字之后，下方
        按鈕消失了」。`dvh` 跟随实际可视高度。支持面比本项目已经依赖的 `color-mix()`
        更宽，所以不是新的门槛。
      */}
      <div className="flex max-h-[88dvh] w-full max-w-3xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--border-color)] px-5 py-4">
          <div className="flex items-center gap-2.5">
            <Library className="h-5 w-5 text-[var(--accent)]" />
            <h2 className="text-[16px] font-semibold text-[var(--text-strong)]">书本与书本导入</h2>
          </div>
          <button
            type="button"
            onClick={() => setBookCatalogOpen(false)}
            className={BTN_GHOST}
            aria-label="关闭"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Tabs */}
        <div className="flex gap-2 border-b border-[var(--border-color)] px-5">
          {(
            [
              { id: 'builtin', label: '经典名著' },
              { id: 'gutendex', label: 'Gutendex 搜索' },
              { id: 'file', label: '文件' },
              { id: 'paste', label: '粘贴' },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`${TAB_BUTTON} ${
                activeTab === tab.id
                  ? 'border-[var(--accent)] text-[var(--accent)]'
                  : 'border-transparent text-[var(--text-muted)] hover:text-[var(--text-strong)]'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-5">
          {/* 1. Built-in samples */}
          {activeTab === 'builtin' && (
            <div className="space-y-2.5">
              <p className="mb-3 text-[13px] text-[var(--text-muted)]">
                样章。
              </p>
              {SAMPLE_BOOKS.map((book) => (
                <button
                  key={book.id}
                  type="button"
                  onClick={() => handleSelectBuiltin(book)}
                  className={BOOK_ROW}
                >
                  <span className="flex min-w-0 items-center gap-3.5">
                    <BookOpen className="h-5 w-5 shrink-0 text-[var(--accent)]" />
                    <span className="min-w-0">
                      <span className="block truncate text-[15px] font-semibold text-[var(--text-strong)]">
                        {book.title}
                      </span>
                      <span className="block truncate text-[13px] text-[var(--text-muted)]">
                        {book.author} · {book.chapters.length} 章
                      </span>
                    </span>
                  </span>
                  <span className="shrink-0 text-[13px] font-semibold uppercase tracking-[0.08em] text-[var(--accent)]">
                    开始阅读 →
                  </span>
                </button>
              ))}
            </div>
          )}

          {/* 2. Online search */}
          {activeTab === 'gutendex' && (
            <div className="space-y-4">
              <form onSubmit={handleSearchGutendex} className="flex gap-2">
                <div className="relative flex-1">
                  <Search className="absolute top-1/2 left-3.5 h-4 w-4 -translate-y-1/2 text-[var(--text-muted)]" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(event) => setSearchQuery(event.target.value)}
                    placeholder="书名或作者英文，例如 dracula / austen / time"
                    className={`${FIELD} pl-10`}
                  />
                </div>
                <button
                  type="submit"
                  disabled={isSearching || !searchQuery.trim()}
                  className={BTN_PRIMARY}
                >
                  {isSearching ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Search className="h-4 w-4" />
                  )}
                  <span>搜索</span>
                </button>
              </form>

              {searchError && <ErrorNote>{searchError}</ErrorNote>}

              {isLoadingBook && (
                <div className="flex flex-col items-center gap-2.5 py-10 text-[var(--text-muted)]">
                  <Loader2 className="h-6 w-6 animate-spin text-[var(--accent)]" />
                  <p className="text-[13px]">正在获取正文并切分章节…</p>
                </div>
              )}

              <div className="space-y-2.5">
                {searchResults.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => handleSelectGutendexBook(item)}
                    className={BOOK_ROW}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-[15px] font-semibold text-[var(--text-strong)]">
                        {item.title}
                      </span>
                      <span className="block truncate text-[13px] text-[var(--text-muted)]">
                        {item.authors?.map((author) => author.name).join(', ') || 'Unknown'} · 下载量{' '}
                        {item.download_count}
                      </span>
                    </span>
                    <span className="shrink-0 text-[13px] font-semibold uppercase tracking-[0.08em] text-[var(--accent)]">
                      载入 →
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* 3. Import a file */}
          {activeTab === 'file' && (
            <div className="space-y-4">
              <p className="text-[13px] leading-relaxed text-[var(--text-muted)]">
                支持 TXT / Markdown / HTML / EPUB。EPUB 按它自己声明的章节顺序分章；
                其余格式按 CHAPTER I.、Letter 2 这类标题分章，认不出来就按词数均分。
              </p>

              {/* 真实 input 藏起来，用下面的按钮触发 —— 与词汇库导入同一写法。 */}
              <input
                ref={fileInputRef}
                type="file"
                accept={IMPORT_ACCEPT}
                onChange={handleImportFile}
                className="hidden"
                aria-hidden="true"
                tabIndex={-1}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isImporting}
                className={`${BTN_PRIMARY} w-full`}
              >
                {isImporting ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Upload className="h-4 w-4" />
                )}
                <span>{isImporting ? '正在解析…' : '选择文件'}</span>
              </button>

              {importError && <ErrorNote>{importError}</ErrorNote>}

              <p className="text-[12px] leading-relaxed text-[var(--text-muted)]">
                导入的书会存进浏览器本地（IndexedDB），刷新后仍在。
                PDF 暂不支持 —— 它抽出的文本质量通常很差，扫描件更是完全抽不出文字。
              </p>
            </div>
          )}

          {/* 4. Paste custom text */}
          {activeTab === 'paste' && (
            <div className="space-y-5">
              <div>
                <label className="mb-2 block text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--text-muted)]">
                  文章标题（可选）
                </label>
                <input
                  type="text"
                  value={customTitle}
                  onChange={(event) => setCustomTitle(event.target.value)}
                  placeholder="例如 Daily News Article / TED Talk Transcript"
                  className={FIELD}
                />
              </div>

              <div>
                <label className="mb-2 block text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--text-muted)]">
                  粘贴英文正文
                </label>
                <textarea
                  ref={customContentRef}
                  rows={9}
                  defaultValue=""
                  onChange={(event) => setHasCustomContent(event.target.value.trim().length > 0)}
                  placeholder="粘贴任意英文文章、新闻、故事或教材文本…"
                  className={`${FIELD} font-serif leading-relaxed`}
                />
              </div>
            </div>
          )}
        </div>

        {/*
          「载入阅读器」放在滚动区**外面**做成面板 footer。原先它在滚动区最底部，
          正文一长就被推到视野之外；配合上面 `dvh` 那个键盘问题，手机上就彻底够不着了。
          只有粘贴页有这个面板级动作，所以按 tab 条件渲染。
        */}
        {activeTab === 'paste' && (
          <div className="border-t border-[var(--border-color)] px-5 py-4">
            <button
              type="button"
              onClick={handleSaveCustomArticle}
              disabled={!hasCustomContent}
              className={`${BTN_PRIMARY} w-full`}
            >
              <FileText className="h-4 w-4" />
              <span>载入阅读器</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

