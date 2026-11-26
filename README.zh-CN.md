<div align="center">

# InputA

[English](README.md) | [简体中文](README.zh-CN.md)

![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Web-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![version](https://img.shields.io/badge/version-0.4.0-blue)
![License](https://img.shields.io/badge/license-MIT-3DA639)

一个非官方、本地优先的 [LingQ](https://www.lingq.com/) 阅读器。Android 提供完整功能；React Web 首版聚焦核心学习闭环：登录、首页、发现、课程/合集预览、阅读、本地词库与闪卡复习。

</div>

> 这是一个直连 LingQ 内部接口的个人自用客户端，与 LingQ 官方无关，接口随时可能变化。使用前需要你自己的 LingQ 账号。

## 功能

- **登录**：支持 LingQ 用户名/邮箱 + 密码，或直接粘贴已有的 API Token。
- **首页**：已知 / 学习中 / 已忽略词数、每日目标、继续学习、按等级分组的推荐、已收藏课程、语言与资料切换。
- **发现**：书架浏览、搜索、等级筛选、来源筛选（LingQ、Netflix、TED 等），内容来自 LingQ 资料库。
- **课程预览与合集页**：封面、简介、总词数、音频时长、课程列表、收藏、加入播放列表、进入复习。
- **阅读器**：Page / Sentence / Scroll 三种模式；点击任意单词查看词典释义、发音、修改词状态、请求 AI 讲解；按真实排版高度分页；可调字号、行高、词距和段距；课程音频支持拖动与自动朗读。
- **词库与复习**：本机词表，支持搜索和全部 / Inputa / 已知 / 已忽略筛选、修改状态、按需查释义，以及自评式闪卡复习。
- **统计**：已知词、学习中词、已读词、每日目标进度、今日学习词数，全部由本机记录计算。
- **播放列表**：保存在设备上的音频队列与内置播放器。
- **AI 工具**：单词精讲与导师对话，使用你自己配置的 OpenAI 兼容接口。
- **导入**：通过 LingQ 的导入接口，从文章 URL 或粘贴文本创建课程。
- **每日提醒**：在自定义时间发送本地通知。
- **中英文界面切换**；AI 讲解可选英文、中文、日文或韩文。

## 技术栈

| 层级 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose、Material 3、自定义深色设计系统 |
| 架构 | 单 Activity、MVVM、StateFlow |
| 依赖注入 | Hilt |
| 网络 | OkHttp、手写 LingQ API 客户端 |
| JSON | kotlinx.serialization |
| 本地存储 | SharedPreferences + EncryptedSharedPreferences |
| 音频 | MediaPlayer + OkHttp 下载缓存，系统 TextToSpeech 兜底 |
| 构建 | Gradle 9.6、AGP 9.4、JDK 17、compile/target SDK 37、min SDK 26 |

项目刻意没有引入 Room、Navigation-Compose 或 Coil，而是保留了自己的一套轻量存储与图片加载实现。

## 数据与隐私

- **没有业务后端或数据库**。Android 直接访问 `https://www.lingq.com/`；由于 LingQ 未开放浏览器 CORS，Web 使用最小同源转发代理。
- Android 的 LingQ Token 保存在 `EncryptedSharedPreferences`；Web 只在内存中保留 Token，刷新后需重新登录。两端都不会记录 Token。
- 词状态、收藏、播放列表、上次阅读、阅读设置和 AI 讲解缓存都只存在本机。
- 密码仅用于换取 Token，不会被保存。
- AI 请求只会发往**你自己**配置的 OpenAI 兼容接口。

词状态有意只保存在本地、不回写 LingQ，因此这里显示的词库和统计可能与你 LingQ 账号里的数据不同。

## 构建与运行

环境要求：JDK 17、带 API 37 的 Android SDK，以及 Android 8.0（API 26）或更高版本的设备/模拟器。

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Windows 下可使用 `gradlew.bat`，或在 Git Bash 中使用 `./gradlew`。

Release 构建会读取 `android/keystore.properties`（已被 gitignore）：

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
cd android
./gradlew :app:assembleRelease
```

### Web

需要 Node.js 24。完整的 Web 开发、部署与测试说明见 [web/README.md](web/README.md)。

```bash
cd web
npm ci
npm run dev
```

```bash
cd web
npm run typecheck
npm run lint
npm test
npm run test:server
npm run test:e2e
npm run build
npm start
```

## 项目结构

```text
android/
  app/src/main/kotlin/com/inputa/next/
    MainActivity.kt        # 单 Activity，手写页面导航
    core/data/             # LingQ API 客户端、数据模型、会话与本地存储
    core/design/           # 深色设计令牌、通用组件、图标、本地化、图片加载
    core/media/            # 音频下载/播放与发音
    core/notify/           # 每日阅读提醒
    feature/               # auth、home、explore、lesson、course、reader、vocabulary、
                           # review、stats、playlist、saved、imports、chat、settings
  app/src/main/res/        # manifest、启动图标、主题
web/
  src/                     # React 功能、接口解析、领域逻辑与本地存储
  server/                  # 白名单 LingQ 代理与 Node 静态服务
  e2e/                     # Playwright 学习闭环测试
```

## 已知限制

- LingQ 内部接口没有公开文档，字段和额度可能变化并导致功能失效。
- Android 暂无自动化测试；CI 会为 pull request 和推送至 `main` 的提交构建 debug APK。Web 已在 CI 中运行解析/领域测试、代理白名单测试、浏览器测试与生产构建。
- Web 首版不包含 AI、播放列表、导入、提醒、完整统计页以及 Android/Web 同步。
- 界面只提供深色主题。
- AI 功能需要自行填写 OpenAI 兼容的 Base URL、API Key 和模型名。

## 贡献

提交、审阅、开发与发布规范由 review 把关。贡献者要求见 [CONTRIBUTING.md](CONTRIBUTING.md)，分支/worktree、PR 后续处理与架构规范以 [AGENTS.md](AGENTS.md) 为准。

- 提交信息与 PR 标题遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：`type(scope): 摘要`，摘要使用小写祈使句，不加句号。
- 常用 type：`feat`、`fix`、`refactor`、`docs`、`test`、`perf`、`build`、`ci`、`chore`。
- 分支名使用 `type/短横线摘要`，例如 `feat/sentence-translation`、`ci/android-debug-build`；不要放版本号、用户名或 issue 编号。从最新的 `origin/main` 创建分支，保持开发周期短，合并后默认保留本地分支。
- 每次代码或文档修改都必须使用与分支配套的 worktree，命名为 `<repository>-worktree-<type>-<short-kebab-summary>`。开发与提交都在 worktree 中进行；主 checkout 仅用于同步、集成审阅和管理操作。合并后默认保留本地 worktree，只有明确要求时才清理。详见 [Local development worktrees](AGENTS.md#local-development-worktrees)。
- scope 写代码区段（`android`、`web`、`data`、`reader`、`explore`、`review`、`vocabulary`、`settings`、`design`），不要写版本号；发版用 `chore(release): bump to X.Y.Z (versionCode N)`。
- 一个提交只做一件事，不要把功能、修复、文档和构建环境调整混在一起。
- 完成规定的变更流程：同步、建分支、创建 worktree、验证、提交、推送、创建 PR、等待 CI、squash 合并，并删除远程分支。除非明确要求清理，否则保留本地 worktree 和分支。禁止直接向 `main` 提交。详见 [Mandatory change workflow](AGENTS.md#mandatory-change-workflow)。
- 仓库使用 squash 合并，PR 标题会成为 `main` 上的提交标题，因此必须符合同样的格式。
- 创建 PR 后，填写英文模板，并设置语义标签、`vX.Y.Z` 发布 milestone 和 `Languages Learning` 项目字段；范围发生变化后要重新核对。详见 [PR follow-up checklist](AGENTS.md#pr-follow-up-checklist)。
- 并行代理必须使用各自独立的分支与 worktree，并分配不重叠的文件；每个代理只验证和记录自己的结果。
- `0.x` 阶段遵循 SemVer：新增功能或行为变更走 minor，只有纯修复才走 patch；`versionName` 与 `versionCode` 一起递增，并同步更新 README 里的版本徽章。
- Tag / Release 使用 `vX.Y.Z`（Release 标题为 `InputA X.Y.Z`）；milestone 使用同样的 `vX.Y.Z`，发版后关闭。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
