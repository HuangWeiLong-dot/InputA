import React, { useState } from 'react';
import { BookmarkPlus, Check, Loader2, Trash2, Volume2 } from 'lucide-react';
import { useAnnotationStore } from '../store/useAnnotationStore';
import { useReaderStore } from '../store/useReaderStore';
import { askOnce } from '../services/aiService';
import { sentenceTranslationPrompt } from '../services/aiPrompts';
import { speakWord } from '../services/ttsService';
import type { SavedSentence } from '../types/reader';
import { RichText } from './RichText';
import { BTN_SM_ICON, SECTION_LABEL } from './ui';

interface ExampleSentencesSectionProps {
  word: string;
  /** 当前所在句子；没有就只剩已保存的例句列表。 */
  sentence?: string;
}

/**
 * 这个词的例句记录。
 *
 * 与 LingKuma 的一处有意分歧：它是**自动**保存例句的（受 autoAddExampleSentences
 * 开关控制，见其 a4_tooltip_new.js），因此没有「保存」按钮，只有每条的删除。
 * 这里改成显式按钮 —— 每次点词都往库里塞例句，很快词汇库就全是被动收集的噪声。
 */
export const ExampleSentencesSection: React.FC<ExampleSentencesSectionProps> = ({
  word,
  sentence,
}) => {
  const { sentences: sentencesMap, addSentence, removeSentence } = useAnnotationStore();
  const { currentBook, currentChapterIndex, currentPageIndex } = useReaderStore();
  const saved = sentencesMap[word.toLowerCase()] ?? [];

  const [isTranslating, setIsTranslating] = useState(false);

  const currentSentence = sentence?.trim() ?? '';
  const alreadySaved =
    currentSentence.length > 0 && saved.some((item) => item.sentence === currentSentence);

  const handleSave = async () => {
    if (!currentSentence || alreadySaved) return;

    const record: SavedSentence = {
      sentence: currentSentence,
      bookId: currentBook?.id,
      bookTitle: currentBook?.title,
      chapterIndex: currentChapterIndex,
      pageIndex: currentPageIndex,
      createdAt: Date.now(),
    };

    // 先落库再取译文（LingKuma 的乐观写入思路）：网络慢或失败时，例句本身
    // 已经存住了。译文晚到没关系 —— addSentence 会把译文并进已有那条。
    addSentence(word, record);

    setIsTranslating(true);
    const result = await askOnce(sentenceTranslationPrompt(word, currentSentence));
    if (result.ok) {
      addSentence(word, { ...record, translation: result.content });
    }
    setIsTranslating(false);
  };

  if (saved.length === 0 && !currentSentence) return null;

  return (
    <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] px-4 py-3.5">
      <div className="flex items-center justify-between gap-2">
        <span className={SECTION_LABEL}>例句记录</span>
        {currentSentence && (
          <button
            type="button"
            onClick={handleSave}
            disabled={alreadySaved || isTranslating}
            className="flex items-center gap-1.5 text-[13px] font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline disabled:text-[var(--text-muted)] disabled:no-underline"
            title={
              alreadySaved ? '这一句已经存过了' : '保存当前句子，并尝试取 AI 译文'
            }
          >
            {isTranslating ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : alreadySaved ? (
              <Check className="h-3.5 w-3.5" />
            ) : (
              <BookmarkPlus className="h-3.5 w-3.5" />
            )}
            {alreadySaved ? '已保存' : '保存本句'}
          </button>
        )}
      </div>

      {saved.length === 0 ? (
        <p className="mt-2.5 text-[13px] leading-relaxed text-[var(--text-muted)]">
          还没有例句。保存当前句子，之后复习时就能看到它在原文里的样子。
        </p>
      ) : (
        <ul className="mt-2.5 space-y-2">
          {saved.map((item) => (
            <li
              key={item.sentence}
              className="border border-[var(--border-color)] bg-[var(--bg-surface)] px-3 py-2"
            >
              <div className="flex items-start justify-between gap-2.5">
                <p className="min-w-0 font-serif text-[14px] leading-relaxed text-[var(--text-main)] italic">
                  “<RichText text={item.sentence} highlight={word} />”
                </p>
                <div className="flex shrink-0 items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => speakWord(item.sentence)}
                    className={BTN_SM_ICON}
                    title="朗读这句"
                    aria-label="朗读这句"
                  >
                    <Volume2 className="h-3.5 w-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => removeSentence(word, item.sentence)}
                    className={BTN_SM_ICON}
                    title="删除这条例句"
                    aria-label="删除这条例句"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>

              {item.translation && (
                <p className="mt-1.5 text-[13px] leading-relaxed text-[var(--text-muted)]">
                  <RichText text={item.translation} />
                </p>
              )}

              {item.bookTitle && (
                <p className="mt-1 font-mono text-[11px] text-[var(--border-strong)]">
                  {item.bookTitle}
                  {item.chapterIndex !== undefined ? ` · 第 ${item.chapterIndex + 1} 章` : ''}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      {saved.length > 0 && (
        <p className="mt-2 text-[12px] text-[var(--text-muted)]">共 {saved.length} 条</p>
      )}
    </div>
  );
};
