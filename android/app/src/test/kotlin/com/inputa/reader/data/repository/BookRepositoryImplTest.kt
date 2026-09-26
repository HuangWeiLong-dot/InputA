package com.inputa.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.inputa.reader.data.local.InputaDatabase
import com.inputa.reader.data.remote.ApiProvider
import com.inputa.reader.domain.book.BookDownloadException
import com.inputa.reader.domain.book.GutendexBook
import com.inputa.reader.domain.model.Book
import com.inputa.reader.domain.model.BookChapter
import com.inputa.reader.domain.model.BookSource
import com.inputa.reader.domain.repository.BackendHealth
import com.inputa.reader.domain.repository.BackendHealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 书籍仓储。分成三块验：内置样书（顺带证明 asset 真的被打进包了）、
 * 存取的往返、以及 Gutendex 搜索走后端。
 *
 * 搜索的**直连兜底**（后端不在时打 gutendex.com）这里不测 —— 那是一个真实的外部
 * 请求，测了就不密封了。它和走后端那条是同一段代码、只是换了 URL。
 */
@RunWith(RobolectricTestRunner::class)
class BookRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var database: InputaDatabase
    private lateinit var server: MockWebServer
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, InputaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    private class FakeHealthRepository(private val value: BackendHealth) : BackendHealthRepository {
        override val health: Flow<BackendHealth> = flowOf(value)
        override suspend fun refresh(): BackendHealth = value
        override suspend fun current(): BackendHealth = value
    }

    private fun repository(
        baseUrl: String = server.url("/").toString(),
        health: BackendHealth = BackendHealth(ok = true, dictionaryAvailable = true),
    ): BookRepositoryImpl = BookRepositoryImpl(
        bookDao = database.bookDao(),
        apiProvider = ApiProvider(
            baseUrlProvider = { baseUrl },
            apiClient = OkHttpClient(),
            healthClient = OkHttpClient(),
            downloadClient = OkHttpClient(),
            searchClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        ),
        health = FakeHealthRepository(health),
        context = context,
        clock = { now },
        json = Json { ignoreUnknownKeys = true },
    )

    private fun ok(body: String) = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .body(body)
        .build()

    // ---------------------------------------------------------------- 内置样书

    /**
     * 这条同时证明 `assets/sample_books.json` 真的被打进了 apk ——
     * 那个文件是从 Web 版的 `src/data/sampleBooks.ts` 生成的，不属于 Gradle 的编译
     * 输入，所以「文件忘了提交」或「assets 目录没接上」都不会让构建失败，
     * 只会在运行时才炸。这里读一次就挡住了。
     */
    @Test
    fun `loads the builtin sample books from assets`() = runTest {
        val books = repository().builtinBooks()

        assertEquals(listOf("alice-in-wonderland", "pride-and-prejudice", "sherlock-holmes"), books.map { it.id })
        assertEquals("Alice's Adventures in Wonderland", books[0].title)
        assertEquals("Lewis Carroll", books[0].author)
        assertEquals(BookSource.BUILTIN, books[0].source)
        assertEquals("en", books[0].language)
        assertEquals(2, books[0].chapters.size)
        assertEquals("CHAPTER I. Down the Rabbit-Hole", books[0].chapters[0].title)
        assertTrue(books[0].chapters[0].content.contains("Alice was beginning to get very tired"))
    }

    @Test
    fun `the builtin books are parsed only once`() = runTest {
        val repository = repository()
        val first = repository.builtinBooks()
        val second = repository.builtinBooks()
        // 同一份实例被复用（缓存生效），不是每次重新读盘解析。
        assertTrue(first === second)
    }

    // ---------------------------------------------------------------- 存取往返

    @Test
    fun `saving a book persists its metadata and chapters`() = runTest {
        val repository = repository()
        repository.save(
            Book(
                id = "gutendex-84",
                title = "Frankenstein",
                author = "Shelley, Mary Wollstonecraft",
                coverUrl = "https://example.com/c.jpg",
                chapters = listOf(
                    BookChapter("CHAPTER I.", "Letter one."),
                    BookChapter("CHAPTER II.", "Letter two."),
                ),
                source = BookSource.GUTENBERG,
                language = "en",
            ),
        )

        val meta = repository.findMeta("gutendex-84")
        assertNotNull(meta)
        assertEquals("Frankenstein", meta?.title)
        assertEquals("Shelley, Mary Wollstonecraft", meta?.author)
        assertEquals(BookSource.GUTENBERG, meta?.source)
        assertEquals(2, meta?.chapterCount)
        // 字符数，不是字节数 —— 见 BookEntity 的说明。
        assertEquals(("Letter one." + "Letter two.").length.toLong(), meta?.charCount)

        assertEquals(listOf("CHAPTER I.", "CHAPTER II."), repository.chapterTitles("gutendex-84"))
        // 按章取：只需要当前这一章，不把整本读进内存。
        assertEquals("Letter two.", repository.chapterContent("gutendex-84", 1))
        assertNull(repository.chapterContent("gutendex-84", 9))
    }

    @Test
    fun `the shelf lists saved books and forgets deleted ones`() = runTest {
        val repository = repository()
        repository.save(Book("a", "Older", "A", chapters = listOf(BookChapter("One", "x")), source = BookSource.GUTENBERG))
        now = 1_700_000_001_000L
        repository.save(Book("b", "Newer", "B", chapters = listOf(BookChapter("One", "y")), source = BookSource.GUTENBERG))

        assertEquals(listOf("Newer", "Older"), repository.observeShelf().first().map { it.title })

        repository.delete("b")

        assertEquals(listOf("Older"), repository.observeShelf().first().map { it.title })
        assertNull(repository.findMeta("b"))
    }

    // ---------------------------------------------------------------- 搜索

    @Test
    fun `searches through the backend when it is up`() = runTest {
        server.enqueue(
            ok(
                """
                {"count":1,"results":[
                  {"id":84,"title":"Frankenstein","authors":[{"name":"Shelley, Mary Wollstonecraft"}],
                   "formats":{"image/jpeg":"https://example.com/c.jpg"},
                   "download_count":4321,"languages":["en"]}
                ]}
                """.trimIndent(),
            ),
        )

        val results = repository().searchGutendex("frankenstein")

        assertEquals(1, results.size)
        assertEquals(84, results[0].id)

        val recorded = server.takeRequest()
        assertEquals("/api/books/search", recorded.url.encodedPath)
        assertEquals("frankenstein", recorded.url.queryParameter("query"))
    }

    @Test
    fun `a blank query costs no request`() = runTest {
        assertEquals(emptyList<GutendexBook>(), repository().searchGutendex("   "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a search failure is reported readably`() = runTest {
        server.enqueue(MockResponse.Builder().code(502).body("""{"error":"upstream"}""").build())

        val error = runCatching { repository().searchGutendex("frankenstein") }.exceptionOrNull()

        assertTrue(error is BookDownloadException)
        assertTrue("message was: ${error?.message}", error?.message?.contains("Gutendex 搜索失败") == true)
    }

    /**
     * 后端不在时**连试都不试**下载，直接给出「去启动后端」的提示。
     *
     * 这条是能密封地测的，因为它在发请求之前就返回了 —— 而且它挡住的是一个真实的
     * 用户困惑：明明手机有网，为什么下不了书。答案是正文下载依赖服务端跟随 302 与
     * 主机白名单，客户端不重复实现一遍。
     */
    @Test
    fun `refuses to download when the backend is down, and says so`() = runTest {
        val repository = repository(health = BackendHealth(ok = false, error = "connection refused"))

        val error = runCatching {
            repository.loadFromGutendex(
                GutendexBook(
                    id = 84,
                    title = "Frankenstein",
                    authors = listOf("Shelley"),
                    formats = mapOf("text/plain; charset=utf-8" to "https://www.gutenberg.org/ebooks/84.txt.utf-8"),
                    downloadCount = 0,
                    languages = listOf("en"),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is BookDownloadException)
        assertTrue("message was: ${error?.message}", error?.message?.contains("npm run server") == true)
        assertEquals("no download attempt should be made", 0, server.requestCount)
    }

    @Test
    fun `downloads through the backend and turns the text into chapters`() = runTest {
        val body = listOf(
            "*** START OF THE PROJECT GUTENBERG EBOOK FRANKENSTEIN ***",
            "",
            "CHAPTER I.",
            "",
            "Letter one text. ".repeat(30).trim(),
            "",
            "CHAPTER II.",
            "",
            "Letter two text. ".repeat(30).trim(),
            "",
            "*** END OF THE PROJECT GUTENBERG EBOOK FRANKENSTEIN ***",
        ).joinToString("\n")
        server.enqueue(MockResponse.Builder().code(200).body(body).build())

        val book = repository().loadFromGutendex(
            GutendexBook(
                id = 84,
                title = "Frankenstein",
                authors = listOf("Shelley, Mary Wollstonecraft"),
                formats = mapOf("text/plain; charset=utf-8" to TXT_URL),
                downloadCount = 0,
                languages = listOf("en"),
            ),
        )

        assertEquals("gutendex-84", book.id)
        assertEquals(listOf("CHAPTER I.", "CHAPTER II."), book.chapters.map { it.title })

        // 正文走的是后端的白名单路由，且 url 作为查询参数被正确编码。
        val recorded = server.takeRequest()
        assertEquals("/api/books/text", recorded.url.encodedPath)
        assertEquals(TXT_URL, recorded.url.queryParameter("url"))
    }

    @Test
    fun `an HTML error page from the backend is not treated as book text`() = runTest {
        // 后端对上游是原样透传的，所以上游的错误页会带着 200 回来。
        server.enqueue(
            MockResponse.Builder().code(200)
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .body("<!DOCTYPE html><html><body>522</body></html>")
                .build(),
        )

        val error = runCatching {
            repository().loadFromGutendex(
                GutendexBook(
                    id = 84,
                    title = "Frankenstein",
                    authors = listOf("Shelley"),
                    formats = mapOf("text/plain; charset=utf-8" to TXT_URL),
                    downloadCount = 0,
                    languages = listOf("en"),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is BookDownloadException)
        assertTrue("message was: ${error?.message}", error?.message?.contains("HTML") == true)
    }

    private companion object {
        const val TXT_URL = "https://www.gutenberg.org/ebooks/84.txt.utf-8"
    }
}
