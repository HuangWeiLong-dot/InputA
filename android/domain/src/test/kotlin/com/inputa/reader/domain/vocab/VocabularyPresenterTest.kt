package com.inputa.reader.domain.vocab

import com.inputa.reader.domain.model.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 生词本的筛选与计数。
 *
 * 这批逻辑原本长在 Web 版的 `VocabularyModal.tsx`（339 行）里，而那边的测试跑在
 * 无 DOM 的环境、零组件渲染覆盖 —— 所以**它从未被测过**。搬到领域层就有了单测，
 * 与 `DefinitionPresenter` 是同一件事。
 */
class VocabularyPresenterTest {

    private val items = listOf(
        VocabularyItem("alice", WordStatus.L5),
        VocabularyItem("wonderland", WordStatus.L2),
        VocabularyItem("rabbit", WordStatus.MASTERED),
        VocabularyItem("cheshire", WordStatus.L4),
        VocabularyItem("caterpillar", WordStatus.MASTERED),
    )

    private fun words(filter: VocabularyFilter, query: String = "") =
        VocabularyPresenter.filter(items, filter, query).map { it.word }

    /** 默认档是「未掌握」—— 那是读者最常看的一档。 */
    @Test
    fun `active keeps only levels 1 to 5`() {
        assertEquals(listOf("alice", "wonderland", "cheshire"), words(VocabularyFilter.ACTIVE))
    }

    @Test
    fun `mastered keeps only mastered`() {
        assertEquals(listOf("rabbit", "caterpillar"), words(VocabularyFilter.MASTERED))
    }

    @Test
    fun `all keeps everything`() {
        assertEquals(5, words(VocabularyFilter.ALL).size)
    }

    @Test
    fun `counts split active from mastered`() {
        assertEquals(VocabularyCounts(active = 3, mastered = 2), VocabularyPresenter.counts(items))
        assertEquals(5, VocabularyPresenter.counts(items).total)
    }

    @Test
    fun `counts of an empty library are zero`() {
        assertEquals(VocabularyCounts(0, 0), VocabularyPresenter.counts(emptyList()))
    }

    // ---------------------------------------------------------------- 搜索

    /** 词库的键统一是小写的，所以搜索词也在判断前小写 —— 两端都不该指望对方的大小写。 */
    @Test
    fun `search is case-insensitive`() {
        assertEquals(listOf("cheshire"), words(VocabularyFilter.ALL, "CHESHIRE"))
        assertEquals(listOf("cheshire"), words(VocabularyFilter.ALL, "chesh"))
    }

    @Test
    fun `search ignores surrounding whitespace`() {
        assertEquals(listOf("alice"), words(VocabularyFilter.ALL, "  alice  "))
    }

    /** 空搜索词等于不筛 —— 而不是「筛出一个空列表」。 */
    @Test
    fun `a blank query does not filter anything out`() {
        assertEquals(5, words(VocabularyFilter.ALL, "   ").size)
        assertEquals(5, words(VocabularyFilter.ALL, "").size)
    }

    /**
     * 搜索与筛选档是**同时**生效的，不是二选一。这条容易写错成「有搜索词就忽略筛选」。
     */
    @Test
    fun `search combines with the filter instead of replacing it`() {
        // "rabbit" 已掌握 —— 在「未掌握」档里搜它应当什么都找不到。
        assertEquals(emptyList<String>(), words(VocabularyFilter.ACTIVE, "rabbit"))
        assertEquals(listOf("rabbit"), words(VocabularyFilter.MASTERED, "rabbit"))
        assertEquals(listOf("rabbit"), words(VocabularyFilter.ALL, "rabbit"))
    }

    @Test
    fun `a query that matches nothing gives an empty list, not an error`() {
        assertEquals(emptyList<String>(), words(VocabularyFilter.ALL, "zzzz"))
    }

    // ---------------------------------------------------------------- 顺序

    /**
     * **刻意不按字母排序**：顺序来自数据库查询（按最近更新在前），这样刚收录的词
     * 出现在最上面，而不是每次打开面板都被重新洗牌到字母序里的某个位置。
     */
    @Test
    fun `the incoming order is preserved`() {
        val ordered = listOf(
            VocabularyItem("zebra", WordStatus.L5),
            VocabularyItem("apple", WordStatus.L5),
            VocabularyItem("mango", WordStatus.L5),
        )
        assertEquals(listOf("zebra", "apple", "mango"), VocabularyPresenter.filter(ordered, VocabularyFilter.ALL, "").map { it.word })
    }
}
