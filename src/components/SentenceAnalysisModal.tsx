import React, { useState } from 'react';
import { X, Sparkles, Settings, Loader2 } from 'lucide-react';
import { useReaderStore } from '../store/useReaderStore';
import { getBackendHealth } from '../services/apiBase';
import { BTN_ACCENT, BTN_GHOST, BTN_PRIMARY, FIELD } from './ui';

const API_KEY_STORAGE_KEY = 'deepseek_api_key';

/** Only used when the local backend is not running (browser calls are CORS blocked). */
const DEEPSEEK_DIRECT_ENDPOINT = 'https://api.deepseek.com/chat/completions';

/** Pull the `error` field out of an error response for a useful message. */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const data = (await response.json()) as { error?: unknown };
    if (typeof data?.error === 'string') return data.error;
  } catch {
    // The body was not JSON; fall back to the status code only.
  }
  return '';
}

const SYSTEM_PROMPT =
  '你是一位精通英语教学的语言学导师。请对用户提供的英文长难句进行结构拆解：1. 主干结构（主谓宾/主系表）；2. 从句与修饰成分拆解（定语从句、状语、伴随分词等）；3. 核心词组与搭配；4. 地道中文翻译。语言简明扼要，适合语言学习者。';

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
  const [apiKey, setApiKey] = useState(() => localStorage.getItem(API_KEY_STORAGE_KEY) || '');
  const [showConfig, setShowConfig] = useState(false);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  // NOTE: this component is mounted with key={selectedSentence} by App.tsx, so
  // picking a new sentence from the reader resets the local state automatically
  // (idiomatic "reset state with a key" instead of a state-syncing effect).

  if (!isSentenceAnalysisOpen) return null;

  const handleSaveApiKey = (key: string) => {
    setApiKey(key);
    localStorage.setItem(API_KEY_STORAGE_KEY, key);
    setShowConfig(false);
  };

  const handleAnalyze = async () => {
    const textToAnalyze = inputSentence.trim();
    if (!textToAnalyze) return;

    setIsAnalyzing(true);
    setAnalysisResult(null);

    const browserKey = apiKey.trim();
    const backend = await getBackendHealth();
    // The backend can hold the key itself (DEEPSEEK_API_KEY), so a browser-side
    // key is optional while it is running.
    const endpoint = backend.ok ? '/api/ai/chat' : DEEPSEEK_DIRECT_ENDPOINT;

    if (!browserKey && !backend.ok) {
      window.setTimeout(() => {
        setAnalysisResult(MOCK_ANALYSIS(textToAnalyze));
        setIsAnalyzing(false);
      }, 400);
      return;
    }

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(browserKey ? { Authorization: `Bearer ${browserKey}` } : {}),
        },
        body: JSON.stringify({
          model: 'deepseek-chat',
          messages: [
            { role: 'system', content: SYSTEM_PROMPT },
            { role: 'user', content: textToAnalyze },
          ],
          stream: false,
        }),
      });

      if (!response.ok) {
        const detail = await readErrorMessage(response);
        throw new Error(`HTTP ${response.status}${detail ? ` · ${detail}` : ''}`);
      }

      const data = await response.json();
      setAnalysisResult(data.choices?.[0]?.message?.content || '模型未返回有效分析结果。');
    } catch (err) {
      const reason = err instanceof Error ? err.message : '未知错误';
      const hint = browserKey
        ? '请检查 API Key 与网络后重试。'
        : '后端未配置 DEEPSEEK_API_KEY，且未在浏览器中填入 Key。';
      setAnalysisResult(`调用 DeepSeek 失败：${reason}\n\n${hint}`);
    } finally {
      setIsAnalyzing(false);
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

          {analysisResult && (
            <div className="border border-[var(--border-color)] bg-[var(--bg-subtle)] p-4 text-[15px] leading-relaxed whitespace-pre-wrap text-[var(--text-main)]">
              {analysisResult}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

