package com.inputa.reader.domain.model

/**
 * 一个词的收录状态。
 *
 *   1-5        熟练度，正文里按色阶高亮（见 WordLevels.highlightLevel），1 最浅（熟知）→ 5 最深（生词）
 *   MASTERED   独立的第 6 个状态「掌握」：正文不再高亮
 *
 * 「从未收录」不在这个类型里 —— Web 版用一个 `'unknown'` 字符串哨兵表示，Kotlin 这边
 * 直接用 **null**（见 [WordLevels.highlightLevel]）。少一个哨兵值，`when` 也就不可能漏分支。
 *
 * `code` 同时是 Room `vocabulary.status` 列的值：1-5 就是等级本身，6 = MASTERED。
 * 这是本类型唯一与持久化耦合的地方，换列类型时只需改 [com.inputa.reader.domain.vocab.WordLevelCodec]。
 */
enum class WordStatus(val code: Int) {
    L1(1),
    L2(2),
    L3(3),
    L4(4),
    L5(5),
    MASTERED(6);

    /**
     * 是不是 1-5 的熟练度等级。MASTERED 返回 false。
     *
     * 对应 Web 版的 `isLevel`。注意它只回答「状态是什么」，**不回答「该用什么颜色」** ——
     * 未收录的词要按 5 级着色，那是 highlightLevel 的事。
     */
    val isLevel: Boolean get() = this != MASTERED
}
