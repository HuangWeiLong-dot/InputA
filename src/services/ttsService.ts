import { apiUrl, getBackendHealth } from './apiBase';
import { useReaderStore } from '../store/useReaderStore';

/**
 * 朗读。三个引擎，按优先级回退：
 *
 *   edge    —— 后端代理 Edge TTS（GET /api/tts）。音质最好，但需要后端在跑，
 *              且那是个非公开接口，随时可能失效。
 *   custom  —— 用户自己的 TTS 服务地址，约定是 **GET 返回可播放的音频字节**
 *              （LingKuma 的 custom 提供者也是这个契约）。
 *   browser —— 今天的行为：先用词典音源，再退到浏览器语音合成。
 *
 * Edge 或自定义失败时**静默退到 browser**，而不是让朗读按钮失效 —— 朗读是
 * 阅读时的随手动作，弹一个错误框比换一种声音更打扰。
 */

/** LingKuma 的自动音色表（其 src/plugin/tts.js 的 playEdgeTTS）。 */
const AUTO_VOICES: Record<string, string> = {
  zh: 'zh-CN-XiaoxiaoNeural',
  en: 'en-US-AriaNeural',
  de: 'de-DE-SeraphinaMultilingualNeural',
  ja: 'ja-JP-NanamiNeural',
  ru: 'ru-RU-DmitryNeural',
  fr: 'fr-FR-VivienneMultilingualNeural',
  es: 'es-ES-ElviraNeural',
};

const DEFAULT_VOICE = 'en-US-AriaNeural';

/** Shown as the placeholder in settings, so the auto rule is discoverable. */
export const AUTO_VOICE_HINT = '英语 en-US-AriaNeural，德语 de-DE-Seraphina，中文 zh-CN-Xiaoxiao 等';

const EDGE_TIMEOUT_MS = 20_000;
/**
 * 在模块加载期就定下来 —— 这正是后端地址必须是**构建期**配置（VITE_API_BASE）
 * 而不是阅读器设置的原因：这个常量从非组件模块里被读取，改成运行时可变就得把
 * 全应用每个模块级 URL 都改成惰性求值的函数。
 */
const EDGE_ENDPOINT = apiUrl('/api/tts');

let activeAudio: HTMLAudioElement | null = null;

function stopActiveAudio(): void {
  if (activeAudio) {
    activeAudio.pause();
    activeAudio = null;
  }
}

/** 由正文语言选音色；认不出来就用英语。 */
export function autoVoiceFor(lang?: string): string {
  if (!lang) return DEFAULT_VOICE;
  const base = lang.split('-')[0]?.toLowerCase() ?? '';
  return AUTO_VOICES[base] ?? DEFAULT_VOICE;
}

/** 展开自定义地址模板里的 `{text}` / `{lang}`。 */
export function expandCustomUrl(template: string, text: string, lang: string): string {
  return template
    .replace(/\{text\}/g, encodeURIComponent(text))
    .replace(/\{lang\}/g, encodeURIComponent(lang));
}

function playUrl(url: string, revokeWhenDone: boolean): Promise<void> {
  stopActiveAudio();

  return new Promise((resolve, reject) => {
    const audio = new Audio(url);
    activeAudio = audio;

    const cleanup = () => {
      if (revokeWhenDone) URL.revokeObjectURL(url);
      if (activeAudio === audio) activeAudio = null;
    };

    audio.onended = () => {
      cleanup();
      resolve();
    };
    audio.onerror = () => {
      cleanup();
      reject(new Error('audio playback failed'));
    };

    void audio.play().catch((error: unknown) => {
      cleanup();
      reject(error instanceof Error ? error : new Error('audio playback was blocked'));
    });
  });
}

async function speakViaEdgeBlob(text: string, voice: string, rate: string): Promise<void> {
  const params = new URLSearchParams({ text, voice, rate });
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), EDGE_TIMEOUT_MS);

  try {
    const response = await fetch(`${EDGE_ENDPOINT}?${params.toString()}`, {
      signal: controller.signal,
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    const blob = await response.blob();
    if (blob.size === 0) throw new Error('empty audio');

    await playUrl(URL.createObjectURL(blob), true);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * 浏览器语音合成。这段逻辑原样来自本仓库原来的 speechService —— 包括那个
 * 按名字挑音色的启发式，它在 Chrome 里比默认音色自然得多。
 */
function fallbackSpeechSynthesis(text: string, lang: string): void {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
    console.warn('Speech synthesis is not available in this browser.');
    return;
  }

  try {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang;
    utterance.rate = 0.9;

    const prefix = lang.split('-')[0];
    const voices = window.speechSynthesis.getVoices();
    const preferred = voices.find(
      (voice) =>
        voice.lang.startsWith(prefix) &&
        (voice.name.includes('Natural') ||
          voice.name.includes('Google') ||
          voice.name.includes('Samantha')),
    );
    if (preferred) utterance.voice = preferred;

    window.speechSynthesis.speak(utterance);
  } catch (error) {
    console.warn('Speech synthesis failed:', error);
  }
}

async function speak(
  text: string,
  options: { audioUrl?: string; lang?: string } = {},
): Promise<void> {
  const trimmed = text.trim();
  if (!trimmed) return;

  const { ttsProvider, ttsVoice, ttsRate, ttsCustomUrlTemplate, currentBook } =
    useReaderStore.getState();

  // 没显式指定语言时用当前读物的 —— 于是所有调用点都不必自己传，
  // 而词典音源、语音合成、自动音色三者拿到的语言是一致的。
  const lang = options.lang ?? currentBook?.language ?? 'en-US';
  const voice = ttsVoice.trim() || autoVoiceFor(lang);

  if (ttsProvider !== 'browser') {
    try {
      if (ttsProvider === 'edge') {
        // 后端没起时别白等一次超时——直接落到浏览器合成。
        const backend = await getBackendHealth();
        if (backend.ok) {
          await speakViaEdgeBlob(trimmed, voice, ttsRate || 'default');
          return;
        }
      } else if (ttsProvider === 'custom') {
        const template = ttsCustomUrlTemplate.trim();
        if (template) {
          await playUrl(expandCustomUrl(template, trimmed, lang), false);
          return;
        }
      }
    } catch (error) {
      console.warn(`TTS via "${ttsProvider}" failed, falling back to the browser:`, error);
    }
  }

  // browser 路径：词典真人音源优先，没有再合成。
  if (options.audioUrl) {
    try {
      await playUrl(options.audioUrl, false);
      return;
    } catch (error) {
      console.warn('Dictionary audio failed, falling back to speech synthesis:', error);
    }
  }

  fallbackSpeechSynthesis(trimmed, lang);
}

/**
 * 朗读一个词或一句话。
 *
 * 保持同步签名（内部 fire-and-forget）：调用点遍布按钮点击处理，改动它们的
 * 返回值没有意义，而朗读本身是「发出去就不管」的行为。
 */
export function speakWord(text: string, audioUrl?: string, lang?: string): void {
  void speak(text, { audioUrl, lang });
}
