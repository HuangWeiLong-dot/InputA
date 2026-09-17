import React, { useState } from 'react';
import { X, Search, BookOpen, FileText, Loader2, AlertCircle, Library } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { SAMPLE_BOOKS } from '../data/sampleBooks';
import { searchGutendexBooks, loadBookFromGutendex } from '../services/gutendexApi';
import type { GutendexBookResult } from '../services/gutendexApi';
import type { Book } from '../types/reader';

const PRIMARY_BUTTON =
  'flex h-9 items-center justify-center gap-1.5 border border-[var(--text-strong)] bg-[var(--text-strong)] px-4 text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--bg-surface)] transition-opacity hover:opacity-85 disabled:cursor-not-allowed disabled:opacity-40';

const FIELD =
  'w-full border border-[var(--border-color)] bg-[var(--bg-main)] px-3 py-2 text-sm text-[var(--text-main)] placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]';

const TAB_BUTTON = '-mb-px border-b-2 px-3 pb-2.5 text-[11px] font-semibold uppercase tracking-[0.08em] transition-colors';

export const BookSelectorModal: React.FC = () => {
  const { isBookCatalogOpen, setBookCatalogOpen, setCurrentBook } = useReaderStore();

  const [activeTab, setActiveTab] = useState<'builtin' | 'gutendex' | 'paste'>('builtin');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<GutendexBookResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isLoadingBook, setIsLoadingBook] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  const [customTitle, setCustomTitle] = useState('');
  const [customContent, setCustomContent] = useState('');

  if (!isBookCatalogOpen) return null;

  const handleSelectBuiltin = (book: Book) => {
    setCurrentBook(book);
    setBookCatalogOpen(false);
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
      setCurrentBook(book);
      setBookCatalogOpen(false);
    } catch (err) {
      const reason = err instanceof Error ? err.message : '网络或跨域限制';
      setSearchError(`加载读物失败：${reason}`);
    } finally {
      setIsLoadingBook(false);
    }
  };

  const handleSaveCustomArticle = () => {
    if (!customContent.trim()) return;

    const customBook: Book = {
      id: `custom-${Date.now()}`,
      title: customTitle.trim() || '自定义导入读物',
      author: 'User Imported',
      source: 'custom',
      chapters: [{ title: 'Section 1', content: customContent.trim() }],
    };

    setCurrentBook(customBook);
    setBookCatalogOpen(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      <div className="flex max-h-[85vh] w-full max-w-2xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--border-color)] p-4">
          <div className="flex items-center gap-2">
            <Library className="h-4 w-4 text-[var(--accent)]" />
            <h2 className="text-sm font-semibold text-[var(--text-strong)]">读物库与书籍导入</h2>
          </div>
          <button
            type="button"
            onClick={() => setBookCatalogOpen(false)}
            className="flex h-7 w-7 items-center justify-center text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-hover)] hover:text-[var(--text-strong)]"
            aria-label="关闭"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Tabs */}
        <div className="flex gap-1 border-b border-[var(--border-color)] px-4">
          {(
            [
              { id: 'builtin', label: '经典名著' },
              { id: 'gutendex', label: 'Gutendex 搜索' },
              { id: 'paste', label: '粘贴文章' },
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
        <div className="flex-1 overflow-y-auto p-5">
          {/* 1. Built-in samples */}
          {activeTab === 'builtin' && (
            <div className="space-y-2">
              <p className="mb-3 text-[11px] text-[var(--text-muted)]">
                内置公版书样章，零网络延迟，开箱即读。
              </p>
              {SAMPLE_BOOKS.map((book) => (
                <button
                  key={book.id}
                  type="button"
                  onClick={() => handleSelectBuiltin(book)}
                  className="flex w-full items-center justify-between gap-4 border border-[var(--border-color)] p-3.5 text-left transition-colors hover:border-[var(--accent)] hover:bg-[var(--bg-hover)]"
                >
                  <span className="flex min-w-0 items-center gap-3">
                    <BookOpen className="h-4 w-4 shrink-0 text-[var(--accent)]" />
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold text-[var(--text-strong)]">
                        {book.title}
                      </span>
                      <span className="block truncate text-[11px] text-[var(--text-muted)]">
                        {book.author} · {book.chapters.length} 章
                      </span>
                    </span>
                  </span>
                  <span className="shrink-0 text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--accent)]">
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
                  <Search className="absolute top-1/2 left-3 h-3.5 w-3.5 -translate-y-1/2 text-[var(--text-muted)]" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(event) => setSearchQuery(event.target.value)}
                    placeholder="书名或作者英文，例如 dracula / austen / time"
                    className={`${FIELD} pl-9`}
                  />
                </div>
                <button
                  type="submit"
                  disabled={isSearching || !searchQuery.trim()}
                  className={PRIMARY_BUTTON}
                >
                  {isSearching ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Search className="h-3.5 w-3.5" />}
                  <span>搜索</span>
                </button>
              </form>

              {searchError && (
                <div className="flex items-start gap-2 border border-[var(--highlight-border)] bg-[var(--highlight-bg)] p-3 text-[11px] leading-relaxed text-[var(--highlight-text)]">
                  <AlertCircle className="mt-px h-3.5 w-3.5 shrink-0" />
                  <span>{searchError}</span>
                </div>
              )}

              {isLoadingBook && (
                <div className="flex flex-col items-center gap-2 py-10 text-[var(--text-muted)]">
                  <Loader2 className="h-5 w-5 animate-spin text-[var(--accent)]" />
                  <p className="text-[11px]">正在获取正文并切分章节…</p>
                </div>
              )}

              <div className="space-y-2">
                {searchResults.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => handleSelectGutendexBook(item)}
                    className="flex w-full items-center justify-between gap-4 border border-[var(--border-color)] p-3.5 text-left transition-colors hover:border-[var(--accent)] hover:bg-[var(--bg-hover)]"
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold text-[var(--text-strong)]">
                        {item.title}
                      </span>
                      <span className="block truncate text-[11px] text-[var(--text-muted)]">
                        {item.authors?.map((author) => author.name).join(', ') || 'Unknown'} · 下载量{' '}
                        {item.download_count}
                      </span>
                    </span>
                    <span className="shrink-0 text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--accent)]">
                      载入 →
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* 3. Paste custom text */}
          {activeTab === 'paste' && (
            <div className="space-y-4">
              <div>
                <label className="mb-1 block text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--text-muted)]">
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
                <label className="mb-1 block text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--text-muted)]">
                  粘贴英文正文
                </label>
                <textarea
                  rows={9}
                  value={customContent}
                  onChange={(event) => setCustomContent(event.target.value)}
                  placeholder="粘贴任意英文文章、新闻、故事或教材文本…"
                  className={`${FIELD} font-serif leading-relaxed`}
                />
              </div>

              <button
                type="button"
                onClick={handleSaveCustomArticle}
                disabled={!customContent.trim()}
                className="flex h-10 w-full items-center justify-center gap-2 border border-[var(--text-strong)] bg-[var(--text-strong)] text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--bg-surface)] transition-opacity hover:opacity-85 disabled:cursor-not-allowed disabled:opacity-40"
              >
                <FileText className="h-3.5 w-3.5" />
                <span>载入阅读器</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

