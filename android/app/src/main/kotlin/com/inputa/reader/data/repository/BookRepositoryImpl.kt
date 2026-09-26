package com.inputa.reader.data.repository

import android.content.Context
import com.inputa.reader.data.local.dao.BookDao
import com.inputa.reader.data.local.entity.BookEntity
import com.inputa.reader.data.local.entity.BookSummaryRow
import com.inputa.reader.data.local.entity.ChapterEntity
import com.inputa.reader.data.remote.ApiProvider
import com.inputa.reader.domain.book.BookDownloadException
import com.inputa.reader.domain.book.GutendexBook
import com.inputa.reader.domain.book.GutendexBookLoader
import com.inputa.reader.domain.book.GutendexMapper
import com.inputa.reader.domain.book.TextDownloader
import com.inputa.reader.domain.model.Book
import com.inputa.reader.domain.model.BookSource
import com.inputa.reader.domain.repository.BackendHealthRepository
import com.inputa.reader.domain.repository.BookRepository
import com.inputa.reader.domain.repository.BookSummary
import com.inputa.reader.domain.util.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 书籍与章节。Web 版**不持久化书**（刷新就回到内置第一本），手机上必须存 ——
 * 这是移植带来的必要改进之一。
 *
 * 正文按章取而不是整本读进内存：Gutendex 的书可以到 12MB，而阅读器同一时刻只需要
 * 当前这一章（分页也只在一章之内进行）。
 */
@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val apiProvider: ApiProvider,
    private val health: BackendHealthRepository,
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
    private val json: Json,
) : BookRepository {

    /** 内置样书只解析一次。asset 是随包的，内容不会变。 */
    @Volatile
    private var builtinCache: List<Book>? = null

    override fun observeShelf(): Flow<List<BookSummary>> =
        bookDao.observeShelf().map { rows -> rows.map(BookSummaryRow::toSummary) }

    override suspend fun findMeta(bookId: String): BookSummary? =
        bookDao.findSummary(bookId)?.toSummary()

    override suspend fun chapterTitles(bookId: String): List<String> = bookDao.chapterTitles(bookId)

    override suspend fun chapterContent(bookId: String, chapterIndex: Int): String? =
        bookDao.chapterContent(bookId, chapterIndex)

    override suspend fun save(book: Book) {
        bookDao.saveBook(
            book = BookEntity(
                bookId = book.id,
                title = book.title,
                author = book.author,
                coverUrl = book.coverUrl,
                source = book.source?.key,
                language = book.language,
                addedAt = clock.nowMillis(),
                // 字符数，不是字节数 —— 见 BookEntity 的说明。
                charCount = book.chapters.sumOf { it.content.length.toLong() },
            ),
            chapters = book.chapters.mapIndexed { index, chapter ->
                ChapterEntity(
                    bookId = book.id,
                    chapterIndex = index,
                    title = chapter.title,
                    content = chapter.content,
                )
            },
        )
    }

    override suspend fun delete(bookId: String) = bookDao.deleteBook(bookId)

    override suspend fun builtinBooks(): List<Book> {
        builtinCache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            val raw = context.assets.open(BUILTIN_ASSET).bufferedReader().use { it.readText() }
            json.decodeFromString<List<SampleBookDto>>(raw).map { it.toDomain() }
        }
        builtinCache = loaded
        return loaded
    }

    /**
     * 搜 Gutendex。
     *
     * 与 Web 版一样，后端在跑时走后端路由，不在时直连 `gutendex.com` —— 搜索只是
     * 一个普通 GET，Android 没有 CORS 限制，所以这条兜底是真的有用。
     * **正文下载没有这条兜底**：那边需要服务端跟随 302 与 SSRF 白名单，见 [downloadText]。
     */
    override suspend fun searchGutendex(query: String): List<GutendexBook> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val useBackend = health.current().ok
        val response = if (useBackend) {
            // 专门的 searchApi：后端等 Gutendex 上游 15 秒，用词典那套 10 秒的超时
            // 会在后端还没答之前就先放弃，读者只会看到一句无意义的 "timeout"。
            apiProvider.searchApi().searchBooks(trimmed)
        } else {
            // 用 HttpUrl 构造而不是手拼查询串：它会把空格编成 %20 而不是 +。
            val direct = "https://gutendex.com/books".toHttpUrl().newBuilder()
                .addQueryParameter("search", trimmed)
                .build()
                .toString()
            // 直连同样是「一个可能很慢的外部搜索」，所以也用 searchApi。
            apiProvider.searchApi().json(direct)
        }

        if (!response.isSuccessful) {
            throw BookDownloadException("Gutendex 搜索失败（HTTP ${response.code()}）")
        }
        return GutendexMapper.parseSearchResults(response.body())
    }

    override suspend fun loadFromGutendex(item: GutendexBook): Book =
        GutendexBookLoader(downloader = ::downloadText).load(item)

    /**
     * 下载一本书的正文。
     *
     * **只走后端。** Gutenberg 会把 `ebooks/<id>.txt.utf-8` 302 到纯 HTTP 的缓存地址，
     * 而 302 跟随、主机白名单（防 SSRF）、以及 latin1 正文的解码判断全都在
     * `server/upstreams.js` 里做过一遍了。客户端再实现一遍既重复又容易漂。
     */
    private suspend fun downloadText(url: String): String {
        if (!health.current().ok) {
            // 这条提示比 Web 版少了 CORS 那段 —— 手机上连不上的原因是「后端没起」，
            // 不是浏览器同源策略。
            throw BookDownloadException("$BACKEND_HINT（本次要下载的是 $url）")
        }

        val label = "本地后端"
        try {
            val response = apiProvider.downloadApi().bookText(url)
            if (!response.isSuccessful) {
                throw BookDownloadException("$label HTTP ${response.code()}")
            }

            val body = response.body() ?: throw BookDownloadException("$label 返回空内容")
            val text = withContext(Dispatchers.IO) { body.string() }

            if (text.trim().isEmpty()) throw BookDownloadException("$label 返回空内容")
            // 后端对上游是原样透传的，所以上游的错误页会带着 200 回来。
            // 只看开头 200 个字符，与 Web 版一致。
            if (HTML_START.containsMatchIn(text.take(200))) {
                throw BookDownloadException("$label 返回的是 HTML 页面")
            }

            return text
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: BookDownloadException) {
            throw error
        } catch (error: Exception) {
            throw BookDownloadException("$label ${error.message ?: "请求失败"}")
        }
    }

    private companion object {
        const val BUILTIN_ASSET = "sample_books.json"

        const val BACKEND_HINT =
            "无法下载 Gutenberg 正文：请在项目根目录运行 \"npm run server\" 启动本地代理后端后重试。"

        val HTML_START = Regex("^\\s*<(?:!doctype|html)", RegexOption.IGNORE_CASE)
    }
}

private fun BookSummaryRow.toSummary(): BookSummary = BookSummary(
    id = bookId,
    title = title,
    author = author,
    coverUrl = coverUrl,
    source = BookSource.fromKey(source),
    language = language,
    addedAt = addedAt,
    charCount = charCount,
    chapterCount = chapterCount,
)

/** `assets/sample_books.json` 的形状。由 Web 版的 `src/data/sampleBooks.ts` 生成。 */
@Serializable
private data class SampleBookDto(
    val id: String,
    val title: String,
    val author: String = "",
    val coverUrl: String? = null,
    val source: String? = null,
    val language: String? = null,
    val chapters: List<SampleChapterDto> = emptyList(),
)

@Serializable
private data class SampleChapterDto(
    val title: String = "",
    val content: String = "",
)

private fun SampleBookDto.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    coverUrl = coverUrl,
    chapters = chapters.map { com.inputa.reader.domain.model.BookChapter(it.title, it.content) },
    source = BookSource.fromKey(source),
    language = language,
)
