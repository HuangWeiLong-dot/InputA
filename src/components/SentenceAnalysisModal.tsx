import React, { useState } from 'react';
import { X, Sparkles, Settings, Loader2, Send } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { getBrowserApiKey, requestChat, saveBrowserApiKey } from '../services/aiService';
import { chatPrompt, chatSystemPrompt, sentenceAnalysisPrompt } from '../services/aiPrompts';
import { formatConversationHistory } from '../services/aiPrompts';
import { RichText } from './RichText';
import { BTN_ACCENT, BTN_GHOST, BTN_PRIMARY, FIELD, SECTION_LABEL } from './ui';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

const MOCK_ANALYSIS = (sentence: string) =>
  `【语法结构拆解示例】（填入 DeepSeek API Key 可获得真实模型分析）

原句：
"${sentence}"

1. 句子主干 (Core Clause)
   • 主语 (Subject)：句首核心名词短语
   • 谓语 (Verb)：主要动作或状态
   • 宾语 / 表语 (Object / Complement)：承接成分

2. 从句与修饰成分 (Modifiers)
   • 定语 / 同位语：补充说明名词特征
   • 状语：交代时间、地点、因果或伴随状态

3. 阅读技巧
   遇到长句先略过插入语与括号内容，锁定谓语动词，再逐个剥离从句。`;

export const SentenceAnalysisModal: React.FC = () => {
  const { isSentenceAnalysisOpen, setSentenceAnalysisOpen, selectedSentence } = useReaderStore();

  const [inputSentence, setInputSentence] = useState(selectedSentence);
  const [analysisResult, setAnalysisResult] = useState<string | null>(null);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  /** 结果是内置示例时不允许追问 —— 没有真模型在另一端。 */
  const [isMock, setIsMock] = useState(false);
  const [apiKey, setApiKey] = useState(() => getBrowserApiKey());
  const [showConfig, setShowConfig] = useState(false);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [isChatting, setIsChatting] = useState(false);
  const [chatError, setChatError] = useState<string | null>(null);

  // NOTE: this component is mounted with key={selectedSentence} by App.tsx, so
  // picking a new sentence from the reader resets the local state automatically
  // (idiomatic "reset state with a key" instead of a state-syncing effect).

  if (!isSentenceAnalysisOpen) return null;

  const handleSaveApiKey = (key: string) => {
    setApiKey(key);
    saveBrowserApiKey(key);
    setShowConfig(false);
  };

  const handleAnalyze = async () => {
    const textToAnalyze = inputSentence.trim();
    if (!textToAnalyze) return;

    setIsAnalyzing(true);
    setAnalysisResult(null);
    setAnalysisError(null);
    setChatMessages([]);
    setChatError(null);

    try {
      // The backend can hold the key itself (DEEPSEEK_API_KEY), so a browser-side
      // key is optional while it is running. The mock only appears when neither
      // is available — see requestChat.
      const result = await requestChat(
        [{ role: 'user', content: sentenceAnalysisPrompt(textToAnalyze) }],
        MOCK_ANALYSIS(textToAnalyze),
      );

      if (result.ok) {
        setAnalysisResult(result.content);
        setIsMock(result.mocked);
      } else {
        setAnalysisError(`${result.message}\n\n${result.hint}`);
        setIsMock(false);
      }
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleChat = async () => {
    const question = chatInput.trim();
    if (!question || isChatting || !analysisResult) return;

    const nextMessages: ChatMessage[] = [...chatMessages, { role: 'user', content: question }];
    setChatMessages(nextMessages);
    setChatInput('');
    setChatError(null);
    setIsChatting(true);

    try {
      // The history is woven into the prompt itself (that is what chatPrompt is
      // for), so it is deliberately not also replayed as separate messages —
      // that would send every turn twice.
      const history = formatConversationHistory(nextMessages.slice(0, -1));
      const result = await requestChat([
        { role: 'system', content: chatSystemPrompt(inputSentence.trim()) },
        { role: 'user', content: chatPrompt(inputSentence.trim(), history, question) },
      ]);

      if (result.ok) {
        setChatMessages([...nextMessages, { role: 'assistant', content: result.content }]);
      } else {
        setChatError(`${result.message}\n\n${result.hint}`);
      }
    } finally {
      setIsChatting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-overlay-in">
      <div className="flex max-h-[88vh] w-full max-w-2xl flex-col border border-[var(--border-color)] bg-[var(--bg-surface)] animate-panel-in">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--border-color)] px-5 py-4">
          <div className="flex items-center gap-2.5">
            <Sparkles className="h-5 w-5 text-[var(--accent)]" />
            <h2 className="text-[16px] font-semibold text-[var(--text-strong)]">AI 长难句语法拆解</h2>
            {apiKey.trim() && (
              <span className="border border-[var(--accent-border)] bg-[var(--accent-soft)] px-2 py-1 font-mono text-[11px] text-[var(--accent)]">
                deepseek-chat
              </span>
            )}
          </div>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setShowConfig(!showConfig)}
              className={BTN_GHOST}
              title="配置 DeepSeek API Key"
              aria-label="配置 API Key"
            >
              <Settings className="h-5 w-5" />
            </button>
            <button
              type="button"
              onClick={() => setSentenceAnalysisOpen(false)}
              className={BTN_GHOST}
              aria-label="关闭"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* API key configuration */}
        {showConfig && (
          <div className="space-y-2.5 border-b border-[var(--accent-border)] bg-[var(--accent-soft)] px-5 py-4">
            <p className="text-[13px] font-semibold text-[var(--text-main)]">
              配置 DeepSeek API Key（保存在本地浏览器，也可在后端设置 DEEPSEEK_API_KEY）
            </p>
            <div className="flex gap-2">
              <input
                type="password"
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                placeholder="sk-..."
                className={FIELD}
              />
              <button
                type="button"
                onClick={() => handleSaveApiKey(apiKey)}
                className={`${BTN_PRIMARY} shrink-0`}
              >
                保存
              </button>
            </div>
            <p className="text-[12px] text-[var(--text-muted)]">未配置 Key 时提供结构拆解示例模板。</p>
          </div>
        )}

        {/* Body */}
        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          <div>
            <label
              htmlFor="sentence-to-analyze"
              className="mb-2 block text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--text-muted)]"
            >
              待分析句子
            </label>
            <textarea
              id="sentence-to-analyze"
              rows={4}
              value={inputSentence}
              onChange={(event) => setInputSentence(event.target.value)}
              placeholder="输入或从正文选取的长难句"
              className={`${FIELD} font-serif leading-relaxed`}
            />
          </div>

          <button
            type="button"
            onClick={handleAnalyze}
            disabled={isAnalyzing || !inputSentence.trim()}
            className={`${BTN_ACCENT} w-full`}
          >
            {isAnalyzing ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                <span>正在拆解句子结构…</span>
              </>
            ) : (
              <>
                <Sparkles className="h-4 w-4" />
                <span>开始拆解语法</span>
              </>
            )}
          </button>

          {/*
            A failure gets the amber "attention" treatment rather than the same
            neutral box the result uses: both used to land in one string slot, so
            a misconfigured key looked exactly like a successful analysis.
          */}
          {analysisError && (
            <div className="border border-[var(--highlight-border)] bg-[var(--highlight-bg)] p-4 text-[14px] leading-relaxed whitespace-pre-wrap text-[var(--highlight-text)]">
              {analysisError}
            </div>
          )}

          {analysisResult && (
            <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] p-4 text-[15px] leading-relaxed text-[var(--text-main)]">
              {isMock && (
                <p className="mb-2.5 border-b border-[var(--border-color)] pb-2 text-[12px] font-semibold text-[var(--highlight-text)]">
                  这是内置示例，不是模型输出。填入 DeepSeek API Key 或启动后端可获得真实分析。
                </p>
              )}
              <RichText text={analysisResult} className="whitespace-pre-wrap" />
            </div>
          )}

          {/* Follow-up conversation, only once there is a real analysis. */}
          {analysisResult && !isMock && (
            <div className="space-y-3 border-t border-[var(--border-color)] pt-4">
              <span className={SECTION_LABEL}>继续追问</span>

              {chatMessages.map((message, index) => (
                <div
                  key={index}
                  className={
                    message.role === 'user'
                      ? 'border-l-2 border-[var(--accent-border)] pl-3'
                      : 'border-l-2 border-[var(--border-color)] pl-3'
                  }
                >
                  <p className="text-[12px] font-semibold text-[var(--text-muted)]">
                    {message.role === 'user' ? '你' : 'AI'}
                  </p>
                  <RichText
                    text={message.content}
                    className="mt-0.5 block text-[14px] leading-relaxed whitespace-pre-wrap text-[var(--text-main)]"
                  />
                </div>
              ))}

              {isChatting && (
                <p className="flex items-center gap-2 text-[13px] text-[var(--text-muted)]">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  正在思考…
                </p>
              )}

              {chatError && (
                <p className="border-l-2 border-[var(--highlight-border)] pl-3 text-[13px] leading-relaxed whitespace-pre-wrap text-[var(--highlight-text)]">
                  {chatError}
                </p>
              )}

              <div className="flex items-start gap-2">
                <textarea
                  rows={2}
                  value={chatInput}
                  onChange={(event) => setChatInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
                      event.preventDefault();
                      void handleChat();
                    }
                  }}
                  placeholder="就这个句子继续提问…"
                  className={`${FIELD} text-[14px] leading-relaxed`}
                />
                <button
                  type="button"
                  onClick={handleChat}
                  disabled={isChatting || !chatInput.trim()}
                  className={`${BTN_ACCENT} h-11 shrink-0`}
                  title="发送（Ctrl/⌘ + Enter）"
                >
                  <Send className="h-4 w-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
