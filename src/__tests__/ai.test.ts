import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  chatPrompt,
  formatConversationHistory,
  grammarGlossPrompt,
  sentenceAnalysisPrompt,
  sentenceTranslationPrompt,
  wordNotePrompt,
} from '../services/aiPrompts';
import { DEEPSEEK_API_KEY_STORAGE_KEY } from '../services/aiService';

function stubResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

/** Answers the health probe, then hands every other URL to `handle`. */
function stubFetch(
  health: 'ok' | 'down',
  handle: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  const seen: Array<{ url: string; init?: RequestInit }> = [];
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url);
    seen.push({ url: target, init });
    if (target === '/api/health') {
      return health === 'ok'
        ? stubResponse({ ok: true, service: 'language-reader-api' })
        : stubResponse({ ok: false }, 404);
    }
    return handle(target, init);
  });
  vi.stubGlobal('fetch', fetchMock);
  return { fetchMock, seen };
}

/** Both services hold module-level state (the health probe), so reload them. */
async function loadService() {
  vi.resetModules();
  return import('../services/aiService');
}

function completion(content: string) {
  return stubResponse({ choices: [{ message: { content } }] });
}

describe('AI client', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('posts through the same-origin backend when it is running', async () => {
    const { seen } = stubFetch('ok', () => completion('  打破  '));

    const { requestChat } = await loadService();
    const result = await requestChat([{ role: 'user', content: 'hi' }]);

    expect(result).toEqual({ ok: true, content: '打破', mocked: false });

    const call = seen.find((entry) => entry.url === '/api/ai/chat');
    expect(call).toBeDefined();
    // The key stays optional: the backend can hold DEEPSEEK_API_KEY itself.
    const headers = (call?.init?.headers ?? {}) as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
    expect(seen.some((entry) => entry.url.startsWith('https://api.deepseek.com'))).toBe(false);
  });

  it('sends a browser key when one is configured', async () => {
    localStorage.setItem(DEEPSEEK_API_KEY_STORAGE_KEY, 'sk-test');
    const { seen } = stubFetch('ok', () => completion('ok'));

    const { requestChat } = await loadService();
    await requestChat([{ role: 'user', content: 'hi' }]);

    const call = seen.find((entry) => entry.url === '/api/ai/chat');
    const headers = (call?.init?.headers ?? {}) as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer sk-test');
  });

  it('returns the mock text only when the backend is down and no key is set', async () => {
    stubFetch('down', () => completion('should not be reached'));

    const { requestChat } = await loadService();
    const result = await requestChat([{ role: 'user', content: 'hi' }], '【示例】');

    expect(result).toEqual({ ok: true, content: '【示例】', mocked: true });
  });

  it('fails instead of mocking when no mock text was supplied', async () => {
    stubFetch('down', () => completion('unused'));

    const { requestChat } = await loadService();
    const result = await requestChat([{ role: 'user', content: 'hi' }]);

    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.hint).toContain('npm run server');
  });

  /**
   * The backend being up but keyless is a real 401 — showing the built-in sample
   * there would disguise a configuration problem as a working feature.
   */
  it('reports a real failure when the backend answers 401, never the mock', async () => {
    stubFetch('ok', () => stubResponse({ error: 'missing DeepSeek API key' }, 401));

    const { requestChat } = await loadService();
    const result = await requestChat([{ role: 'user', content: 'hi' }], '【示例】');

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.message).toContain('HTTP 401');
      expect(result.message).toContain('missing DeepSeek API key');
      expect(result.hint).toContain('DEEPSEEK_API_KEY');
    }
  });

  it('treats an empty model reply as a failure rather than blank output', async () => {
    stubFetch('ok', () => completion('   '));

    const { requestChat } = await loadService();
    const result = await requestChat([{ role: 'user', content: 'hi' }]);

    expect(result.ok).toBe(false);
  });

  it('survives a network error and explains what to do', async () => {
    stubFetch('ok', () => {
      throw new Error('network is unreachable');
    });

    const { requestChat } = await loadService();
    const result = await requestChat([{ role: 'user', content: 'hi' }]);

    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.message).toContain('network is unreachable');
  });
});

describe('AI prompt templates', () => {
  const word = 'ubiquitous';
  const sentence = 'Screens are ubiquitous in modern life.';

  /**
   * LingKuma expands `{word}` / `{sentence}` with String.replace, which only
   * replaces the first occurrence — and its own aiPrompt mentions {word} several
   * times. These templates interpolate instead, so no brace may survive.
   */
  const renders: Array<[string, string]> = [
    ['word note', wordNotePrompt(word, sentence)],
    ['grammar gloss', grammarGlossPrompt(word, sentence)],
    ['sentence translation', sentenceTranslationPrompt(word, sentence)],
    ['sentence analysis', sentenceAnalysisPrompt(sentence)],
  ];

  it.each(renders)('substitutes its inputs in the %s prompt', (_name, prompt) => {
    expect(prompt).not.toContain('{word}');
    expect(prompt).not.toContain('{sentence}');
  });

  it('puts the word and the sentence into their prompts', () => {
    expect(wordNotePrompt(word, sentence)).toContain(word);
    expect(wordNotePrompt(word, sentence)).toContain(sentence);
    expect(grammarGlossPrompt(word, sentence)).toContain(word);
    expect(sentenceTranslationPrompt(word, sentence)).toContain(sentence);
  });

  it('asks for Markdown bold in the sentence translation, not HTML', () => {
    const prompt = sentenceTranslationPrompt(word, sentence);
    expect(prompt).toContain('Markdown加粗');
    expect(prompt).not.toContain('<strong>');
  });

  it('builds the follow-up prompt from the sentence, history and question', () => {
    const history = formatConversationHistory([
      { role: 'user', content: '为什么用这个词？' },
      { role: 'assistant', content: '因为它强调普遍性。' },
    ]);
    const prompt = chatPrompt(sentence, history, '能再举一例吗？');

    expect(prompt).toContain(sentence);
    expect(prompt).toContain('用户: 为什么用这个词？');
    expect(prompt).toContain('AI: 因为它强调普遍性。');
    expect(prompt).toContain('能再举一例吗？');
  });
});
