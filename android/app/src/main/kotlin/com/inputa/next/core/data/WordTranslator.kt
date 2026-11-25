package com.inputa.next.core.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 点词弹窗最后一行那句话的机器翻译。
 *
 * 两个来源，都不需要 key、国内可直连：
 *  - 有道词典 `dict.youdao.com/jsonapi`：英语用 `ec`（带词性的中文释义），
 *    其它语种用 `web_trans` 里 `@same` 的那条（韩语、法语等能直接对上）；
 *  - Bing 网页翻译 `cn.bing.com`：要先拿 `IG` + `key`/`token`（会话缓存 20 分钟）再 POST。
 *
 * 用 `HttpURLConnection` 而不是 OkHttp：这条链路要精确控制重定向（Bing 的 POST 常被
 * 302 到另一个节点，跟随时会丢 body）和 Cookie，直接控制更可靠，也便于在 JVM 上验证。
 *
 * 不再使用 MyMemory：它的记忆库会把 "English" 翻成「中文」。
 */
@Singleton
class WordTranslator @Inject constructor(
    private val json: Json,
) {

    private data class BingSession(
        val host: String,
        val ig: String,
        val key: String,
        val token: String,
        val cookie: String,
        val obtainedAt: Long,
    )

    // 这个类是 @Singleton，阅读/复习/词库三个页面会并发调用，共享状态必须线程安全。
    @Volatile
    private var session: BingSession? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val cookies = ArrayList<String>()

    /** 把 [text] 翻译成 [target]（`zh` / `en` / `ja` / `ko`）；失败返回 null。 */
    suspend fun translate(text: String, target: String, source: String?): String? =
        translateInternal(text, target, source, maxLength = 60, useDictionary = true)

    /** 整句翻译：只走 Bing（词典查句子没意义），上限放宽到一整段。 */
    suspend fun translateSentence(text: String, target: String, source: String?): String? =
        translateInternal(text, target, source, maxLength = SENTENCE_MAX_CHARS, useDictionary = false)

    private suspend fun translateInternal(
        text: String,
        target: String,
        source: String?,
        maxLength: Int,
        useDictionary: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        val clean = text.trim()
        val targetCode = normalize(target) ?: return@withContext null
        val sourceCode = normalize(source)
        if (clean.isEmpty()) return@withContext null
        if (sourceCode == targetCode) return@withContext null
        // 超长不再静默放弃：按上限截断后照常翻译，避免面板只显示「暂无释义」。
        val input = if (clean.length > maxLength) clean.take(maxLength) else clean

        val cacheKey = if (useDictionary) "$targetCode|${input.lowercase()}" else "s|$targetCode|${input.hashCode()}"
        cache[cacheKey]?.let { return@withContext it }

        // 有道词库（ec/jc/kc）返回的永远是中文释义，只有目标语言是中文时才能用；
        // 目标语言是英/日/韩时必须只走 Bing，否则会拼出「中文；English」这种混合内容。
        val dictionary = if (useDictionary && targetCode == "zh") {
            runCatching { youdao(input, sourceCode) }.getOrNull()
        } else {
            null
        }
        // 词典给的是规范义项，Bing 的 LLM 翻译更贴近现在的常用义（比如 `すごい`
        // 词典只有「可怕」，Bing 给「厉害」）。两个都拿，合并成一行。
        val machine = if (dictionary != null && sourceCode == "en") {
            null
        } else {
            runCatching { bing(input, targetCode, sourceCode) }.getOrNull()
        }
        val result = merge(dictionary, machine)

        val value = result?.trim()?.takeIf { it.isNotEmpty() && !it.equals(input, ignoreCase = true) }
        if (value != null) cache[cacheKey] = value
        value
    }

    /** 合并词典释义和机翻：重复的只留一份，不同的用「；」接上。 */
    private fun merge(primary: String?, secondary: String?): String? {
        val first = primary?.trim()?.takeIf { it.isNotEmpty() }
        val second = secondary?.trim()?.takeIf { it.isNotEmpty() }
        if (first == null) return second
        if (second == null) return first
        if (first.contains(second) || second.contains(first)) return first
        return "$first；$second"
    }

    // ------------------------------------------------------------------
    // 有道词典
    // ------------------------------------------------------------------

    private fun youdao(text: String, source: String?): String? {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val le = source?.takeIf { it.isNotBlank() } ?: "en"
        val body = httpGet("https://dict.youdao.com/jsonapi?q=$encoded&le=$le&client=mobile&keyfrom=mdict.7.2.0")
            ?: return null
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null

        // 英汉 / 日汉 / 韩汉：`<section>.word[0].trs[*]` 里是带词性的中文释义。
        senses(root, "ec", truncate = true)?.let { return it }
        senses(root, "jc", truncate = false)?.let { return it }
        senses(root, "kc", truncate = false)?.let { return it }

        // 其它语种：web_trans 里 key 就是这个词、且标了 @same 的那条，value 是中文。
        val webTrans = (root["web_trans"] as? JsonObject)?.get("web-translation") as? JsonArray
        var similar: String? = null
        webTrans?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val key = (obj["key"] as? JsonPrimitive)?.content?.trim()
            val trans = obj["trans"] as? JsonArray
            val value = ((trans?.firstOrNull() as? JsonObject)?.get("value") as? JsonPrimitive)?.content?.trim()
            if (key.isNullOrBlank() || value.isNullOrBlank()) return@forEach
            val same = (obj["@same"] as? JsonPrimitive)?.content == "true"
            if (same && key.equals(text, ignoreCase = true)) return value
            if (similar == null && key.equals(text, ignoreCase = true)) similar = value
        }
        return similar
    }

    /**
     * 从有道词典的某个词库里取中文释义。
     *
     * `ec`（英汉）的 `i` 里已经带词性，形如 "n. 书，书籍；本子，簿册；…"，按「；」截断；
     * `jc` / `kc`（日汉、韩汉）的 `pos` 是独立字段，拼在释义前面（如 "名·自他サ 学习，用功"）。
     */
    private fun senses(root: JsonObject, section: String, truncate: Boolean): String? {
        val words = (root[section] as? JsonObject)?.get("word") as? JsonArray ?: return null
        val firstWord = words.firstOrNull() as? JsonObject ?: return null
        val trs = firstWord["trs"] as? JsonArray ?: return null
        val lines = ArrayList<String>(2)
        for (element in trs) {
            if (lines.size >= 2) break
            val entry = element as? JsonObject ?: continue
            val pos = (entry["pos"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val tr = entry["tr"] as? JsonArray ?: continue
            val meanings = ArrayList<String>(2)
            for (item in tr) {
                if (meanings.size >= 2) break
                val l = (item as? JsonObject)?.get("l") as? JsonObject ?: continue
                val i = l["i"] as? JsonArray ?: continue
                var text = (i.firstOrNull() as? JsonPrimitive)?.content?.trim().orEmpty()
                if (text.isBlank()) continue
                text = text.replace(Regex("<[^>]+>"), "").trim()
                if (truncate) text = text.split('；').first().trim()
                // "[" 开头的是例句片段，不是释义。
                if (text.isBlank() || text.startsWith("[")) continue
                // 词典里常见 "1.单词。词语" 这种编号和句号，收拾成 "单词，词语"。
                text = text
                    .replace(Regex("""^\d+[.、]\s*"""), "")
                    .replace("。", "，")
                    .trim()
                    .trimEnd('，', '。', ';', '；')
                if (text.isNotBlank() && meanings.none { it == text }) meanings += text
            }
            if (meanings.isNotEmpty()) {
                lines += if (pos.isBlank()) meanings.joinToString("，") else "$pos ${meanings.joinToString("，")}"
            }
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("；")
    }

    // ------------------------------------------------------------------
    // Bing 网页翻译
    // ------------------------------------------------------------------

    private fun bing(text: String, target: String, source: String?): String? {
        val to = if (target == "zh") "zh-Hans" else target
        // 明确告诉 Bing 源语言：只发一个单词时自动识别经常认错。
        val from = when {
            source == null -> "auto-detect"
            source == "zh" -> "zh-Hans"
            else -> source
        }
        var current = session?.takeIf { System.currentTimeMillis() - it.obtainedAt < SESSION_TTL_MS }
        if (current == null) current = refreshSession() ?: return null

        var body = postTranslation(current, text, from, to)
        if (body.isNullOrBlank()) {
            session = null
            current = refreshSession() ?: return null
            body = postTranslation(current, text, from, to)
        }
        return parseBing(body)
    }

    private fun refreshSession(): BingSession? {
        for (host in HOSTS) {
            val page = httpGetWithUrl("$host/translator") ?: continue
            val html = page.second
            val ig = Regex("""IG:"([0-9A-Fa-f]+)"""").find(html)?.groupValues?.get(1) ?: continue
            val helper = Regex(
                """params_AbusePreventionHelper\s*=\s*\[(\d+),"([^"]+)",(\d+)]""",
            ).find(html) ?: continue
            // 页面可能被重定向到另一个节点（www ↔ cn），POST 必须发到页面真正的来源。
            val base = baseUrlOf(page.first)
            return BingSession(
                host = base,
                ig = ig,
                key = helper.groupValues[1],
                token = helper.groupValues[2],
                cookie = synchronized(cookies) { cookies.joinToString("; ") },
                obtainedAt = System.currentTimeMillis(),
            ).also { session = it }
        }
        return null
    }

    private fun baseUrlOf(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url
        val slash = url.indexOf('/', schemeEnd + 3)
        return if (slash < 0) url else url.substring(0, slash)
    }

    private fun postTranslation(session: BingSession, text: String, from: String, to: String): String? {
        val form = "fromLang=" + enc(from) +
            "&text=" + enc(text) +
            "&to=" + enc(to) +
            "&token=" + enc(session.token) +
            "&key=" + session.key
        var url = "${session.host}/ttranslatev3?isVertical=1&&IG=${session.ig}&IID=translator.5028.1"
        repeat(2) {
            val response = httpPost(url, form) ?: return null
            if (response.code == 200) return response.body
            // Bing 常把 POST 302 到另一个节点；跟着 Location 再发一次，body 不能丢。
            val location = response.location
            if (response.code in 300..399 && !location.isNullOrBlank()) {
                url = if (location.startsWith("http")) location else session.host + location
                return@repeat
            }
            return null
        }
        return null
    }

    private fun parseBing(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val array = json.parseToJsonElement(body) as? JsonArray ?: return null
            val first = array.firstOrNull() as? JsonObject ?: return null
            val translations = first["translations"] as? JsonArray ?: return null
            val entry = translations.firstOrNull() as? JsonObject ?: return null
            (entry["text"] as? JsonPrimitive)?.content?.trim()
        }.getOrNull()
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private class Response(val code: Int, val body: String, val location: String?)

    private fun httpGet(url: String): String? = httpGetWithUrl(url)?.second

    private fun httpGetWithUrl(url: String): Pair<String, String>? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Encoding", "identity")
        }
        val code = connection.responseCode
        if (code != 200) {
            connection.errorStream?.close()
            null
        } else {
            collectCookies(connection)
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.url.toString() to body
        }
    }.getOrNull()

    private fun httpPost(url: String, form: String): Response? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept-Encoding", "identity")
            synchronized(cookies) { if (cookies.isNotEmpty()) setRequestProperty("Cookie", cookies.joinToString("; ")) }
        }
        connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        collectCookies(connection)
        Response(code, body, connection.getHeaderField("Location"))
    }.getOrNull()

    private fun collectCookies(connection: HttpURLConnection) {
        connection.headerFields?.forEach { (name, values) ->
            if (name == null || !name.equals("Set-Cookie", ignoreCase = true)) return@forEach
            values?.forEach { header ->
                val pair = header.substringBefore(';').trim()
                if (!pair.contains('=')) return@forEach
                synchronized(cookies) {
                    cookies.removeAll { it.substringBefore('=').equals(pair.substringBefore('='), ignoreCase = true) }
                    cookies += pair
                }
            }
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun normalize(code: String?): String? {
        val value = code?.trim()?.lowercase()?.replace('_', '-')?.takeIf { it.isNotBlank() } ?: return null
        return value.substringBefore('-').takeIf { it.isNotBlank() }
    }

    private companion object {
        const val SESSION_TTL_MS = 20 * 60 * 1000L
        const val SENTENCE_MAX_CHARS = 600
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val HOSTS = listOf("https://cn.bing.com", "https://www.bing.com")
    }
}
