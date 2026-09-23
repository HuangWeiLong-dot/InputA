import React, { useMemo } from 'react';
import { useReaderStore } from '../store/useReaderStore';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { tokenizeText, type Token } from '../utils/tokenizer';
import type { WordStatus } from '../types/reader';
import { WORD_LEVEL_BG, WORD_LEVEL_LABELS, isLevel } from '../utils/wordLevel';

interface ReaderAreaProps {
  onSelectWord: (word: string, surroundingSentence: string) => void;
  activeWord: string | null;
}

/**
 * Word styling.
 *
 * A collected word is painted with its proficiency colour (1 lightest → 5
 * deepest); a mastered word and a word never collected both render as plain
 * text, so the page calms down as the reader learns.
 *
 * The background rules below are mutually exclusive on purpose: each word gets
 * exactly one of the level tint or WORD_IDLE, and the active marker only adds a
 * background when there is no level colour to compete with. Tailwind resolves
 * two background-color utilities by stylesheet order, not by intent, so overlap
 * here is a bug rather than a precedence the code can rely on.
 */
const WORD_BASE = 'cursor-pointer transition-colors duration-100';
const WORD_IDLE = 'hover:bg-[var(--bg-hover)]';

/**
 * The word open in the definition panel, so it is easy to find again after the
 * panel scrolls the text out of view: always an accent outline, plus a tint only
 * where it does not have to compete with a proficiency colour.
 */
const WORD_ACTIVE_OUTLINE = 'outline-2 outline-offset-[3px] outline-[var(--accent)]';
const WORD_ACTIVE = `${WORD_ACTIVE_OUTLINE} bg-[var(--accent-soft)]`;

export const ReaderArea: React.FC<ReaderAreaProps> = ({ onSelectWord, activeWord }) => {
  const { currentBook, currentChapterIndex, currentPageIndex, pages, fontSize, lineHeight } =
    useReaderStore();

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
    <main className="w-full flex-1">
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
            <p key={paraIdx} className="text-pretty">
              {paraTokens.map((token, tokenIdx) => {
                if (!token.isWord) {
                  return <span key={token.id}>{token.raw}</span>;
                }

                const status: WordStatus | 'unknown' =
                  wordsState[token.cleanWord] ?? 'unknown';
                const level = isLevel(status) ? status : null;
                const isActive = activeWord?.toLowerCase() === token.cleanWord;

                let className = `${WORD_BASE} `;
                className += level ? `${WORD_LEVEL_BG[level]} ` : `${WORD_IDLE} `;
                if (isActive) {
                  className += level ? `${WORD_ACTIVE_OUTLINE} ` : `${WORD_ACTIVE} `;
                }

                const tooltip = level
                  ? `熟练度 ${level} · ${WORD_LEVEL_LABELS[level]}：${token.cleanWord}（点击查看释义）`
                  : status === 'mastered'
                    ? `已掌握：${token.cleanWord}（点击查看释义）`
                    : `点击查词：${token.cleanWord}`;

                return (
                  <span
                    key={token.id}
                    onClick={() => onSelectWord(token.cleanWord, getSentenceForToken(paraTokens, tokenIdx))}
                    className={className}
                    title={tooltip}
                  >
                    {token.raw}
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
