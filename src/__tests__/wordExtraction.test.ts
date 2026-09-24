import { describe, it, expect } from 'vitest';
import { extractWords } from '../utils/wordExtraction';

describe('Extracting lookup candidates from a sentence', () => {
  it('splits a sentence into words and drops the punctuation', () => {
    const words = extractWords('Screens are ubiquitous, and that is that.');
    expect(words.map((word) => word.clean)).toEqual([
      'screens',
      'are',
      'ubiquitous',
      'and',
      'that',
      'is',
    ]);
  });

  it('de-duplicates case-insensitively but keeps the first spelling', () => {
    const words = extractWords('Alice saw alice and ALICE again.');

    expect(words.filter((word) => word.clean === 'alice')).toHaveLength(1);
    // The panel should read "Alice", not a lower-cased version of it.
    expect(words.find((word) => word.clean === 'alice')?.raw).toBe('Alice');
  });

  it('keeps contractions and hyphenated words whole', () => {
    const clean = extractWords("He didn't like the well-known rabbit-hole.").map((w) => w.clean);
    expect(clean).toContain("didn't");
    expect(clean).toContain('well-known');
    expect(clean).toContain('rabbit-hole');
  });

  it('drops single letters, bare numbers and symbols', () => {
    // "a" and "I" are grammar, not vocabulary; 42 and --- are not words at all.
    const clean = extractWords('I saw a cat, 42 times --- really!').map((word) => word.clean);
    expect(clean).toEqual(['saw', 'cat', 'times', 'really']);
  });

  it('keeps accented words intact, so non-English text is usable', () => {
    const clean = extractWords('Le café était naïve à Paris.').map((word) => word.clean);
    expect(clean).toContain('café');
    expect(clean).toContain('naïve');
  });

  it('strips surrounding quotes so the word still matches its vocabulary key', () => {
    // The tokenizer strips edge apostrophes when it files a word, so the
    // extraction has to strip them too or the lookup misses.
    const clean = extractWords("'twas the night").map((word) => word.clean);
    expect(clean).toContain('twas');
  });

  it('answers nothing for blank input', () => {
    expect(extractWords('')).toEqual([]);
    expect(extractWords('   ')).toEqual([]);
    expect(extractWords('...')).toEqual([]);
  });
});
