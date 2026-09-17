import React, { useMemo, useState } from 'react';
import { X, Search, Volume2, Check, Trash2, BookMarked, Download, RotateCcw } from 'lucide-react';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { useReaderStore } from '../store/useReaderStore';
import { speakWord } from '../services/speechService';

const FIELD =
  'w-full border border-[var(--border-color)] bg-[var(--bg-main)] px-3 py-1.5 text-sm text-[var(--text-main)] placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]';

const SMALL_BUTTON =
  'flex h-7 items-center gap-1 border border-[var(--border-color)] px-2 text-[11px] font-semibold text-[var(--text-main)] transition-colors hover:bg-[var(--bg-hover)]';

export const VocabularyModal: React.FC = () => {
  const isVocabularyOpen = useReaderStore((state) => state.isVocabularyOpen);
  const setVocabularyOpen = useReaderStore((state) => state.setVocabularyOpen);
  const { words, markKnown, markLearning, removeWord, clearVocabulary } = useVocabularyStore();

  const [filterType, setFilterType] = useState<'all' | 'learning' | 'known'>('learning');
  const [searchTerm, setSearchTerm] = useState('');

  const wordEntries = useMemo(
    () => Object.entries(words).map(([word, status]) => ({ word, status })),
    [words],
  );

  const filteredEntries = useMemo(() => {
    const term = searchTerm.toLowerCase().trim();
    return wordEntries.filter((item) => {
      if (filterType !== 'all' && item.status !== filterType) return false;
      if (term && !item.word.includes(term)) return false;
      return true;
    });
  }, [wordEntries, filterType, searchTerm]);

  const learningCount = wordEntries.filter((entry) => entry.status === 'learning').length;
  const knownCount = wordEntries.filter((entry) => entry.status === 'known').length;

  if (!isVocabularyOpen) return null;

  const handleExportJSON = () => {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(words, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', `vocabulary_${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  const handleClear = () => {
    if (window.confirm('确定要清空所有生词与已会记录吗？此操作无法撤销。')) {
      clearVocabulary();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      <div className="flex max-h-[85vh] w-full max-w-xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--border-color)] p-4">
          <div className="flex items-center gap-2">
            <BookMarked className="h-4 w-4 text-[var(--highlight-border)]" />
            <h2 className="text-sm font-semibold text-[var(--text-strong)]">词汇库管理</h2>
            <span className="ml-1 font-mono text-[11px] text-[var(--text-muted)]">
              {learningCount} 生词 / {knownCount} 已会
            </span>
          </div>
          <button
            type="button"
            onClick={() => setVocabularyOpen(false)}
            className="flex h-7 w-7 items-center justify-center text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-hover)] hover:text-[var(--text-strong)]"
            aria-label="关闭"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Filters */}
        <div className="space-y-3 border-b border-[var(--border-color)] bg-[var(--bg-subtle)] p-4">
          <div className="relative">
            <Search className="absolute top-1/2 left-3 h-3.5 w-3.5 -translate-y-1/2 text-[var(--text-muted)]" />
            <input
              type="text"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="搜索收录的单词"
              className={`${FIELD} pl-9`}
            />
          </div>

          <div className="flex items-center justify-between gap-2">
            <div className="flex border border-[var(--border-color)] bg-[var(--bg-main)]">
              {(
                [
                  { id: 'learning', label: `生词 ${learningCount}` },
                  { id: 'known', label: `已会 ${knownCount}` },
                  { id: 'all', label: `全部 ${wordEntries.length}` },
                ] as const
              ).map((filter) => (
                <button
                  key={filter.id}
                  type="button"
                  onClick={() => setFilterType(filter.id)}
                  className={`px-2.5 py-1 text-[11px] font-semibold transition-colors ${
                    filterType === filter.id
                      ? 'bg-[var(--text-strong)] text-[var(--bg-surface)]'
                      : 'text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-strong)]'
                  }`}
                >
                  {filter.label}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={handleExportJSON}
                className={SMALL_BUTTON}
                title="导出词汇 JSON 备份"
              >
                <Download className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">导出</span>
              </button>
              <button
                type="button"
                onClick={handleClear}
                className="flex h-7 items-center border border-[var(--border-color)] px-2 text-[var(--text-muted)] transition-colors hover:border-[var(--danger)] hover:text-[var(--danger)]"
                title="清空词库"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        </div>

        {/* Word list */}
        <div className="flex-1 overflow-y-auto p-4">
          {filteredEntries.length === 0 && (
            <div className="py-12 text-center">
              <p className="text-sm text-[var(--text-muted)]">暂无符合条件的单词</p>
              <p className="mt-1.5 text-[11px] text-[var(--text-muted)]">
                阅读时点击任意词，即会作为生词收录到这里。
              </p>
            </div>
          )}

          <div className="space-y-1.5">
            {filteredEntries.map(({ word, status }) => (
              <div
                key={word}
                className="flex items-center justify-between gap-3 border border-[var(--border-color)] px-3 py-2 transition-colors hover:bg-[var(--bg-hover)]"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <button
                    type="button"
                    onClick={() => speakWord(word)}
                    className="flex h-7 w-7 shrink-0 items-center justify-center border border-[var(--border-color)] text-[var(--text-main)] transition-colors hover:bg-[var(--bg-surface)]"
                    title="朗读"
                    aria-label={`朗读 ${word}`}
                  >
                    <Volume2 className="h-3.5 w-3.5" />
                  </button>
                  <span className="truncate font-serif text-sm text-[var(--text-strong)]">{word}</span>
                  <span
                    className={`shrink-0 border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-[0.12em] ${
                      status === 'learning'
                        ? 'border-[var(--highlight-border)] bg-[var(--highlight-bg)] text-[var(--highlight-text)]'
                        : 'border-[var(--success)] text-[var(--success)]'
                    }`}
                  >
                    {status === 'learning' ? '生词' : '已会'}
                  </span>
                </div>

                <div className="flex shrink-0 items-center gap-1">
                  {status === 'learning' ? (
                    <button
                      type="button"
                      onClick={() => markKnown(word)}
                      className="flex h-7 items-center gap-1 border border-[var(--border-color)] px-2 text-[11px] font-semibold text-[var(--text-main)] transition-colors hover:border-[var(--success)] hover:text-[var(--success)]"
                      title="标记为已会"
                    >
                      <Check className="h-3.5 w-3.5" />
                      <span className="hidden sm:inline">设为已会</span>
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => markLearning(word)}
                      className="flex h-7 items-center gap-1 border border-[var(--border-color)] px-2 text-[11px] font-semibold text-[var(--text-main)] transition-colors hover:border-[var(--highlight-border)] hover:text-[var(--highlight-border)]"
                      title="重新标为生词"
                    >
                      <RotateCcw className="h-3.5 w-3.5" />
                      <span className="hidden sm:inline">标为生词</span>
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => removeWord(word)}
                    className="flex h-7 w-7 items-center justify-center border border-[var(--border-color)] text-[var(--text-muted)] transition-colors hover:border-[var(--danger)] hover:text-[var(--danger)]"
                    title="移出词汇库"
                    aria-label={`移出 ${word}`}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};


