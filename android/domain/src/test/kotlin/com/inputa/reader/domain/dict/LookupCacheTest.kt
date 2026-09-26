package com.inputa.reader.domain.dict

import com.inputa.reader.domain.model.DictionaryEntry
import com.inputa.reader.domain.model.DictionarySource
import com.inputa.reader.domain.model.LookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓存的缓存规则。
 *
 * 这批断言在 Web 版里散落在 `dictionary.test.ts` 的两条用例里（「没找到会被缓存」与
 * 「故障不被缓存」）。这里额外钉住的是**上界**与**故障拒绝是结构性的** ——
 * Web 版靠调用点自觉，Android 这边写进了 `put`，将来新增调用点也绕不过去。
 */
class LookupCacheTest {

    private fun found(word: String) = LookupResult.Found(
        entry = DictionaryEntry(word = word),
        source = DictionarySource.LOCAL_ECDICT,
    )

    @Test
    fun `stores hits and misses`() {
        val cache = LookupCache()

        cache.put("very", found("very"))
        cache.put("zzzz", LookupResult.NotFound)

        assertEquals(found("very"), cache.get("very"))
        assertEquals(LookupResult.NotFound, cache.get("zzzz"))
    }

    /**
     * 最关键的一条：故障一旦被缓存，界面上那个「重新查询」按钮就永远打不通，
     * 用户只能杀进程。所以 `put` 直接拒绝它。
     */
    @Test
    fun `never stores an outage`() {
        val cache = LookupCache()

        cache.put("very", LookupResult.Unavailable)

        assertNull(cache.get("very"))
        assertFalse(cache.contains("very"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `keys are normalized so case and padding do not matter`() {
        val cache = LookupCache()
        cache.put("Very", found("very"))

        assertEquals(found("very"), cache.get("very"))
        assertEquals(found("very"), cache.get("  VERY  "))
    }

    @Test
    fun `evicts the least recently used entry once full`() {
        val cache = LookupCache(maxEntries = 3)

        cache.put("one", found("one"))
        cache.put("two", found("two"))
        cache.put("three", found("three"))

        // 摸一下 one，让 two 成为最久未用的。
        cache.get("one")

        cache.put("four", found("four"))

        assertEquals(3, cache.size())
        assertNull("two was the least recently used", cache.get("two"))
        assertEquals(found("one"), cache.get("one"))
        assertEquals(found("four"), cache.get("four"))
    }

    @Test
    fun `a miss for an unknown word is null, not a cached NotFound`() {
        val cache = LookupCache()
        assertNull(cache.get("never-seen"))
        assertFalse(cache.contains("never-seen"))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = LookupCache()
        cache.put("very", found("very"))

        cache.clear()

        assertEquals(0, cache.size())
        assertNull(cache.get("very"))
    }

    @Test
    fun `refuses to evict below a single entry`() {
        val cache = LookupCache(maxEntries = 1)
        cache.put("one", found("one"))
        cache.put("two", found("two"))

        assertEquals(1, cache.size())
        assertEquals(found("two"), cache.get("two"))
    }

    /** 缺失的词不被缓存，所以它每次都还得问一遍 —— 这是对的，不是遗漏。 */
    @Test
    fun `an entry that was never put is not the same as a cached miss`() {
        val cache = LookupCache()
        cache.put("known", LookupResult.NotFound)
        assertTrue(cache.contains("known"))
        assertFalse(cache.contains("unknown"))
    }
}
