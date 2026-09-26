package com.inputa.reader.domain.vocab

import com.inputa.reader.domain.model.WordStatus

/** 生词本的三个筛选档。默认「未掌握」—— 那是读者最常看的一档。 */
enum class VocabularyFilter {
    /** 1-5 级，还没掌握的。 */
    ACTIVE,
    MASTERED,
    ALL,
}

data class VocabularyItem(
    val word: String,
    val status: WordStatus,
)

data class VocabularyCounts(
    val active: Int,
    val mastered: Int,
) {
    val total: Int get() = active + mastered
}

/**
 * 生词本的筛选与计数。纯函数。
 *
 * 这批逻辑原本在 Web 版的 `VocabularyModal.tsx` 里（339 行组件），而那边的测试跑在
 * 无 DOM 的环境、零组件渲染覆盖 —— 所以**它从未被测过**。搬到领域层就有了单测，
 * 与 `DefinitionPresenter` 是同一件事。
 */
object VocabularyPresenter {

    /**
     * 按筛选档与搜索词过滤。
     *
     * 搜索是**大小写无关的子串匹配**，且针对的键本身就是小写的（词库的键统一为
     * `normalizeKey` 的结果）—— 所以判断之前先把搜索词也小写并去空白。
     *
     * @param insertionOrder 键的插入顺序由调用方给出（来自数据库查询）。
     *   刻意**不按字母排序**：读者期望新收录的词出现在它被收进来的位置附近，
     *   而不是每次打开面板都被重新洗牌。
     */
    fun filter(
        items: List<VocabularyItem>,
        filter: VocabularyFilter,
        query: String = "",
    ): List<VocabularyItem> {
        val needle = query.trim().lowercase()
        return items.filter { item ->
            matchesFilter(item.status, filter) &&
                (needle.isEmpty() || item.word.contains(needle))
        }
    }

    fun counts(items: List<VocabularyItem>): VocabularyCounts {
        var active = 0
        var mastered = 0
        for (item in items) {
            if (WordLevels.isLevel(item.status)) active++ else mastered++
        }
        return VocabularyCounts(active = active, mastered = mastered)
    }

    private fun matchesFilter(status: WordStatus, filter: VocabularyFilter): Boolean = when (filter) {
        VocabularyFilter.ACTIVE -> WordLevels.isLevel(status)
        VocabularyFilter.MASTERED -> !WordLevels.isLevel(status)
        VocabularyFilter.ALL -> true
    }
}
