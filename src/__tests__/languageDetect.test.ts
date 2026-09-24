import { describe, it, expect } from 'vitest';
import { detectLanguage } from '../services/languageDetect';
import { bionicSplit, bionicBoldLength, isBionicEligible } from '../utils/bionic';

describe('Language detection: non-spaced scripts', () => {
  it('tells Japanese, Chinese and Korean apart', () => {
    expect(detectLanguage('これは日本語の文章です。').language).toBe('ja');
    expect(detectLanguage('这是一段中文文本。').language).toBe('zh');
    expect(detectLanguage('이것은 한국어 문장입니다.').language).toBe('ko');
  });

  /**
   * Japanese prose always carries kana, so kanji alone means Chinese. Checking
   * kana first is what keeps a Japanese sentence out of the Chinese bucket.
   */
  it('reads mixed kanji-and-kana text as Japanese, not Chinese', () => {
    expect(detectLanguage('彼は本を読んでいる。').language).toBe('ja');
  });

  it('is confident about the scripts it recognises', () => {
    expect(detectLanguage('これは日本語です').confidence).toBeGreaterThan(0.9);
  });
});

describe('Language detection: other scripts', () => {
  it('recognises Cyrillic, Greek, Arabic and Thai', () => {
    expect(detectLanguage('Привет, как дела?').language).toBe('ru');
    expect(detectLanguage('Καλημέρα, τι κάνεις;').language).toBe('el');
    expect(detectLanguage('مرحبا كيف حالك').language).toBe('ar');
    expect(detectLanguage('สวัสดีครับ').language).toBe('th');
  });

  it('separates Latin-script languages by their function words', () => {
    expect(
      detectLanguage(
        'The rabbit was not in the garden, and it was his hat that she took from the table.',
      ).language,
    ).toBe('en');

    expect(
      detectLanguage(
        'Der Mann ist nicht mit dem Hund in das Haus gegangen, und ich habe auch nicht die Frau gesehen.',
      ).language,
    ).toBe('de');

    expect(
      detectLanguage(
        'Le chat est dans la maison et il ne pas vous voir, mais nous sommes sur la table avec une lampe.',
      ).language,
    ).toBe('fr');

    expect(
      detectLanguage(
        'El libro está en la mesa y no se puede ver, pero los niños con su madre para una tarde.',
      ).language,
    ).toBe('es');
  });

  it('reports low confidence for a short or ambiguous sample', () => {
    // One short sentence is not enough evidence to put a label on a book.
    expect(detectLanguage('Hello there.').confidence).toBeLessThan(0.9);
    expect(detectLanguage('').confidence).toBe(0);
  });

  it('always answers a usable language, even for text it cannot place', () => {
    expect(detectLanguage('xyzzy plugh').language).toBe('en');
    expect(detectLanguage('   ').language).toBe('en');
    expect(detectLanguage('1234 5678').language).toBe('en');
  });
});

describe('Bionic reading', () => {
  /** LingKuma's rule: 40% of the word, rounded up. */
  it('bolds 40% of the word, rounded up', () => {
    expect(bionicBoldLength('a')).toBe(1);
    expect(bionicBoldLength('the')).toBe(2);
    expect(bionicBoldLength('hello')).toBe(2);
    expect(bionicBoldLength('reader')).toBe(3);
    expect(bionicBoldLength('ubiquitous')).toBe(4);
  });

  it('splits a word into the bold head and the rest', () => {
    expect(bionicSplit('reading')).toEqual({ bold: 'rea', rest: 'ding' });
    expect(bionicSplit('cat')).toEqual({ bold: 'ca', rest: 't' });
  });

  it('never loses characters', () => {
    for (const word of ['a', 'the', 'hello', 'ubiquitous', 'antidisestablishmentarianism']) {
      const { bold, rest } = bionicSplit(word);
      expect(bold + rest).toBe(word);
    }
  });

  it('leaves CJK alone, since a bold first character means nothing there', () => {
    expect(bionicSplit('日本語')).toEqual({ bold: '', rest: '日本語' });
    expect(bionicSplit('中文')).toEqual({ bold: '', rest: '中文' });
    expect(isBionicEligible('hello')).toBe(true);
    expect(isBionicEligible('日本語')).toBe(false);
  });

  it('counts accented letters as one character each, not as bytes', () => {
    // "café" is four characters (é is one UTF-16 unit), so ceil(4 * 0.4) = 2.
    // Counting UTF-8 bytes would make it five and bold one character too many.
    expect('café'.length).toBe(4);
    expect(bionicSplit('café')).toEqual({ bold: 'ca', rest: 'fé' });
  });

  it('does nothing for an empty word', () => {
    expect(bionicSplit('')).toEqual({ bold: '', rest: '' });
    expect(isBionicEligible('')).toBe(false);
  });
});
