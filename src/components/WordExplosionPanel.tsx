import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Check, Languages, Loader2, Sparkles, Volume2, X } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { lookupWord } from '../services/dictionaryApi';
import { askOnce } from '../services/aiService';
import { sentenceTranslationPrompt } from '../services/aiPrompts';
import { speakWord } from '../services/ttsService';
import { extractWords } from '../utils/wordExtraction';
import { tokenizeText } from '../utils/tokenizer';
import { RichText } from './RichText';
import { BTN_ACCENT_SOFT, BTN_GHOST, BTN_ON_HIGHLIGHT, BTN_SM, SECTION_LABEL } from './ui';

interface WordExplosionPanelProps {
  /** 点词后打开释义面板（面板里再点某个词时用）。 */
  onSelectWord: (word: string, sentence: string) => void;
}

interface WordLookup {
  /** 优先中文释义，没有就用英文释义的第一条。 */
  summary: string;
}

/** 取这条词条里最值得显示的一行释义。 */
function summarize(translation?: string, english?: string): string {
  const chinese = translation
    ?.split('\n')
    .map((line) => line.trim())
    .find(Boolean);
  return chinese || english || '';
}

/**
 * 单词爆炸：把一句话里所有还没收录的词一次列出来。
 *
 * 触发入口是释义面板里的「本句生词」按钮。LingKuma 那边是「点句子里任意位置」
 * 触发（其 a7_words_boom.js），但 InputA 的单击已经被「打开释义抽屉」占用了，
 * 加一个显式按钮比抢手势清楚。
 *
 * 一处与 LingKuma 的有意分歧：**打开面板不改动任何词的状态**。LingKuma 会把列出
 * 的词乐观地置为「学习中」（它那边的状态 1），那在 InputA 里等于把整句词都标成
 * 5 级生词、正文立刻变色 —— 只是看一眼不该改动词库。这里只有用户明确点「已掌握」
 * 或点某个词去查释义时才写状态。
 */
export const WordExplosionPanel: React.FC<WordExplosionPanelProps> = ({ onSelectWord }) => {
  const { isWordExplosionOpen, explosionSentence, setWordExplosionOpen } = useReaderStore();
  const words = useVocabularyStore((state) => state.words);
  const markMastered = useVocabularyStore((state) => state.markMastered);

  // 只存已经拿到的结果：`lookups[word] === undefined` 就代表还在查。
  // 把「加载中」做成推导值而不是一份同步写入的状态，避免在 effect 里立刻 setState。
  const [lookups, setLookups] = useState<Record<string, WordLookup>>({});
  const requestedRef = useRef<Set<string>>(new Set());
  const [translation, setTranslation] = useState<string | null>(null);
  const [isTranslating, setIsTranslating] = useState(false);
  const [translationError, setTranslationError] = useState<string | null>(null);

  const sentence = explosionSentence;

  /**
   * 还没收录的词 = 面板要列的东西。
   *
   * 依赖 `words` 让「标记为已掌握」后该词立刻从列表里消失 —— 面板问的就是
   * 「这句里还有什么是我不会的」，标记完就不该再占位。
   */
  const unknownWords = useMemo(
    () => extractWords(sentence).filter((entry) => words[entry.clean] === undefined),
    [sentence, words],
  );

  /** 同样一组词，但用于在原句里按词元打标。 */
  const unknownSet = useMemo(
    () => new Set(unknownWords.map((entry) => entry.clean)),
    [unknownWords],
  );

  // 逐词取释义。词典服务自带模块级缓存，所以重复的词或重新打开都不会再发请求。
  // 用 ref 记已发出的词，这样 effect 不必依赖 lookups（否则每次结果落地都会重跑）。
  useEffect(() => {
    if (!isWordExplosionOpen) return;

    for (const entry of unknownWords) {
      if (requestedRef.current.has(entry.clean)) continue;
      requestedRef.current.add(entry.clean);

      void lookupWord(entry.clean)
        .then((result) => {
          const english = result.entry?.meanings[0]?.definitions[0]?.definition;
          const summary = summarize(result.entry?.extra?.translation, english);
          setLookups((prev) => ({ ...prev, [entry.clean]: { summary } }));
        })
        .catch(() => {
          // lookupWord 本身不抛，这里只是兜底，免得 spinner 永远转下去。
          setLookups((prev) => ({ ...prev, [entry.clean]: { summary: '' } }));
        });
    }
  }, [isWordExplosionOpen, unknownWords]);

  if (!isWordExplosionOpen) return null;

  const handleTranslate = async () => {
    setIsTranslating(true);
    setTranslationError(null);

    // 目标词取句子里第一个生词；没有生词就取整句的第一个词。
    const anchor = unknownWords[0]?.clean ?? extractWords(sentence)[0]?.clean ?? '';
    const result = await askOnce(sentenceTranslationPrompt(anchor, sentence));

    if (result.ok) {
      setTranslation(result.content);
    } else {
      setTranslationError(`${result.message}\n\n${result.hint}`);
    }
    setIsTranslating(false);
  };

  const handleMarkAll = () => {
    for (const entry of unknownWords) markMastered(entry.clean);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      <div className="flex max-h-[88vh] w-full max-w-2xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--border-color)] px-5 py-4">
          <div className="flex items-center gap-2.5">
            <Sparkles className="h-5 w-5 text-[var(--accent)]" />
            <h2 className="text-[16px] font-semibold text-[var(--text-strong)]">本句生词</h2>
            <span className="font-mono text-[13px] text-[var(--text-muted)]">
              {unknownWords.length} 个
            </span>
          </div>
          <button
            type="button"
            onClick={() => setWordExplosionOpen(false)}
            className={BTN_GHOST}
            aria-label="关闭"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          {/*
            The sentence in full, with every still-unknown word marked in place.
            Concatenating only the unknown words would be much harder to read
            than the sentence they came from.
          */}
          <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] px-4 py-3.5">
            <span className={SECTION_LABEL}>原句</span>
            <p className="mt-2 font-serif text-[16px] leading-relaxed text-[var(--text-main)]">
              {tokenizeText(sentence).map((token) =>
                token.isWord && unknownSet.has(token.cleanWord) ? (
                  <span key={token.id} className="bg-[var(--level-5-bg)] px-0.5">
                    {token.raw}
                  </span>
                ) : (
                  <React.Fragment key={token.id}>{token.raw}</React.Fragment>
                ),
              )}
            </p>
          </div>

          {unknownWords.length === 0 ? (
            <div className="py-10 text-center">
              <p className="text-[15px] text-[var(--text-main)]">这句里没有未收录的词了 🎉</p>
              <p className="mt-2 text-[13px] text-[var(--text-muted)]">
                点过的词会记为「生词」，其余在翻页时会自动记为「已掌握」。
              </p>
            </div>
          ) : (
            <>
              <button
                type="button"
                onClick={handleTranslate}
                disabled={isTranslating}
                className={`${BTN_ACCENT_SOFT} w-full`}
              >
                {isTranslating ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Languages className="h-4 w-4" />
                )}
                <span>{isTranslating ? '正在翻译整句…' : 'AI 翻译整句'}</span>
              </button>

              {translationError && (
                <p className="border border-[var(--highlight-border)] bg-[var(--highlight-bg)] px-4 py-3 text-[13px] leading-relaxed whitespace-pre-wrap text-[var(--highlight-text)]">
                  {translationError}
                </p>
              )}

              {translation && (
                <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] px-4 py-3">
                  <span className={SECTION_LABEL}>整句翻译</span>
                  <RichText
                    text={translation}
                    className="mt-1.5 block text-[15px] leading-relaxed text-[var(--text-main)]"
                  />
                </div>
              )}

              <ul className="space-y-2">
                {unknownWords.map((entry) => {
                  const lookup = lookups[entry.clean];
                  return (
                    <li
                      key={entry.clean}
                      className="flex items-start justify-between gap-3 border border-[var(--border-color)] px-3.5 py-2.5"
                    >
                      <div className="min-w-0">
                        <button
                          type="button"
                          onClick={() => onSelectWord(entry.clean, sentence)}
                          className="font-serif text-[17px] font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline"
                          title="查看完整释义"
                        >
                          {entry.raw}
                        </button>
                        {/* 没有结果 = 还在查（见 lookups 的注释）。 */}
                        {lookup === undefined ? (
                          <Loader2 className="mt-1 h-3.5 w-3.5 animate-spin text-[var(--text-muted)]" />
                        ) : (
                          <p className="mt-0.5 text-[13px] leading-relaxed text-[var(--text-muted)]">
                            {lookup.summary || '词典未收录'}
                          </p>
                        )}
                      </div>

                      <div className="flex shrink-0 items-center gap-2">
                        <button
                          type="button"
                          onClick={() => speakWord(entry.clean)}
                          className={BTN_SM}
                          title="朗读"
                          aria-label={`朗读 ${entry.raw}`}
                        >
                          <Volume2 className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => markMastered(entry.clean)}
                          className={`${BTN_SM} hover:border-[var(--success)] hover:text-[var(--success)]`}
                          title="标记为已掌握"
                        >
                          <Check className="h-4 w-4" />
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </>
          )}
        </div>

        {unknownWords.length > 0 && (
          <div className="flex shrink-0 items-center justify-between gap-3 border-t border-[var(--border-color)] bg-[var(--bg-subtle)] px-5 py-4">
            <p className="text-[13px] text-[var(--text-muted)]">
              标记后该词不再高亮，也不再出现在这里。
            </p>
            <button type="button" onClick={handleMarkAll} className={BTN_ON_HIGHLIGHT}>
              <Check className="h-4 w-4" />
              <span>全部标为已掌握</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
