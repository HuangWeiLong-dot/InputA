import { describe, it, expect } from 'vitest';
import { buildBackup, isEmptyBackup, parseBackup } from '../utils/backup';
import type { BackupData } from '../utils/backup';

const SAMPLE: BackupData = {
  words: { ubiquitous: 3, run: 'mastered', curiosity: 5 },
  notes: { ubiquitous: ['到处都是', '无处不在'] },
  sentences: {
    ubiquitous: [
      { sentence: 'Screens are ubiquitous.', translation: '屏幕**无处不在**。', createdAt: 1 },
    ],
  },
};

describe('Backup round trip', () => {
  it('writes a versioned file and reads it back unchanged', () => {
    const file = buildBackup(SAMPLE, '2026-09-24T00:00:00.000Z');
    expect(file.version).toBe(2);
    expect(file.exportedAt).toBe('2026-09-24T00:00:00.000Z');

    // Through JSON, exactly as a downloaded file would travel.
    const restored = parseBackup(JSON.parse(JSON.stringify(file)));
    expect(restored).toEqual(SAMPLE);
  });

  it('keeps the three data sets separate', () => {
    const restored = parseBackup(buildBackup(SAMPLE, 'now'));
    expect(Object.keys(restored?.words ?? {})).toHaveLength(3);
    expect(restored?.notes.ubiquitous).toEqual(['到处都是', '无处不在']);
    expect(restored?.sentences.ubiquitous?.[0].translation).toBe('屏幕**无处不在**。');
  });
});

describe('Backup compatibility', () => {
  /**
   * The old export wrote the bare map, while localStorage wrapped the same map in
   * {words: {...}}. Both historically existed, so both have to import.
   */
  it('reads a v1 bare map', () => {
    const restored = parseBackup({ ubiquitous: 3, alice: 'known' });
    expect(restored?.words).toEqual({ ubiquitous: 3, alice: 'mastered' });
    expect(restored?.notes).toEqual({});
    expect(restored?.sentences).toEqual({});
  });

  it('reads a v1 wrapped payload, as found in localStorage', () => {
    const restored = parseBackup({ words: { curiosity: 'learning', wonderland: 2 } });
    expect(restored?.words).toEqual({ curiosity: 5, wonderland: 2 });
    expect(restored?.notes).toEqual({});
  });

  it('keeps importing a v2 file that lost its annotations', () => {
    const restored = parseBackup({ version: 2, words: { run: 1 } });
    expect(restored?.words).toEqual({ run: 1 });
    expect(restored?.sentences).toEqual({});
  });

  it('drops unreadable entries and keeps the rest', () => {
    const restored = parseBackup({
      version: 2,
      words: { good: 2, junk: 'nonsense', worse: { level: 3 } },
      notes: { run: ['跑', 42, null], broken: 'not an array' },
      sentences: { run: [{ sentence: 'He runs.' }, { nothing: true }, 'nope'] },
    });

    expect(restored?.words).toEqual({ good: 2 });
    expect(restored?.notes).toEqual({ run: ['跑'] });
    expect(restored?.sentences).toEqual({ run: [{ sentence: 'He runs.' }] });
  });

  it('rejects things that are not backups at all', () => {
    expect(parseBackup(null)).toBeNull();
    expect(parseBackup('a string')).toBeNull();
    expect(parseBackup([1, 2, 3])).toBeNull();
    expect(parseBackup(42)).toBeNull();
  });

  /**
   * Without the version guard this file would be read as the bare map
   * `{version: 2}` — one bogus word named "version", which importing would then
   * use to replace the reader's entire vocabulary.
   */
  it('rejects a versioned file with no word list', () => {
    expect(parseBackup({ version: 2 })).toBeNull();
    expect(parseBackup({ version: 2, words: 'not an object' })).toBeNull();
  });
});

describe('Empty backup detection', () => {
  it('flags a backup with nothing in it', () => {
    // Importing this would silently wipe the reader's vocabulary.
    expect(isEmptyBackup({ words: {}, notes: {}, sentences: {} })).toBe(true);

    const parsed = parseBackup({ words: {} });
    expect(parsed).not.toBeNull();
    expect(isEmptyBackup(parsed as BackupData)).toBe(true);
  });

  it('does not flag a backup that carries any of the three sets', () => {
    expect(isEmptyBackup(SAMPLE)).toBe(false);
    expect(isEmptyBackup({ words: {}, notes: { run: ['跑'] }, sentences: {} })).toBe(false);
    expect(isEmptyBackup({ words: { run: 5 }, notes: {}, sentences: {} })).toBe(false);
  });
});
