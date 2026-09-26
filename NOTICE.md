# 第三方来源与许可

本项目的部分功能移植自 [LingKuma](https://github.com/lingkuma/LingKuma)（MIT 许可），
图标几何取自 [lucide](https://lucide.dev)（ISC 许可）。下面逐项列出搬运了什么、
对应到本仓库的哪个文件，以便追溯。

## LingKuma

Copyright © 2024 lingkuma <linghikki@gmail.com> · MIT License（全文见文末）

| 本仓库 | 来源（LingKuma） | 搬运内容 |
| --- | --- | --- |
| `src/services/aiPrompts.ts` | `src/options/options.js`、`src/service/a3_aiFragen.js` | AI 提示词原文：单词/短语翻译、语法精要、例句翻译、语法拆解 few-shot、追问对话 |
| `src/services/languageDetect.ts` | `src/service/a7_words_boom.js` | 中日韩判定所用的字符区间，以及「日文优先于中文」的判别顺序 |
| `src/utils/bionic.ts` | `src/plugin/bionic.js` | 仿生阅读的加粗规则（词长 40%，向上取整）与跳过中日韩的取舍 |
| `server/edgeTts.ts` | `src/plugin/edge_tts.js` | Edge TTS 的 WebSocket 协议：端点、`Sec-MS-GEC` 令牌算法、`speech.config` 与 SSML 报文、音频帧的切分 |
| `src/utils/wordExtraction.ts` | `src/service/a7_words_boom.js` | 单词爆炸的取词与去重思路（分词改用本项目自己的 tokenizer，理由见该文件注释） |
| `src/services/ttsService.ts` | `src/plugin/tts.js` | 按语言自动选 Edge 音色的对照表 |
| `src/store/useAnnotationStore.ts` | `background.js` | 「单词笔记」即一组自由文本、「同句只存一条」的数据形状 |

移植时对若干处做了修正，每一处都在对应文件的注释里写明了原因，例如：Edge TTS 的
音频帧改为按长度前缀解析、SSML 文本做了 XML 转义、`xml:lang` 不再写死为 `en-US`、
单词爆炸的分词改为复用本仓库的 tokenizer 以保证与词库键一致。

### 未采用的部分

LingKuma 自带的内置 API 池（`background.js` 的 `applyDefaultConfig`，含若干
base64 混淆的第三方 key）**没有**被搬运。本项目只使用用户自己配置的 DeepSeek Key。

其语言检测模型 `efficient-language-detector-js`（Apache-2.0，单文件约 982KB）
也**没有**被捆绑：本项目改用一个几 KB 的启发式实现（`src/services/languageDetect.ts`），
因为这里的用途只是显示标签与选音色。

---

## lucide

Copyright © 2026 Lucide Icons and Contributors · ISC License（全文见下）

| 本仓库 | 来源（lucide） | 搬运内容 |
| --- | --- | --- |
| `src/**/*.tsx` | `lucide-react` ^1.46.0 | Web 版界面里全部图标的**选择与用法**（`BookOpen`、`BookMarked`、`ChevronLeft`、`Check`、`Sparkles`、`Volume2` 等，共十余个） |
| `android/app/src/main/kotlin/com/inputa/reader/ui/icons/InputaIcons.kt` | 同上，逐条取自 `dist/esm/icons/` 下对应文件 | 十六个图标在 24×24 视口下的**几何**，即每个 `<path>` 的 `d` 属性原文，改写为 Compose 的 `ImageVector` |

Android 端**没有**搬运 SVG 文件本身，也不依赖任何图标库：把路径数据转写成 Kotlin 的
`ImageVector` 是为了让「24×24 视口、2 描边、圆头圆角连接、20dp 渲染」这套规格只写一处，
并可被 `InputaIconsTest` 断言。转写时把 lucide 的 `<circle>` 元素改写成了等价的
两段圆弧（`ImageVector` 没有圆形节点），其余路径逐字保留 —— 所以形状与 Web 版一致。

---

## MIT License

```
The MIT License (MIT)

Copyright © 2024 lingkuma linghikki@gmail.com

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

## ISC License

```
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

## 其他数据来源

- **ECDICT**（`data/stardict.db`，不随仓库分发）—— 离线词库，见 README。
- **Project Gutenberg / Gutendex** —— 公版书正文与书目检索。
- **Edge TTS**（`speech.platform.bing.com`）—— 微软为 Edge 朗读功能提供的服务，
  属于非公开接口，本项目仅作代理转发，不保证可用性。
