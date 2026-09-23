# Comprehensible Input Reader · 语言学习阅读器

一个给英语学习者用的「可理解输入」阅读器：**点一下不认识的词，它记进生词本并显示释义；翻到下一页，这一页没点过的词自动算作「已掌握」。**

读完一本书，词汇量就自动分好级了 —— 不用手抄，也不用背词表。词库和阅读进度只存在你自己的浏览器里，没有账号，不上传。

**能做什么**

- 📖 三本经典名著样章开箱即读，也能搜索 Project Gutenberg 全库，或直接粘贴任意英文文章
- 👆 点词即查：中文释义、英文释义、音标、考试标签（中考 / 高考 / 四六级 / 考研 / 托福 / 雅思 / GRE）、BNC・COCA 词频、柯林斯星级、词形变化
- 🔊 朗读：优先词典真人音源，没有就用系统语音合成
- 🎚 5 级熟练度色阶，正文里颜色越深表示越不熟
- ✨ AI 语法拆解：把当前句子交给 DeepSeek 拆结构、讲从句、给翻译
- 🌗 明亮 / 羊皮纸 / 夜间三套主题，字号 16–30px 可调
- 💾 词库可搜索、朗读、导出 JSON 备份

技术栈：React 19 + TypeScript + Vite + Tailwind CSS v4 + Zustand，另含一个 Node 后端（`server/`，为绕开跨域而生）和一个只读的本地词典（`better-sqlite3` + `data/stardict.db`）。

---

## 快速开始

需要 **Node ≥ 22.18**（当前开发使用 24.x）。

```bash
npm install
npm start          # 构建 + 启动单源服务
```

然后打开 <http://localhost:8787>。

`npm start` 是单源模式：前端与后端同一个端口，零跨域。改代码要用热更新时，开两个终端：

```bash
npm run server   # 1) 代理后端 → http://localhost:8787
npm run dev      # 2) Vite → http://localhost:5173（/api 自动转发到后端）
```

端口可用 `API_PORT=9000 npm run server` 覆盖（Vite 会读取同名环境变量）。

> 想要**中文释义**还需要一个约 850MB 的词库文件，见下文〈可选：词库文件与 AI Key〉。
> 没有它也能正常阅读，只是没有中文释义与考试标签。

## 怎么用

### 1. 先选一本书

点顶栏左上角的「书本」，弹出「书本与书本导入」，三个标签页：

| 标签页 | 能做什么 |
| --- | --- |
| 经典名著 | 三本内置样章（《爱丽丝梦游仙境》《傲慢与偏见》《福尔摩斯探案集》），点一下立即开始 |
| Gutendex 搜索 | 按书名或作者搜索 Project Gutenberg 全库，用英文关键词，例如 `dracula` / `austen` / `time` |
| 粘贴 | 把任意英文文章、新闻、故事或教材文本贴进来，可填一个标题 —— 适合读自己找的材料 |

### 2. 阅读与点词

- **点击任意单词** → 记入生词本（5 级「生词」，正文里最深的高亮），同时弹出释义面板；
- **翻页**：点「上一页 / 下一页」，或用键盘 **← / →**、**PageUp / PageDown**（弹窗打开或输入框聚焦时，快捷键自动让位给那个控件）；
- 每页约 220 词，导入的长文会按章节标题自动切章。

### 3. 释义面板里能做什么

| 区域 | 说明 |
| --- | --- |
| 顶部状态 | 这个词当前的熟练度（未标记 / 1–5 级 / 已掌握）与释义来源 |
| 音标旁的 5 段色阶 | 点一下手动指定熟练度：1 最浅（熟知）→ 5 最深（生词） |
| 中文释义 | 来自本地词库，附考试标签、词性占比、词频、柯林斯星级 |
| 英文释义 | 按词性分组，来自在线词典 |
| 当前所在句子 | 显示这个词所在的整句，右侧「AI 拆解语法 →」可直接送去分析 |
| 底部按钮 | 🔊 朗读、标记为已掌握 / 标为生词、移出词汇库 |

### 4. 词汇库

顶栏的「… 未掌握」按钮打开词汇库：按「未掌握 / 已掌握 / 全部」筛选，可搜索、逐词朗读、改状态，并且能**导出 JSON 备份**（换电脑或清缓存前建议导一份）。「清空词库」不可撤销。

### 5. 主题与字号

顶栏中间三个图标切换**明亮 / 羊皮纸 / 夜间**，默认是羊皮纸。字号用 A− / A+ 调，范围 16–30px，默认 20px。

### 6. AI 语法拆解

顶栏「AI 分析」按钮打开面板，可以直接粘一段句子；也可以点释义面板里的「AI 拆解语法 →」把当前句子带过去。**需要 DeepSeek API Key**，两种放法：

- 填在面板里的输入框 → 存进浏览器 localStorage，随请求头发送；
- 或放在后端环境变量 `DEEPSEEK_API_KEY=sk-...`（此时前端会自动省略 Authorization 头）。

没有 Key 时面板会显示一段**内置示例输出**，标题里写明「填入 DeepSeek API Key 可获得真实模型分析」——
别把它当成真实结果。

## 词汇学习规则

每个收录的词带一个熟练度，**颜色越深表示越不熟**（界面上只显示标签与颜色，不显示数字）：

| 熟练度 | 标签 | |
| --- | --- | --- |
| 1 | 熟知 | 最浅 |
| 2 | 熟悉 | |
| 3 | 一般 | |
| 4 | 模糊 | |
| 5 | 生词 | 最深 |
| — | 已掌握 | 独立的第 6 个状态，正文不再高亮 |

- **点击正文里的单词** → 记为 5 级「生词」（最深），同时打开释义面板；已经收录过（含已掌握）的词只查释义、不改状态；
- **翻到下一页** → 本页未被点击的词自动记为「已掌握」（**不会覆盖**已经收录的词，用户手选的等级优先）；
- 释义面板的音标旁有 5 段色阶，**点击即可手动指定任意一级**；
- 底部的「标记为已掌握」按钮决定「掌握」状态，掌握后正文不再高亮，写作时不再分散注意力；
- 词库按「未掌握 / 已掌握」筛选，每行显示该词的熟练度名称与对应颜色，支持搜索、朗读、导出 JSON。

色阶不是写死的 5 组颜色：每一级由主题的 `--level-ink` 与页面底色按比例混合（`color-mix`）得到，
因此三个主题各自协调、必然单调递增：色相统一在黄色（明亮 95° / 羊皮纸 93° / 夜间 91°），
只有墨色明度随底色变化，夜间是「深底上逐渐变亮的黄」而不是把浅黄直接搬过去。见 `src/index.css`。

旧版本只有 `learning` / `known` 两态，读取 localStorage 时会自动迁移，
老用户的词库不会丢：`learning → 5`、`known → 已掌握`（见 `src/utils/wordLevel.ts`）。

## 数据存在哪里

没有账号，也没有服务端数据库。所有内容都在**你当前浏览器的 localStorage** 里：

| 键 | 内容 |
| --- | --- |
| `language_reader_vocabulary` | 词库：每个词的状态（1–5 级或已掌握） |
| `language_reader_progress` | 读到哪本书、第几章、第几页 |
| `language_reader_settings` | 主题、字号、行高 |
| `deepseek_api_key` | 你在浏览器里填的 DeepSeek Key |

因此：换个浏览器或清掉缓存，词库就空了（**先用词汇库的「导出」备份**）；无痕窗口关掉即消失。

## 可选：词库文件与 AI Key

### 词库文件（约 850MB，不随仓库提供）

中文释义、考试标签、词频、柯林斯星级都来自一个离线 ECDICT 词库（约 340 万词条，SQLite 格式）。
把下载到的文件放到 `data/stardict.db` 即可：

- 数据源：[ECDICT](https://github.com/skywind3000/ECDICT)；
- 想放到别处就设 `STARDICT_DB=/path/to/stardict.db`；
- 文件不存在时 `/api/dict` 返回 `503`，其余接口照常工作。前端会通过 `/api/health` 的
  `dictionaryAvailable: false` 识别并跳过它 —— 阅读、英文释义、朗读都不受影响，只是没有中文释义。

程序用 `readonly: true` + `fileMustExist: true` 打开它，**只读、不写、不会替你建库**。

## 常见问题

| 症状 | 原因与处理 |
| --- | --- |
| 点词后没有中文释义 | 缺 `data/stardict.db`，见上一节 |
| 界面提示「请运行 `npm run server`」 | 只跑了 `npm run dev`，后端没启动 |
| 搜到了书却打不开正文 | 同上：Gutenberg 正文必须走后端（它不返回 CORS 头，且 302 到明文 http 地址） |
| AI 分析报错或没有回复 | 没配 DeepSeek Key，或 Key 已失效 |
| 换浏览器后词库空了 | 词库存在浏览器本地，用「导出 JSON」搬过去 |
| 端口 8787 被占用 | `API_PORT=9000 npm run server` |

---

以下为开发文档。

## 为什么需要后端

浏览器无法直接访问这些上游：

| 上游 | 问题 |
| --- | --- |
| Project Gutenberg 正文 | 不返回 `Access-Control-Allow-Origin`；且 `ebooks/<id>.txt.utf-8` 会 302 到**明文 http** 的 cache 地址（HTTPS 页面下属混合内容，必被拦截） |
| api.dictionaryapi.dev | 源站故障（522）时返回的错误页没有任何 CORS 头，浏览器直接报 `blocked by CORS policy` / `ERR_FAILED` |
| api.deepseek.com | 完全不接受浏览器跨域调用 |

后端在服务端发起这些请求：没有 CORS 限制，并且可以安全地跟随 http 重定向。

## 后端路由

| 方法 | 路由 | 转发到 |
| --- | --- | --- |
| GET | `/api/health` | —（前端用它判断是否走同源路由） |
| GET | `/api/books/search?query=` | `gutendex.com/books?search=&languages=en` |
| GET | `/api/books/text?url=` | `www.gutenberg.org`（**服务端跟随 302**，仅白名单主机，SSRF 防护） |
| GET | `/api/dict?word=` | —（本地 SQLite `data/stardict.db`，ECDICT，只读） |
| GET | `/api/dictionary/word/:word` | `api.dictionaryapi.dev` |
| GET | `/api/dictionary/wiktionary/:word` | `en.wiktionary.org` REST |
| GET | `/api/dictionary/datamuse/:word` | `api.datamuse.com` |
| POST | `/api/ai/chat` | `api.deepseek.com/chat/completions` |

上游的**状态码原样透传**（例如查无此词仍是 404），因此前端的降级与缓存逻辑保持有效。

Node 版本要求来自这里：后端 `server/dictLookup.ts` 直接由 Node 内置的 TypeScript 类型剥离加载，
后端因此没有构建步骤；`better-sqlite3` 走 Node 官方预编译二进制，无需本地编译工具链。

## 离线词典：`GET /api/dict?word={单词}`

`data/stardict.db` 是 ECDICT（StarDict 词库，约 340 万词条）的 SQLite 文件，后端用 `better-sqlite3`
以 `readonly: true` + `fileMustExist: true` 打开 —— **只读、不写、不建库**。查询走 `word` / `sw` 索引，
约 1ms 返回，不产生任何外部请求。

```bash
curl "http://localhost:8787/api/dict?word=very"
```

```json
{
  "query": "very",
  "word": "very",
  "matchedBy": "word",
  "phonetic": "'veri",
  "translation": "a. 真正的, 恰好的, 十足的, 特有的\nadv. 非常, 完全",
  "definition": "s. precisely as stated\nr. used as intensifiers; ...",
  "pos": "r:92/j:8",
  "partsOfSpeech": [{ "code": "r", "label": "副词", "abbr": "adv.", "percent": 92 }],
  "tag": "zk gk",
  "tags": [{ "code": "zk", "label": "中考" }, { "code": "gk", "label": "高考" }],
  "lemma": null,
  "inflection": null,
  "forms": [],
  "collins": 5,
  "oxford": true,
  "bnc": 80,
  "frq": 105,
  "audio": null
}
```

| 字段 | 含义 |
| --- | --- |
| `phonetic` | 音标，库中原样（`'veri` / `rʌn`），本词条缺失时用原形的补全 |
| `translation` / `definition` | 中文释义 / 英文释义，多行 |
| `partsOfSpeech` / `pos` | 词性（解析后，按占比降序 / 原始占比串） |
| `tags` / `tag` | 考试标签：中考 `zk`、高考 `gk`、四级 `cet4`、六级 `cet6`、考研 `ky`、托福 `toefl`、雅思 `ielts`、GRE |
| `lemma` / `inflection` / `forms` | 原形、本词条的变形类型、全部词形变化（`running` → `run` / 现在分词） |
| `collins` / `oxford` / `bnc` / `frq` | 柯林斯星级 1-5、牛津核心词、BNC 与当代语料库词频排名 |

| 情况 | 响应 |
| --- | --- |
| 命中 | `200` + 词条 JSON（UTF-8） |
| 词库中没有（含空词、纯符号） | `404`，`lookupWord()` 返回 `null`，不抛异常 |
| 缺少 `word` 参数 | `400` |
| 非 GET | `405` |
| `data/stardict.db` 不存在 | `503`，其余接口照常工作，`/api/health` 中 `dictionaryAvailable: false` |

查询词先规范化（小写、去首尾标点、压缩空白、弯撇号 `’`→`'`），因此 `Very`、`"very,"`、`don’t` 都能命中；
精确匹配失败时再用 ECDICT 的 `sw` 列兜底（忽略空格与连字符），例如 `a couchpotato` 能查到 `a couch potato`。

### 前端如何使用

`src/services/dictionaryApi.ts` 的查询链按「信息量 + 速度」排序：

```
local-ecdict（GET /api/dict，离线，约 1ms）
  → dictionaryapi.dev（英文释义 + 发音）
  → wiktionary
  → datamuse
```

- 命中本地词库即返回，不再发出任何在线请求；`DictionaryEntry.extra` 里带上中文释义、考试标签、
  词性分布、原形/词形变化与柯林斯星级、BNC/COCA 词频，释义面板会单独渲染「中文释义」区块与标签。
- 本地词库是**可选**来源：`/api/health` 里 `dictionaryAvailable` 为 `false` 时直接跳过它（不浪费请求）；
  它自身超时或 5xx 只记日志，不会把「词库里没有这个词」误报成「词典服务不可用」。
- 英文释义按行首的 WordNet 词性代码（`n.` / `v.` / `a.` / `s.` / `r.`）分组后填入 `meanings`，
  因此在线来源与离线来源在界面上是同一种结构；ECDICT 不含例句，所以离线释义没有 `example`。
- 词条原文与点击的词不一致时（如点 `a couchpotato` 命中 `a couch potato`）面板会显示「词库词条」，
  变形词则显示可点击的「原形 run（现在分词）」。

## 导入一本书的两步流程

1. **搜索**：Gutendex 返回书目信息 + `formats` 下载链接表；
2. **下载正文**：取 `formats` 里 `text/plain*` 的链接（UTF-8 优先）再请求一次，拿到整本书的纯文本；
3. 后端返回后，前端剥掉 Project Gutenberg 法务头尾，按 `CHAPTER I.` / `Letter 2` 等标题切章（没有标题则按约 1500 词切分）。

## 没有后端时的降级

只跑 `npm run dev`（未启动后端）时：健康探测失败，前端改为直连上游 —— Gutendex 搜索、Wiktionary、Datamuse 自带 CORS 仍可用；**Gutenberg 正文、离线词典与 DeepSeek 会失败**，界面上会给出「请运行 `npm run server`」的明确提示。

## 脚本

| 命令 | 作用 |
| --- | --- |
| `npm run dev` | Vite 开发服务器（HMR） |
| `npm run server` | 启动 Node 代理后端 |
| `npm start` | 构建 + 单源生产服务 |
| `npm run build` | `tsc -b && vite build` |
| `npm test` | Vitest（离线词典 `server/dictLookup.test.ts`、词典降级链、Gutendex 导入、分词/分页/词汇规则） |
| `npm run lint` | oxlint |
