package com.inputa.reader.domain.dict

import com.inputa.reader.domain.model.DictionaryEntry
import com.inputa.reader.domain.model.DictionarySource
import com.inputa.reader.domain.model.LookupResult
import kotlinx.serialization.json.JsonElement

/**
 * 单次 provider 请求的结果。**三态，不是一个可空载荷** —— 这是整条降级链的核心。
 *
 *   [Payload] 对方给出了 JSON
 *   [Missing] 对方回 404，就是「没有这个词」——**确定结论**
 *   [Failure] 网络 / 5xx / 无法解析 ——**临时故障**
 *
 * 只有 [Failure] 才可能让 UI 显示「词典服务暂时无法访问」。
 */
sealed interface ProviderOutcome {
    data class Payload(val payload: JsonElement) : ProviderOutcome
    data object Missing : ProviderOutcome
    data class Failure(val reason: String) : ProviderOutcome
}

/**
 * 发一个 GET 并读成 JSON。领域层不认识 HTTP —— 真实实现在 `:app` 用 OkHttp，
 * 测试里换成假的，于是整条降级链能在纯 JVM 上验。
 */
fun interface ProviderRequester {
    suspend fun request(url: String): ProviderOutcome
}

/** 一个来源，以及试它的顺序。 */
data class ProviderSpec(
    val name: DictionarySource,
    /** 同源后端路由（server/index.js 提供）。 */
    val backendUrl: ((String) -> String)? = null,
    /** 直连上游 URL，后端不可用时用（Android 没有 CORS 限制，所以这条是真的有用）。 */
    val directUrl: ((String) -> String)? = null,
    /**
     * 可选的补充来源（离线 ECDICT 文件）：它传输失败只记日志，
     * **绝不能**把一个「词典里没这个词」变成「服务故障」。
     */
    val optional: Boolean = false,
    /** 只有我们自己后端才有、且仅在数据文件存在时才能用的路由。 */
    val requiresLocalDictionary: Boolean = false,
    val parse: (JsonElement?, String) -> DictionaryEntry?,
)

/** 一个待尝试的 URL，以及它是走后端还是直连 —— 只用于日志与测试断言。 */
data class ProviderUrl(val url: String, val viaBackend: Boolean)

/**
 * 来源注册表，顺序即降级顺序：最丰富的在前。
 *
 *   1. local-ecdict      离线 ECDICT（中文释义、考试标签、词性、原形；约 1ms，需要后端与数据文件）
 *   2. dictionaryapi.dev 英文释义 + 发音音频
 *   3. wiktionary        英文释义
 *   4. datamuse          最后的兜底释义
 */
val DICTIONARY_PROVIDERS: List<ProviderSpec> = listOf(
    ProviderSpec(
        name = DictionarySource.LOCAL_ECDICT,
        // 离线、约 1ms、带中文释义与考试标签：先问它，再走网络。
        backendUrl = { word -> "/api/dict?word=${encodePathSegment(word)}" },
        optional = true,
        requiresLocalDictionary = true,
        parse = ::parseLocalEcdict,
    ),
    ProviderSpec(
        name = DictionarySource.DICTIONARY_API,
        backendUrl = { word -> "/api/dictionary/word/${encodePathSegment(word)}" },
        directUrl = { word ->
            "https://api.dictionaryapi.dev/api/v2/entries/en/${encodePathSegment(word)}"
        },
        parse = ::parseDictionaryApi,
    ),
    ProviderSpec(
        name = DictionarySource.WIKTIONARY,
        backendUrl = { word -> "/api/dictionary/wiktionary/${encodePathSegment(word)}" },
        directUrl = { word ->
            "https://en.wiktionary.org/api/rest_v1/page/definition/${encodePathSegment(word)}"
        },
        parse = ::parseWiktionary,
    ),
    ProviderSpec(
        name = DictionarySource.DATAMUSE,
        backendUrl = { word -> "/api/dictionary/datamuse/${encodePathSegment(word)}" },
        directUrl = { word -> "https://api.datamuse.com/words?sp=${encodePathSegment(word)}&md=d&max=1" },
        parse = ::parseDatamuse,
    ),
)

/**
 * 一个来源要试的 URL，按顺序。
 *
 * 后端在跑时同源路由排第一；直连上游 URL **保留为每个来源自己的兜底**，
 * 因为服务器与手机的网络可达性可能不同（DNS、WAF、运营商）。
 * Web 版还有 CORS 的考虑，Android 没有 —— 但「后端挂了但手机有网」时直连仍然救得回来。
 */
fun providerCandidates(
    provider: ProviderSpec,
    word: String,
    backendUp: Boolean,
    localDictionaryReady: Boolean,
): List<ProviderUrl> {
    // 离线文件没有直连上游，而 /api/health 已经说了它不在时也不必再问一次。
    if (provider.requiresLocalDictionary && !localDictionaryReady) return emptyList()

    val urls = ArrayList<ProviderUrl>(2)
    if (backendUp) provider.backendUrl?.let { urls += ProviderUrl(it(word), viaBackend = true) }
    provider.directUrl?.let { urls += ProviderUrl(it(word), viaBackend = false) }
    return urls
}

/**
 * 词典降级链。
 *
 * 一个来源失败绝不会中断查询：换下一个。离线那个是 optional，所以它的故障
 * 不会被报成「词典不可用」。
 *
 * @param log 调试输出。默认丢弃；`:app` 传一个受 `BuildConfig.DEBUG` 保护的实现，
 *   测试里则可以把这些行收集起来断言。
 */
class DictionaryLookup(
    private val requester: ProviderRequester,
    private val cache: LookupCache = LookupCache(),
    private val providers: List<ProviderSpec> = DICTIONARY_PROVIDERS,
    private val log: (String) -> Unit = {},
) {

    suspend fun lookup(
        word: String,
        backendUp: Boolean,
        localDictionaryReady: Boolean,
    ): LookupResult {
        val clean = word.trim().lowercase()
        if (clean.isEmpty()) return LookupResult.NotFound

        cache.get(clean)?.let { return it }

        val attempts = ArrayList<String>(providers.size)
        var sawProviderFailure = false

        for (provider in providers) {
            val candidates = providerCandidates(provider, clean, backendUp, localDictionaryReady)
            if (candidates.isEmpty()) {
                attempts += "${provider.name.key}: 未启用"
                continue
            }

            var entry: DictionaryEntry? = null
            var providerFailed = false

            for (candidate in candidates) {
                val via = if (candidate.viaBackend) "backend" else "direct"
                when (val outcome = requester.request(candidate.url)) {
                    is ProviderOutcome.Failure -> {
                        attempts += "${provider.name.key}($via): ${outcome.reason}"
                        log("[dictionary] \"$clean\" failed on ${provider.name.key} via $via: ${outcome.reason}")
                        // 可选的补充来源不能把「没这个词」变成「服务故障」。
                        if (!provider.optional) providerFailed = true
                        continue
                    }

                    // 404 与「答了但解析不出」都是确定结论，所以这个来源的另一条传输
                    // 不再重试 —— 换传输只会再拿一次同样的答复。
                    ProviderOutcome.Missing -> {
                        attempts += "${provider.name.key}: 未收录"
                        break
                    }

                    is ProviderOutcome.Payload -> {
                        entry = provider.parse(outcome.payload, clean)
                        if (entry == null) attempts += "${provider.name.key}: 无有效释义"
                        break
                    }
                }
            }

            if (entry != null) {
                val found = LookupResult.Found(entry, provider.name)
                cache.put(clean, found)
                return found
            }

            if (providerFailed) sawProviderFailure = true
        }

        // 干净的「没有这个词」**不**算故障，所以只有必需来源的传输/5xx 失败
        // 才可能浮出成「服务不可用」。
        val result = if (sawProviderFailure) LookupResult.Unavailable else LookupResult.NotFound

        // LookupCache.put 自身拒绝缓存 Unavailable，这里不必再判一次。
        cache.put(clean, result)

        log("[dictionary] no entry for \"$clean\" (${attempts.joinToString("; ")})")
        return result
    }
}

/**
 * 百分号编码一个**路径片段或查询值**。
 *
 * 用 `encodeURLPathSegment` 的语义而不是 `java.net.URLEncoder`：后者是
 * `application/x-www-form-urlencoded` 的规则，会把空格编成 `+`、把撇号编成 `%27`，
 * 与 JS 的 `encodeURIComponent` 不一致 —— 而 Web 版的测试恰好钉住了
 * `ain't` 在 URL 里保持裸撇号（`/api/dict?word=ain't`）这一点。
 */
internal fun encodePathSegment(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
    val builder = StringBuilder(value.length)
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val char = byte.toInt().toChar()
        if (char in unreserved) {
            builder.append(char)
        } else {
            builder.append('%')
            builder.append(HEX[(byte.toInt() shr 4) and 0xF])
            builder.append(HEX[byte.toInt() and 0xF])
        }
    }
    return builder.toString()
}

private const val HEX = "0123456789ABCDEF"
