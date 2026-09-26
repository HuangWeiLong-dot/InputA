import { describe, expect, it } from 'vitest';
import {
  decodeEntities,
  epubToBookChapters,
  htmlToBookChapters,
  htmlToPlainText,
  markdownToPlainText,
  parseKindOf,
  textToBookChapters,
} from '../services/bookImport';

/**
 * 文件导入。
 *
 * 测试跑在 vitest 的 node 环境里（没有 jsdom），所以这里碰不到 `File`、`FileReader`、
 * `DOMParser` —— 这也正是解析层被写成纯函数的原因。EPUB 那几个用例是**在代码里现造 zip
 * 字节**（见 buildZip），因为要覆盖的正是格式里最容易出错的那几处：
 *
 *   - method 8（deflate）：绝大多数 EPUB 都用它
 *   - method 0（stored）：小文件可以完全不压缩
 *   - data descriptor（局部头 flag bit 3）：局部头里的尺寸是 0，只看它会解出空内容
 */

/* ------------------------------ 现造一个 zip ------------------------------ */

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i += 1) {
    let c = i;
    for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (const byte of bytes) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

async function deflateRaw(bytes: Uint8Array): Promise<Uint8Array> {
  // 泛型与断言的理由见 bookImport.ts 里 inflateRaw 的注释（TS 的 lib.dom 泛型摩擦）。
  const source: ReadableStream<BufferSource> = new ReadableStream<BufferSource>({
    start(controller) {
      controller.enqueue(bytes as BufferSource);
      controller.close();
    },
  });
  const compressed = source.pipeThrough(new CompressionStream('deflate-raw'));
  return new Uint8Array(await new Response(compressed).arrayBuffer());
}

interface ZipInput {
  name: string;
  data: string;
  /** true = 不压缩（method 0）。 */
  store?: boolean;
  /** true = 尺寸只写在数据之后的 descriptor 里（flag bit 3），局部头里是 0。 */
  dataDescriptor?: boolean;
}

/**
 * 造一个最小但合法的 zip（只含本地头、中央目录、EOCD；没有 ZIP64）。
 *
 * CRC 是认真算的，不是填 0 —— 否则这份测试夹具本身是个畸形文件，将来若给读取端加上
 * CRC 校验，测试会以「夹具坏了」的形式失败，而不是「读取端错了」。
 */
async function buildZip(files: ZipInput[]): Promise<ArrayBuffer> {
  const encoder = new TextEncoder();
  const parts: Uint8Array[] = [];
  const central: Uint8Array[] = [];
  let offset = 0;

  for (const file of files) {
    const raw = encoder.encode(file.data);
    const nameBytes = encoder.encode(file.name);
    const method = file.store ? 0 : 8;
    const body = file.store ? raw : await deflateRaw(raw);
    const crc = crc32(raw);

    // flag bit 11 = 文件名是 UTF-8；bit 3 = 尺寸由后面的 descriptor 给出。
    const flags = 0x0800 | (file.dataDescriptor ? 0x0008 : 0);

    const local = new Uint8Array(30 + nameBytes.length);
    const localView = new DataView(local.buffer);
    localView.setUint32(0, 0x04034b50, true);
    localView.setUint16(4, 20, true);
    localView.setUint16(6, flags, true);
    localView.setUint16(8, method, true);
    localView.setUint16(26, nameBytes.length, true);
    if (!file.dataDescriptor) {
      localView.setUint32(14, crc, true);
      localView.setUint32(18, body.length, true);
      localView.setUint32(22, raw.length, true);
    }
    local.set(nameBytes, 30);

    parts.push(local, body);

    if (file.dataDescriptor) {
      const descriptor = new Uint8Array(16);
      const descriptorView = new DataView(descriptor.buffer);
      descriptorView.setUint32(0, 0x08074b50, true);
      descriptorView.setUint32(4, crc, true);
      descriptorView.setUint32(8, body.length, true);
      descriptorView.setUint32(12, raw.length, true);
      parts.push(descriptor);
    }

    const entry = new Uint8Array(46 + nameBytes.length);
    const entryView = new DataView(entry.buffer);
    entryView.setUint32(0, 0x02014b50, true);
    entryView.setUint16(4, 20, true);
    entryView.setUint16(6, 20, true);
    entryView.setUint16(8, flags, true);
    entryView.setUint16(10, method, true);
    entryView.setUint32(16, crc, true);
    entryView.setUint32(20, body.length, true);
    entryView.setUint32(24, raw.length, true);
    entryView.setUint16(28, nameBytes.length, true);
    entryView.setUint32(42, offset, true);
    entry.set(nameBytes, 46);
    central.push(entry);

    offset += local.length + body.length + (file.dataDescriptor ? 16 : 0);
  }

  const centralSize = central.reduce((total, part) => total + part.length, 0);
  const eocd = new Uint8Array(22);
  const eocdView = new DataView(eocd.buffer);
  eocdView.setUint32(0, 0x06054b50, true);
  eocdView.setUint16(8, files.length, true);
  eocdView.setUint16(10, files.length, true);
  eocdView.setUint32(12, centralSize, true);
  eocdView.setUint32(16, offset, true);

  const all = [...parts, ...central, eocd];
  const total = all.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(total);
  let at = 0;
  for (const part of all) {
    out.set(part, at);
    at += part.length;
  }
  return out.buffer;
}

/* ------------------------------- EPUB 夹具 ------------------------------- */

const CONTAINER_XML = `<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>`;

const OPF = `<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata><dc:title>测试书</dc:title></metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="cover" linear="no"/>
    <itemref idref="c1"/>
    <itemref idref="c2"/>
  </spine>
</package>`;

const CH1 = `<html><head><title>测试书</title></head><body>
  <h1>Chapter One</h1><p>The quick brown fox jumps over the lazy dog.</p></body></html>`;

const CH2 = `<html><head><title>测试书</title></head><body>
  <h2>Chapter Two</h2><p>It was the best of times, it was the worst of times.</p></body></html>`;

/** 一份最小的合法 EPUB；`variant` 决定各条目怎么压缩。 */
function epubFiles(variant: 'deflate' | 'stored' | 'descriptor') {
  const options: Partial<ZipInput> =
    variant === 'stored' ? { store: true } : variant === 'descriptor' ? { dataDescriptor: true } : {};
  const ep = (name: string, data: string): ZipInput => ({ name, data, ...options });
  return [
    ep('mimetype', 'application/epub+zip'),
    ep('META-INF/container.xml', CONTAINER_XML),
    ep('OEBPS/content.opf', OPF),
    ep('OEBPS/nav.xhtml', '<html><body><nav>目录</nav></body></html>'),
    ep('OEBPS/cover.xhtml', '<html><body><img src="cover.png"/></body></html>'),
    ep('OEBPS/text/ch1.xhtml', CH1),
    ep('OEBPS/text/ch2.xhtml', CH2),
  ];
}

/* --------------------------------- 用例 --------------------------------- */

describe('decodeEntities', () => {
  it('handles named, decimal and hexadecimal entities', () => {
    expect(decodeEntities('a &amp; b &lt;c&gt; &#8217;quoted&#8217; &#x2014; dash')).toBe(
      'a & b <c> ’quoted’ — dash',
    );
  });

  it('keeps an entity it does not recognise, rather than eating it', () => {
    expect(decodeEntities('&nosuchentity; stays')).toBe('&nosuchentity; stays');
    expect(decodeEntities('&#x110000; out of range')).toBe('&#x110000; out of range');
    // 代理区的码点会让 fromCodePoint 抛异常，所以必须被挡下来。
    expect(decodeEntities('&#xD800; surrogate')).toBe('&#xD800; surrogate');
  });
});

describe('htmlToPlainText', () => {
  it('drops script and style content entirely', () => {
    const html = '<p>before</p><script>var x = "secret";</script><style>.a{color:red}</style><p>after</p>';
    expect(htmlToPlainText(html)).not.toContain('secret');
    expect(htmlToPlainText(html)).not.toContain('color');
    expect(htmlToPlainText(html)).toBe('before\n\nafter');
  });

  it('separates block elements with a blank line, which is what pagination keys on', () => {
    // 相邻块级元素之间留**空行**（\n\n）而不是单换行：`paginateText` 是按空行切段落的
    // （tokenizer.ts），单换行会把两段粘成一段、丢掉段落边界。
    expect(htmlToPlainText('<div>one</div><div>two</div>')).toBe('one\n\ntwo');
    expect(htmlToPlainText('<p>one</p><p>two</p>')).toBe('one\n\ntwo');
    // 段内的 <br> 是行内换行，只给一个换行 —— 不该被当成新段落。
    expect(htmlToPlainText('a<br/>b')).toBe('a\nb');
  });

  it('leaves inline tags without adding spaces', () => {
    // 重点：`<b>the</b> <i>cat</i>` 不能变成 "the  cat"，否则词间距就被标签弄乱了。
    expect(htmlToPlainText('<p><b>the</b> <i>cat</i></p>')).toBe('the cat');
  });

  it('decodes entities and collapses runs of whitespace', () => {
    expect(htmlToPlainText('<p>fish &amp; chips</p>\n\n\n<p>next</p>')).toBe('fish & chips\n\nnext');
    expect(htmlToPlainText('<p>a&nbsp;&nbsp;&nbsp;b</p>')).toBe('a b');
  });
});

describe('markdownToPlainText', () => {
  it('strips leading hashes so the chapter splitter can see the heading', () => {
    // 这一条有实际作用：不剥 `#`，`## CHAPTER II` 认不出，整本 md 会退化成一个均分块。
    expect(markdownToPlainText('## CHAPTER II\n\ntext')).toBe('CHAPTER II\n\ntext');
  });

  it('reduces a link to its label', () => {
    expect(markdownToPlainText('see [the docs](https://example.com) now')).toBe('see the docs now');
  });

  it('leaves emphasis markers alone rather than risking eaten text', () => {
    // 刻意保留：去掉 `**` 有吃掉正文的风险，而词本身仍然可点可查。
    expect(markdownToPlainText('**bold** and _it_')).toBe('**bold** and _it_');
  });
});

describe('parseKindOf', () => {
  it('recognises the supported extensions regardless of case', () => {
    expect(parseKindOf('book.epub')).toBe('epub');
    expect(parseKindOf('BOOK.EPUB')).toBe('epub');
    expect(parseKindOf('a.txt')).toBe('text');
    expect(parseKindOf('a.markdown')).toBe('markdown');
    expect(parseKindOf('a.HTM')).toBe('html');
  });

  it('rejects what it does not support', () => {
    expect(parseKindOf('paper.pdf')).toBeNull();
    expect(parseKindOf('no-extension')).toBeNull();
  });
});

describe('textToBookChapters', () => {
  it('splits on CHAPTER headings through the shared splitter', () => {
    // 每章正文都要超过 200 字符：切分器会把小于 200 字符的碎片并进前一章，
    // 夹具太短的话两章会被合并（这一点本身也是它的既有行为）。
    const text = [
      'CHAPTER I.',
      'It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.',
      '',
      'However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families.',
      '',
      'CHAPTER II.',
      'Mr Bennet was among the earliest of those who waited on Mr Bingley. He had always intended to visit him, though to the last always assuring his wife that he should not go.',
      '',
      'Till the evening after the visit was paid she had no knowledge of it. It was then disclosed in the following manner: he had been one of the first to wait on Mr Bingley.',
    ].join('\n');
    const chapters = textToBookChapters(text, 'Fallback');

    expect(chapters).toHaveLength(2);
    expect(chapters[0].title).toContain('CHAPTER I');
    expect(chapters[1].content).toContain('Mr Bennet');
  });

  it('falls back to one chapter for a short text with no headings', () => {
    const chapters = textToBookChapters('Just one short paragraph.', 'My Article');
    expect(chapters).toHaveLength(1);
    expect(chapters[0].title).toBe('My Article');
  });
});

describe('htmlToBookChapters', () => {
  it('strips markup before splitting', () => {
    const chapters = htmlToBookChapters('<h2>CHAPTER I</h2><p>Hello <b>world</b></p>', 'Untitled');
    expect(chapters).toHaveLength(1);
    expect(chapters[0].content).toContain('Hello world');
    expect(chapters[0].content).not.toContain('<');
  });
});

describe('epubToBookChapters', () => {
  it.each(['deflate', 'stored', 'descriptor'] as const)(
    'reads a minimal EPUB with %s entries',
    async (variant) => {
      const chapters = await epubToBookChapters(await buildZip(epubFiles(variant)));

      // 封面（linear="no"）与目录页（properties="nav"）都不该出现，所以正好两章。
      expect(chapters).toHaveLength(2);
      expect(chapters[0].title).toBe('Chapter One');
      expect(chapters[0].content).toContain('quick brown fox');
      expect(chapters[1].title).toBe('Chapter Two');
      expect(chapters[1].content).toContain('best of times');
    },
  );

  it('prefers the heading over <title>, which repeats the book name in every file', async () => {
    // 夹具里每章 <title> 都是「测试书」；若不优先看 <h1>，四十章会同名。
    const chapters = await epubToBookChapters(await buildZip(epubFiles('deflate')));
    expect(chapters.map((chapter) => chapter.title)).toEqual(['Chapter One', 'Chapter Two']);
  });

  it('skips a spine entry whose file is missing instead of failing the import', async () => {
    const files = epubFiles('deflate').filter((file) => file.name !== 'OEBPS/text/ch2.xhtml');
    const chapters = await epubToBookChapters(await buildZip(files));
    expect(chapters).toHaveLength(1);
  });

  it('reports a clear error when there is no container.xml', async () => {
    const files = epubFiles('deflate').filter((file) => file.name !== 'META-INF/container.xml');
    await expect(epubToBookChapters(await buildZip(files))).rejects.toThrow(/container\.xml/);
  });

  it('reports a clear error when nothing but images can be extracted', async () => {
    // 图片版（扫描件）的典型形态：spine 里**有**正经内容，但每页抽出来都是空。
    // 注意与「spine 是空的」区分开 —— 那是另一个错误，别让夹具走错分支。
    const imageOnlyOpf = `<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata><dc:title>扫描件</dc:title></metadata>
  <manifest><item id="p1" href="page1.xhtml" media-type="application/xhtml+xml"/></manifest>
  <spine><itemref idref="p1"/></spine>
</package>`;
    const files = [
      { name: 'META-INF/container.xml', data: CONTAINER_XML },
      { name: 'OEBPS/content.opf', data: imageOnlyOpf },
      { name: 'OEBPS/page1.xhtml', data: '<html><body><img src="page1.png" alt=""/></body></html>' },
    ];
    await expect(epubToBookChapters(await buildZip(files))).rejects.toThrow(/抽不出文字/);
  });

  it('rejects something that is not a zip at all', async () => {
    const notAZip = new TextEncoder().encode('this is plainly not a zip file');
    await expect(epubToBookChapters(notAZip.buffer as ArrayBuffer)).rejects.toThrow(/中央目录/);
  });
});
