package com.inputa.reader.domain.dict

import com.inputa.reader.domain.model.LookupResult

/**
 * 查词结果的内存缓存。
 *
 * Web 版是一个**无界、无 TTL** 的进程内 `Map`。那在浏览器标签页里合理（活几分钟到
 * 几小时），但 Android 进程可以活好几天，所以这里加了上界。用访问序 LRU：
 * 反复查的词留下，一次性查过的词先被挤掉。
 *
 * 两条规则写在这个类里而不是调用点，因为它们是**不变量**而不是调用方的选择：
 *
 *   1. **绝不为故障落缓存。** `LookupResult.Unavailable` 是临时的，一旦缓存下来，
 *      UI 上那个「重新查询」按钮就永远打不通了 —— 用户只能杀进程。Web 版靠调用点
 *      自觉，这里由 [put] 直接拒绝，将来新增调用点也不可能绕过。
 *   2. 缓存键是规范化后的词（trim + 小写），与词库的键同一套规则。
 *
 * 线程安全：协程可能从不同线程查同一个词，所以每个操作都加锁。
 * 这是个极短的临界区，不值得上更复杂的东西。
 */
class LookupCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    // accessOrder = true 才是 LRU：每次 get 都会把条目移到队尾。
    private val entries = object : LinkedHashMap<String, LookupResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LookupResult>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(word: String): LookupResult? = entries[normalize(word)]

    @Synchronized
    fun put(word: String, result: LookupResult) {
        // 故障不缓存，见类注释。
        if (result is LookupResult.Unavailable) return
        entries[normalize(word)] = result
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size

    /** 仅供测试：缓存里有没有这个词。 */
    @Synchronized
    fun contains(word: String): Boolean = entries.containsKey(normalize(word))

    private fun normalize(word: String): String = word.trim().lowercase()

    companion object {
        /**
         * 比 Web 版的无界多了个界，但足够大：读者一次会话里反复看的是同一批词，
         * 而每条结果只有几 KB。200 条约等于一两章的生词量。
         */
        const val DEFAULT_MAX_ENTRIES = 200
    }
}
