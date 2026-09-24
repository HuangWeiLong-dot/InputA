import { describe, it, expect, beforeEach, vi } from 'vitest';
import { tokenizeText, extractWordsFromTokens, paginateText } from '../utils/tokenizer';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { normalizeWordStatus } from '../utils/wordLevel';

describe('Tokenizer Utility', () => {
  it('correctly splits text into words, punctuation, and contractions', () => {
    const text = "Alice didn't like the well-known rabbit-hole, did she?";
    const tokens = tokenizeText(text);

    const words = tokens.filter((t) => t.isWord).map((t) => t.cleanWord);
    expect(words).toContain('alice');
    expect(words).toContain("didn't");
    expect(words).toContain('like');
    expect(words).toContain('the');
    expect(words).toContain('well-known');
    expect(words).toContain('rabbit-hole');
    expect(words).toContain('did');
    expect(words).toContain('she');
  });

  it('extracts unique lowercase words from tokens', () => {
    const text = 'The rabbit and the other rabbit were running.';
    const tokens = tokenizeText(text);
    const unique = extractWordsFromTokens(tokens);

    expect(unique).toContain('the');
    expect(unique).toContain('rabbit');
    expect(unique).toContain('and');
    expect(unique).toContain('other');
    expect(unique).toContain('were');
    expect(unique).toContain('running');
    // Ensure case insensitivity and deduplication
    expect(unique.filter((w) => w === 'the').length).toBe(1);
    expect(unique.filter((w) => w === 'rabbit').length).toBe(1);
  });

  it('paginates text into comfortable chunks while keeping paragraphs intact', () => {
    const paragraph1 = 'A '.repeat(150);
    const paragraph2 = 'B '.repeat(150);
    const fullText = `${paragraph1}\n\n${paragraph2}`;

    const pages = paginateText(fullText, 100);
    expect(pages.length).toBeGreaterThanOrEqual(2);
    expect(pages[0]).toContain('A');
    expect(pages[1]).toContain('B');
  });
});

/**
 * The word regex used to be [a-zA-Z0-9], which split "café" into "caf" + "é" — so
 * a French or German book had almost no whole words left to click, and clicking
 * filed fragments as vocabulary.
 */
describe('Tokenizer: words outside ASCII', () => {
  const wordsIn = (text: string) =>
    tokenizeText(text)
      .filter((token) => token.isWord)
      .map((token) => token.cleanWord);

  it('keeps accented Latin words whole', () => {
    expect(wordsIn('Le café était naïf.')).toEqual(['le', 'café', 'était', 'naïf']);
    expect(wordsIn('Über die Brücke.')).toEqual(['über', 'die', 'brücke']);
  });

  it('keeps other spaced scripts whole too', () => {
    expect(wordsIn('Привет мир')).toEqual(['привет', 'мир']);
    expect(wordsIn('Καλημέρα κόσμε')).toEqual(['καλημέρα', 'κόσμε']);
  });

  /**
   * CJK has no spaces, so there is no honest place to cut a "word" without a real
   * segmenter — and the page-turn rule would file whole sentences as vocabulary.
   * Leaving them unclickable is the deliberate trade-off.
   */
  it('does not treat CJK runs as clickable words', () => {
    expect(wordsIn('hello 你好 world')).toEqual(['hello', 'world']);
    expect(wordsIn('日本語のテキスト')).toEqual([]);
    expect(wordsIn('한국어 텍스트')).toEqual([]);
  });
});

describe('Vocabulary Store: levels and the page-turn rule', () => {
  beforeEach(() => {
    useVocabularyStore.getState().clearVocabulary();
  });

  it('stores a manually picked proficiency level, case-insensitively', () => {
    const store = useVocabularyStore.getState();
    store.setWordLevel('ubiquitous', 3);

    expect(useVocabularyStore.getState().getWordStatus('ubiquitous')).toBe(3);
    expect(useVocabularyStore.getState().getWordStatus('UBIQUITOUS')).toBe(3);
  });

  it('files a clicked word as 生词 only when it is not already filed', () => {
    const store = useVocabularyStore.getState();

    store.markAsNewWord('ubiquitous');
    expect(useVocabularyStore.getState().words['ubiquitous']).toBe(5);

    // Calling it again changes nothing, and the lookup is case-insensitive.
    store.markAsNewWord('UBIQUITOUS');
    expect(useVocabularyStore.getState().words['ubiquitous']).toBe(5);

    // A level the reader picked by hand survives a click in the text.
    store.setWordLevel('wonderland', 2);
    store.markAsNewWord('wonderland');
    expect(useVocabularyStore.getState().words['wonderland']).toBe(2);

    // So does 已掌握: clicking must not resurrect a mastered word as 生词.
    store.markMastered('alice');
    store.markAsNewWord('alice');
    expect(useVocabularyStore.getState().words['alice']).toBe('mastered');
  });

  it('marks a word as mastered', () => {
    const store = useVocabularyStore.getState();
    store.markMastered('ephemeral');

    expect(useVocabularyStore.getState().getWordStatus('ephemeral')).toBe('mastered');
  });

  it('files unclicked page words as mastered, and never overwrites a level', () => {
    const store = useVocabularyStore.getState();

    // Clicked during reading → level 5; levelled by hand → level 2.
    store.setWordLevel('curiosity', 5);
    store.setWordLevel('wonderland', 2);

    const pageWords = ['alice', 'curiosity', 'wonderland', 'rabbit'];
    const newlyMarked = store.markPageWordsAsMastered(pageWords);

    // Only the two never-seen words are filed.
    expect(newlyMarked).toBe(2);

    const words = useVocabularyStore.getState().words;
    expect(words['curiosity']).toBe(5); // still "生词", the reader clicked it
    expect(words['wonderland']).toBe(2); // the reader's own judgement wins
    expect(words['alice']).toBe('mastered');
    expect(words['rabbit']).toBe('mastered');
  });

  it('leaves already-mastered words alone and ignores single letters', () => {
    const store = useVocabularyStore.getState();
    store.markMastered('already');

    expect(store.markPageWordsAsMastered(['already', 'a', 'I', 'b'])).toBe(0);
    expect(useVocabularyStore.getState().words['already']).toBe('mastered');
  });

  it('removes a word from vocabulary completely', () => {
    const store = useVocabularyStore.getState();
    store.setWordLevel('temporary', 4);
    expect(store.getWordStatus('temporary')).toBe(4);

    store.removeWord('temporary');
    expect(store.getWordStatus('temporary')).toBe('unknown');
  });
});

/**
 * The two-state vocabulary ('learning' / 'known') shipped before the 5-level
 * scale, so saved data has to be migrated rather than silently dropped.
 */
describe('Word status migration', () => {
  it('maps the old two-state values onto the levels they meant', () => {
    expect(normalizeWordStatus('learning')).toBe(5); // clicked → 生词
    expect(normalizeWordStatus('known')).toBe('mastered'); // page turn → 掌握
  });

  it('passes through current values and rejects anything else', () => {
    expect(normalizeWordStatus(1)).toBe(1);
    expect(normalizeWordStatus(5)).toBe(5);
    expect(normalizeWordStatus('mastered')).toBe('mastered');
    expect(normalizeWordStatus(0)).toBeNull();
    expect(normalizeWordStatus(6)).toBeNull();
    expect(normalizeWordStatus(2.5)).toBeNull();
    expect(normalizeWordStatus(undefined)).toBeNull();
    expect(normalizeWordStatus({ level: 3 })).toBeNull();
  });

  /**
   * The end-to-end version of the above: a word list actually sitting in
   * localStorage, read back by a freshly loaded store. This is the case that
   * silently empties a real reader's vocabulary if the migration regresses.
   */
  it('loads a word list saved by the two-state version without losing words', async () => {
    localStorage.setItem(
      'language_reader_vocabulary',
      JSON.stringify({ words: { curiosity: 'learning', alice: 'known', wonderland: 3 } }),
    );

    vi.resetModules();
    const { useVocabularyStore: freshStore } = await import('../store/useVocabularyStore');

    expect(freshStore.getState().words).toEqual({
      curiosity: 5, // was clicked → 生词
      alice: 'mastered', // was auto-filed → 掌握
      wonderland: 3, // already a level, untouched
    });
  });

  it('skips unrecognizable entries and keeps the rest', async () => {
    localStorage.setItem(
      'language_reader_vocabulary',
      JSON.stringify({ words: { junk: 'nonsense', good: 2 } }),
    );

    vi.resetModules();
    const { useVocabularyStore: freshStore } = await import('../store/useVocabularyStore');

    expect(freshStore.getState().words).toEqual({ good: 2 });
  });
});
