import { describe, it, expect, beforeEach } from 'vitest';
import { tokenizeText, extractWordsFromTokens, paginateText } from '../utils/tokenizer';
import { useVocabularyStore } from '../store/useVocabularyStore';

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

describe('Vocabulary Store & Auto-Known Page Turn Logic', () => {
  beforeEach(() => {
    useVocabularyStore.getState().clearVocabulary();
  });

  it('marks a word as learning and persists to state', () => {
    const store = useVocabularyStore.getState();
    store.markLearning('ubiquitous');

    expect(useVocabularyStore.getState().getWordStatus('ubiquitous')).toBe('learning');
    expect(useVocabularyStore.getState().getWordStatus('UBIQUITOUS')).toBe('learning');
  });

  it('marks a word as known', () => {
    const store = useVocabularyStore.getState();
    store.markKnown('ephemeral');

    expect(useVocabularyStore.getState().getWordStatus('ephemeral')).toBe('known');
  });

  it('auto-marks unmarked words as known when turning page, but preserves learning words', () => {
    const store = useVocabularyStore.getState();

    // User clicked 'curiosity' during reading
    store.markLearning('curiosity');

    // Current page contains: 'alice', 'curiosity', 'wonderland', 'rabbit'
    const pageWords = ['alice', 'curiosity', 'wonderland', 'rabbit'];

    const newlyMarked = store.markPageWordsAsKnown(pageWords);

    // Should have marked 'alice', 'wonderland', 'rabbit' (3 words) as known
    expect(newlyMarked).toBe(3);

    const currentWords = useVocabularyStore.getState().words;
    expect(currentWords['curiosity']).toBe('learning'); // Must remain learning!
    expect(currentWords['alice']).toBe('known');
    expect(currentWords['wonderland']).toBe('known');
    expect(currentWords['rabbit']).toBe('known');
  });

  it('removes a word from vocabulary completely', () => {
    const store = useVocabularyStore.getState();
    store.markLearning('temporary');
    expect(store.getWordStatus('temporary')).toBe('learning');

    store.removeWord('temporary');
    expect(store.getWordStatus('temporary')).toBe('unknown');
  });
});
