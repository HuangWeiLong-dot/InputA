import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useAnnotationStore } from '../store/useAnnotationStore';
import { useVocabularyStore } from '../store/useVocabularyStore';
import {
  normalizeAnnotations,
  normalizeNoteList,
  normalizeSavedSentence,
  normalizeSentenceList,
} from '../utils/annotations';

describe('Annotation store: notes', () => {
  beforeEach(() => {
    useAnnotationStore.getState().clearAnnotations();
    localStorage.clear();
  });

  it('adds a note, trimming it and keying the word case-insensitively', () => {
    const store = useAnnotationStore.getState();
    store.addNote('Ubiquitous', ' 到处都是的  ');

    // The word key is normalised, so lookups work whatever the casing.
    expect(useAnnotationStore.getState().getNotes('ubiquitous')).toEqual(['到处都是的']);
    expect(useAnnotationStore.getState().getNotes('UBIQUITOUS')).toEqual(['到处都是的']);
  });

  it('keeps several distinct notes and ignores blanks', () => {
    const store = useAnnotationStore.getState();
    store.addNote('run', '跑');
    store.addNote('run', '经营（a business）');
    store.addNote('run', '   ');
    store.addNote('run', '');

    expect(useAnnotationStore.getState().getNotes('run')).toEqual(['跑', '经营（a business）']);
  });

  it('does not add the same note twice', () => {
    const store = useAnnotationStore.getState();
    store.addNote('run', '跑');
    store.addNote('run', '跑');

    expect(useAnnotationStore.getState().getNotes('run')).toEqual(['跑']);
  });

  it('removes one note and drops the key when the last one goes', () => {
    const store = useAnnotationStore.getState();
    store.addNote('run', '跑');
    store.addNote('run', '经营');

    store.removeNote('run', '跑');
    expect(useAnnotationStore.getState().getNotes('run')).toEqual(['经营']);

    store.removeNote('run', '经营');
    expect(useAnnotationStore.getState().notes['run']).toBeUndefined();
  });
});

describe('Annotation store: example sentences', () => {
  beforeEach(() => {
    useAnnotationStore.getState().clearAnnotations();
    localStorage.clear();
  });

  it('saves a sentence and de-duplicates by the sentence text', () => {
    const store = useAnnotationStore.getState();
    store.addSentence('alice', { sentence: 'Alice was bored.', createdAt: 1 });
    store.addSentence('alice', { sentence: 'Alice was bored.', createdAt: 2 });

    expect(useAnnotationStore.getState().getSentences('alice')).toEqual([
      { sentence: 'Alice was bored.', createdAt: 1 },
    ]);
  });

  it('fills in a translation that arrives after the sentence was saved', () => {
    const store = useAnnotationStore.getState();
    // Saving and translating run in parallel, so the translation can land later.
    // It has to be merged in, not dropped as a duplicate.
    store.addSentence('alice', { sentence: 'Alice was bored.', createdAt: 1 });
    store.addSentence('alice', {
      sentence: 'Alice was bored.',
      translation: '爱丽丝很无聊。',
      createdAt: 2,
    });

    expect(useAnnotationStore.getState().getSentences('alice')).toEqual([
      { sentence: 'Alice was bored.', translation: '爱丽丝很无聊。', createdAt: 1 },
    ]);
  });

  it('does not overwrite a translation that is already stored', () => {
    const store = useAnnotationStore.getState();
    store.addSentence('alice', { sentence: 'S.', translation: '原译文' });
    store.addSentence('alice', { sentence: 'S.', translation: '新译文' });

    expect(useAnnotationStore.getState().getSentences('alice')[0].translation).toBe('原译文');
  });

  it('keeps the provenance of a sentence and removes it on request', () => {
    const store = useAnnotationStore.getState();
    store.addSentence('alice', {
      sentence: 'Down the rabbit-hole.',
      bookId: 'alice-in-wonderland',
      bookTitle: "Alice's Adventures in Wonderland",
      chapterIndex: 0,
      pageIndex: 2,
      createdAt: 1,
    });

    const [saved] = useAnnotationStore.getState().getSentences('alice');
    expect(saved.bookTitle).toBe("Alice's Adventures in Wonderland");
    expect(saved.pageIndex).toBe(2);

    store.removeSentence('alice', 'Down the rabbit-hole.');
    expect(useAnnotationStore.getState().getSentences('alice')).toEqual([]);
  });
});

describe('Annotation store: independent from the vocabulary', () => {
  beforeEach(() => {
    useAnnotationStore.getState().clearAnnotations();
    useVocabularyStore.getState().clearVocabulary();
    localStorage.clear();
  });

  /**
   * The load-bearing regression: proficiency values must stay scalars, and the
   * annotations must never be written into the vocabulary's storage key. If the
   * two ever share a key, normalizeWordMap drops every word on next load.
   */
  it('writes annotations to their own key and leaves the vocabulary untouched', () => {
    useVocabularyStore.getState().setWordLevel('ubiquitous', 3);
    useAnnotationStore.getState().addNote('ubiquitous', '到处都是');
    useAnnotationStore.getState().addSentence('ubiquitous', { sentence: 'S.' });

    const vocabulary = JSON.parse(localStorage.getItem('language_reader_vocabulary') || '{}');
    expect(vocabulary.words).toEqual({ ubiquitous: 3 });

    const annotations = JSON.parse(localStorage.getItem('language_reader_annotations') || '{}');
    expect(annotations.notes).toEqual({ ubiquitous: ['到处都是'] });
    expect(Object.keys(annotations.sentences)).toEqual(['ubiquitous']);
  });

  it('drops a word\'s annotations together with the word', () => {
    const words = useVocabularyStore.getState();
    const annotations = useAnnotationStore.getState();

    words.setWordLevel('temporary', 4);
    annotations.addNote('temporary', '暂时的');
    annotations.addSentence('temporary', { sentence: 'S.' });

    // The caller pairs the two (see VocabularyModal); the stores stay independent.
    words.removeWord('temporary');
    annotations.removeWordAnnotations('temporary');

    expect(useVocabularyStore.getState().getWordStatus('temporary')).toBe('unknown');
    expect(useAnnotationStore.getState().notes['temporary']).toBeUndefined();
    expect(useAnnotationStore.getState().sentences['temporary']).toBeUndefined();
  });

  it('leaves the other words alone when clearing one word', () => {
    const annotations = useAnnotationStore.getState();
    annotations.addNote('run', '跑');
    annotations.addNote('walk', '走');

    annotations.removeWordAnnotations('run');

    expect(useAnnotationStore.getState().notes['run']).toBeUndefined();
    expect(useAnnotationStore.getState().notes['walk']).toEqual(['走']);
  });
});

describe('Annotation normalisation', () => {
  it('keeps well-formed notes and rejects everything else', () => {
    expect(normalizeNoteList(['  跑  ', '跑', '', 42, null, '走'])).toEqual(['跑', '走']);
    expect(normalizeNoteList('not an array')).toEqual([]);
    expect(normalizeNoteList(undefined)).toEqual([]);
  });

  it('requires a sentence and drops the fields it cannot use', () => {
    expect(normalizeSavedSentence({ sentence: '  Hello.  ' })).toEqual({ sentence: 'Hello.' });
    expect(normalizeSavedSentence({ sentence: '   ' })).toBeNull();
    expect(normalizeSavedSentence({ translation: '只有译文' })).toBeNull();
    expect(normalizeSavedSentence(null)).toBeNull();

    expect(
      normalizeSavedSentence({
        sentence: 'Hello.',
        translation: ' 你好。 ',
        bookId: '',
        pageIndex: 3.5,
        createdAt: 'yesterday',
      }),
    ).toEqual({ sentence: 'Hello.', translation: '你好。' });
  });

  it('de-duplicates sentences by text, first one wins', () => {
    const list = normalizeSentenceList([
      { sentence: 'A.', translation: '第一' },
      { sentence: 'A.', translation: '第二' },
      { sentence: 'B.' },
    ]);
    expect(list).toEqual([{ sentence: 'A.', translation: '第一' }, { sentence: 'B.' }]);
  });

  it('normalises the word keys and drops empty maps', () => {
    const data = normalizeAnnotations({
      notes: { '  Ubiquitous ': ['到处都是'], Empty: [], '   ': ['没有键'] },
      sentences: { RUN: [{ sentence: 'He runs.' }] },
    });

    // Without key normalisation these entries would never be found on lookup,
    // because every read goes through trim().toLowerCase().
    expect(Object.keys(data.notes)).toEqual(['ubiquitous']);
    expect(data.notes.ubiquitous).toEqual(['到处都是']);
    expect(Object.keys(data.sentences)).toEqual(['run']);
  });

  it('answers an empty set for anything that is not annotation data', () => {
    // This is what an imported v1 backup (words only) looks like.
    expect(normalizeAnnotations(undefined)).toEqual({ notes: {}, sentences: {} });
    expect(normalizeAnnotations({ words: { run: 5 } })).toEqual({ notes: {}, sentences: {} });
  });
});

/**
 * The end-to-end version: annotations actually sitting in localStorage, read
 * back by a freshly loaded store. This is the path a returning reader takes.
 */
describe('Annotation migration and loading', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('loads saved annotations and skips the entries it cannot read', async () => {
    localStorage.setItem(
      'language_reader_annotations',
      JSON.stringify({
        notes: { run: ['跑', 42], broken: 'not an array' },
        sentences: { run: [{ sentence: 'He runs.' }, { nothing: true }] },
      }),
    );

    vi.resetModules();
    const { useAnnotationStore: freshStore } = await import('../store/useAnnotationStore');

    expect(freshStore.getState().notes).toEqual({ run: ['跑'] });
    expect(freshStore.getState().sentences).toEqual({ run: [{ sentence: 'He runs.' }] });
  });

  it('survives a corrupt annotations payload instead of throwing', async () => {
    localStorage.setItem('language_reader_annotations', '{ this is not json');

    vi.resetModules();
    const { useAnnotationStore: freshStore } = await import('../store/useAnnotationStore');

    expect(freshStore.getState().notes).toEqual({});
    expect(freshStore.getState().sentences).toEqual({});
  });

  it('replaces the whole set on import and normalises what it takes', () => {
    const store = useAnnotationStore.getState();
    store.addNote('gone', '会被替换掉');
    expect(useAnnotationStore.getState().notes['gone']).toBeDefined();

    store.importAnnotations({ notes: { Kept: ['留下'], empty: [] } });

    const state = useAnnotationStore.getState();
    expect(state.notes).toEqual({ kept: ['留下'] });
    expect(state.sentences).toEqual({});
  });
});
