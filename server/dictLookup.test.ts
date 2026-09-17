import { describe, expect, it } from 'vitest';
import { isDictionaryAvailable, lookupWord, normalizeWord } from './dictLookup.ts';

/**
 * GET /api/dict reads ECDICT through server/dictLookup.ts. These tests pin the
 * contract the route relies on:
 *
 *   - 查不到 → null（路由转 404），绝不抛异常
 *   - 大小写、首尾标点、弯撇号、空格差异都能命中
 *   - 变形词给出原形、变形类型，并用原形的音标补全
 *
 * data/stardict.db 约 850MB，通常不随代码一起分发，因此依赖它的用例在文件
 * 缺失时整体跳过（normalizeWord 是纯函数，始终运行）。
 */
const dictionaryPresent = isDictionaryAvailable();

describe('normalizeWord', () => {
  it('trims, lowercases and drops surrounding punctuation', () => {
    expect(normalizeWord('  Very ')).toBe('very');
    expect(normalizeWord('"Hello,"')).toBe('hello');
    expect(normalizeWord('(run)')).toBe('run');
  });

  it('keeps inner apostrophes and hyphens, normalises curly ones', () => {
    expect(normalizeWord("Don't")).toBe("don't");
    expect(normalizeWord('don\u2019t')).toBe("don't");
    expect(normalizeWord('well-known')).toBe('well-known');
  });

  it('collapses inner whitespace and drops punctuation-only input', () => {
    expect(normalizeWord('ice   cream')).toBe('ice cream');
    expect(normalizeWord('???')).toBe('');
    expect(normalizeWord('   ')).toBe('');
  });
});

describe.skipIf(!dictionaryPresent)('lookupWord against data/stardict.db', () => {
  it('returns phonetic, Chinese/English definitions, part of speech and exam tags', () => {
    const entry = lookupWord('very');

    expect(entry).not.toBeNull();
    expect(entry?.word).toBe('very');
    expect(entry?.matchedBy).toBe('word');
    expect(entry?.phonetic).toBeTruthy();
    expect(entry?.translation).toContain('非常');
    expect(entry?.definition).toBeTruthy();
    expect(entry?.partsOfSpeech[0]).toMatchObject({ code: 'r', label: '副词', abbr: 'adv.' });
    expect(entry?.partsOfSpeech[0]?.percent).toBeGreaterThan(0);
    expect(entry?.tags.map((tag) => tag.label)).toEqual(expect.arrayContaining(['中考', '高考']));
  });

  it('ignores case and surrounding punctuation', () => {
    expect(lookupWord('  "Very," ')?.word).toBe('very');
  });

  it('falls back to the separator-free index when the spelling differs', () => {
    const entry = lookupWord('a couchpotato');

    expect(entry?.matchedBy).toBe('sw');
    expect(entry?.word).toBe('a couch potato');
    expect(entry?.translation).toBeTruthy();
  });

  it('reports the lemma and inflection of an inflected form', () => {
    const running = lookupWord('running');

    expect(running?.word).toBe('running');
    expect(running?.lemma).toBe('run');
    expect(running?.inflection).toBe('现在分词');
    expect(running?.forms.some((form) => form.code === '0')).toBe(false);
  });

  it('borrows the lemma phonetic when the inflected entry has none', () => {
    const runs = lookupWord('runs');

    expect(runs?.lemma).toBe('run');
    expect(runs?.phonetic).toBe(lookupWord('run')?.phonetic);
    expect(runs?.phonetic).toBeTruthy();
  });

  it('expands the exchange column into readable forms', () => {
    const entry = lookupWord('abandon');

    expect(entry?.forms).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: 'p', label: '过去式', words: ['abandoned'] }),
        expect.objectContaining({ code: 'i', label: '现在分词', words: ['abandoning'] }),
      ]),
    );
    expect(entry?.tags.map((tag) => tag.code)).toEqual(expect.arrayContaining(['cet4', 'cet6']));
  });

  it('answers null (not an error) for words outside the dictionary', () => {
    expect(lookupWord('zzzzqq')).toBeNull();
    expect(lookupWord('???')).toBeNull();
    expect(lookupWord('')).toBeNull();
  });
});
