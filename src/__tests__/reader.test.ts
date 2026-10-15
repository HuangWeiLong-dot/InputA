import { describe, it, expect, beforeEach, vi } from 'vitest';
import { tokenizeText, extractWordsFromTokens, paginateText } from '../utils/tokenizer';
import { useVocabularyStore } from '../store/useVocabularyStore';
import { normalizeWordStatus, highlightLevel, isUnknown } from '../utils/wordLevel';

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

    // Only the two never-seen words are filed, and they are named in the result
    // so the caller can offer to undo exactly those two.
    expect(newlyMarked).toEqual(['alice', 'rabbit']);

    const words = useVocabularyStore.getState().words;
    expect(words['curiosity']).toBe(5); // still "生词", the reader clicked it
    expect(words['wonderland']).toBe(2); // the reader's own judgement wins
    expect(words['alice']).toBe('mastered');
    expect(words['rabbit']).toBe('mastered');
  });

  it('leaves already-mastered words alone and ignores single letters', () => {
    const store = useVocabularyStore.getState();
    store.markMastered('already');

    expect(store.markPageWordsAsMastered(['already', 'a', 'I', 'b'])).toEqual([]);
    expect(useVocabularyStore.getState().words['already']).toBe('mastered');
  });

  /**
   * The undo behind 「完成阅读」. What it must NOT do is delete a word the reader
   * has since given a level of its own - that is a newer judgement than the one
   * being undone.
   */
  it('undoes a page turn without touching a word the reader has re-levelled', () => {
    const store = useVocabularyStore.getState();
    const filed = store.markPageWordsAsMastered(['alice', 'rabbit', 'hatter']);
    expect(filed).toEqual(['alice', 'rabbit', 'hatter']);

    // After the turn, the reader decides 'hatter' is not a 掌握 word after all.
    store.setWordLevel('hatter', 2);

    store.undoMasteredWords(filed);

    const words = useVocabularyStore.getState().words;
    expect(words['alice']).toBeUndefined();
    expect(words['rabbit']).toBeUndefined();
    expect(words['hatter']).toBe(2); // survived: the reader's later choice wins
    expect(store.getWordStatus('alice')).toBe('unknown');
  });

  it('never files a word, and is safe to call twice', () => {
    const store = useVocabularyStore.getState();
    const filed = store.markPageWordsAsMastered(['alice', 'rabbit']);

    store.undoMasteredWords(filed);
    // A second undo (the reader pressed 撤销, or an old batch is replayed) must
    // not write anything back.
    store.undoMasteredWords(filed);

    expect(useVocabularyStore.getState().words).toEqual({});
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

/**
 * What the text paints, as opposed to what the store records.
 *
 * The store's three states map onto three different looks: a level gets its
 * colour, a mastered word gets nothing at all, and an uncollected word gets a
 * neutral dashed underline (WORD_UNFILED) and no fill. The last two both report
 * "no fill" here, which is exactly why `isUnknown` exists alongside this — a
 * caller that only had `highlightLevel` could not tell them apart, and an
 * uncollected word used to borrow 生词's colour and look filed.
 *
 * An uncollected word is still not filed by any of this: the page-turn rule
 * keeps seeing it as uncollected, which is what the last test pins.
 */
describe('Highlight level', () => {
  it('leaves a word that was never collected without a fill', () => {
    expect(highlightLevel('unknown')).toBeNull();
  });

  it('paints a collected word with the level it was given', () => {
    expect(highlightLevel(1)).toBe(1);
    expect(highlightLevel(3)).toBe(3);
    expect(highlightLevel(5)).toBe(5);
  });

  it('leaves a mastered word unhighlighted', () => {
    expect(highlightLevel('mastered')).toBeNull();
  });

  /** Both report no fill; only `isUnknown` says which one is which. */
  it('tells an uncollected word apart from a mastered one', () => {
    expect(isUnknown('unknown')).toBe(true);
    expect(isUnknown('mastered')).toBe(false);
    expect(isUnknown(3)).toBe(false);
  });

  it('does not file anything — the word stays uncollected to every other reader', () => {
    const before = { ...useVocabularyStore.getState().words };
    highlightLevel('unknown');
    isUnknown('unknown');
    expect(useVocabularyStore.getState().words).toEqual(before);
  });
});
