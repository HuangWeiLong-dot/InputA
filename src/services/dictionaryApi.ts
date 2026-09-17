import type { DefinitionItem, DictionaryEntry, DictionaryExtra, Meaning } from '../types/reader';
import { getBackendHealth } from './apiBase';

export type DictionarySource =
  | 'local-ecdict'
  | 'dictionaryapi.dev'
  | 'wiktionary'
  | 'datamuse';

export interface DictionaryLookupResult {
  entry: DictionaryEntry | null;
  /** Which provider answered, for a small attribution label in the UI. */
  source: DictionarySource | null;
  /**
   * true  = every provider failed because of network / CORS / 5xx errors,
   *         so the word state is unknown and retrying makes sense.
   * false = the providers answered and none of them knows the word.
   */
  unavailable: boolean;
}

const REQUEST_TIMEOUT_MS = 8000;

type Json = unknown;

const cache = new Map<string, DictionaryLookupResult>();

/**
 * Outcome of a single provider request.
 *   - payload: the provider answered with JSON
 *   - missing: the provider answered 404, i.e. it simply has no such word
 *   - failure: network / CORS / 5xx / unparsable response
 * Only real failures may surface as "service unavailable" in the UI.
 */
type ProviderOutcome =
  | { kind: 'payload'; payload: Json }
  | { kind: 'missing' }
  | { kind: 'failure'; reason: string };

/** fetch + JSON with a hard timeout so an unhealthy upstream cannot hang the UI. */
async function requestProvider(url: string): Promise<ProviderOutcome> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    });

    if (res.status === 404) {
      return { kind: 'missing' };
    }
    if (!res.ok) {
      return { kind: 'failure', reason: `HTTP ${res.status}` };
    }

    return { kind: 'payload', payload: (await res.json()) as Json };
  } catch (error) {
    return { kind: 'failure', reason: error instanceof Error ? error.message : 'request failed' };
  } finally {
    clearTimeout(timer);
  }
}

/** Dictionaries ship HTML markup / entities; the UI renders plain text. */
function stripMarkup(input: string): string {
  return input
    .replace(/<[^>]*>/g, '')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ')
    .trim();
}

/* ------------------------------ Provider: dictionaryapi.dev ---------------- */

interface RawApiEntry {
  word?: string;
  phonetic?: string;
  phonetics?: Array<{ text?: string; audio?: string }>;
  meanings?: Array<{
    partOfSpeech?: string;
    definitions?: Array<{ definition?: string; example?: string }>;
  }>;
}

function parseDictionaryApi(payload: Json, fallbackWord: string): DictionaryEntry | null {
  if (!Array.isArray(payload) || payload.length === 0) return null;
  const first = payload[0] as RawApiEntry;
  if (!first || typeof first !== 'object') return null;

  const meanings: Meaning[] = [];
  for (const rawMeaning of first.meanings ?? []) {
    const definitions: DefinitionItem[] = [];
    for (const rawDefinition of rawMeaning.definitions ?? []) {
      if (!rawDefinition?.definition) continue;
      definitions.push({
        definition: stripMarkup(rawDefinition.definition),
        example: rawDefinition.example ? stripMarkup(rawDefinition.example) : undefined,
      });
    }
    if (definitions.length > 0) {
      meanings.push({ partOfSpeech: rawMeaning.partOfSpeech || 'unknown', definitions });
    }
  }
  if (meanings.length === 0) return null;

  const phonetics = first.phonetics ?? [];
  let audioUrl: string | undefined;
  for (const phonetic of phonetics) {
    if (typeof phonetic.audio === 'string' && phonetic.audio.trim().length > 0) {
      audioUrl = phonetic.audio;
      if (/-us\.mp3|-uk\.mp3/.test(phonetic.audio)) break;
    }
  }

  return {
    word: first.word || fallbackWord,
    phonetic: first.phonetic || phonetics.find((p) => p.text)?.text,
    phonetics,
    meanings,
    audioUrl,
  };
}

/* ------------------------------ Provider: Wiktionary REST ------------------ */

interface RawWiktionaryDefinition {
  definition?: string;
  parsedExamples?: Array<{ example?: string }>;
  examples?: string[];
}

interface RawWiktionarySection {
  partOfSpeech?: string;
  definitions?: RawWiktionaryDefinition[];
}

function parseWiktionary(payload: Json, fallbackWord: string): DictionaryEntry | null {
  const sections = (payload as { en?: RawWiktionarySection[] } | null)?.en;
  if (!Array.isArray(sections)) return null;

  const meanings: Meaning[] = [];
  for (const section of sections) {
    const definitions: DefinitionItem[] = [];
    for (const rawDefinition of section.definitions ?? []) {
      const definition = stripMarkup(rawDefinition.definition ?? '');
      if (!definition) continue;
      const rawExample = rawDefinition.parsedExamples?.[0]?.example ?? rawDefinition.examples?.[0];
      const example = rawExample ? stripMarkup(rawExample) : '';
      definitions.push({ definition, example: example || undefined });
    }
    if (definitions.length > 0) {
      meanings.push({ partOfSpeech: section.partOfSpeech || 'unknown', definitions });
    }
  }
  if (meanings.length === 0) return null;

  return { word: fallbackWord, meanings };
}

/* ------------------------------ Provider: Datamuse ------------------------- */

const DATAMUSE_POS_LABELS: Record<string, string> = {
  n: 'noun',
  v: 'verb',
  adj: 'adjective',
  adv: 'adverb',
  u: 'unknown',
};

interface RawDatamuseEntry {
  word?: string;
  defs?: string[];
}

function parseDatamuse(payload: Json, fallbackWord: string): DictionaryEntry | null {
  if (!Array.isArray(payload) || payload.length === 0) return null;
  const first = payload[0] as RawDatamuseEntry;
  const defs = first?.defs;
  if (!defs || defs.length === 0) return null;

  // Datamuse packs every definition as "part-of-speech<TAB>definition".
  const grouped = new Map<string, DefinitionItem[]>();
  for (const rawDef of defs) {
    const fields = rawDef.split('\t');
    const abbreviation = fields[0] ?? '';
    const definition = stripMarkup(fields[fields.length - 1] ?? '');
    if (!definition) continue;
    const partOfSpeech = DATAMUSE_POS_LABELS[abbreviation] || abbreviation || 'unknown';
    const list = grouped.get(partOfSpeech);
    if (list) {
      list.push({ definition });
    } else {
      grouped.set(partOfSpeech, [{ definition }]);
    }
  }
  if (grouped.size === 0) return null;

  return {
    word: first?.word || fallbackWord,
    meanings: Array.from(grouped, ([partOfSpeech, definitions]) => ({ partOfSpeech, definitions })),
  };
}

/* --------------------- Provider: local ECDICT (/api/dict) ------------------ */

/**
 * WordNet 词性代码（ECDICT 英文释义行的前缀，如 "n. a score…" / "s. precisely…"）
 * → 与其它来源一致的英文词性名，这样 UI 不必区分来源。
 */
const ECDICT_DEFINITION_POS: Record<string, string> = {
  n: 'noun',
  v: 'verb',
  a: 'adjective',
  s: 'adjective',
  r: 'adverb',
};

interface RawLocalEntry {
  word?: string;
  phonetic?: string | null;
  translation?: string | null;
  definition?: string | null;
  tags?: Array<{ label?: string }>;
  partsOfSpeech?: Array<{ label?: string; abbr?: string; percent?: number }>;
  lemma?: string | null;
  inflection?: string | null;
  forms?: Array<{ label?: string; words?: string[] }>;
  collins?: number | null;
  oxford?: boolean;
  bnc?: number | null;
  frq?: number | null;
  audio?: string | null;
}

/**
 * Payload of GET /api/dict (see server/dictLookup.ts): a local ECDICT row with
 * Chinese + English definitions, exam tags, inflections and frequency ranks.
 * ECDICT ships no example sentences, so meanings carry definitions only.
 */
function parseLocalEcdict(payload: Json, fallbackWord: string): DictionaryEntry | null {
  const raw = payload as RawLocalEntry | null;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;

  // English definitions are one per line, prefixed with a WordNet POS code;
  // a line without a code lands in "unknown" instead of being mislabelled.
  const grouped = new Map<string, DefinitionItem[]>();
  for (const line of (raw.definition ?? '').split('\n')) {
    const text = stripMarkup(line);
    if (!text) continue;

    const matched = /^([a-z])(?:\.)?\s+(.+)$/i.exec(text);
    const partOfSpeech = ECDICT_DEFINITION_POS[matched?.[1]?.toLowerCase() ?? ''];
    const definition = partOfSpeech ? matched?.[2] ?? '' : text;
    if (!definition) continue;

    const key = partOfSpeech ?? 'unknown';
    const list = grouped.get(key);
    if (list) {
      list.push({ definition });
    } else {
      grouped.set(key, [{ definition }]);
    }
  }

  const translation = raw.translation ?? '';
  if (grouped.size === 0 && !translation.trim()) return null;

  const frequency: DictionaryExtra['frequency'] = {};
  if (typeof raw.collins === 'number' && raw.collins > 0) frequency.collins = raw.collins;
  if (raw.oxford === true) frequency.oxford = true;
  if (typeof raw.bnc === 'number' && raw.bnc > 0) frequency.bnc = raw.bnc;
  if (typeof raw.frq === 'number' && raw.frq > 0) frequency.frq = raw.frq;

  const examTags = (raw.tags ?? [])
    .map((tag) => tag.label)
    .filter((label): label is string => Boolean(label));

  const extra: DictionaryExtra = {
    translation: translation.trim() || undefined,
    examTags: examTags.length > 0 ? examTags : undefined,
    partsOfSpeech: (raw.partsOfSpeech ?? [])
      .filter((part) => Boolean(part?.label))
      .map((part) => ({
        label: part.label as string,
        abbr: part.abbr || (part.label as string),
        percent: part.percent ?? 0,
      })),
    lemma: raw.lemma || undefined,
    inflection: raw.inflection || undefined,
    forms: (raw.forms ?? [])
      .filter((form) => Boolean(form?.label) && (form?.words?.length ?? 0) > 0)
      .map((form) => ({ label: form.label as string, words: form.words as string[] })),
    frequency: Object.keys(frequency).length > 0 ? frequency : undefined,
  };

  const phonetics =
    raw.phonetic || raw.audio
      ? [{ text: raw.phonetic ?? undefined, audio: raw.audio ?? undefined }]
      : [];

  return {
    word: raw.word || fallbackWord,
    phonetic: raw.phonetic ?? undefined,
    phonetics,
    meanings: Array.from(grouped, ([partOfSpeech, definitions]) => ({ partOfSpeech, definitions })),
    audioUrl: raw.audio ?? undefined,
    extra,
  };
}

/* ------------------------- Provider registry + lookup ---------------------- */

interface ProviderSpec {
  name: DictionarySource;
  /** Same-origin route served by server/index.js when the backend is running. */
  backendUrl?: (word: string) => string;
  /** Direct upstream URL, used when the backend is not running (CORS blocked). */
  directUrl?: (word: string) => string;
  /**
   * Optional enrichment (the offline ECDICT file): a transport failure here is
   * logged but must not turn a plain "unknown word" into a reported outage.
   */
  optional?: boolean;
  /** Route that only exists on our backend, and only when the DB file is there. */
  requiresLocalDictionary?: boolean;
  parse: (payload: Json, word: string) => DictionaryEntry | null;
}

const PROVIDERS: ProviderSpec[] = [
  {
    name: 'local-ecdict',
    // Offline, ~1ms, Chinese definitions and exam tags: ask it before the network.
    backendUrl: (word) => `/api/dict?word=${encodeURIComponent(word)}`,
    optional: true,
    requiresLocalDictionary: true,
    parse: parseLocalEcdict,
  },
  {
    name: 'dictionaryapi.dev',
    backendUrl: (word) => `/api/dictionary/word/${encodeURIComponent(word)}`,
    directUrl: (word) =>
      `https://api.dictionaryapi.dev/api/v2/entries/en/${encodeURIComponent(word)}`,
    parse: parseDictionaryApi,
  },
  {
    name: 'wiktionary',
    backendUrl: (word) => `/api/dictionary/wiktionary/${encodeURIComponent(word)}`,
    directUrl: (word) =>
      `https://en.wiktionary.org/api/rest_v1/page/definition/${encodeURIComponent(word)}`,
    parse: parseWiktionary,
  },
  {
    name: 'datamuse',
    backendUrl: (word) => `/api/dictionary/datamuse/${encodeURIComponent(word)}`,
    directUrl: (word) =>
      `https://api.datamuse.com/words?sp=${encodeURIComponent(word)}&md=d&max=1`,
    parse: parseDatamuse,
  },
];

/**
 * URLs to try for one provider, in order. With the backend up its same-origin
 * route comes first; the direct upstream URL stays as a per-provider fallback,
 * because the server and the browser can have different network reachability
 * (DNS, WAF, IPv6...).
 */
function providerCandidates(
  provider: ProviderSpec,
  word: string,
  backendUp: boolean,
  localDictionaryReady: boolean,
): string[] {
  // No direct upstream for the offline file, and no point in asking when
  // /api/health already said it is missing.
  if (provider.requiresLocalDictionary && !localDictionaryReady) return [];

  const urls: string[] = [];
  if (backendUp && provider.backendUrl) urls.push(provider.backendUrl(word));
  if (provider.directUrl) urls.push(provider.directUrl(word));
  return urls;
}

/**
 * Look a word up in several dictionaries, richest first:
 *
 *   1. `local-ecdict`     – offline ECDICT via GET /api/dict (Chinese 释义,
 *                          考试标签, 词性, 原形; ~1ms, needs the backend + DB)
 *   2. `dictionaryapi.dev` – English definitions + audio
 *   3. `wiktionary`        – English definitions
 *   4. `datamuse`          – last-resort English definitions
 *
 * A failing provider never breaks the lookup: the next one is tried. The local
 * one is optional, so its outage is not reported as "dictionary unavailable".
 */
export async function lookupWord(word: string): Promise<DictionaryLookupResult> {
  const clean = word.trim().toLowerCase();
  if (!clean) {
    return { entry: null, source: null, unavailable: false };
  }

  const cached = cache.get(clean);
  if (cached) return cached;

  // The health probe also reports whether the server can read data/stardict.db,
  // so a backend without the file costs no extra request per lookup.
  const health = await getBackendHealth();
  const backendUp = health.ok;
  const localDictionaryReady = backendUp && health.dictionaryAvailable !== false;

  const attempts: string[] = [];
  let sawProviderFailure = false;

  for (const provider of PROVIDERS) {
    const candidates = providerCandidates(provider, clean, backendUp, localDictionaryReady);
    if (candidates.length === 0) {
      attempts.push(`${provider.name}: 未启用`);
      continue;
    }

    let entry: DictionaryEntry | null = null;
    let providerFailed = false;

    for (const url of candidates) {
      const viaBackend = url.startsWith('/api/');
      const outcome = await requestProvider(url);

      if (outcome.kind === 'failure') {
        attempts.push(`${provider.name}(${viaBackend ? 'backend' : 'direct'}): ${outcome.reason}`);
        console.warn(
          `[dictionary] "${clean}" failed on ${provider.name} via ${viaBackend ? 'backend' : 'direct'}: ${outcome.reason}`,
        );
        // An optional enrichment provider must not turn "unknown word" into an outage.
        if (!provider.optional) providerFailed = true;
        continue;
      }

      // 'missing' (404) and an unparsable payload are definitive answers, so the
      // other transport is not retried for this provider.
      if (outcome.kind === 'missing') {
        attempts.push(`${provider.name}: 未收录`);
        break;
      }

      entry = provider.parse(outcome.payload, clean);
      if (!entry) attempts.push(`${provider.name}: 无有效释义`);
      break;
    }

    if (entry) {
      const result: DictionaryLookupResult = { entry, source: provider.name, unavailable: false };
      cache.set(clean, result);
      return result;
    }

    if (providerFailed) {
      sawProviderFailure = true;
    }
  }

  const result: DictionaryLookupResult = {
    entry: null,
    source: null,
    // Clean answers that just lack the word are NOT an outage, so only a
    // transport/5xx failure of a required provider may surface as "unavailable".
    unavailable: sawProviderFailure,
  };

  // Cache real "not found" answers only; outages must stay retryable.
  if (!sawProviderFailure) {
    cache.set(clean, result);
  }

  console.info(`[dictionary] no entry for "${clean}" (${attempts.join('; ')})`);
  return result;
}


/** Convenience wrapper that only exposes the entry. */
export async function fetchWordDefinition(word: string): Promise<DictionaryEntry | null> {
  return (await lookupWord(word)).entry;
}

