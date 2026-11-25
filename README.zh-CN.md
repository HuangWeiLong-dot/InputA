<div align="center">

# InputA

[English](README.md) | [简体中文](README.zh-CN.md)

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Hilt](https://img.shields.io/badge/DI-Hilt-2C4F7C)
![OkHttp](https://img.shields.io/badge/HTTP-OkHttp-3E4348)
![kotlinx.serialization](https://img.shields.io/badge/JSON-kotlinx.serialization-7F52FF)
![MVVM](https://img.shields.io/badge/architecture-MVVM-6E4AFF)
![Gradle](https://img.shields.io/badge/Gradle-9.6-02303A?logo=gradle&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-17-007396?logo=openjdk&logoColor=white)
![SDK](https://img.shields.io/badge/compile%2Ftarget%20SDK-37-3DDC84)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84)
![version](https://img.shields.io/badge/version-0.4.1-blue)
![License](https://img.shields.io/badge/license-MIT-3DA639)
![Storage](https://img.shields.io/badge/storage-SharedPreferences-15803D)
![AI](https://img.shields.io/badge/AI-OpenAI%20compatible-412991)
![Theme](https://img.shields.io/badge/theme-dark-111113)
![UI](https://img.shields.io/badge/UI-English%20%7C%20%E4%B8%AD%E6%96%87-blue)

一个非官方、本地优先的 Android 版 [LingQ](https://www.lingq.com/) 阅读器。阅读课程、点词查义、把生词记录留在本机，并播放课程音频；界面为纯深色自定义 Compose 风格。

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

- **没有自建后端**，应用直接访问 `https://www.lingq.com/`。
- LingQ Token 保存在 `EncryptedSharedPreferences` 中，不会写入日志。
- 词状态、收藏、播放列表、上次阅读、阅读设置和 AI 讲解缓存都只存在本机。
- 密码仅用于换取 Token，不会被保存。
- AI 请求只会发往**你自己**配置的 OpenAI 兼容接口。
- 翻译会把点选的单词或长按的句子发往有道词典（`dict.youdao.com`）或 Bing（`cn.bing.com`）。该功能默认开启，可在「设置 → 词弹窗」里关闭，关闭后文本只留在本机。

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
```

## 已知限制

- LingQ 内部接口没有公开文档，字段和额度可能变化并导致功能失效。
- 目前没有自动化测试，也没有 CI。
- 界面只提供深色主题。
- AI 功能需要自行填写 OpenAI 兼容的 Base URL、API Key 和模型名。

## 贡献

提交与发布规范由 review 把关，完整规则见 [CLAUDE.md](CLAUDE.md)。

- 提交信息与 PR 标题遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：`type(scope): 摘要`，摘要使用小写祈使句，不加句号。
- 常用 type：`feat`、`fix`、`refactor`、`docs`、`test`、`perf`、`build`、`ci`、`chore`。
- 分支名使用 `type/短横线摘要`，例如 `feat/sentence-translation`、`ci/android-debug-build`；不要放版本号、用户名或 issue 编号。从当前 `main` 创建分支，合并后删除。
- scope 写代码区段（`android`、`data`、`reader`、`explore`、`review`、`vocabulary`、`settings`、`design`），不要写版本号；发版用 `chore(release): bump to X.Y.Z (versionCode N)`。
- 一个提交只做一件事，不要把功能、修复、文档和构建环境调整混在一起。
- 仓库使用 squash 合并，PR 标题会成为 `main` 上的提交标题，因此必须符合同样的格式。
- `0.x` 阶段遵循 SemVer：新增功能或行为变更走 minor，只有纯修复才走 patch；`versionName` 与 `versionCode` 一起递增，并同步更新 README 里的版本徽章。
- Tag / Release 使用 `vX.Y.Z`（Release 标题为 `InputA X.Y.Z`）；milestone 使用同样的 `vX.Y.Z`，发版后关闭。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
