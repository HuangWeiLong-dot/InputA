import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UNKNOWN_PART_OF_SPEECH } from '../services/dictionaryApi';

/**
 * The dictionary lookup used to break entirely whenever the browser could not
 * reach api.dictionaryapi.dev (no CORS header on its 522 error pages). These
 * tests pin the current contract:
 *
 *   - the offline ECDICT file answers first (GET /api/dict) when the backend
 *     reports it is available, and its absence/failure never turns a missing
 *     word into a reported outage
 *   - with the backend running, every request is same-origin (/api/...)
 *   - without it, the direct upstream chain is used
 *   - a 404 ("no such word") is not an outage, a transport failure is
 */

const WIKTIONARY_PAYLOAD = {
  en: [
    {
      partOfSpeech: 'Adverb',
      language: 'English',
      definitions: [
        {
          definition: 'To a <a href="/wiki/great">great</a> extent or degree.',
          parsedExamples: [{ example: 'That dress is <b>very</b> you.' }],
          examples: ['That dress is <b>very</b> you.'],
        },
      ],
    },
  ],
};

const DATAMUSE_PAYLOAD = [
  {
    word: 'very',
    score: 8040,
    defs: ['adv\tTo a great extent or degree. ', 'adj\tTrue, real, actual. '],
  },
];

const DICTIONARY_API_PAYLOAD = [
  {
    word: 'very',
    phonetic: '/ˈveri/',
    phonetics: [{ text: '/ˈveri/', audio: 'https://example.com/very-us.mp3' }],
    meanings: [{ partOfSpeech: 'adverb', definitions: [{ definition: 'To a high degree.' }] }],
  },
];

/** GET /api/dict (server/dictLookup.ts) — the offline ECDICT row for "very". */
const LOCAL_ECDICT_PAYLOAD = {
  query: 'very',
  word: 'very',
  matchedBy: 'word',
  phonetic: "'veri",
  translation: 'a. 真正的, 恰好的, 十足的, 特有的\nadv. 非常, 完全',
  definition:
    "r. used as intensifiers; `real' is sometimes used informally for `really'\nr. precisely so",
  pos: 'r:92/j:8',
  partsOfSpeech: [
    { code: 'r', label: '副词', abbr: 'adv.', percent: 92 },
    { code: 'j', label: '形容词', abbr: 'adj.', percent: 8 },
  ],
  tag: 'zk gk',
  tags: [
    { code: 'zk', label: '中考' },
    { code: 'gk', label: '高考' },
  ],
  lemma: null,
  inflection: null,
  forms: [],
  collins: 5,
  oxford: true,
  bnc: 80,
  frq: 105,
  audio: null,
};

/** An inflected row: running → run, with the rest of its exchange column. */
const LOCAL_ECDICT_INFLECTED_PAYLOAD = {
  query: 'running',
  word: 'running',
  matchedBy: 'word',
  phonetic: "'rʌniŋ",
  translation: 'n. 赛跑, 流出, 运转\na. 流动的, 跑着的, 连续的',
  definition: 'n. the state of being in operation',
  pos: 'j:63/n:37',
  partsOfSpeech: [{ code: 'j', label: '形容词', abbr: 'adj.', percent: 63 }],
  tag: 'gk',
  tags: [{ code: 'gk', label: '高考' }],
  lemma: 'run',
  inflection: '现在分词',
  forms: [{ code: 's', label: '复数', words: ['runnings'] }],
  collins: 4,
  oxford: true,
  bnc: 3269,
  frq: 3252,
  audio: null,
};

/**
 * A definition column that opens with the article "A" - i.e. an ordinary English
 * sentence, not a WordNet code. Roughly 30% of ECDICT definition lines look like
 * this, and they used to be labelled "adjective" with the first word eaten.
 */
const LOCAL_ECDICT_ARTICLE_A_PAYLOAD = {
  query: 'air bed',
  word: 'air bed',
  matchedBy: 'word',
  phonetic: null,
  translation: 'n. 充气床垫',
  definition: 'A sack or matters inflated with air, and used as a bed.',
  pos: null,
  partsOfSpeech: [],
  tag: null,
  tags: [],
  lemma: null,
  inflection: null,
  forms: [],
  collins: null,
  oxford: false,
  bnc: null,
  frq: null,
  audio: null,
};

/** A coded line ("n. …") followed by a line with no code at all. */
const LOCAL_ECDICT_MIXED_PAYLOAD = {
  ...LOCAL_ECDICT_ARTICLE_A_PAYLOAD,
  query: "ain't",
  word: "ain't",
  translation: 'are not 的缩写',
  definition: "n. a score in baseball\n   [Colloq. or illiterate speech]. See An't.",
};

function stubResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

/**
 * Mock that answers the health probe deterministically and records every URL.
 * `dictionaryAvailable: false` mimics a backend without data/stardict.db.
 */
function stubFetch(
  health: 'ok' | 'down',
  handle: (url: string) => Response | Promise<Response>,
  options: { dictionaryAvailable?: boolean } = {},
) {
  const seen: string[] = [];
  const fetchMock = vi.fn(async (url: string) => {
    const target = String(url);
    seen.push(target);
    if (target === '/api/health') {
      return health === 'ok'
        ? stubResponse({
            ok: true,
            service: 'language-reader-api',
            dictionaryAvailable: options.dictionaryAvailable ?? true,
          })
        : stubResponse({ ok: false }, 404);
    }
    return handle(target);
  });
  vi.stubGlobal('fetch', fetchMock);
  return { fetchMock, seen };
}

/** The service keeps a module-level cache, so every test gets a fresh module. */
async function loadService() {
  vi.resetModules();
  return import('../services/dictionaryApi');
}

describe('Dictionary lookup', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('uses same-origin backend routes and never touches a cross-origin URL', async () => {
    const { seen } = stubFetch('ok', (url) => {
      if (url === '/api/dictionary/word/very') return stubResponse(DICTIONARY_API_PAYLOAD);
      return stubResponse({}, 404);
    });

    const { lookupWord } = await loadService();
    const result = await lookupWord('very');

    expect(result.source).toBe('dictionaryapi.dev');
    expect(result.entry?.phonetic).toBe('/ˈveri/');
    expect(result.entry?.audioUrl).toBe('https://example.com/very-us.mp3');
    expect(seen).toContain('/api/dictionary/word/very');
    expect(seen.some((url) => url.startsWith('https://'))).toBe(false);
  });

  it('falls back to Wiktionary when dictionaryapi.dev is unreachable', async () => {
    const { seen } = stubFetch('down', (url) => {
      if (url.includes('entries/en')) {
        throw new TypeError('Failed to fetch'); // 522 / CORS blocked
      }
      return stubResponse(WIKTIONARY_PAYLOAD);
    });

    const { lookupWord } = await loadService();
    const result = await lookupWord('very');

    expect(result.unavailable).toBe(false);
    expect(result.source).toBe('wiktionary');
    expect(result.entry?.meanings[0].partOfSpeech).toBe('Adverb');
    // HTML markup from the dictionary must be stripped before rendering.
    expect(result.entry?.meanings[0].definitions[0].definition).toBe(
      'To a great extent or degree.',
    );
    expect(result.entry?.meanings[0].definitions[0].example).toBe('That dress is very you.');
    // Without a backend the direct upstream URL is used.
    expect(seen).toContain('https://api.dictionaryapi.dev/api/v2/entries/en/very');
  });

  it('falls back to Datamuse when both richer sources fail', async () => {
    stubFetch('down', (url) => {
      if (url.includes('datamuse')) return stubResponse(DATAMUSE_PAYLOAD);
      throw new TypeError('Failed to fetch');
    });

    const { lookupWord } = await loadService();
    const result = await lookupWord('very');

    expect(result.source).toBe('datamuse');
    expect(result.entry?.meanings.map((meaning) => meaning.partOfSpeech)).toEqual([
      'adverb',
      'adjective',
    ]);
    expect(result.entry?.meanings[0].definitions[0].definition).toBe('To a great extent or degree.');
  });

  it('reports an outage when every source fails at the transport level', async () => {
    stubFetch('down', () => {
      throw new TypeError('Failed to fetch');
    });

    const { lookupWord } = await loadService();
    const result = await lookupWord('very');

    expect(result.entry).toBeNull();
    expect(result.unavailable).toBe(true);
  });

  it('treats an upstream 404 as "word not found", not as an outage', async () => {
    stubFetch('down', () => stubResponse({ title: 'No Definitions Found' }, 404));

    const { lookupWord } = await loadService();
    const result = await lookupWord('zzzznotaword');

    expect(result.entry).toBeNull();
    expect(result.unavailable).toBe(false);
  });

  it('caches "not found" answers so the next click costs no request', async () => {
    const { fetchMock } = stubFetch('down', () => stubResponse({}, 404));

    const { lookupWord } = await loadService();
    await lookupWord('zzzznotaword');
    const callsAfterFirstLookup = fetchMock.mock.calls.length;

    const second = await lookupWord('zzzznotaword');
    expect(second.entry).toBeNull();
    expect(second.unavailable).toBe(false);
    expect(fetchMock.mock.calls.length).toBe(callsAfterFirstLookup);
  });

  it('retries a failed word instead of caching the outage', async () => {
    let healthy = false;
    stubFetch('down', () => {
      if (!healthy) throw new TypeError('Failed to fetch');
      return stubResponse(DICTIONARY_API_PAYLOAD);
    });

    const { lookupWord } = await loadService();
    const failed = await lookupWord('very');
    expect(failed.unavailable).toBe(true);

    healthy = true;
    const recovered = await lookupWord('very');

    expect(recovered.unavailable).toBe(false);
    expect(recovered.source).toBe('dictionaryapi.dev');
    expect(recovered.entry?.phonetic).toBe('/ˈveri/');
  });

  it('answers from the offline ECDICT dictionary before touching the network', async () => {
    const { seen } = stubFetch('ok', (url) => {
      if (url === '/api/dict?word=very') return stubResponse(LOCAL_ECDICT_PAYLOAD);
      return stubResponse({}, 404);
    });

    const { lookupWord } = await loadService();
    const result = await lookupWord('very');

    expect(result.source).toBe('local-ecdict');
    expect(result.unavailable).toBe(false);
    expect(result.entry?.phonetic).toBe("'veri");
    // Chinese definitions, exam tags and frequency only exist offline.
    expect(result.entry?.extra?.translation).toContain('非常');
    expect(result.entry?.extra?.examTags).toEqual(['中考', '高考']);
    expect(result.entry?.extra?.partsOfSpeech?.[0]).toEqual({
      label: '副词',
      abbr: 'adv.',
      percent: 92,
    });
    expect(result.entry?.extra?.frequency).toEqual({ collins: 5, oxford: true, bnc: 80, frq: 105 });
    // English definitions keep their WordNet part-of-speech grouping.
    expect(result.entry?.meanings.map((meaning) => meaning.partOfSpeech)).toEqual(['adverb']);
    expect(result.entry?.meanings[0].definitions.map((item) => item.definition)).toEqual([
      "used as intensifiers; `real' is sometimes used informally for `really'",
      'precisely so',
    ]);
    // The offline hit ends the lookup: no online provider is asked.
    expect(seen).toEqual(['/api/health', '/api/dict?word=very']);
  });

  it('carries the inflection data of an offline entry through', async () => {
    stubFetch('ok', (url) =>
      url === '/api/dict?word=running'
        ? stubResponse(LOCAL_ECDICT_INFLECTED_PAYLOAD)
        : stubResponse({}, 404),
    );

    const { lookupWord } = await loadService();
    const entry = (await lookupWord('running')).entry;

    expect(entry?.word).toBe('running');
    expect(entry?.extra?.lemma).toBe('run');
    expect(entry?.extra?.inflection).toBe('现在分词');
    expect(entry?.extra?.forms).toEqual([{ label: '复数', words: ['runnings'] }]);
  });

  it('keeps the first word of a definition that opens with the article "A"', async () => {
    // Regression: the POS prefix used to allow an optional dot, so the leading
    // "A" of a plain English sentence was consumed as the WordNet code `a.` -
    // the line came out labelled "adjective" and had lost its first word.
    stubFetch('ok', (url) =>
      url === '/api/dict?word=air%20bed'
        ? stubResponse(LOCAL_ECDICT_ARTICLE_A_PAYLOAD)
        : stubResponse({}, 404),
    );

    const { lookupWord } = await loadService();
    const entry = (await lookupWord('air bed')).entry;

    // Not "adjective", and the sentence is intact including its first word.
    expect(entry?.meanings.map((meaning) => meaning.partOfSpeech)).toEqual([
      UNKNOWN_PART_OF_SPEECH,
    ]);
    expect(entry?.meanings[0].definitions.map((item) => item.definition)).toEqual([
      'A sack or matters inflated with air, and used as a bed.',
    ]);
  });

  it('labels a coded line and leaves an uncoded one unlabelled', async () => {
    // "n. " keeps its noun group; the bracketed usage note carries no code, so
    // it must keep its whole text under the sentinel instead of being guessed at.
    // encodeURIComponent leaves "'" alone, so the request URL keeps the bare
    // apostrophe rather than %27.
    stubFetch('ok', (url) =>
      url === "/api/dict?word=ain't" ? stubResponse(LOCAL_ECDICT_MIXED_PAYLOAD) : stubResponse({}, 404),
    );

    const { lookupWord } = await loadService();
    const entry = (await lookupWord("ain't")).entry;

    expect(entry?.meanings.map((meaning) => meaning.partOfSpeech)).toEqual([
      'noun',
      UNKNOWN_PART_OF_SPEECH,
    ]);
    expect(entry?.meanings[0].definitions.map((item) => item.definition)).toEqual([
      'a score in baseball',
    ]);
    expect(entry?.meanings[1].definitions[0].definition).toBe(
      "[Colloq. or illiterate speech]. See An't.",
    );
  });

  it('skips the offline dictionary when the backend reports no database', async () => {
    const { seen } = stubFetch(
      'ok',
      (url) =>
        url === '/api/dictionary/word/very'
          ? stubResponse(DICTIONARY_API_PAYLOAD)
          : stubResponse({}, 404),
      { dictionaryAvailable: false },
    );

    const { lookupWord } = await loadService();
    const result = await lookupWord('very');

    expect(result.source).toBe('dictionaryapi.dev');
    expect(seen).not.toContain('/api/dict?word=very');
  });

  it('does not report an outage when only the optional offline dictionary fails', async () => {
    stubFetch('ok', (url) => {
      // Only the offline route breaks; the online providers answer plainly.
      if (url.startsWith('/api/dict?')) throw new TypeError('Failed to fetch');
      return stubResponse({}, 404);
    });

    const { lookupWord } = await loadService();
    const result = await lookupWord('zzzznotaword');

    expect(result.entry).toBeNull();
    // The online sources answered "no such word", so this is not an outage.
    expect(result.unavailable).toBe(false);
  });
});
