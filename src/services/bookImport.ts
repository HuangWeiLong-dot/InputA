import { detectLanguage } from './languageDetect';
import { processGutenbergText } from './gutendexApi';
import type { Book, BookChapter } from '../types/reader';

/**
 * 把用户选的文件变成一本可读的书。
 *
 * 支持 TXT / Markdown / HTML / EPUB。**刻意不支持 PDF**：它需要 pdfjs-dist（约 1MB+），
 * 而抽出来的文本质量通常很差（分栏错位、页眉页脚混进正文、连字断裂），扫描件更是完全
 * 抽不出文字。对阅读器来说 PDF 是最差的格式，为它引入一个大依赖不划算。
 *
 * 这一层除 `readBookFile` 外全是**纯函数**（字符串/字节 → `BookChapter[]`），这是硬要求：
 * 测试跑在 vitest 的 node 环境里、没有 jsdom，`File`/`FileReader`/`DOMParser` 全都不可用。
 * 于是把「选择文件」这件薄事单独留在最后一个函数里，解析逻辑全部可单测。
 */

/** 只列 HTML 文本里常见的那些。认不出来的实体**原样保留** —— 宁可留下 `&foo;` 也不要吃掉内容。 */
const NAMED_ENTITIES: Record<string, string> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
  nbsp: ' ',
  mdash: '—',
  ndash: '–',
  hellip: '…',
  lsquo: '‘',
  rsquo: '’',
  ldquo: '“',
  rdquo: '”',
  times: '×',
  copy: '©',
  reg: '®',
  deg: '°',
};

/** 解 HTML 实体：具名、十进制 `&#8217;`、十六进制 `&#x2019;` 三种。 */
export function decodeEntities(text: string): string {
  return text.replace(/&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);/g, (whole, body: string) => {
    if (body[0] === '#') {
      const hex = body[1] === 'x' || body[1] === 'X';
      const code = Number.parseInt(hex ? body.slice(2) : body.slice(1), hex ? 16 : 10);
      // 超出 Unicode 范围或撞上代理区的码点会抛异常，那就当认不出来。
      if (!Number.isFinite(code) || code <= 0 || code > 0x10ffff) return whole;
      if (code >= 0xd800 && code <= 0xdfff) return whole;
      return String.fromCodePoint(code);
    }
    return NAMED_ENTITIES[body.toLowerCase()] ?? whole;
  });
}

/**
 * HTML → 纯文本。
 *
 * 刻意不用 `DOMParser`：node 测试环境没有它。EPUB 里的 XHTML 是机器生成的、结构规整，
 * 字符串处理足够；代价是不处理畸形 HTML 的边界情况，而这里不需要。
 *
 * 块级标签换成换行、行内标签直接去掉 —— 后者很重要：`<b>the</b> <i>cat</i>` 若把标签
 * 换成空格会得到 "the  cat"，段落里的词间距会被弄乱。
 */
export function htmlToPlainText(html: string): string {
  const withoutHidden = html
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/<(script|style|head)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, ' ');

  const withBreaks = withoutHidden.replace(
    /<\/?(p|div|br|li|tr|td|h[1-6]|section|article|blockquote|pre|figure|hr)\b[^>]*>/gi,
    '\n',
  );

  return decodeEntities(withBreaks.replace(/<[^>]*>/g, ''))
    .replace(/[ \t ]+/g, ' ')
    .replace(/ *\n */g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/**
 * Markdown → 纯文本。**刻意只做两件事**，理由与 `inlineMarkdown.ts` 那条一致：
 * 宁可少处理，也不要让不认识的语法把正文吃掉。
 *
 * 1. 剥掉行首的 `#`（`## Chapter 2` → `Chapter 2`）。不只是好看：章节切分器认的是
 *    「Chapter/Letter/Section + 编号」，不剥的话这一行认不出来，整本 md 会退化成一个
 *    Section N 的均分块。
 * 2. `[label](url)` → `label`。URL 在正文里就是点击噪音，而 label 才是读者要的词。
 *
 * 其余（`**粗体**`、列表符号、代码围栏）一律原样留着：形容词法标记只是碍眼，
 * 但把它们去掉就有吃掉正文的风险，而词本身仍然可点可查。
 */
export function markdownToPlainText(markdown: string): string {
  return markdown
    .replace(/^[ \t]*#{1,6}[ \t]+/gm, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1');
}

/* ------------------------------- zip（EPUB 用） ------------------------------ */

interface ZipEntry {
  name: string;
  /** 0 = 不压缩（stored），8 = deflate。EPUB 只用这两种。 */
  method: number;
  compressedSize: number;
  /** 局部头在文件里的偏移。 */
  offset: number;
}

const EOCD_SIGNATURE = 0x06054b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const LOCAL_SIGNATURE = 0x04034b50;

/**
 * 读出 zip 的中央目录。
 *
 * 手写而不引依赖：本仓库有零依赖的传统（`server/edgeTts.ts` 连 WebSocket 客户端都是
 * 手写的），而 EPUB 需要的 zip 子集很小 —— 没有分卷、没有 ZIP64、没有加密。
 *
 * 尺寸一律**以中央目录为准**：写了 data descriptor 的条目（局部头 bit 3）局部头里的
 * 尺寸是 0，只看局部头会解出空内容。这是这个格式最经典的一个坑。
 */
function readCentralDirectory(bytes: Uint8Array): ZipEntry[] {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);

  // 中央目录结尾记录在文件末尾，后面可能跟最多 65535 字节的注释，所以从尾部往回找。
  let eocd = -1;
  const lowest = Math.max(0, bytes.byteLength - (22 + 0xffff));
  for (let i = bytes.byteLength - 22; i >= lowest; i -= 1) {
    if (view.getUint32(i, true) === EOCD_SIGNATURE) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Error('不是有效的 zip（找不到中央目录结尾记录）');

  const count = view.getUint16(eocd + 10, true);
  const directoryOffset = view.getUint32(eocd + 16, true);
  if (count === 0xffff || directoryOffset === 0xffffffff) {
    throw new Error('这个 zip 使用了 ZIP64，暂不支持（EPUB 一般不会）');
  }

  const decoder = new TextDecoder();
  const entries: ZipEntry[] = [];
  let p = directoryOffset;

  for (let i = 0; i < count; i += 1) {
    if (view.getUint32(p, true) !== CENTRAL_SIGNATURE) {
      throw new Error('zip 的中央目录已损坏');
    }
    if ((view.getUint16(p + 8, true) & 0x1) !== 0) {
      throw new Error('这个 zip 是加密的，无法读取');
    }
    const method = view.getUint16(p + 10, true);
    const compressedSize = view.getUint32(p + 20, true);
    const nameLength = view.getUint16(p + 28, true);
    const extraLength = view.getUint16(p + 30, true);
    const commentLength = view.getUint16(p + 32, true);
    const offset = view.getUint32(p + 42, true);
    const name = decoder.decode(bytes.subarray(p + 46, p + 46 + nameLength));

    entries.push({ name, method, compressedSize, offset });
    p += 46 + nameLength + extraLength + commentLength;
  }

  return entries;
}

/** 用 `DecompressionStream('deflate-raw')` 解压：浏览器与 Node 18+ 都有，无需依赖。 */
async function inflateRaw(raw: Uint8Array): Promise<Uint8Array> {
  // 这里两处类型上的别扭都来自 TS 6 的 lib.dom 泛型，不是运行期问题：
  //   - DecompressionStream 的 writable 是 `WritableStream<BufferSource>`，所以管道
  //     这一侧的 chunk 类型要写成 BufferSource 而不是更窄的 Uint8Array
  //   - `BufferSource` 要求底层是 ArrayBuffer，而 `Uint8Array` 默认参数化为
  //     `Uint8Array<ArrayBufferLike>`（含 SharedArrayBuffer 的可能），因此要断言一次
  const source: ReadableStream<BufferSource> = new ReadableStream<BufferSource>({
    start(controller) {
      controller.enqueue(raw as BufferSource);
      controller.close();
    },
  });
  const inflated = source.pipeThrough(new DecompressionStream('deflate-raw'));
  return new Uint8Array(await new Response(inflated).arrayBuffer());
}

async function readEntry(bytes: Uint8Array, entry: ZipEntry): Promise<Uint8Array> {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (view.getUint32(entry.offset, true) !== LOCAL_SIGNATURE) {
    throw new Error(`zip 条目「${entry.name}」的局部头已损坏`);
  }
  const nameLength = view.getUint16(entry.offset + 26, true);
  const extraLength = view.getUint16(entry.offset + 28, true);
  const start = entry.offset + 30 + nameLength + extraLength;
  const raw = bytes.subarray(start, start + entry.compressedSize);

  if (entry.method === 0) return raw;
  if (entry.method !== 8) throw new Error(`zip 条目「${entry.name}」的压缩方式 ${entry.method} 不支持`);
  return inflateRaw(raw);
}

/* --------------------------------- EPUB --------------------------------- */

/** 从一个标签的属性里取值，单双引号都认。 */
function attrOf(tag: string, name: string): string | null {
  const double = new RegExp(`\\b${name}\\s*=\\s*"([^"]*)"`, 'i').exec(tag);
  if (double) return double[1];
  const single = new RegExp(`\\b${name}\\s*=\\s*'([^']*)'`, 'i').exec(tag);
  return single ? single[1] : null;
}

/** 取第一个匹配标签的某个属性（用于 `container.xml` 里的 `<rootfile full-path>`）。 */
function firstAttrOf(xml: string, tagName: string, attrName: string): string | null {
  const tag = new RegExp(`<(?:\\w+:)?${tagName}\\b([^>]*)>`, 'i').exec(xml);
  return tag ? attrOf(tag[1], attrName) : null;
}

/**
 * 取一章的标题。**先看标题标签，再看 `<title>`** —— 顺序不能反：EPUB 的 `<title>` 常
 * 被每一章都写成书名，于是四十章全叫同一个名字；`<h1>` 才是这一章自己的标题。
 */
function chapterTitleOf(xhtml: string): string | null {
  const heading = /<h[1-6]\b[^>]*>([\s\S]*?)<\/h[1-6]\s*>/i.exec(xhtml);
  const fromHeading = heading ? htmlToPlainText(heading[1]) : '';
  if (fromHeading) return fromHeading.slice(0, 80);

  const title = /<title\b[^>]*>([\s\S]*?)<\/title\s*>/i.exec(xhtml);
  const fromTitle = title ? htmlToPlainText(title[1]) : '';
  return fromTitle ? fromTitle.slice(0, 80) : null;
}

/**
 * EPUB → 章节。
 *
 * 走的是规范里那条链：`META-INF/container.xml` → 它指向的 OPF → OPF 里的 manifest
 * （id → href）与 spine（阅读顺序）。按 spine 成章比按标题切分更忠实 —— 电子书自己
 * 就声明了章节边界，没必要再猜。
 */
export async function epubToBookChapters(buffer: ArrayBuffer): Promise<BookChapter[]> {
  const bytes = new Uint8Array(buffer);
  const entries = readCentralDirectory(bytes);
  const byName = new Map(entries.map((entry) => [entry.name, entry]));
  const decoder = new TextDecoder();

  const readText = async (name: string): Promise<string | null> => {
    const entry = byName.get(name);
    if (!entry) return null;
    return decoder.decode(await readEntry(bytes, entry));
  };

  const container = await readText('META-INF/container.xml');
  if (!container) throw new Error('不是有效的 EPUB（缺少 META-INF/container.xml）');
  const rootPath = firstAttrOf(container, 'rootfile', 'full-path');
  if (!rootPath) throw new Error('不是有效的 EPUB（container.xml 里没有 rootfile）');

  const opfPath = decodeURIComponent(rootPath);
  const opf = await readText(opfPath);
  if (!opf) throw new Error(`EPUB 里找不到 OPF 文件：${opfPath}`);
  const baseDir = opfPath.includes('/') ? opfPath.slice(0, opfPath.lastIndexOf('/') + 1) : '';

  // manifest：id → href。带 properties="nav" 的是目录页，不是正文，跳过。
  const manifest = new Map<string, string>();
  for (const match of opf.matchAll(/<(?:\w+:)?item\b([^>]*)>/gi)) {
    const tag = match[1];
    const id = attrOf(tag, 'id');
    const href = attrOf(tag, 'href');
    const properties = attrOf(tag, 'properties') ?? '';
    if (id && href && !properties.split(/\s+/).includes('nav')) manifest.set(id, href);
  }

  const ordered: string[] = [];
  for (const match of opf.matchAll(/<(?:\w+:)?itemref\b([^>]*)>/gi)) {
    const tag = match[1];
    // linear="no" 是封面、版权页这类不参与阅读顺序的内容（EPUB 2 的写法）。
    if (/\blinear\s*=\s*"(no|false)"/i.test(tag)) continue;
    const href = attrOf(tag, 'idref');
    const resolved = href ? manifest.get(href) : undefined;
    if (resolved) ordered.push(resolved);
  }
  if (ordered.length === 0) throw new Error('不是有效的 EPUB（spine 里没有任何正文）');

  const chapters: BookChapter[] = [];
  for (const href of ordered) {
    // OPF 里的 href 是相对 OPF 所在目录的 URI，且可能被百分号编码（中文文件名常见）。
    const path = decodeURIComponent(baseDir + href).replace(/^\.\//, '');
    const xhtml = await readText(path);
    if (xhtml === null) continue;
    const content = htmlToPlainText(xhtml);
    if (!content) continue; // 纯图片页会抽成空，跳过而不是留一个空章节
    chapters.push({ title: chapterTitleOf(xhtml) ?? `第 ${chapters.length + 1} 章`, content });
  }

  if (chapters.length === 0) {
    throw new Error('这本 EPUB 里抽不出文字 —— 很可能是图片版（扫描件），或它只含封面与目录。');
  }
  return chapters;
}

/* ------------------------------ 面向 UI 的入口 ----------------------------- */

/** 按扩展名判断解析方式。返回 null 表示不支持。 */
export function parseKindOf(fileName: string): 'epub' | 'html' | 'markdown' | 'text' | null {
  const match = /\.([^.]+)$/.exec(fileName);
  if (!match) return null;
  switch (match[1].toLowerCase()) {
    case 'epub':
      return 'epub';
    case 'html':
    case 'htm':
    case 'xhtml':
      return 'html';
    case 'md':
    case 'markdown':
      return 'markdown';
    case 'txt':
    case 'text':
      return 'text';
    default:
      return null;
  }
}

/** 做语言检测用的样本。取第一个像样的章节即可 —— `detectLanguage` 本来就只采样开头。 */
function languageProbe(chapters: BookChapter[]): string {
  const substantial = chapters.find((chapter) => chapter.content.length > 400);
  return (substantial ?? chapters[0])?.content ?? '';
}

/** TXT / Markdown → 章节：一律喂给 Gutenberg 那条切分器（见它的注释，它与书源无关）。 */
export function textToBookChapters(rawText: string, fallbackTitle: string): BookChapter[] {
  return processGutenbergText(rawText, fallbackTitle);
}

export function htmlToBookChapters(html: string, fallbackTitle: string): BookChapter[] {
  return processGutenbergText(htmlToPlainText(html), fallbackTitle);
}

/**
 * 文件 → Book。这一层刻意很薄。
 *
 * 只有它碰 `File`（DOM 类型），所以测试够不到它 —— 这正是把解析全部留在上面几个纯函数
 * 里的原因：这里只剩「按扩展名分派」和「填元数据」两件无需测试的事。
 */
export async function readBookFile(file: File): Promise<Book> {
  const title = file.name.replace(/\.[^.]+$/, '').trim() || '导入的书';
  const kind = parseKindOf(file.name);
  if (kind === null) throw new Error(`不支持的文件类型：${file.name}`);

  let chapters: BookChapter[];
  if (kind === 'epub') {
    chapters = await epubToBookChapters(await file.arrayBuffer());
  } else if (kind === 'html') {
    chapters = htmlToBookChapters(await file.text(), title);
  } else if (kind === 'markdown') {
    chapters = textToBookChapters(markdownToPlainText(await file.text()), title);
  } else {
    chapters = textToBookChapters(await file.text(), title);
  }

  return {
    id: `custom-${Date.now()}`,
    title,
    author: 'User Imported',
    source: 'custom',
    language: detectLanguage(languageProbe(chapters)).language,
    chapters,
  };
}
