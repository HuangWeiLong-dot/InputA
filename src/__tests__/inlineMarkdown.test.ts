import { describe, it, expect } from 'vitest';
import { parseInlineMarkdown, splitOnWord } from '../utils/inlineMarkdown';

describe('Inline markdown parsing', () => {
  it('splits bold and code out of the surrounding text', () => {
    expect(parseInlineMarkdown('屏幕**无处不在**，见 `ubiquitous` 一词。')).toEqual([
      { kind: 'text', text: '屏幕' },
      { kind: 'bold', text: '无处不在' },
      { kind: 'text', text: '，见 ' },
      { kind: 'code', text: 'ubiquitous' },
      { kind: 'text', text: ' 一词。' },
    ]);
  });

  it('passes plain text through as a single token', () => {
    expect(parseInlineMarkdown('普通译文')).toEqual([{ kind: 'text', text: '普通译文' }]);
  });

  it('leaves malformed markers alone rather than eating the text', () => {
    // Unbalanced or empty markers must not swallow the surrounding characters.
    expect(parseInlineMarkdown('a ** b')).toEqual([{ kind: 'text', text: 'a ** b' }]);
    expect(parseInlineMarkdown('****')).toEqual([{ kind: 'text', text: '****' }]);
    expect(parseInlineMarkdown('a *b* c')).toEqual([{ kind: 'text', text: 'a *b* c' }]);
  });

  it('handles an empty string', () => {
    expect(parseInlineMarkdown('')).toEqual([]);
  });
});

describe('Whole-word splitting for the looked-up word', () => {
  it('marks the target word and leaves the rest alone', () => {
    expect(splitOnWord('Screens are ubiquitous today.', 'ubiquitous')).toEqual([
      { text: 'Screens are ', match: false },
      { text: 'ubiquitous', match: true },
      { text: ' today.', match: false },
    ]);
  });

  it('matches whatever casing the sentence happens to use', () => {
    const parts = splitOnWord('Ubiquitous screens.', 'ubiquitous');
    expect(parts[0]).toEqual({ text: 'Ubiquitous', match: true });
  });

  it('does not match inside a longer word', () => {
    // "run" must not light up inside "running".
    expect(splitOnWord('They were running.', 'run')).toEqual([
      { text: 'They were running.', match: false },
    ]);
  });

  it('matches a hyphenated or apostrophised word as a whole', () => {
    // \b would break these at the hyphen / apostrophe.
    expect(splitOnWord('A well-known fact.', 'well-known')[1]).toEqual({
      text: 'well-known',
      match: true,
    });
    expect(splitOnWord('He didn’t go.', "didn't")[1]).toEqual({
      text: 'didn’t',
      match: true,
    });
  });

  it('handles accents, so non-English sentences still mark the word', () => {
    expect(splitOnWord('Le café est ouvert.', 'café')[1]).toEqual({ text: 'café', match: true });
  });

  it('returns the text untouched when there is no word to mark', () => {
    expect(splitOnWord('Some sentence.', '')).toEqual([{ text: 'Some sentence.', match: false }]);
    expect(splitOnWord('Some sentence.', 'absent')).toEqual([
      { text: 'Some sentence.', match: false },
    ]);
  });

  it('treats regex metacharacters in the word as literal text', () => {
    // A word straight out of a dictionary can contain characters that would
    // otherwise change the pattern.
    const parts = splitOnWord('Use a (b) here.', '(b)');
    expect(parts.some((part) => part.match)).toBe(true);
  });
});
