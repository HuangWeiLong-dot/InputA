import { describe, it, expect } from 'vitest';
import { autoVoiceFor, expandCustomUrl } from '../services/ttsService';

describe('Automatic voice selection', () => {
  it('picks a voice for the languages the reader can show', () => {
    expect(autoVoiceFor('en-US')).toBe('en-US-AriaNeural');
    expect(autoVoiceFor('de-DE')).toBe('de-DE-SeraphinaMultilingualNeural');
    expect(autoVoiceFor('zh')).toBe('zh-CN-XiaoxiaoNeural');
    expect(autoVoiceFor('fr-FR')).toBe('fr-FR-VivienneMultilingualNeural');
  });

  it('is case-insensitive about the language tag', () => {
    expect(autoVoiceFor('EN-us')).toBe('en-US-AriaNeural');
    expect(autoVoiceFor('DE')).toBe('de-DE-SeraphinaMultilingualNeural');
  });

  it('falls back to English for anything unknown or missing', () => {
    // An unknown language must still produce a usable voice, never an empty one.
    expect(autoVoiceFor('xx-YY')).toBe('en-US-AriaNeural');
    expect(autoVoiceFor(undefined)).toBe('en-US-AriaNeural');
    expect(autoVoiceFor('')).toBe('en-US-AriaNeural');
  });
});

describe('Custom TTS URL templates', () => {
  it('substitutes the text and language, URL-encoded', () => {
    const url = expandCustomUrl(
      'https://example.com/tts?q={text}&lang={lang}',
      'hello world & goodbye',
      'en-US',
    );

    expect(url).toBe(
      'https://example.com/tts?q=hello%20world%20%26%20goodbye&lang=en-US',
    );
  });

  it('replaces every occurrence, not just the first', () => {
    // LingKuma expands its prompts with String.replace, which stops after one;
    // a template that mentions {text} twice needs both filled.
    const url = expandCustomUrl('https://x.test/{lang}/{text}?b={text}', 'a b', 'de');
    expect(url).toBe('https://x.test/de/a%20b?b=a%20b');
  });

  it('leaves a template without placeholders alone', () => {
    expect(expandCustomUrl('https://x.test/fixed.mp3', 'ignored', 'en')).toBe(
      'https://x.test/fixed.mp3',
    );
  });
});
