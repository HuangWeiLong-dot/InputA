import React, { useState } from 'react';
import { X, Search, BookOpen, FileText, Loader2, AlertCircle, Library } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { SAMPLE_BOOKS } from '../data/sampleBooks';
import { searchGutendexBooks, loadBookFromGutendex } from '../services/gutendexApi';
import type { GutendexBookResult } from '../services/gutendexApi';
import { detectLanguage } from '../services/languageDetect';
import type { Book } from '../types/reader';
import { BTN_GHOST, BTN_PRIMARY, FIELD } from './ui';

const TAB_BUTTON =
  '-mb-px border-b-2 px-4 py-2 text-[13px] font-semibold uppercase tracking-[0.08em] transition-colors';

/** One clickable book row, used by the built-in shelf and the search results. */
const BOOK_ROW =
  'flex w-full items-center justify-between gap-4 border border-[var(--border-color)] p-4 text-left transition-colors hover:border-[var(--accent)] hover:bg-[var(--bg-hover)]';

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
    const content = customContent.trim();
    if (!content) return;

    // 粘贴的正文没有任何书源元数据，所以这里跑一次本地检测 —— 语言标签与朗读
    // 音色都靠它。置信度低也照存：检测出来的是「最可能」，比没有强。
    const detected = detectLanguage(content);

    const customBook: Book = {
      id: `custom-${Date.now()}`,
      title: customTitle.trim() || '自定义导入',
      author: 'User Imported',
      source: 'custom',
      language: detected.language,
      chapters: [{ title: 'Section 1', content }],
    };

    setCurrentBook(customBook);
    setBookCatalogOpen(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      <div className="flex max-h-[88vh] w-full max-w-3xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
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

              {searchError && (
                <div className="flex items-start gap-2.5 border border-[var(--highlight-border)] bg-[var(--highlight-bg)] p-4 text-[13px] leading-relaxed text-[var(--highlight-text)]">
                  <AlertCircle className="mt-px h-4 w-4 shrink-0" />
                  <span>{searchError}</span>
                </div>
              )}

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

          {/* 3. Paste custom text */}
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
                className={`${BTN_PRIMARY} w-full`}
              >
                <FileText className="h-4 w-4" />
                <span>载入阅读器</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

