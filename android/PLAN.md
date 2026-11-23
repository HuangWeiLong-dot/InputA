# InputA · 重构方案（Android · 深色 · 1:1 LingQ 7）

> 状态：待确认后开工。  
> 目标：在 `android/` 从零重写 Android 客户端，完全复刻 LingQ 7.0.28 的课程 / 阅读器 / 词库 / 复习 / 统计 / AI，只做深色主题。  
> 内容与账号：直连 LingQ 官方 API（自用测试），不自建后端。

---

## 1. 范围与原则

- **平台**：只做 Android。Web 端归档，不参与本次重构。
- **代码位置**：`InputA/android/` 全新工程；旧 Web 端与旧原生版已移除。
- **主题**：只做深色（截图 1:1）。浅色/羊皮纸后续再说。
- **后端**：无自建服务。词典/课程/词库/统计/AI 全部走 LingQ 官方接口。
- **认证**：用户名 + 密码 → DRF Token（`Authorization: Token <token>`），只存本机。
- **原则**：先复刻真实交互，再谈优化；不造服务端没有的数据。

---

## 2. 技术栈

- Kotlin + Jetpack Compose（Material 3 基础，视觉全部自定义）
- Hilt（DI）
- Room（课程缓存、词库、进度、复习、统计）
- DataStore（设置、登录 token 元信息）
- OkHttp + kotlinx.serialization（LingQ API）
- Coil（课程封面）
- 系统 `android.speech.tts` + LingQ TTS 接口（按接口能力取舍）
- 导航：Navigation-Compose（或单 Activity 手写状态，二选一，开工时定）

---

## 3. 模块划分

```
android/
  app/                     # 单 Activity、导航壳、底栏
  core/design/             # 深色令牌、字体、组件（按钮/卡片/胶囊/底栏/毛玻璃）
  core/data/               # LingQ API、Room、DataStore、仓库实现
  core/domain/             # 模型与用例（纯 Kotlin）
  feature/auth/            # 登录
  feature/home/            # 首页
  feature/discover/        # 发现/课程列表
  feature/lesson/          # 课程预览
  feature/reader/          # 阅读器
  feature/vocabulary/      # 词库
  feature/review/          # 复习（闪卡/填空/听写/配对/句子）
  feature/stats/           # 统计
  feature/playlist/        # 播放列表
  feature/settings/        # 设置
  feature/ai/              # 狐狸 AI / Lynx
```

依赖方向：`app → feature → core/domain ← core/data`；feature 之间不互相依赖。

---

## 4. 页面清单（逐张对应截图）

| 页面 | 关键内容 |
| --- | --- |
| 登录 | 邮箱/用户名 + 密码；错误提示；token 持久化 |
| 首页 | 头像 + 旗帜角标 + 姓名 + 设置；黑色 banner；`N Known Words` 蓝青带 + `All Stats`；今日目标卡；Continue Studying 课程卡（蓝/黄/白计数、进度条、`% New Words`、等级、时长）；Picked For You；5 槽毛玻璃底栏 |
| 发现 | 搜索 `What interests you?`；等级/来源/标签筛选；课程卡；不感兴趣/不推荐 |
| 课程预览 | 标题/封面、总词/独立词/新词、时长、`Start Lesson`、订阅课程、更多菜单 |
| 阅读器 | 顶栏返回 + 进度线 + 狐狸 AI；正文点词；词弹窗（等级 1–4、标签、三词典、短语、从此处读、忽略/掌握）；底栏播放/模式/闪卡/上下翻页/AI；模式菜单（Page/Sentence/Scroll）；阅读器菜单底部面板；蓝词结算 |
| 词库 | 关闭、列表/卡片切换、搜索、`Importance`、`All N terms`、等级圆标 + 词 + 释义 + 朗读 |
| 复习 | 活动选择：闪卡/填空/听写/配对 + 本书闪卡/句子复习；翻卡、判分、结算 |
| 统计 | 顶部进度环；7 天活动；统计明细；Known Words 知识水平环；LingQ 有数据的金币/连续天数则接真实接口 |
| 播放列表 | 当前列表、队列、播放器 |
| 设置 | LEARNING / LANGUAGES / APP / HELP / ACCOUNT 分组，按可用接口取舍 |
| AI（狐狸） | 对话、课程上下文、建议问题、配置（若走 LingQ Lynx 则用官方额度） |

---

## 5. 数据模型（本地）

- `Session(token, userId, username, avatarUrl)`
- `Language(code, name, flag)`
- `Course(id, title, imageUrl, language, level, lessonCount, isSubscribed)`
- `Lesson(id, courseId, title, audioUrl, durationSec, wordCount, uniqueWordCount, newWordCount, text, sentences)`
- `TokenStatus(term, status: 0..4/-1, importance, updatedAt)`
- `Card(id, term, status, dueAt, interval, ease)`
- `ReadingProgress(lessonId, page, sentence, percent, updatedAt)`
- `DailyStats(day, wordsRead, pagesRead, lingqsCreated, listeningSec)`
- `PlaylistItem(lessonId, order)`
- `Settings(theme=dark, fontSize, lineHeight, ttsVoice, dailyGoal, popup options, ai config)`

---

## 6. LingQ API 对接（来自 APK 逆向文档）

基址：`https://www.lingq.com/`（备用 `https://api.lingq.com/`）。

| 能力 | 端点 |
| --- | --- |
| 登录 | `POST api/v2/api-token-auth/`（form: username/password） |
| 资料 | `GET api/v2/profiles/{pk}/`、`GET api/v3/profiles/` |
| 课程/资料库 | `api/v3/{language}/lessons/*`、`shelves/*`、`collections/*` |
| 课程正文 | `lessons/{id}/text`、`sentences`（GET/POST）、`words`、`info`、`bookmark`、`complete` |
| 课程统计 | `lesson-stats`、`counters` |
| 搜索 | `search/`、`search/fast/`、`lesson-tags`、`tag-cloud` |
| 词状态 | `known-words/`、`ignored-words/`、`hints/*`、`user-dictionaries/*` |
| SRS | `cards/*`（列表/创建/PATCH/examples/review/bulk） |
| 进度/统计 | `progress/*`、`streak`、`study-stats`、`chart_data`、`stats` |
| 播放列表 | `playlists/*`（按实际端点补） |
| TTS | `v3 tts/`、`lessons/{id}/gentts/`、`tts-voices/` |
| AI / Lynx | `chat/*`（create-stream、respond-stream、config、messages） |
| 挑战/徽章 | `challenges/*`、`milestones`、`badges`（有数据才显示） |

请求头：`Authorization: Token <token>`、`Accept: application/json`、`Content-Type: application/x-www-form-urlencoded`（表单端点）。

> 风险：这是非公开接口，字段与额度可能变化；先按逆向文档实现，运行时用真实账号验证。

---

## 7. 登录与安全

- 登录页输入用户名/密码 → 调 `api-token-auth/` → 拿 `token`。
- token 存 `EncryptedSharedPreferences`（不写日志、不进仓库）。
- 401：清 session，回登录页。
- 不在聊天/代码里传真实账号密码；需要验证时由你在设备上输入。

---

## 8. 设计系统（深色）

- 底色 `#1C1C1E`、卡片 `#2C2C2E`、次级面 `#3A3A3C`、描边 `#3A3A3C`
- 主蓝 `#5B9BE6`、成功绿 `#34C759`、危险红 `#FF453A`
- 词等级：蓝 `#2D4481`、黄 `#675126/#4D3E13/#332B17`
- 圆角：卡片 20、胶囊 999、封面 18；控件 12
- 字体：DM Sans（界面）+ 衬线正文
- 底栏：悬浮毛玻璃胶囊、图标选中态胶囊、按下态
- 所有可点元素都有按下反馈（缩放/底色/透明度）

---

## 9. 分阶段任务

**P0 骨架**
- `android/` Gradle 工程、Hilt、主题、深色令牌、底栏壳、导航
- 登录页 + token 存储 + 401 处理

**P1 内容浏览**
- 首页（继续学习、Known Words、今日目标、推荐）
- 发现（搜索/筛选/课程卡）
- 课程预览

**P2 阅读器**
- Page / Sentence / Scroll 三种模式
- 点词弹窗、等级、短语、三词典、TTS、读书进度
- 顶栏进度 + 狐狸 AI 入口；底栏播放/模式/闪卡/翻页/AI
- 蓝词结算

**P3 词库与复习**
- 词库平铺列表、Importance、等级圆标、朗读
- 闪卡/填空/听写/配对/句子复习
- 翻页自动收录与撤销

**P4 统计、AI、播放列表、设置**
- 统计页（真实接口数据）
- Lynx AI 对话（真实接口）
- 播放列表、设置分组

**P5 1:1 校准**
- 与截图逐屏对照（间距、字号、颜色、按下态、毛玻璃）
- 真机验证与性能检查

---

## 10. 验收标准

- 每张参考截图都有对应页面与状态（正常/按下/空/加载/错误）。
- 登录、课程列表、正文、点词、词状态、复习、统计走真实 LingQ 接口。
- 断网时有明确错误与重试，不显示假数据。
- 深色主题下对比度可读；底栏毛玻璃与按下态一致。
- 不包含任何硬编码密钥、账号或后端地址。

---

## 11. 需要你提供的

1. 一个用于自测的 LingQ 账号（登录由你在设备上输入，不要发到聊天里）。
2. 确认是否接受“非公开接口可能变化”的风险。
3. 确认从 P0 开始直接写代码；若要先看交互原型，也可以先做 P0 的可运行壳。

---

## 12. 下一步

- 你确认本方案后，我创建 `android/` 工程并完成 **P0（壳 + 深色设计系统 + 登录）**。
- P0 装到手机验收后，再按 P1 → P5 推进。
