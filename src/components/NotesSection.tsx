import React, { useState } from 'react';
import { Check, Loader2, Sparkles, X } from 'lucide-react';
import { useAnnotationStore } from '../store/useAnnotationStore';
import { askOnce } from '../services/aiService';
import { grammarGlossPrompt, wordNotePrompt } from '../services/aiPrompts';
import { BTN_SM, FIELD, SECTION_LABEL } from './ui';

interface NotesSectionProps {
  word: string;
  /** 当前所在句子。没有它就没有上下文，AI 推荐无从谈起，按钮也不显示。 */
  sentence?: string;
}

/**
 * 关于这个词的笔记。
 *
 * 「笔记」在数据上就是一组字符串（`notes[word]: string[]`），手写的和被采纳的
 * AI 建议**事后无法区分** —— 这一点是照搬 LingKuma 的设计（它的单词笔记就是
 * `translations[]` 数组，用户文本与 AI 文本同构）。好处是采纳之后它就是你自己的
 * 笔记，可以随便改；代价是没有来源标记。
 */
export const NotesSection: React.FC<NotesSectionProps> = ({ word, sentence }) => {
  const { notes: notesMap, addNote, removeNote } = useAnnotationStore();
  const notes = notesMap[word.toLowerCase()] ?? [];

  const [draft, setDraft] = useState('');
  const [suggestions, setSuggestions] = useState<{ translation?: string; grammar?: string }>({});
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canAskAi = Boolean(sentence?.trim());

  const handleAddDraft = () => {
    const text = draft.trim();
    if (!text) return;
    addNote(word, text);
    setDraft('');
  };

  const handleAskAi = async () => {
    if (!sentence) return;

    setIsLoading(true);
    setError(null);
    setSuggestions({});

    // 两条建议并行取：它们互不依赖，串行只会白等一个来回。
    //
    // 刻意不给 mockContent —— 示例文本一旦被「采纳」就成了用户自己的笔记，
    // 把假数据混进真实学习记录里比什么都不显示更糟。取不到就直说怎么配。
    const [translation, grammar] = await Promise.all([
      askOnce(wordNotePrompt(word, sentence)),
      askOnce(grammarGlossPrompt(word, sentence), {
        system: '你是一位语言学习助手，回答务必简短。',
      }),
    ]);

    const next: { translation?: string; grammar?: string } = {};
    if (translation.ok) next.translation = translation.content;
    if (grammar.ok) next.grammar = grammar.content;
    setSuggestions(next);

    if (!translation.ok && !grammar.ok) {
      setError(`${translation.message}\n${translation.hint}`);
    }
    setIsLoading(false);
  };

  const handleAdopt = (text: string, kind: 'translation' | 'grammar') => {
    addNote(word, text);
    setSuggestions((prev) => ({ ...prev, [kind]: undefined }));
  };

  const suggestionRows = [
    { kind: 'translation' as const, label: '翻译', text: suggestions.translation },
    { kind: 'grammar' as const, label: '用法精要', text: suggestions.grammar },
  ].filter((row) => row.text);

  return (
    <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] px-4 py-3.5">
      <div className="flex items-center justify-between gap-2">
        <span className={SECTION_LABEL}>我的笔记</span>
        {canAskAi && (
          <button
            type="button"
            onClick={handleAskAi}
            disabled={isLoading}
            className="flex items-center gap-1.5 text-[13px] font-semibold text-[var(--accent)] underline decoration-1 underline-offset-2 hover:no-underline disabled:opacity-50"
            title="让 AI 针对这个句子给出翻译与用法建议"
          >
            {isLoading ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Sparkles className="h-3.5 w-3.5" />
            )}
            AI 推荐
          </button>
        )}
      </div>

      {error && (
        <p className="mt-2.5 border-l-2 border-[var(--highlight-border)] pl-2.5 text-[13px] leading-relaxed whitespace-pre-wrap text-[var(--highlight-text)]">
          {error}
        </p>
      )}

      {/* AI 建议：只有点「采纳」才会进笔记，未采纳的不会落库。 */}
      {suggestionRows.length > 0 && (
        <div className="mt-2.5 space-y-2">
          {suggestionRows.map((row) => (
            <div
              key={row.kind}
              className="flex items-start justify-between gap-2.5 border border-[var(--accent-border)] bg-[var(--accent-soft)] px-3 py-2"
            >
              <div className="min-w-0">
                <span className="font-mono text-[11px] tracking-[0.08em] uppercase text-[var(--accent)]">
                  AI {row.label}
                </span>
                <p className="mt-0.5 text-[14px] leading-relaxed whitespace-pre-wrap text-[var(--text-main)]">
                  {row.text}
                </p>
              </div>
              <button
                type="button"
                onClick={() => handleAdopt(row.text as string, row.kind)}
                className={`${BTN_SM} shrink-0`}
                title="采纳为我的笔记"
              >
                <Check className="h-4 w-4" />
                <span>采纳</span>
              </button>
            </div>
          ))}
        </div>
      )}

      {notes.length > 0 && (
        <ul className="mt-2.5 space-y-1.5">
          {notes.map((note) => (
            <li
              key={note}
              className="group flex items-start justify-between gap-2.5 border border-[var(--border-color)] bg-[var(--bg-surface)] px-3 py-2"
            >
              <p className="min-w-0 text-[14px] leading-relaxed break-words whitespace-pre-wrap text-[var(--text-main)]">
                {note}
              </p>
              <button
                type="button"
                onClick={() => removeNote(word, note)}
                className="shrink-0 text-[var(--text-muted)] transition-colors hover:text-[var(--danger)]"
                title="删除这条笔记"
                aria-label="删除这条笔记"
              >
                <X className="h-4 w-4" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-2.5 flex items-start gap-2">
        <textarea
          rows={2}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            // Ctrl/Cmd+Enter 提交；单独的 Enter 要留给换行。
            if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
              event.preventDefault();
              handleAddDraft();
            }
          }}
          placeholder="写下你对这个词的理解…"
          className={`${FIELD} font-serif text-[14px] leading-relaxed`}
        />
        <button
          type="button"
          onClick={handleAddDraft}
          disabled={!draft.trim()}
          className={`${BTN_SM} h-11 shrink-0`}
          title="添加笔记（Ctrl/⌘ + Enter）"
        >
          添加
        </button>
      </div>
    </div>
  );
};
