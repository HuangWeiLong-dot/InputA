import React, { useMemo, useState } from 'react';
import { X, Search, Volume2, Check, Trash2, BookMarked, Download, RotateCcw } from 'lucide-react';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { useReaderStore } from '../store/useReaderStore';
import { speakWord } from '../services/speechService';
import { WORD_LEVEL_BG, WORD_LEVEL_LABELS, isLevel, levelLabel } from '../utils/wordLevel';
import {
  BADGE,
  BTN_GHOST,
  BTN_SM,
  BTN_SM_DANGER_ICON,
  BTN_SM_ICON,
  FIELD,
  SEGMENT,
  SEGMENT_BOX,
  SEGMENT_OFF,
  SEGMENT_ON,
} from './ui';

/** 未掌握 = 仍带熟练度色阶（1-5 级）的词。 */
type FilterType = 'all' | 'active' | 'mastered';

export const VocabularyModal: React.FC = () => {
  const isVocabularyOpen = useReaderStore((state) => state.isVocabularyOpen);
  const setVocabularyOpen = useReaderStore((state) => state.setVocabularyOpen);
  const { words, setWordLevel, markMastered, removeWord, clearVocabulary } = useVocabularyStore();

  const [filterType, setFilterType] = useState<FilterType>('active');
  const [searchTerm, setSearchTerm] = useState('');

  const wordEntries = useMemo(
    () => Object.entries(words).map(([word, status]) => ({ word, status })),
    [words],
  );

  const filteredEntries = useMemo(() => {
    const term = searchTerm.toLowerCase().trim();
    return wordEntries.filter((item) => {
      if (filterType === 'active' && !isLevel(item.status)) return false;
      if (filterType === 'mastered' && item.status !== 'mastered') return false;
      if (term && !item.word.includes(term)) return false;
      return true;
    });
  }, [wordEntries, filterType, searchTerm]);

  const activeCount = wordEntries.filter((entry) => isLevel(entry.status)).length;
  const masteredCount = wordEntries.filter((entry) => entry.status === 'mastered').length;

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
    if (window.confirm('确定要清空整本词汇库吗？所有熟练度与掌握记录都会丢失，此操作无法撤销。')) {
      clearVocabulary();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      <div className="flex max-h-[88vh] w-full max-w-2xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--border-color)] px-5 py-4">
          <div className="flex min-w-0 items-center gap-2.5">
            <BookMarked className="h-5 w-5 shrink-0 text-[var(--highlight-border)]" />
            <h2 className="truncate text-[16px] font-semibold text-[var(--text-strong)]">
              词汇库管理
            </h2>
            {/* The count keeps its own width and yields the space to the title. */}
            <span className="ml-1 shrink-0 font-mono text-[13px] whitespace-nowrap text-[var(--text-muted)]">
              {activeCount} 未掌握 / {masteredCount} 已掌握
            </span>
          </div>
          <button
            type="button"
            onClick={() => setVocabularyOpen(false)}
            className={BTN_GHOST}
            aria-label="关闭"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Filters */}
        <div className="space-y-3 border-b border-[var(--border-color)] bg-[var(--bg-subtle)] px-5 py-4">
          <div className="relative">
            <Search className="absolute top-1/2 left-3.5 h-4 w-4 -translate-y-1/2 text-[var(--text-muted)]" />
            <input
              type="text"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="搜索收录的单词"
              className={`${FIELD} pl-10`}
            />
          </div>

          {/*
            flex-wrap + ml-auto: on a narrow panel the filters keep their own
            line and the export/clear pair drops below them, rather than the
            three segments being squeezed until their labels wrap and push the
            row out of the panel.
          */}
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className={`${SEGMENT_BOX} bg-[var(--bg-main)]`}>
              {(
                [
                  { id: 'active', label: `未掌握 ${activeCount}` },
                  { id: 'mastered', label: `已掌握 ${masteredCount}` },
                  { id: 'all', label: `全部 ${wordEntries.length}` },
                ] as const
              ).map((filter) => (
                <button
                  key={filter.id}
                  type="button"
                  onClick={() => setFilterType(filter.id)}
                  className={`${SEGMENT} h-10 px-3.5 ${filterType === filter.id ? SEGMENT_ON : SEGMENT_OFF}`}
                >
                  {filter.label}
                </button>
              ))}
            </div>

            <div className="ml-auto flex shrink-0 items-center gap-2">
              <button
                type="button"
                onClick={handleExportJSON}
                className={BTN_SM}
                title="导出词汇 JSON 备份"
              >
                <Download className="h-4 w-4" />
                <span className="hidden sm:inline">导出</span>
              </button>
              <button
                type="button"
                onClick={handleClear}
                className={BTN_SM_DANGER_ICON}
                title="清空词库"
                aria-label="清空词库"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        {/* Word list */}
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {filteredEntries.length === 0 && (
            <div className="py-12 text-center">
              <p className="text-[15px] text-[var(--text-muted)]">暂无符合条件的单词</p>
              <p className="mt-2 text-[13px] text-[var(--text-muted)]">
                阅读时点击任意词，即会记为「生词」收录到这里；翻页时未点击的词会自动记为「已掌握」。
              </p>
            </div>
          )}

          <div className="space-y-2">
            {filteredEntries.map(({ word, status }) => (
              <div
                key={word}
                className="flex items-center justify-between gap-3 border border-[var(--border-color)] px-3.5 py-2.5 transition-colors hover:bg-[var(--bg-hover)]"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <button
                    type="button"
                    onClick={() => speakWord(word)}
                    className={BTN_SM_ICON}
                    title="朗读"
                    aria-label={`朗读 ${word}`}
                  >
                    <Volume2 className="h-4 w-4" />
                  </button>
                  <span className="truncate font-serif text-[17px] text-[var(--text-strong)]">
                    {word}
                  </span>
                  {/* The proficiency chip: the same colour the word carries in
                      the text, so a word can be recognised across both views. */}
                  <span
                    className={`${BADGE} border-[var(--border-color)] ${
                      isLevel(status)
                        ? `${WORD_LEVEL_BG[status]} text-[var(--text-main)]`
                        : 'text-[var(--success)]'
                    }`}
                    title={
                      isLevel(status)
                        ? `熟练度 ${levelLabel(status)}：${status === 5 ? '最不熟' : status === 1 ? '最熟' : '中等'}`
                        : '已掌握'
                    }
                  >
                    {isLevel(status) ? WORD_LEVEL_LABELS[status] : '已掌握'}
                  </span>
                </div>

                <div className="flex shrink-0 items-center gap-2">
                  {status === 'mastered' ? (
                    <button
                      type="button"
                      onClick={() => setWordLevel(word, 5)}
                      className={`${BTN_SM} hover:border-[var(--highlight-border)] hover:text-[var(--highlight-border)]`}
                      title="重新记为 5 级「生词」"
                    >
                      <RotateCcw className="h-4 w-4" />
                      <span className="hidden sm:inline">标为生词</span>
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => markMastered(word)}
                      className={`${BTN_SM} hover:border-[var(--success)] hover:text-[var(--success)]`}
                      title="标记为已掌握"
                    >
                      <Check className="h-4 w-4" />
                      <span className="hidden sm:inline">已掌握</span>
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => removeWord(word)}
                    className={BTN_SM_DANGER_ICON}
                    title="移出词汇库"
                    aria-label={`移出 ${word}`}
                  >
                    <Trash2 className="h-4 w-4" />
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


