package com.inputa.reader.domain.model

import com.inputa.reader.domain.text.WORDS_PER_PAGE

/** 三套主题。`key` 就是持久化用的值。 */
enum class AppTheme(val key: String) {
    Light("light"),
    Sepia("sepia"),
    Dark("dark");

    companion object {
        /**
         * 刻意不用 `valueOf`：它遇到未知的持久化值会抛异常，而降级安装或手改过的文件
         * 都可能产生未知值。与 [com.inputa.reader.domain.vocab.WordLevelCodec] 的
         * 「无法识别就丢弃」同姿态，区别只是这里有一个合理的兜底值。
         *
         * 兜底选 Sepia：Web 版的默认主题就是它（index.html 的内联脚本也拿它兜底）。
         */
        fun fromKey(raw: String?): AppTheme = entries.firstOrNull { it.key == raw } ?: Sepia
    }
}

/** 朗读引擎。第二阶段才会真正用到，但设置项现在就存在，免得迁移。 */
enum class TtsProvider(val key: String) {
    Edge("edge"),
    Custom("custom"),
    Browser("browser");

    companion object {
        fun fromKey(raw: String?): TtsProvider = entries.firstOrNull { it.key == raw } ?: Edge
    }
}

/**
 * 阅读与朗读设置。字段与 Web 版 `useReaderStore` 的 `ReaderSettings` 一一对应，
 * 外加两个 Android 特有的：[serverBaseUrl] 与 [wordsPerPage]。
 *
 * **每个字段都必须有默认值。** Web 版曾经把 `JSON.parse` 的结果直接返回，
 * 于是每次新增设置项，老用户的该字段都是 `undefined` 并直接流进渲染
 * （字号变成 `undefinedpx`）。DataStore 的「每个 key 单独取默认值」从结构上
 * 根除了这个问题 —— 只要这里的默认值齐全，就不可能拿到空值。
 */
data class ReaderSettings(
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val theme: AppTheme = AppTheme.Sepia,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val ttsProvider: TtsProvider = TtsProvider.Edge,
    /** 空 = 按书籍语言自动挑。 */
    val ttsVoice: String = "",
    /** Edge 的 rate 参数，形如 `default` / `+20%`。 */
    val ttsRate: String = "default",
    /** 用户自己的 TTS 服务地址模板，支持 `{text}` 与 `{lang}`。 */
    val ttsCustomUrlTemplate: String = "",
    val bionicEnabled: Boolean = false,
    val readingRulerEnabled: Boolean = false,
    /** 后端地址。默认值见 [DEFAULT_SERVER_BASE_URL]。 */
    val serverBaseUrl: String = DEFAULT_SERVER_BASE_URL,
    val wordsPerPage: Int = WORDS_PER_PAGE,
)

const val DEFAULT_FONT_SIZE_SP = 20
const val DEFAULT_LINE_HEIGHT = 1.8f

/** 字号的可调范围，供设置页的步进器使用。与 Web 版 Header 里的 16..30 一致。 */
val FONT_SIZE_RANGE = 16..30

/**
 * 线上后端地址的 base64。**刻意不写成明文**，理由与 Web 版 `src/services/apiBase.ts` 里的
 * `decodeApiBase` 相同：这个地址不该在公开仓库里一眼可见。
 *
 * ⚠️ **这是混淆，不是加密。** 解出来只要一行，APK 里也照样能搜到还原后的字符串。别把它
 * 当安全措施。
 */
private const val DEFAULT_SERVER_BASE_URL_B64 = "aHR0cHM6Ly80My0xNjctMTk2LTQzLnNzbGlwLmlvLw=="

/**
 * 后端地址的线上默认值 —— **release 构建**用的是它。
 *
 * debug 构建会被 app 模块的 `buildConfigField` 覆盖成 `http://10.0.2.2:8787/`
 * （模拟器里指向宿主机的地址，后端跑在开发机上）。覆盖之所以放在 app 模块而不是这里：
 * `:domain` 是纯 Kotlin、看不到 `BuildConfig` —— 那正是这个模块能在纯 JVM 上跑测试的原因。
 *
 * 给一个真实的线上地址而不是留空，是为了装完就能用，不必先让用户找到设置页填地址。
 * 要连自己机器上的 server/，在设置页改即可 —— 那里存下的值优先于这个默认值。
 *
 * 用 `java.util.Base64` 而不是 `android.util.Base64`：这个模块不依赖 android，而
 * `java.util.Base64` 从 Android API 26 起就有（minSdk 正是 26）。
 *
 * 解不出来不做运行期兜底：常量写错会让 NormalizeBaseUrlTest 立刻变红，而一个静默的
 * 降级值（比如空串）反而会把问题推到发请求的那一刻。
 */
val DEFAULT_SERVER_BASE_URL: String =
    String(java.util.Base64.getDecoder().decode(DEFAULT_SERVER_BASE_URL_B64))

/**
 * 合法主机名或 IP 字面量，可带端口。只做**形状**校验，不做 DNS 解析。
 *
 * 两条分支：IPv6 字面量（方括号包起来）与普通的「标签.标签」主机名。
 * 为什么值得写这个正则：没有它时，像 `javascript:alert(1)` 这样不含 `://` 的输入
 * 会被补成 `http://javascript:alert(1)/` —— 一个看起来合法、实际连不上的地址，
 * 于是用户看到的是「连不上服务」而不是「地址不合法」，排查方向完全被带偏。
 */
private val HOST_PATTERN = Regex(
    "^(?:\\[[0-9A-Fa-f:.]+]" +
        "|[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?" +
        "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*)" +
        "(?::\\d{1,5})?$",
)

/**
 * 把用户输入的服务器地址规范成 Retrofit 能用的 baseUrl。
 *
 * 三件事必须做，否则失败信息会很难懂：
 *   1. **必须以 `/` 结尾。** Retrofit 否则抛 `IllegalArgumentException: baseUrl must end in /`。
 *      用户输入的是「主机和端口」，补斜杠是我们的活。
 *   2. **必须有 http/https 的 scheme。** 裸写 `192.168.1.20:8787` 会被 `java.net.URI`
 *      解析成 scheme = `192.168.1.20`，于是请求发不出去且报错莫名其妙。
 *   3. **主机必须长得像主机。** 见 [HOST_PATTERN]。
 *
 * @return 规范化后的地址；无法解析时返回 null，由调用方在输入框上显示原因。
 */
fun normalizeBaseUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    // 没有 `://` 就补一个 http://（局域网自建后端最常见的写法）。
    val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"

    val scheme = withScheme.substringBefore("://", "").lowercase()
    if (scheme != "http" && scheme != "https") return null

    val authority = withScheme.substringAfter("://")
    val host = authority.substringBefore('/').substringBefore('?').substringBefore('#')
    if (!HOST_PATTERN.matches(host)) return null

    val path = authority.substringAfter(host, "")
    // 去掉查询串与片段 —— 它们不属于 baseUrl。
    val cleanPath = path.substringBefore('?').substringBefore('#')

    return "$scheme://$host" + cleanPath.trimEnd('/') + "/"
}
