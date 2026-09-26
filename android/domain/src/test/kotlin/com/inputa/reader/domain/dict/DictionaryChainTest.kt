package com.inputa.reader.domain.dict

import com.inputa.reader.domain.model.DictionarySource
import com.inputa.reader.domain.model.LookupResult
import com.inputa.reader.domain.model.PartOfSpeechShare
import com.inputa.reader.domain.model.WordFrequency
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 词典降级链。逐条移植自 Web 版的 `src/__tests__/dictionary.test.ts`（417 行，
 * 全项目最密的一份规格）。
 *
 * 这条链曾经完全坏掉过：浏览器连不上 api.dictionaryapi.dev（它的 522 错误页没有
 * CORS 头），于是查词整体不可用。所以下面每条断言都对应一个真实故障：
 *
 *   - 离线 ECDICT 文件在时先答它，而它的缺失/失败**不能**把「没这个词」变成「服务故障」
 *   - 后端在跑时每个请求都走同源路由，绝不碰跨域 URL
 *   - 后端不在时走直连上游链
 *   - 404（没这个词）不是故障，传输失败才是
 *
 * 与 Web 版测试的一处结构性差异：那边 health 探测也在 fetch 里，所以 `seen` 数组包含
 * `/api/health`。这里健康状态是**传进链的参数**（探测是 `:app` 的职责），所以 `seen` 只
 * 记 provider 请求 —— 断言更聚焦于「问了哪些来源」本身。
 */
class DictionaryChainTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    /** 假的 requester：按 URL 作答，并记下问过哪些 URL。 */
    private class FakeRequester(private val handler: (String) -> ProviderOutcome) : ProviderRequester {
        val seen = mutableListOf<String>()
        override suspend fun request(url: String): ProviderOutcome {
            seen += url
            return handler(url)
        }
    }

    private fun failure() = ProviderOutcome.Failure("Failed to fetch")
    private fun missing() = ProviderOutcome.Missing

    private fun payload(text: String) = ProviderOutcome.Payload(json(text))

    // ---------------------------------------------------------------- 夹具

    private val wiktionaryPayload = """
        {"en":[{"partOfSpeech":"Adverb","language":"English","definitions":[
          {"definition":"To a <a href=\"/wiki/great\">great</a> extent or degree.",
           "parsedExamples":[{"example":"That dress is <b>very</b> you."}],
           "examples":["That dress is <b>very</b> you."]}]}]}
    """.trimIndent()

    private val datamusePayload = """
        [{"word":"very","score":8040,
          "defs":["adv\tTo a great extent or degree. ","adj\tTrue, real, actual. "]}]
    """.trimIndent()

    private val dictionaryApiPayload = """
        [{"word":"very","phonetic":"/ˈveri/",
          "phonetics":[{"text":"/ˈveri/","audio":"https://example.com/very-us.mp3"}],
          "meanings":[{"partOfSpeech":"adverb","definitions":[{"definition":"To a high degree."}]}]}]
    """.trimIndent()

    /** GET /api/dict —— 离线 ECDICT 里 "very" 那一行。 */
    private val localEcdictPayload = """
        {"query":"very","word":"very","matchedBy":"word","phonetic":"'veri",
         "translation":"a. 真正的, 恰好的, 十足的, 特有的\nadv. 非常, 完全",
         "definition":"r. used as intensifiers; `real' is sometimes used informally for `really'\nr. precisely so",
         "pos":"r:92/j:8",
         "partsOfSpeech":[{"code":"r","label":"副词","abbr":"adv.","percent":92},
                          {"code":"j","label":"形容词","abbr":"adj.","percent":8}],
         "tag":"zk gk",
         "tags":[{"code":"zk","label":"中考"},{"code":"gk","label":"高考"}],
         "lemma":null,"inflection":null,"forms":[],
         "collins":5,"oxford":true,"bnc":80,"frq":105,"audio":null}
    """.trimIndent()

    /** 一个变形词条：running → run，外加 exchange 列里的其余信息。 */
    private val localEcdictInflectedPayload = """
        {"query":"running","word":"running","matchedBy":"word","phonetic":"'rʌniŋ",
         "translation":"n. 赛跑, 流出, 运转\na. 流动的, 跑着的, 连续的",
         "definition":"n. the state of being in operation",
         "pos":"j:63/n:37",
         "partsOfSpeech":[{"code":"j","label":"形容词","abbr":"adj.","percent":63}],
         "tag":"gk","tags":[{"code":"gk","label":"高考"}],
         "lemma":"run","inflection":"现在分词",
         "forms":[{"code":"s","label":"复数","words":["runnings"]}],
         "collins":4,"oxford":true,"bnc":3269,"frq":3252,"audio":null}
    """.trimIndent()

    /**
     * 释义列以冠词 "A" 开头 —— 即一句普通的英文，而不是 WordNet 词性代码。
     * ECDICT 里约 30% 的释义行长这样，它们曾经被标成「形容词」且首词被吃掉。
     */
    private val localEcdictArticleAPayload = """
        {"query":"air bed","word":"air bed","matchedBy":"word","phonetic":null,
         "translation":"n. 充气床垫",
         "definition":"A sack or matters inflated with air, and used as a bed.",
         "pos":null,"partsOfSpeech":[],"tag":null,"tags":[],"lemma":null,"inflection":null,
         "forms":[],"collins":null,"oxford":false,"bnc":null,"frq":null,"audio":null}
    """.trimIndent()

    /** 一条带代码的行（"n. …"）后面跟一条完全没有代码的行。 */
    private val localEcdictMixedPayload = """
        {"query":"ain't","word":"ain't","matchedBy":"word","phonetic":null,
         "translation":"are not 的缩写",
         "definition":"n. a score in baseball\n   [Colloq. or illiterate speech]. See An't.",
         "pos":null,"partsOfSpeech":[],"tag":null,"tags":[],"lemma":null,"inflection":null,
         "forms":[],"collins":null,"oxford":false,"bnc":null,"frq":null,"audio":null}
    """.trimIndent()

    // ---------------------------------------------------------------- 用例

    @Test
    fun `uses same-origin backend routes and never touches a cross-origin URL`() = runTest {
        val requester = FakeRequester { url ->
            if (url == "/api/dictionary/word/very") payload(dictionaryApiPayload) else missing()
        }
        val result = DictionaryLookup(requester).lookup("very", backendUp = true, localDictionaryReady = true)

        val found = result as LookupResult.Found
        assertEquals(DictionarySource.DICTIONARY_API, found.source)
        assertEquals("/ˈveri/", found.entry.phonetic)
        assertEquals("https://example.com/very-us.mp3", found.entry.audioUrl)

        assertTrue(requester.seen.contains("/api/dictionary/word/very"))
        assertTrue(
            "no absolute URL should be touched while the backend is up, saw ${requester.seen}",
            requester.seen.none { it.startsWith("https://") },
        )
    }

    @Test
    fun `falls back to Wiktionary when dictionaryapi dev is unreachable`() = runTest {
        val requester = FakeRequester { url ->
            if (url.contains("entries/en")) failure() else payload(wiktionaryPayload)
        }
        val result = DictionaryLookup(requester)
            .lookup("very", backendUp = false, localDictionaryReady = false)

        val found = result as LookupResult.Found
        assertEquals(DictionarySource.WIKTIONARY, found.source)
        assertEquals("Adverb", found.entry.meanings[0].partOfSpeech)
        // 词典返回的 HTML 标记必须在渲染前去掉。
        assertEquals("To a great extent or degree.", found.entry.meanings[0].definitions[0].definition)
        assertEquals("That dress is very you.", found.entry.meanings[0].definitions[0].example)
        // 没有后端时走直连上游 URL。
        assertTrue(requester.seen.contains("https://api.dictionaryapi.dev/api/v2/entries/en/very"))
    }

    @Test
    fun `falls back to Datamuse when both richer sources fail`() = runTest {
        val requester = FakeRequester { url ->
            if (url.contains("datamuse")) payload(datamusePayload) else failure()
        }
        val result = DictionaryLookup(requester)
            .lookup("very", backendUp = false, localDictionaryReady = false)

        val found = result as LookupResult.Found
        assertEquals(DictionarySource.DATAMUSE, found.source)
        assertEquals(listOf("adverb", "adjective"), found.entry.meanings.map { it.partOfSpeech })
        assertEquals("To a great extent or degree.", found.entry.meanings[0].definitions[0].definition)
    }

    @Test
    fun `reports an outage when every source fails at the transport level`() = runTest {
        val result = DictionaryLookup(FakeRequester { failure() })
            .lookup("very", backendUp = false, localDictionaryReady = false)

        assertEquals(LookupResult.Unavailable, result)
    }

    @Test
    fun `treats an upstream 404 as word not found, not as an outage`() = runTest {
        val result = DictionaryLookup(FakeRequester { missing() })
            .lookup("zzzznotaword", backendUp = false, localDictionaryReady = false)

        assertEquals(LookupResult.NotFound, result)
    }

    @Test
    fun `caches not found answers so the next click costs no request`() = runTest {
        val requester = FakeRequester { missing() }
        val chain = DictionaryLookup(requester)

        chain.lookup("zzzznotaword", backendUp = false, localDictionaryReady = false)
        val afterFirst = requester.seen.size

        val second = chain.lookup("zzzznotaword", backendUp = false, localDictionaryReady = false)

        assertEquals(LookupResult.NotFound, second)
        assertEquals("second lookup must not hit the network", afterFirst, requester.seen.size)
    }

    @Test
    fun `retries a failed word instead of caching the outage`() = runTest {
        var healthy = false
        val requester = FakeRequester { url ->
            if (!healthy) failure()
            else if (url.contains("/api/dictionary/word/")) payload(dictionaryApiPayload) else missing()
        }
        val chain = DictionaryLookup(requester)

        assertEquals(LookupResult.Unavailable, chain.lookup("very", backendUp = true, localDictionaryReady = true))

        healthy = true
        val recovered = chain.lookup("very", backendUp = true, localDictionaryReady = true)

        val found = recovered as LookupResult.Found
        assertEquals(DictionarySource.DICTIONARY_API, found.source)
        assertEquals("/ˈveri/", found.entry.phonetic)
    }

    @Test
    fun `answers from the offline ECDICT dictionary before touching the network`() = runTest {
        val requester = FakeRequester { url ->
            if (url == "/api/dict?word=very") payload(localEcdictPayload) else missing()
        }
        val result = DictionaryLookup(requester)
            .lookup("very", backendUp = true, localDictionaryReady = true)

        val found = result as LookupResult.Found
        assertEquals(DictionarySource.LOCAL_ECDICT, found.source)
        assertEquals("'veri", found.entry.phonetic)

        // 中文释义、考试标签、词频只有离线来源才有。
        assertEquals(true, found.entry.extra?.translation?.contains("非常"))
        assertEquals(listOf("中考", "高考"), found.entry.extra?.examTags)
        assertEquals(
            PartOfSpeechShare(label = "副词", abbr = "adv.", percent = 92),
            found.entry.extra?.partsOfSpeech?.first(),
        )
        assertEquals(
            WordFrequency(collins = 5, oxford = true, bnc = 80, frq = 105),
            found.entry.extra?.frequency,
        )

        // 英文释义保留 WordNet 的词性分组。
        assertEquals(listOf("adverb"), found.entry.meanings.map { it.partOfSpeech })
        assertEquals(
            listOf(
                "used as intensifiers; `real' is sometimes used informally for `really'",
                "precisely so",
            ),
            found.entry.meanings[0].definitions.map { it.definition },
        )

        // 离线命中即终止查询：不再问任何在线来源。
        assertEquals(listOf("/api/dict?word=very"), requester.seen)
    }

    @Test
    fun `carries the inflection data of an offline entry through`() = runTest {
        val requester = FakeRequester { url ->
            if (url == "/api/dict?word=running") payload(localEcdictInflectedPayload) else missing()
        }
        val found = DictionaryLookup(requester)
            .lookup("running", backendUp = true, localDictionaryReady = true) as LookupResult.Found

        assertEquals("running", found.entry.word)
        assertEquals("run", found.entry.extra?.lemma)
        assertEquals("现在分词", found.entry.extra?.inflection)
        assertEquals(1, found.entry.extra?.forms?.size)
        assertEquals("复数", found.entry.extra?.forms?.first()?.label)
        assertEquals(listOf("runnings"), found.entry.extra?.forms?.first()?.words)
    }

    /**
     * 回归：词性前缀曾经允许点号可选，于是普通英文句子开头的 "A" 被当成 WordNet
     * 代码 `a.` 吃掉 —— 那一行被标成「形容词」，而且丢了第一个词。
     */
    @Test
    fun `keeps the first word of a definition that opens with the article A`() = runTest {
        val requester = FakeRequester { url ->
            if (url == "/api/dict?word=air%20bed") payload(localEcdictArticleAPayload) else missing()
        }
        val found = DictionaryLookup(requester)
            .lookup("air bed", backendUp = true, localDictionaryReady = true) as LookupResult.Found

        assertEquals(listOf(UNKNOWN_PART_OF_SPEECH), found.entry.meanings.map { it.partOfSpeech })
        assertEquals(
            listOf("A sack or matters inflated with air, and used as a bed."),
            found.entry.meanings[0].definitions.map { it.definition },
        )
    }

    @Test
    fun `labels a coded line and leaves an uncoded one unlabelled`() = runTest {
        // "n. " 保留名词分组；方括号里的用法说明不带代码，所以必须原样归入哨兵分组，
        // 而不是被猜成某个词性。
        val requester = FakeRequester { url ->
            if (url == "/api/dict?word=ain't") payload(localEcdictMixedPayload) else missing()
        }
        val found = DictionaryLookup(requester)
            .lookup("ain't", backendUp = true, localDictionaryReady = true) as LookupResult.Found

        assertEquals(listOf("noun", UNKNOWN_PART_OF_SPEECH), found.entry.meanings.map { it.partOfSpeech })
        assertEquals(
            listOf("a score in baseball"),
            found.entry.meanings[0].definitions.map { it.definition },
        )
        assertEquals(
            "[Colloq. or illiterate speech]. See An't.",
            found.entry.meanings[1].definitions[0].definition,
        )
    }

    @Test
    fun `skips the offline dictionary when the backend reports no database`() = runTest {
        val requester = FakeRequester { url ->
            if (url == "/api/dictionary/word/very") payload(dictionaryApiPayload) else missing()
        }
        val found = DictionaryLookup(requester)
            .lookup("very", backendUp = true, localDictionaryReady = false) as LookupResult.Found

        assertEquals(DictionarySource.DICTIONARY_API, found.source)
        assertTrue(
            "the offline route must not be asked when the backend has no database",
            requester.seen.none { it.startsWith("/api/dict?") },
        )
    }

    @Test
    fun `does not report an outage when only the optional offline dictionary fails`() = runTest {
        val requester = FakeRequester { url ->
            // 只有离线路由坏掉；在线来源正常作答「没这个词」。
            if (url.startsWith("/api/dict?")) failure() else missing()
        }
        val result = DictionaryLookup(requester)
            .lookup("zzzznotaword", backendUp = true, localDictionaryReady = true)

        // 在线来源明确答了「没这个词」，所以这不是故障。
        assertEquals(LookupResult.NotFound, result)
    }

    /**
     * 编码规则必须与 JS 的 `encodeURIComponent` 一致 —— 它不转义 `'`，
     * 而 `URLEncoder` 会转成 `%27`。Web 版有断言钉住 URL 里那条裸撇号。
     */
    @Test
    fun `encodes a word the way encodeURIComponent does`() = runTest {
        val requester = FakeRequester { missing() }
        DictionaryLookup(requester).lookup("ain't", backendUp = true, localDictionaryReady = true)

        assertTrue(
            "expected a bare apostrophe, saw ${requester.seen}",
            requester.seen.contains("/api/dict?word=ain't"),
        )
    }

    @Test
    fun `encodes a space as percent-20, not as a plus`() = runTest {
        val requester = FakeRequester { missing() }
        DictionaryLookup(requester).lookup("air bed", backendUp = true, localDictionaryReady = true)

        assertTrue(
            "expected %20 for the space, saw ${requester.seen}",
            requester.seen.contains("/api/dict?word=air%20bed"),
        )
    }

    @Test
    fun `a blank word is not found and costs no request`() = runTest {
        val requester = FakeRequester { missing() }
        val chain = DictionaryLookup(requester)

        assertEquals(LookupResult.NotFound, chain.lookup("   ", backendUp = true, localDictionaryReady = true))
        assertEquals(emptyList<String>(), requester.seen)
    }

    /** 缓存键是规范化后的词，所以大小写与首尾空白不影响命中。 */
    @Test
    fun `the cache key is normalized`() = runTest {
        val requester = FakeRequester { url ->
            if (url == "/api/dict?word=very") payload(localEcdictPayload) else missing()
        }
        val chain = DictionaryLookup(requester)

        chain.lookup("very", backendUp = true, localDictionaryReady = true)
        val seenAfterFirst = requester.seen.size
        chain.lookup("  VERY  ", backendUp = true, localDictionaryReady = true)

        assertEquals(seenAfterFirst, requester.seen.size)
    }
}
