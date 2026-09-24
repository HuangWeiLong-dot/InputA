import { containsCjk } from '../utils/tokenizer';

/**
 * 轻量语言检测。
 *
 * 用途只有三个：显示语言标签、挑 TTS 音色、选字体 —— 因为范围定在「检测与适配」，
 * 分词与词典仍以英文为主（见 README / 本次整合方案）。所以这里刻意不引入
 * LingKuma 用的那个 982KB 的 ELD 模型：为了一个标签付近 1MB 不值。
 *
 * 两部分：
 *   1. 中日韩等无空格文字按字符区间判别。区间**直接复用 tokenizer 的 CJK_PATTERN**，
 *      与分词、仿生阅读保持同一套定义。
 *   2. 拉丁语系用高频停用词打分。样本足够时这种方法相当稳（一两个常见词就能定），
 *      而它要判别的也正是「这段正文大概是哪种语言」。
 *
 * 判不出来时返回 'en'：这是本应用默认的阅读语言，而且调用方拿到的永远是一个
 * 可用的语言标签，不需要再处理 null。
 */

export interface LanguageGuess {
  /** ISO 639-1（或带地区的标签）。 */
  language: string;
  /** 检测依据的强度，0-1；低置信度时可以据此决定不显示标签。 */
  confidence: number;
}

/** 各语言的判别用停用词。取的是高频且区分度大的虚词。 */
const STOPWORDS: Record<string, string[]> = {
  en: ['the', 'and', 'of', 'to', 'in', 'is', 'that', 'it', 'was', 'for', 'with', 'as', 'his', 'her', 'not', 'but', 'you', 'this', 'have', 'from'],
  de: ['der', 'die', 'das', 'und', 'ich', 'nicht', 'ist', 'mit', 'sich', 'auf', 'ein', 'eine', 'auch', 'als', 'aber', 'wir', 'sie', 'dem', 'den', 'zu'],
  fr: ['le', 'la', 'les', 'des', 'et', 'est', 'que', 'qui', 'pour', 'dans', 'pas', 'vous', 'nous', 'sur', 'une', 'avec', 'plus', 'mais', 'son', 'au'],
  es: ['el', 'la', 'los', 'las', 'de', 'que', 'y', 'en', 'un', 'una', 'por', 'con', 'para', 'no', 'se', 'su', 'como', 'más', 'pero', 'sus'],
  it: ['il', 'lo', 'la', 'gli', 'le', 'di', 'che', 'e', 'per', 'con', 'una', 'non', 'sono', 'come', 'più', 'ma', 'suo', 'sua', 'nel', 'del'],
  pt: ['o', 'a', 'os', 'as', 'de', 'que', 'e', 'em', 'um', 'uma', 'para', 'com', 'não', 'se', 'por', 'mais', 'como', 'mas', 'seu', 'sua'],
  nl: ['de', 'het', 'een', 'en', 'van', 'ik', 'niet', 'is', 'dat', 'op', 'aan', 'met', 'zijn', 'voor', 'maar', 'als', 'ook', 'die', 'er', 'was'],
};

/**
 * 判别用的字符区间。语序上与 tokenizer 的 CJK_PATTERN 同源，但分得更细：
 * 只判「是不是中日韩」不足以选音色，还要把它们彼此分开。
 */
const KANA = /[぀-ゟ゠-ヿ]/;
const HANGUL = /[ᄀ-ᇿ㄰-㆏가-힯]/;
const HAN = /[一-鿿]/;
const CYRILLIC = /[Ѐ-ӿ]/;
const GREEK = /[Ͱ-Ͽ]/;
const ARABIC = /[؀-ۿ]/;
const HEBREW = /[֐-׿]/;
const THAI = /[฀-๿]/;

/**
 * 日文优先于中文：日文正文里必然混有假名，而中文正文几乎不会有。
 * 所以先看假名，再退回汉字。
 */
function detectNonSpaced(text: string): LanguageGuess | null {
  if (KANA.test(text)) return { language: 'ja', confidence: 0.95 };
  if (HANGUL.test(text)) return { language: 'ko', confidence: 0.95 };
  if (HAN.test(text)) return { language: 'zh', confidence: 0.9 };
  return null;
}

function detectByScript(text: string): LanguageGuess | null {
  if (CYRILLIC.test(text)) return { language: 'ru', confidence: 0.85 };
  if (GREEK.test(text)) return { language: 'el', confidence: 0.85 };
  if (ARABIC.test(text)) return { language: 'ar', confidence: 0.85 };
  if (HEBREW.test(text)) return { language: 'he', confidence: 0.85 };
  if (THAI.test(text)) return { language: 'th', confidence: 0.85 };
  return null;
}

/** 只留字母，其余当分隔符 —— 标点与数字对停用词命中没有帮助。 */
function tokenizeForScoring(text: string): string[] {
  return text
    .toLowerCase()
    .split(/[^\p{L}]+/u)
    .filter(Boolean);
}

function detectSpaced(text: string): LanguageGuess {
  const tokens = tokenizeForScoring(text);
  if (tokens.length === 0) return { language: 'en', confidence: 0 };

  const counts = new Map<string, number>();
  for (const [language, words] of Object.entries(STOPWORDS)) {
    let hits = 0;
    for (const word of words) {
      // 逐个计数而不是用 Set：一篇长文里 "the" 出现几十次，是有力的证据。
      for (const token of tokens) {
        if (token === word) hits++;
      }
    }
    if (hits > 0) counts.set(language, hits);
  }

  if (counts.size === 0) return { language: 'en', confidence: 0 };

  let best = 'en';
  let bestScore = 0;
  let secondScore = 0;
  for (const [language, score] of counts) {
    if (score > bestScore) {
      secondScore = bestScore;
      best = language;
      bestScore = score;
    } else if (score > secondScore) {
      secondScore = score;
    }
  }

  // 命中数越多越可信；与第二名的差距越大越可信。两者取小。
  const volume = Math.min(1, bestScore / 8);
  const margin = bestScore > 0 ? (bestScore - secondScore) / bestScore : 0;
  return { language: best, confidence: Math.min(volume, margin) };
}

/**
 * 猜这段文字的语言。空白或判不出来时给 `en`、置信度 0 ——
 * 调用方永远拿到一个可用的标签，不需要再判 null。
 */
export function detectLanguage(text: string): LanguageGuess {
  const sample = text.trim();
  if (!sample) return { language: 'en', confidence: 0 };

  const nonSpaced = detectNonSpaced(sample);
  if (nonSpaced) return nonSpaced;

  // 中日韩之外的其它非拉丁文字：按字符区间就够，不用看停用词。
  if (containsCjk(sample)) return { language: 'zh', confidence: 0.5 };

  const byScript = detectByScript(sample);
  if (byScript) return byScript;

  return detectSpaced(sample);
}
