package com.inputa.reader.data.repository

import com.inputa.reader.data.remote.ApiProvider
import com.inputa.reader.data.remote.OkHttpProviderRequester
import com.inputa.reader.domain.dict.DICTIONARY_PROVIDERS
import com.inputa.reader.domain.dict.DictionaryLookup
import com.inputa.reader.domain.dict.ProviderOutcome
import com.inputa.reader.domain.model.DictionarySource
import com.inputa.reader.domain.model.LookupResult
import com.inputa.reader.domain.repository.BackendHealth
import com.inputa.reader.domain.repository.BackendHealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * HTTP 接线。链的**规则**已经在 `:domain` 的 DictionaryChainTest 里用假 requester
 * 逐条验过了（而且更全），所以这里只验两件只有真 HTTP 才能验的事：
 *
 *   1. Retrofit 的 `@Url` 传**相对路径**时确实会解析到配置的 baseUrl ——
 *      整个网络层的设计都押在这一点上，而它没法用假 requester 验。
 *   2. 状态码到三态的映射：404 → 「没这个词」，其余非 2xx 与无法解析 → 传输故障。
 *
 * 用 Robolectric 只是为了 `android.util.Log`（降级链在 DEBUG 下会打日志）。
 */
@RunWith(RobolectricTestRunner::class)
class DictionaryRepositoryImplTest {

    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer

    @Before
    fun setUp() {
        serverA = MockWebServer().apply { start() }
        serverB = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        serverA.close()
        serverB.close()
    }

    /** 记下「读缓存」与「强制重探」各被调了几次。 */
    private class CountingHealthRepository(private var value: BackendHealth) : BackendHealthRepository {
        var refreshCount = 0
            private set
        var currentCount = 0
            private set

        override val health: Flow<BackendHealth> = flowOf(value)

        override suspend fun refresh(): BackendHealth {
            refreshCount++
            return value
        }

        override suspend fun current(): BackendHealth {
            currentCount++
            return value
        }
    }

    private var baseUrl: String = ""

    /**
     * 来源列表**去掉直连上游 URL**。
     *
     * 这一步是让测试密封的关键：真实配置里，后端路由失败后链会去试
     * `https://api.dictionaryapi.dev/...` —— 那是打向公网的真实请求，
     * 测试会随网络状况时好时坏。去掉直连候选后所有请求都落在 MockWebServer 上。
     * （直连 URL 的选择逻辑本身已经在 `:domain` 的 DictionaryChainTest 里验过。）
     */
    private val hermeticProviders = DICTIONARY_PROVIDERS.map { it.copy(directUrl = null) }

    private fun apiProvider(server: MockWebServer): ApiProvider {
        baseUrl = server.url("/").toString()
        return ApiProvider(
            baseUrlProvider = { baseUrl },
            apiClient = OkHttpClient(),
            healthClient = OkHttpClient(),
            downloadClient = OkHttpClient(),
            searchClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    private fun repository(
        server: MockWebServer = serverA,
        health: CountingHealthRepository = CountingHealthRepository(
            BackendHealth(ok = true, dictionaryAvailable = true),
        ),
    ): DictionaryRepositoryImpl {
        val provider = apiProvider(server)
        val chain = DictionaryLookup(
            requester = OkHttpProviderRequester(provider),
            providers = hermeticProviders,
        )
        return DictionaryRepositoryImpl(chain, health)
    }

    private fun ok(body: String) = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .body(body)
        .build()

    private fun status(code: Int) = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .body("""{"error":"nope"}""")
        .build()

    /** 一条最小的 /api/dict 命中。 */
    private val ecdictBody = """
        {"query":"very","word":"very","matchedBy":"word","phonetic":"'veri",
         "translation":"adv. 非常","definition":"r. precisely so","pos":"r:100",
         "partsOfSpeech":[],"tag":null,"tags":[],"lemma":null,"inflection":null,
         "forms":[],"collins":null,"oxford":false,"bnc":null,"frq":null,"audio":null}
    """.trimIndent()

    @Test
    fun `resolves a relative backend path against the configured server`() = runTest {
        serverA.enqueue(ok(ecdictBody))

        val result = repository().lookup("very")

        val found = result as LookupResult.Found
        assertEquals(DictionarySource.LOCAL_ECDICT, found.source)
        assertEquals("'veri", found.entry.phonetic)

        val recorded = serverA.takeRequest()
        assertEquals("/api/dict", recorded.url.encodedPath)
        assertEquals("very", recorded.url.queryParameter("word"))
    }

    @Test
    fun `maps a 404 to a not-found answer, not an outage`() = runTest {
        repeat(4) { serverA.enqueue(status(404)) }

        assertEquals(LookupResult.NotFound, repository().lookup("zzzznotaword"))
    }

    @Test
    fun `maps a 5xx to an outage`() = runTest {
        repeat(4) { serverA.enqueue(status(502)) }

        assertEquals(LookupResult.Unavailable, repository().lookup("very"))
    }

    /**
     * 后端对上游是**状态码与响应体原样透传**的，包括把上游返回的 HTML 也标成
     * `application/json`。所以反序列化会失败 —— 那必须算传输故障，而不是「没这个词」。
     */
    @Test
    fun `maps an unparseable 200 body to an outage`() = runTest {
        repeat(4) {
            serverA.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "application/json; charset=utf-8")
                    .body("<html><body>522</body></html>")
                    .build(),
            )
        }

        assertEquals(LookupResult.Unavailable, repository().lookup("very"))
    }

    /**
     * 回归：**Retrofit 的 kotlinx-serialization 转换器对裸文本是宽松的** ——
     * 一段 `<html>…</html>` 配着 `application/json` 回来时它不抛异常，而是安静地
     * 解成一个字符串标量。
     *
     * 不挡这一道的话，链会把它当成「答了但没释义」，于是读者在后端或反向代理坏掉时
     * 看到的是「词典均未收录该词」—— 一个错误的结论，而且会被缓存下来。
     *
     * 这条断言直接盯住传输层，所以将来换 JSON 库或改转换器配置时，一旦宽松性变化
     * 就会立刻失败，而不是悄悄退化成语义错误。
     */
    @Test
    fun `an html body with a json content type is a transport failure, not a payload`() = runTest {
        serverA.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .body("<html><body>522</body></html>")
                .build(),
        )

        val outcome = OkHttpProviderRequester(apiProvider(serverA)).request("/api/dict")

        assertTrue("expected a Failure, got $outcome", outcome is ProviderOutcome.Failure)
    }

    @Test
    fun `skips the offline route when the backend reports no dictionary`() = runTest {
        repeat(4) { serverA.enqueue(status(404)) }

        val result = repository(
            health = CountingHealthRepository(BackendHealth(ok = true, dictionaryAvailable = false)),
        ).lookup("very")

        assertEquals(LookupResult.NotFound, result)
        // 请求已经全部记下了，不必带超时等待。
        val requestedPaths = (0 until serverA.requestCount).map { serverA.takeRequest().url.encodedPath }
        assertTrue(
            "the offline route must not be requested, saw $requestedPaths",
            requestedPaths.none { it == "/api/dict" },
        )
    }

    /**
     * 服务器地址是用户可改的设置，而 Retrofit 的 baseUrl 不可变 ——
     * 所以改地址后必须重建实例。这条钉住那层缓存确实按地址失效。
     */
    // --------------------------------------------------------- 健康状态的重探

    /** 普通查词读缓存的健康状态，不付探测的代价（后端没在跑时那是 2.5 秒）。 */
    @Test
    fun `a plain lookup reads the cached health`() = runTest {
        val health = CountingHealthRepository(BackendHealth(ok = true, dictionaryAvailable = true))
        repeat(4) { serverA.enqueue(status(404)) }

        repository(health = health).lookup("very")

        assertEquals(1, health.currentCount)
        assertEquals("must not re-probe on a normal lookup", 0, health.refreshCount)
    }

    /**
     * 回归：**设备验证时发现的**。健康状态按服务器地址缓存，而 Android 没有浏览器的
     * 「刷新页面」—— 所以「用户刚把后端启起来 → 点重新查询」这个最该成功的场景，
     * 会因为还拿着旧的「后端不在」结论而永远失败。重试必须重探。
     */
    @Test
    fun `retrying re-probes the backend health instead of trusting the cache`() = runTest {
        val health = CountingHealthRepository(BackendHealth(ok = false, error = "连接被拒绝"))
        repeat(4) { serverA.enqueue(status(404)) }

        repository(health = health).lookup("very", refreshBackend = true)

        assertEquals("the retry must force a fresh probe", 1, health.refreshCount)
        assertEquals("and must not fall back to the cache", 0, health.currentCount)
    }

    @Test
    fun `picks up a changed server address on the same instance`() = runTest {
        val repository = repository(server = serverA)

        serverA.enqueue(ok(ecdictBody))
        assertTrue(repository.lookup("very") is LookupResult.Found)
        assertEquals("/api/dict", serverA.takeRequest().url.encodedPath)

        // 把地址指向 B，同一个仓储实例的下一查必须打到 B，而不是继续打 A。
        baseUrl = serverB.url("/").toString()
        serverB.enqueue(ok(ecdictBody))

        assertTrue(repository.lookup("another") is LookupResult.Found)

        val recorded = serverB.takeRequest()
        assertEquals("/api/dict", recorded.url.encodedPath)
        assertEquals("another", recorded.url.queryParameter("word"))
        assertEquals("A must not receive the second lookup", 1, serverA.requestCount)
    }
}
