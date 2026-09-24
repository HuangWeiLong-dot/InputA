import { getBackendHealth } from './apiBase';

/**
 * 所有 AI 调用的唯一入口。
 *
 * 原先这套 fetch 直接写在 SentenceAnalysisModal 里。整合 LingKuma 的四个 AI 能力
 * （推荐笔记 ①②、例句翻译、追问对话）后会有五六处复制，所以抽到这里，请求形状
 * 保持原样不变 —— 后端 /api/ai/chat 只是把 body 原样转发给 DeepSeek。
 */

export const DEEPSEEK_API_KEY_STORAGE_KEY = 'deepseek_api_key';

/** 只在后端没起时用。浏览器直连 DeepSeek 会被 CORS 拦掉，这条是纯粹的兜底。 */
const DEEPSEEK_DIRECT_ENDPOINT = 'https://api.deepseek.com/chat/completions';

const MODEL = 'deepseek-chat';

/** 让示例文本也走一遍「正在请求」的界面，否则一瞬间闪过去反而让人以为坏了。 */
const MOCK_DELAY_MS = 400;

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface AiSuccess {
  ok: true;
  content: string;
  /** true = 这是内置示例，不是模型输出。界面必须明确标出来。 */
  mocked: boolean;
}

export interface AiFailure {
  ok: false;
  message: string;
  /** 面向用户的下一步建议（配 Key / 起后端）。 */
  hint: string;
}

export type AiResult = AiSuccess | AiFailure;

/** 读浏览器里填的 Key。隐私模式下 localStorage 可能直接抛异常，所以裹起来。 */
export function getBrowserApiKey(): string {
  try {
    return localStorage.getItem(DEEPSEEK_API_KEY_STORAGE_KEY) || '';
  } catch {
    return '';
  }
}

export function saveBrowserApiKey(key: string): void {
  try {
    localStorage.setItem(DEEPSEEK_API_KEY_STORAGE_KEY, key);
  } catch (err) {
    console.error('Failed to save the DeepSeek API key:', err);
  }
}

/** 把错误响应里的 `error` 字段挖出来，好过只显示一个状态码。 */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const data = (await response.json()) as { error?: unknown };
    if (typeof data?.error === 'string') return data.error;
  } catch {
    // 响应体不是 JSON，只能退回状态码。
  }
  return '';
}

/**
 * 发一轮对话请求。
 *
 * `mockContent` 决定「既没有浏览器 Key、后端也没起」时怎么办：给了就返回这段
 * 示例文本（`mocked: true`），没给就是失败。
 *
 * 注意条件是两个都缺 —— 后端在线但没配 DEEPSEEK_API_KEY 是一条**真实的 401**，
 * 那时要给配置提示，而不是拿假结果糊弄用户。
 */
export async function requestChat(
  messages: ChatMessage[],
  mockContent?: string,
): Promise<AiResult> {
  const browserKey = getBrowserApiKey().trim();
  const backend = await getBackendHealth();

  if (!browserKey && !backend.ok) {
    if (mockContent === undefined) {
      return {
        ok: false,
        message: '无法连接 AI 服务。',
        hint: '请先运行 npm run server，或在 AI 面板里填入 DeepSeek API Key。',
      };
    }
    await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
    return { ok: true, content: mockContent, mocked: true };
  }

  // 后端在线时走后端（它也可以自己持有 Key）；否则退回浏览器直连。
  const endpoint = backend.ok ? '/api/ai/chat' : DEEPSEEK_DIRECT_ENDPOINT;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(browserKey ? { Authorization: `Bearer ${browserKey}` } : {}),
      },
      body: JSON.stringify({ model: MODEL, messages, stream: false }),
    });

    if (!response.ok) {
      const detail = await readErrorMessage(response);
      throw new Error(`HTTP ${response.status}${detail ? ` · ${detail}` : ''}`);
    }

    const data = (await response.json()) as {
      choices?: Array<{ message?: { content?: unknown } }>;
    };
    const content = data?.choices?.[0]?.message?.content;
    if (typeof content !== 'string' || !content.trim()) {
      return { ok: false, message: '模型未返回有效内容。', hint: hintFor(browserKey) };
    }

    return { ok: true, content: content.trim(), mocked: false };
  } catch (err) {
    const reason = err instanceof Error ? err.message : '未知错误';
    return { ok: false, message: `调用 DeepSeek 失败：${reason}`, hint: hintFor(browserKey) };
  }
}

function hintFor(browserKey: string): string {
  return browserKey
    ? '请检查 API Key 与网络后重试。'
    : '后端未配置 DEEPSEEK_API_KEY，且未在浏览器中填入 Key。';
}

/** 单轮「一问一答」的便捷包装：system 可选，其余作为 user 消息。 */
export async function askOnce(
  userContent: string,
  options: { system?: string; mockContent?: string } = {},
): Promise<AiResult> {
  const messages: ChatMessage[] = [];
  if (options.system) messages.push({ role: 'system', content: options.system });
  messages.push({ role: 'user', content: userContent });
  return requestChat(messages, options.mockContent);
}
