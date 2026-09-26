package com.inputa.reader.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * 后端 HTTP 接口（server/index.js）。
 *
 * 两个刻意的选择：
 *
 * 1. **全部返回 `Response<T>`。** Web 版的降级与缓存逻辑完全建立在能读到状态码上
 *    （404 是确定结论、5xx 是故障）。Retrofit 在非 2xx 时把 `body()` 留空、原始响应
 *    放进 `errorBody()`，所以状态码判断天然成立。
 *
 * 2. **词典相关的路由收成泛型收发**（[json]），而不是给每个上游绑一个 DTO。
 *    上游形状差异大且会变，而移植过来的解析器已经把它们归一化了 —— 绑 DTO 等于
 *    把那份已经测过的解析逻辑再写一遍，还要跟着上游漂。
 */
interface InputaApi {

    /** `GET /api/health`。 */
    @GET("api/health")
    suspend fun health(): Response<HealthDto>

    /**
     * 发一个 GET 并读成 JSON。
     *
     * [url] 既可以是相对路径（后端路由，如 `/api/dict?word=very`），
     * 也可以是绝对 URL（直连上游）—— Retrofit 的 `@Url` 两种都收，
     * 所以一个方法就够覆盖降级链里的两类候选。
     */
    @GET
    suspend fun json(@Url url: String): Response<JsonElement>

    /**
     * Gutendex 书目搜索。后端会加上 `languages=en` 并原样透传响应。
     *
     * 用 `@Query` 而不是自己拼查询串：Retrofit 的 `addQueryParameter` 会做正确的
     * 百分号编码（空格 → `%20`，不是 `+`），而手拼很容易漏掉这一步。
     */
    @GET("api/books/search")
    suspend fun searchBooks(@Query("query") query: String): Response<JsonElement>

    /**
     * 经后端下载整本书正文。
     *
     * 走 `/api/books/text?url=` 而不是直连 Gutenberg：SSRF 白名单（只允许四台
     * Gutenberg 主机）与 302 跟随都在服务端做，客户端不必也不该重复实现一遍。
     *
     * `@Streaming` 是必要的：一本书可以到 12MB，不流式会把整个响应先缓进内存。
     */
    @Streaming
    @GET("api/books/text")
    suspend fun bookText(@Query("url") url: String): Response<ResponseBody>
}

/**
 * `GET /api/health` 的响应。
 *
 * 字段都有默认值，且 **`dictionaryAvailable` 是可空的**：老版本服务器不返回它，
 * 而 `null`（没说）与 `false`（明确说了没有）要区别对待 —— 前者照常试离线词库，
 * 后者直接跳过。一个非空 `Boolean = true` 的默认值会把这两种情况混成一种。
 */
@Serializable
data class HealthDto(
    val ok: Boolean = false,
    val service: String? = null,
    val uptimeSeconds: Int? = null,
    val dictionaryAvailable: Boolean? = null,
    val deepseekKeyConfigured: Boolean = false,
)
