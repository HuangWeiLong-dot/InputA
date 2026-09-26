package com.inputa.reader.domain.book

import com.inputa.reader.domain.json.asArrayOrNull
import com.inputa.reader.domain.json.asObjectOrNull
import com.inputa.reader.domain.json.intOrNull
import com.inputa.reader.domain.json.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Gutendex（Project Gutenberg 的书目 API）相关模型与纯逻辑。
 *
 * 导入一本书是**两步**流程：
 *   1. `/books?search=` 返回书目信息（标题、作者、封面）以及一张 `formats` 表，
 *      里面是各种格式的下载链接，包括纯文本（`text/plain; charset=utf-8`）。
 *   2. 再单独请求那条链接，才拿到正文。
 *
 * 第二步在 Web 上必须走后端代理（Gutenberg 不返回 CORS 头，且会把
 * `ebooks/<id>.txt.utf-8` 302 到纯 HTTP 的缓存地址）。Android 没有 CORS 限制，
 * 但仍然走同一条后端路由：SSRF 白名单与 302 跟随都在服务端做，客户端不必重复实现。
 */

data class GutendexBook(
    val id: Int,
    val title: String,
    val authors: List<String>,
    val formats: Map<String, String>,
    val downloadCount: Int,
    /**
     * 书源自己声明的语言（如 `["en"]`）。Gutendex 一直在返回它，只是 Web 版过去丢掉了。
     * 它比本地检测可靠，所以优先用作 `Book.language`。
     */
    val languages: List<String>,
)

object PlainTextUrls {

    private const val PLAIN_TEXT_PREFIX = "text/plain"

    /** 优先 UTF-8，其次 US-ASCII，最后任何 `text/plain*`。 */
    private val PREFERRED_KEYS = listOf(
        "text/plain; charset=utf-8",
        "text/plain; charset=UTF-8",
        "text/plain; charset=us-ascii",
        "text/plain",
    )

    private val HTTP_URL = Regex("^https?://", RegexOption.IGNORE_CASE)

    /**
     * 一条书目记录给出的全部纯文本下载链接，**最优的在前**（UTF-8 版先于 US-ASCII 版），
     * 并去重。顺序就是下载时的尝试顺序。
     */
    fun collect(item: GutendexBook): List<String> {
        // LinkedHashSet 同时给出「去重」与「保持插入顺序」，正好是 Web 版
        // `if (!urls.includes(v)) urls.push(v)` 的语义。
        val urls = LinkedHashSet<String>()

        fun add(value: String?) {
            if (value.isNullOrEmpty() || !HTTP_URL.containsMatchIn(value)) return
            urls += value
        }

        for (key in PREFERRED_KEYS) add(item.formats[key])
        for ((key, value) in item.formats) {
            if (key.lowercase().startsWith(PLAIN_TEXT_PREFIX)) add(value)
        }

        return urls.toList()
    }
}

/**
 * 把 Gutendex 的搜索响应映射成领域模型。纯函数，所以能单测。
 *
 * 字段名保持线上格式（`download_count`）不做重命名，这样与 Web 版的
 * `GutendexBookResult` 可以逐字段对照。
 */
object GutendexMapper {

    fun parseSearchResults(payload: JsonElement?): List<GutendexBook> {
        val results = payload.asObjectOrNull()?.get("results").asArrayOrNull() ?: return emptyList()
        return results.mapNotNull { parseBook(it) }
    }

    fun parseBook(element: JsonElement?): GutendexBook? {
        val obj = element.asObjectOrNull() ?: return null
        val id = obj["id"].intOrNull() ?: return null

        return GutendexBook(
            id = id,
            title = obj.stringField("title"),
            authors = obj["authors"].asArrayOrNull().orEmpty()
                .mapNotNull { it.asObjectOrNull()?.stringField("name")?.takeIf(String::isNotEmpty) },
            formats = obj["formats"].asObjectOrNull().orEmpty()
                .mapNotNull { (key, value) -> value.stringOrNull()?.let { key to it } }
                .toMap(),
            downloadCount = obj["download_count"].intOrNull() ?: 0,
            languages = obj["languages"].asArrayOrNull().orEmpty()
                .mapNotNull { it.stringOrNull() }
                .filter { it.isNotEmpty() },
        )
    }
}

private fun JsonObject.stringField(key: String): String = this[key].stringOrNull().orEmpty()
