/**
 * Offline dictionary lookups against the read-only ECDICT / StarDict SQLite
 * file that ships in ./data/stardict.db (about 3.4M headwords).
 *
 * Why a local file: the free online dictionaries this app can fall back to
 * (api.dictionaryapi.dev, Wiktionary, Datamuse) are slow, rate-limited and
 * regularly answer 5xx. ECDICT already carries 音标 / 中文释义 / 英文释义 /
 * 词性 / 柯林斯星级 / 考试标签, so /api/dict answers in roughly a millisecond
 * with no network round-trip at all.
 *
 * The connection is opened with `readonly: true` and `fileMustExist: true`, so
 * no bug in this module can write to - or create - the database file. When the
 * file is missing the module stays silent and every lookup returns null; the
 * route turns that into a 503 instead of killing the server.
 *
 * This file is TypeScript and is imported directly by server/index.js: Node
 * 22.18+ / 23.6+ / 24 strips the types on load, so there is no build step for
 * the backend. Keep the syntax erasable (no enum, no namespace, no parameter
 * properties) - `erasableSyntaxOnly` in tsconfig.node.json enforces this.
 */
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Database from 'better-sqlite3';

const PROJECT_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** Default location; set STARDICT_DB to point at another copy of the file. */
export const DICTIONARY_PATH = process.env.STARDICT_DB
  ? path.resolve(process.env.STARDICT_DB)
  : path.join(PROJECT_ROOT, 'data', 'stardict.db');

/**
 * ECDICT `pos` 的单字母代码 → 词性标签。
 * 代码来自源词典（用词性明确的词反推：the→a:100、of→i:100、and→c:99、
 * happy 类→j、quickly→r、three→m、who→p、oh→u、this→d、to→t）。
 */
const PART_OF_SPEECH_LABELS: Record<string, { zh: string; abbr: string }> = {
  n: { zh: '名词', abbr: 'n.' },
  v: { zh: '动词', abbr: 'v.' },
  j: { zh: '形容词', abbr: 'adj.' },
  a: { zh: '冠词 / 限定词', abbr: 'art.' },
  r: { zh: '副词', abbr: 'adv.' },
  m: { zh: '数词', abbr: 'num.' },
  i: { zh: '介词', abbr: 'prep.' },
  c: { zh: '连词', abbr: 'conj.' },
  p: { zh: '代词', abbr: 'pron.' },
  d: { zh: '限定词', abbr: 'det.' },
  u: { zh: '感叹词', abbr: 'interj.' },
  t: { zh: '不定式标记', abbr: 'to' },
};

/** ECDICT `tag` 字段 → 考试标签。 */
const EXAM_TAG_LABELS: Record<string, string> = {
  zk: '中考',
  gk: '高考',
  cet4: '四级',
  cet6: '六级',
  ky: '考研',
  toefl: '托福',
  ielts: '雅思',
  gre: 'GRE',
};

/** ECDICT `exchange` 字段的变形代码 → 中文名称（0 / 1 另行处理）。 */
const FORM_LABELS: Record<string, string> = {
  p: '过去式',
  d: '过去分词',
  i: '现在分词',
  '3': '第三人称单数',
  r: '比较级',
  t: '最高级',
  s: '复数',
};

/** 词性及其在词条释义中的占比。 */
export interface DictPartOfSpeech {
  /** ECDICT 单词性代码，如 n / v / j / r。 */
  code: string;
  /** 中文词性，如「名词」。代码未知时回退为代码本身。 */
  label: string;
  /** 英文缩写，如「n.」。 */
  abbr: string;
  /** 占比（0-100），越小越边缘。 */
  percent: number;
}

/** 考试标签（中考 / 高考 / 四级 …）。 */
export interface DictExamTag {
  /** ECDICT 标签代码，如 cet4。 */
  code: string;
  /** 中文标签，如「四级」。代码未知时回退为代码本身。 */
  label: string;
}

/** 一条词形变化，如 { code: 'p', label: '过去式', words: ['ran'] }。 */
export interface DictForm {
  /** ECDICT 代码：p / d / i / 3 / r / t / s。 */
  code: string;
  /** 中文名称，如「过去式」。 */
  label: string;
  /** 该变形下的所有拼写。 */
  words: string[];
}

/** /api/dict 的响应体。 */
export interface DictEntry {
  /** 规范化后的查询词（小写、首尾去标点）。 */
  query: string;
  /** 词库中的词条原文，可能与 query 大小写或拼写不同。 */
  word: string;
  /** 命中方式：word = 词条精确匹配（忽略大小写）；sw = 忽略空格/连字符/撇号后匹配。 */
  matchedBy: 'word' | 'sw';
  /** 音标，库中原样（如 「rʌn」「'veri」）；本词条没有时用原形的音标补全。 */
  phonetic: string | null;
  /** 中文释义（多行）。 */
  translation: string | null;
  /** 英文释义（多行）。 */
  definition: string | null;
  /** 原始词性占比字段，如 "n:15/v:85"。 */
  pos: string | null;
  /** 解析后的词性，按占比降序。 */
  partsOfSpeech: DictPartOfSpeech[];
  /** 原始考试标签字段，如 "cet4 cet6 ky"。 */
  tag: string | null;
  /** 解析后的考试标签。 */
  tags: DictExamTag[];
  /** 原形：变形词的 exchange 会指向它，如 running → run。 */
  lemma: string | null;
  /** 本词条是原形的哪种变形（中文），如「现在分词」；本身就是原形时为 null。 */
  inflection: string | null;
  /** 词形变化列表。 */
  forms: DictForm[];
  /** 柯林斯星级 1-5；0 表示未收录，统一返回 null。 */
  collins: number | null;
  /** 是否牛津核心词。 */
  oxford: boolean;
  /** 英国国家语料库词频排名，越小越常用。 */
  bnc: number | null;
  /** 当代英语语料库词频排名，越小越常用。 */
  frq: number | null;
  /** 发音音频地址（库中大多为空）。 */
  audio: string | null;
}

/** stardict 表的原始列（只取接口用得到的）。 */
interface DictRow {
  word: string;
  phonetic: string | null;
  definition: string | null;
  translation: string | null;
  pos: string | null;
  tag: string | null;
  collins: number | null;
  oxford: number | null;
  bnc: number | null;
  frq: number | null;
  exchange: string | null;
  audio: string | null;
}

const ENTRY_COLUMNS =
  'word, phonetic, definition, translation, pos, tag, collins, oxford, bnc, frq, exchange, audio';

/** 一次准备好的语句；懒加载，避免只为探测健康就打开文件。 */
interface DictionaryStatements {
  /** 精确匹配：走 stardict_2 (word) 唯一索引，大小写不敏感。 */
  byWord: Database.Statement<[string], DictRow>;
  /** 忽略空格 / 连字符 / 撇号：走 stardict_3 (sw, word) 索引。 */
  byStripped: Database.Statement<[string], DictRow>;
  /** 取原形的音标，用于给缺音标的变形词补全。 */
  phoneticByWord: Database.Statement<[string], { phonetic: string | null }>;
}

let openAttempted = false;
let statements: DictionaryStatements | null = null;

/**
 * Open data/stardict.db once, read-only. Returns null (and logs once) when the
 * file is absent or unreadable, so the rest of the API keeps working.
 */
function openDictionary(): DictionaryStatements | null {
  if (openAttempted) return statements;
  openAttempted = true;

  try {
    // readonly + fileMustExist: a lookup can neither write nor create the DB.
    const connection = new Database(DICTIONARY_PATH, { readonly: true, fileMustExist: true });
    statements = {
      byWord: connection.prepare<[string], DictRow>(
        `SELECT ${ENTRY_COLUMNS} FROM stardict WHERE word = ? COLLATE NOCASE LIMIT 1`,
      ),
      byStripped: connection.prepare<[string], DictRow>(
        // sw 命中可能有多条（mothers → mothers / mother's / moth-ers），按
        // 「先要有释义，再要拼写最短」挑一条，避免随机拿到 moth-ers。
        `SELECT ${ENTRY_COLUMNS} FROM stardict WHERE sw = ? COLLATE NOCASE
         ORDER BY (translation IS NULL OR translation = '') ASC, length(word) ASC, word COLLATE NOCASE ASC
         LIMIT 1`,
      ),
      phoneticByWord: connection.prepare<[string], { phonetic: string | null }>(
        'SELECT phonetic FROM stardict WHERE word = ? COLLATE NOCASE LIMIT 1',
      ),
    };
    console.log(`[dict] stardict ready (readonly): ${DICTIONARY_PATH}`);
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    console.warn(`[dict] cannot open ${DICTIONARY_PATH}: ${reason}`);
    console.warn('[dict] /api/dict will answer 503 until the file is in place');
    statements = null;
  }

  return statements;
}

/** true when the SQLite file could be opened; /api/dict answers 503 otherwise. */
export function isDictionaryAvailable(): boolean {
  return openDictionary() !== null;
}

/** 释放文件句柄；测试或热替换词库文件时用得上（下次查询会重新打开）。 */
export function closeDictionary(): void {
  if (!statements) return;
  // The prepared statements all share one connection.
  statements.byWord.database.close();
  statements = null;
  openAttempted = false;
}

/** 空串 / 纯空白 → null，让 JSON 里少一些无意义的字段。 */
function cleanText(value: string | null | undefined): string | null {
  const text = (value ?? '').trim();
  return text.length > 0 ? text : null;
}

/** 0 表示「未收录」，与其他未知值一起归一为 null。 */
function cleanNumber(value: number | null | undefined): number | null {
  return typeof value === 'number' && value > 0 ? value : null;
}

/**
 * 查询词规范化：NFC、弯撇号归一、压缩空白、去掉首尾标点（保留词内撇号与连字符）、小写。
 * 「"Hello,"」→「hello」，「don’t」→「don't」，「ice  cream」→「ice cream」。
 */
export function normalizeWord(raw: string): string {
  return raw
    .normalize('NFC')
    .replace(/[\u2018\u2019\u02bc\u2032]/gu, "'")
    .trim()
    .replace(/\s+/gu, ' ')
    .replace(/^[^\p{L}\p{N}']+|[^\p{L}\p{N}']+$/gu, '')
    .toLowerCase();
}

/**
 * ECDICT 的 sw 列：去掉空格、连字符、撇号后的形式，
 * 因此 icecream / ice-cream / ice cream 都能互相命中。
 */
function strippedForm(word: string): string {
  return word.replace(/[^\p{L}\p{N}]/gu, '');
}

/** exchange 里 1: 的值（如 "3s"）→「第三人称单数/复数」；未知代码原样保留。 */
function describeInflection(code: string): string {
  return code
    .split('')
    .map((char) => FORM_LABELS[char] ?? char)
    .join('/');
}

interface ParsedExchange {
  /** 0: 指向的原形。 */
  lemma: string | null;
  /** 1: 描述的本词条变形类型。 */
  inflection: string | null;
  /** 其余代码展开成词形变化表。 */
  forms: DictForm[];
}

/** 'p:ran/i:running/3:runs/0:run/1:i' → { lemma, inflection, forms }。 */
function parseExchange(raw: string | null): ParsedExchange {
  const forms: DictForm[] = [];
  let lemma: string | null = null;
  let inflection: string | null = null;

  for (const chunk of (raw ?? '').split('/')) {
    const separator = chunk.indexOf(':');
    if (separator <= 0) continue;

    const code = chunk.slice(0, separator);
    const value = chunk.slice(separator + 1).trim();
    if (!value) continue;

    if (code === '0') {
      lemma = value;
      continue;
    }
    if (code === '1') {
      inflection = describeInflection(value);
      continue;
    }

    const existing = forms.find((form) => form.code === code);
    if (existing) {
      existing.words.push(value);
    } else {
      forms.push({ code, label: FORM_LABELS[code] ?? code, words: [value] });
    }
  }

  return { lemma, inflection, forms };
}

/** "n:15/v:85" → 按占比降序的词性数组。 */
function parsePartsOfSpeech(raw: string | null): DictPartOfSpeech[] {
  const parts: DictPartOfSpeech[] = [];

  for (const chunk of (raw ?? '').split('/')) {
    const [code, percent] = chunk.split(':');
    if (!code) continue;

    const meta = PART_OF_SPEECH_LABELS[code];
    parts.push({
      code,
      label: meta?.zh ?? code,
      abbr: meta?.abbr ?? code,
      percent: Number.parseInt(percent ?? '', 10) || 0,
    });
  }

  return parts.sort((a, b) => b.percent - a.percent);
}

/** "cet4 cet6 ky" → [{ code: 'cet4', label: '四级' }, …]。 */
function parseTags(raw: string | null): DictExamTag[] {
  return (raw ?? '')
    .split(/\s+/u)
    .filter(Boolean)
    .map((code) => ({ code, label: EXAM_TAG_LABELS[code] ?? code }));
}

/**
 * Look a word up in the local dictionary.
 *
 * Returns null when the word is unknown (the route turns that into a 404) and
 * never throws for a missing word: only a broken database surfaces as an
 * exception, so callers can tell 「查不到」 apart from 「服务坏了」.
 */
export function lookupWord(raw: string): DictEntry | null {
  const query = normalizeWord(raw ?? '');
  if (!query) return null;

  // Callers should check isDictionaryAvailable() first (the route answers 503).
  const dictionary = openDictionary();
  if (!dictionary) return null;

  let row = dictionary.byWord.get(query);
  let matchedBy: DictEntry['matchedBy'] = 'word';

  if (!row) {
    const stripped = strippedForm(query);
    if (stripped && stripped !== query) {
      row = dictionary.byStripped.get(stripped);
      matchedBy = 'sw';
    }
  }
  if (!row) return null;

  const exchange = parseExchange(row.exchange);

  // 变形词常常没有自己的音标（runs / studies），用原形的补上。
  let phonetic = cleanText(row.phonetic);
  if (!phonetic && exchange.lemma) {
    phonetic = cleanText(dictionary.phoneticByWord.get(exchange.lemma)?.phonetic);
  }

  return {
    query,
    word: row.word,
    matchedBy,
    phonetic,
    translation: cleanText(row.translation),
    definition: cleanText(row.definition),
    pos: cleanText(row.pos),
    partsOfSpeech: parsePartsOfSpeech(row.pos),
    tag: cleanText(row.tag),
    tags: parseTags(row.tag),
    lemma: cleanText(exchange.lemma),
    inflection: exchange.inflection,
    forms: exchange.forms,
    collins: cleanNumber(row.collins),
    oxford: row.oxford === 1,
    bnc: cleanNumber(row.bnc),
    frq: cleanNumber(row.frq),
    audio: cleanText(row.audio),
  };
}
