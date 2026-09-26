import React, { useMemo, useRef, useState } from 'react';
import {
  X,
  Search,
  Volume2,
  Check,
  Trash2,
  BookMarked,
  Download,
  Upload,
  RotateCcw,
} from 'lucide-react';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { useAnnotationStore } from '../store/useAnnotationStore';
import { useReaderStore } from '../store/useReaderStore';
import { speakWord } from '../services/ttsService';
import { WORD_LEVEL_BG, WORD_LEVEL_LABELS, isLevel, levelLabel } from '../utils/wordLevel';
import { buildBackup, isEmptyBackup, parseBackup } from '../utils/backup';
import type { BackupData } from '../utils/backup';
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
  const { words, setWordLevel, markMastered, removeWord, clearVocabulary, importVocabulary } =
    useVocabularyStore();
  const { notes, sentences, importAnnotations, clearAnnotations, removeWordAnnotations } =
    useAnnotationStore();

  const fileInputRef = useRef<HTMLInputElement>(null);

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
    const payload = buildBackup({ words, notes, sentences }, new Date().toISOString());
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(payload, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', `vocabulary_${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  /**
   * 导入是**替换**而不是合并（两个 store 的 import 动作都是替换语义），
   * 所以先说清楚，别让用户以为是在追加。
   */
  const handleImportFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // 先清空 value，否则连续选同一个文件不会再次触发 change。
    event.target.value = '';
    if (!file) return;

    let parsed: BackupData | null;
    try {
      parsed = parseBackup(JSON.parse(await file.text()));
    } catch {
      window.alert('这个文件不是有效的 JSON，无法导入。');
      return;
    }

    if (!parsed) {
      window.alert('这个文件的结构不是本应用导出的词汇备份。');
      return;
    }

    // 空备份等于「清空词库」。真到那一步用户会用「清空词库」按钮，
    // 所以这里更可能是选错了文件 —— 拒绝比默默抹掉数据安全。
    if (isEmptyBackup(parsed)) {
      window.alert('这个备份里没有任何数据，已取消导入（避免误清空现有词汇库）。');
      return;
    }

    const wordCount = Object.keys(parsed.words).length;
    const noteCount = Object.keys(parsed.notes).length;
    const sentenceCount = Object.keys(parsed.sentences).length;
    const confirmed = window.confirm(
      `导入会**替换**当前的词汇库，不是在现有数据上追加。\n\n` +
        `文件内容：${wordCount} 个单词、${noteCount} 条词笔记、${sentenceCount} 条例句。\n\n` +
        `继续导入吗？`,
    );
    if (!confirmed) return;

    importVocabulary(parsed.words);
    importAnnotations({ notes: parsed.notes, sentences: parsed.sentences });
  };

  const handleClear = () => {
    if (
      window.confirm(
        '确定要清空整本词汇库吗？所有熟练度、笔记与例句都会丢失，此操作无法撤销。',
      )
    ) {
      clearVocabulary();
      clearAnnotations();
    }
  };

  /** 移出词汇库时连笔记和例句一起清掉，否则会留下永远查不到的孤儿数据。 */
  const handleRemoveWord = (word: string) => {
    removeWord(word);
    removeWordAnnotations(word);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      {/* dvh 而不是 vh：手机上 vh 不随键盘收缩，面板底部会被键盘顶出可视区。 */}
      <div className="flex max-h-[88dvh] w-full max-w-2xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
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
                title="导出词汇 JSON 备份（含笔记与例句）"
              >
                <Download className="h-4 w-4" />
                <span className="hidden sm:inline">导出</span>
              </button>
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className={BTN_SM}
                title="从 JSON 备份导入（会替换当前词汇库）"
              >
                <Upload className="h-4 w-4" />
                <span className="hidden sm:inline">导入</span>
              </button>
              {/* Hidden: the button above is the visible trigger. Keeping the
                  real input in the DOM is what makes a file picker openable
                  from a click handler. */}
              <input
                ref={fileInputRef}
                type="file"
                accept=".json,application/json"
                onChange={handleImportFile}
                className="hidden"
                aria-hidden="true"
                tabIndex={-1}
              />
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
                    onClick={() => handleRemoveWord(word)}
                    className={BTN_SM_DANGER_ICON}
                    title="移出词汇库（连同该词的笔记与例句）"
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


