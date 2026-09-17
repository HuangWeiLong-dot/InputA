# Comprehensible Input Reader · 语言学习阅读器

可理解输入（Comprehensible Input）阅读器：点击任意单词即收录为生词并查看释义，翻页时本页未点击的词自动记为「已会」。

React 19 + TypeScript + Vite + Tailwind CSS v4 + Zustand，另含一个 Node 后端（`server/`）用于解决跨域，以及一个只读的本地词典（`better-sqlite3` + `data/stardict.db`）。

## 为什么需要后端

浏览器无法直接访问这些上游：

| 上游 | 问题 |
| --- | --- |
| Project Gutenberg 正文 | 不返回 `Access-Control-Allow-Origin`；且 `ebooks/<id>.txt.utf-8` 会 302 到**明文 http** 的 cache 地址（HTTPS 页面下属混合内容，必被拦截） |
| api.dictionaryapi.dev | 源站故障（522）时返回的错误页没有任何 CORS 头，浏览器直接报 `blocked by CORS policy` / `ERR_FAILED` |
| api.deepseek.com | 完全不接受浏览器跨域调用 |

后端在服务端发起这些请求：没有 CORS 限制，并且可以安全地跟随 http 重定向。

## 运行方式

**开发（两个终端）**

```bash
npm run server   # 1) 代理后端 → http://localhost:8787
npm run dev      # 2) Vite → http://localhost:5173（/api 自动转发到后端）
```

**生产（单源，零跨域）**

```bash
npm start        # = npm run build && node server/index.js
# 然后打开 http://localhost:8787
```

后端端口可用 `API_PORT=9000 npm run server` 覆盖（Vite 会读取同名环境变量）。

DeepSeek Key 有两种放法：浏览器里填写（存 localStorage，随请求头发送），或放在后端环境变量 `DEEPSEEK_API_KEY=sk-...`（此时前端会自动省略 Authorization 头）。

需要 Node ≥ 22.18（当前开发使用 24.x）：后端 `server/dictLookup.ts` 直接由 Node 内置的 TypeScript 类型剥离加载，后端因此没有构建步骤；`better-sqlite3` 走 Node 官方预编译二进制，无需本地编译工具链。

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

词库文件可用环境变量 `STARDICT_DB=/path/to/stardict.db` 指向别处，数据源自 [ECDICT](https://github.com/skywind3000/ECDICT)，
未随仓库提供（约 850MB），缺失时该接口自动降级为 503。

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

## 词汇学习规则

- 点击单词 → 立即标记为 `learning`（琥珀色高亮）并写入 localStorage；
- 翻到下一页 → 本页未点击的词自动记为 `known`（**不会覆盖** `learning`）；
- 词库支持筛选、搜索、朗读、导出 JSON。

## 脚本

| 命令 | 作用 |
| --- | --- |
| `npm run dev` | Vite 开发服务器（HMR） |
| `npm run server` | 启动 Node 代理后端 |
| `npm start` | 构建 + 单源生产服务 |
| `npm run build` | `tsc -b && vite build` |
| `npm test` | Vitest（离线词典 `server/dictLookup.test.ts`、词典降级链、Gutendex 导入、分词/分页/词汇规则） |
| `npm run lint` | oxlint |
