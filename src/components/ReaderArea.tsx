import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useReaderStore } from '../store/useReaderStore';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { tokenizeText, type Token } from '../utils/tokenizer';
import { bionicSplit, isBionicEligible } from '../utils/bionic';
import type { WordStatus } from '../types/reader';
import {
  WORD_LEVEL_BG,
  WORD_LEVEL_LABELS,
  WORD_UNFILED,
  highlightLevel,
  isLevel,
  isUnknown,
} from '../utils/wordLevel';

interface ReaderAreaProps {
  onSelectWord: (word: string, surroundingSentence: string) => void;
  activeWord: string | null;
}

/**
 * Word styling.
 *
 * Three outcomes, three looks:
 *   - a collected word is painted with its proficiency colour (1 lightest → 5 deepest);
 *   - a word never collected gets a neutral grey dashed underline and NO fill
 *     (WORD_UNFILED) — the reader should see at a glance what is still unprocessed
 *     (see highlightLevel), without an unprocessed word looking like one they
 *     deliberately filed as 生词;
 *   - a mastered word renders as plain text, so the page calms down as the reader
 *     works through it.
 *
 * The background rules below are mutually exclusive on purpose: each word gets
 * exactly one of the level tint or WORD_IDLE, and the active marker only adds a
 * background when there is no level colour to compete with. Tailwind resolves
 * two background-color utilities by stylesheet order, not by intent, so overlap
 * here is a bug rather than a precedence the code can rely on. The dashed
 * underline is a border, not a background, so it may sit alongside WORD_IDLE.
 */
const WORD_BASE = `cursor-pointer transition-colors duration-100 rounded-[var(--word-radius)]`;
const WORD_IDLE = 'hover:bg-[var(--bg-hover)]';

/**
 * The word open in the definition panel, so it is easy to find again after the
 * panel scrolls the text out of view: always an accent outline, plus a tint only
 * where it does not have to compete with a proficiency colour.
 */
const WORD_ACTIVE_OUTLINE = 'outline-2 outline-offset-[3px] outline-[var(--accent)]';
const WORD_ACTIVE = `${WORD_ACTIVE_OUTLINE} bg-[var(--accent-soft)]`;

export const ReaderArea: React.FC<ReaderAreaProps> = ({ onSelectWord, activeWord }) => {
  const {
    currentBook,
    currentChapterIndex,
    currentPageIndex,
    pages,
    fontSize,
    lineHeight,
    bionicEnabled,
    readingRulerEnabled,
  } = useReaderStore();

  const wordsState = useVocabularyStore((state) => state.words);

  const currentPageText = pages[currentPageIndex] || '';

  // Paragraphs of the current page.
  const paragraphs = useMemo(() => {
    if (!currentPageText.trim()) return [];
    return currentPageText
      .replace(/\r\n/g, '\n')
      .split(/\n\s*\n/)
      .filter((paragraph) => paragraph.trim().length > 0);
  }, [currentPageText]);

  const tokenizedParagraphs = useMemo(() => {
    return paragraphs.map((paragraph, index) => tokenizeText(paragraph, index));
  }, [paragraphs]);

  /** Sentence (punctuation delimited) that contains the clicked token. */
  const getSentenceForToken = (paraTokens: Token[], targetIdx: number): string => {
    let startIdx = 0;
    let endIdx = paraTokens.length - 1;

    for (let i = targetIdx; i >= 0; i--) {
      if (/[.!?]/.test(paraTokens[i].raw) && i !== targetIdx) {
        startIdx = i + 1;
        break;
      }
    }

    for (let i = targetIdx; i < paraTokens.length; i++) {
      if (/[.!?]/.test(paraTokens[i].raw)) {
        endIdx = i;
        break;
      }
    }

    return paraTokens
      .slice(startIdx, endIdx + 1)
      .map((token) => token.raw)
      .join('')
      .trim();
  };

  /**
   * 阅读标尺：指针所在段落保持清晰，其余变暗。
   *
   * 段落索引直接由事件目标向上找最近的 <p> 得到 —— 比拿坐标去算几何关系简单，
   * 也不会因为字号/行高改变而失准。
   */
  const [focusedParagraph, setFocusedParagraph] = useState<number | null>(null);
  const pointerRef = useRef<{ x: number; y: number } | null>(null);

  const paragraphIndexAt = useCallback((target: EventTarget | null): number | null => {
    const element = target instanceof Element ? target.closest('p') : null;
    if (!element) return null;
    const index = Number(element.getAttribute('data-paragraph-index'));
    return Number.isInteger(index) ? index : null;
  }, []);

  const handlePointerMove = useCallback(
    (event: React.MouseEvent) => {
      if (!readingRulerEnabled) return;
      pointerRef.current = { x: event.clientX, y: event.clientY };
      setFocusedParagraph(paragraphIndexAt(event.target));
    },
    [readingRulerEnabled, paragraphIndexAt],
  );

  /**
   * 滚动时必须用光标坐标重新命中一次。
   *
   * LingKuma 的标尺只监听 mousemove、不监听滚动（其 readingRuler.js），
   * 于是光标不动、页面滚动时那一条会留在原地。这里补上。
   */
  useEffect(() => {
    if (!readingRulerEnabled) return;

    const handleScroll = () => {
      const pointer = pointerRef.current;
      if (!pointer) return;
      setFocusedParagraph(paragraphIndexAt(document.elementFromPoint(pointer.x, pointer.y)));
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [readingRulerEnabled, paragraphIndexAt]);

  // 关掉标尺时不必清空 focusedParagraph —— 这里把它推导成 null 即可，
  // 于是不需要一个「设置项变了就同步改状态」的 effect。翻页由 App 上的 key
  // 重挂整个组件来复位。
  const dimmedParagraph = readingRulerEnabled ? focusedParagraph : null;

  if (!currentBook) {
    return (
      <main className="flex flex-1 items-center justify-center p-8 text-center">
        <div className="max-w-sm">
          <p className="text-[16px] font-semibold text-[var(--text-strong)]">暂无打开的读物</p>
          <p className="mt-2 text-[14px] leading-relaxed text-[var(--text-muted)]">
            点击顶部「读物库」选择一本经典英文名著，或粘贴任意英文文章开始阅读。
          </p>
        </div>
      </main>
    );
  }

  const currentChapter = currentBook.chapters[currentChapterIndex];

  return (
    <main className="w-full flex-1" onMouseMove={handlePointerMove}>
      <div className="mx-auto w-full max-w-[46rem] px-5 pt-10 pb-14 sm:px-6">
        {/* Chapter heading, shown on the first page of a chapter */}
        {currentPageIndex === 0 && (
          <header className="mb-9 border-b border-[var(--border-color)] pb-5">
            <p className="text-[12px] font-semibold uppercase tracking-[0.18em] text-[var(--text-muted)]">
              {currentBook.title}
            </p>
            <h2 className="mt-2 font-serif text-3xl leading-snug font-bold text-[var(--text-strong)]">
              {currentChapter?.title || 'Chapter'}
            </h2>
          </header>
        )}

        <div
          className="space-y-7 font-serif"
          style={{ fontSize: `${fontSize}px`, lineHeight }}
        >
          {tokenizedParagraphs.map((paraTokens, paraIdx) => (
            <p
              key={paraIdx}
              data-paragraph-index={paraIdx}
              className={`text-pretty transition-opacity duration-150 ${
                dimmedParagraph !== null && dimmedParagraph !== paraIdx ? 'opacity-25' : ''
              }`}
            >
              {paraTokens.map((token, tokenIdx) => {
                if (!token.isWord) {
                  return <span key={token.id}>{token.raw}</span>;
                }

                const status: WordStatus | 'unknown' =
                  wordsState[token.cleanWord] ?? 'unknown';
                // Three different questions, deliberately kept apart: `level` is
                // what the word actually IS (only the tooltip may say it), `tint`
                // is the fill it gets, and `unfiled` is whether it was ever
                // collected. Two of the three states fill nothing — `tint` alone
                // cannot tell an uncollected word from a mastered one.
                const level = isLevel(status) ? status : null;
                const tint = highlightLevel(status);
                // `isUnknown` rather than `status === 'unknown'`: the index
                // signature says every key exists, so TS narrows `status` to
                // WordStatus and rejects the comparison as impossible (TS2367).
                const unfiled = isUnknown(status);
                const isActive = activeWord?.toLowerCase() === token.cleanWord;

                let className = `${WORD_BASE} `;
                if (tint) {
                  className += `${WORD_LEVEL_BG[tint]} `;
                } else if (unfiled) {
                  className += `${WORD_UNFILED} ${WORD_IDLE} `;
                } else {
                  className += `${WORD_IDLE} `;
                }
                if (isActive) {
                  // Only a mastered word has no mark of its own, so only it takes
                  // the accent-soft fill. An uncollected word must NOT: filling it
                  // would make the one word the reader is looking at look like a
                  // filed one, exactly when they need to see it is not.
                  className += tint || unfiled ? `${WORD_ACTIVE_OUTLINE} ` : `${WORD_ACTIVE} `;
                }

                const tooltip = unfiled
                  ? `未收录：${token.cleanWord}（点击查词）`
                  : level
                    ? `熟练度 ${level} · ${WORD_LEVEL_LABELS[level]}：${token.cleanWord}（点击查看释义）`
                    : `已掌握：${token.cleanWord}（点击查看释义）`;

                return (
                  <span
                    key={token.id}
                    onClick={() => onSelectWord(token.cleanWord, getSentenceForToken(paraTokens, tokenIdx))}
                    className={className}
                    title={tooltip}
                  >
                    {bionicEnabled && isBionicEligible(token.raw) ? (
                      <>
                        <strong className="font-bold">{bionicSplit(token.raw).bold}</strong>
                        {bionicSplit(token.raw).rest}
                      </>
                    ) : (
                      token.raw
                    )}
                  </span>
                );
              })}
            </p>
          ))}

          {tokenizedParagraphs.length === 0 && (
            <p className="py-12 text-center text-[15px] text-[var(--text-muted)]">当前页面内容为空</p>
          )}
        </div>
      </div>
    </main>
  );
};
