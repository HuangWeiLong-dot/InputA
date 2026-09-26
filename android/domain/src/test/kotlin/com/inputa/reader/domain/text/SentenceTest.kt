package com.inputa.reader.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 这批测试是**移植带来的净增覆盖**：`getSentenceForToken` 在 Web 版里长在
 * ReaderArea.tsx 内部，而 Web 版的测试跑在无 DOM 的 node 环境、没有任何组件渲染
 * 覆盖，所以它从未被测过。搬进领域层之后才测得上。
 */
class SentenceTest {

    private fun sentenceAt(text: String, word: String): String {
        val tokens = Tokenizer.tokenize(text)
        val index = tokens.indexOfFirst { it.isWord && it.cleanWord == word.lowercase() }
        return Sentence.forToken(tokens, index)
    }

    @Test
    fun `returns the sentence containing the word`() {
        val text = "Alice was beginning to get very tired. Then she saw the rabbit."
        assertEquals("Alice was beginning to get very tired.", sentenceAt(text, "tired"))
        assertEquals("Then she saw the rabbit.", sentenceAt(text, "rabbit"))
    }

    @Test
    fun `starts at the beginning when there is no earlier terminator`() {
        val text = "First part here and then suddenly the end. Second sentence."
        assertEquals("First part here and then suddenly the end.", sentenceAt(text, "suddenly"))
    }

    /**
     * 已知且**有意保留**的行为：不处理缩写。`.` 就是判据，所以 `Mr.` 会被当成句尾。
     * 这是已发布的行为，不是 bug —— 换一套缩写规则会改变面板里显示的句子、
     * 进而改变 AI 分析与保存的例句，属于产品决策而不是移植。
     *
     * sampleBooks.ts 里就有这样的文本，所以这是真实会遇到的输入。
     */
    @Test
    fun `truncates at an abbreviation, as the shipped web version does`() {
        val text = "Mr. Bennet was among the earliest of those who waited on Mr. Bingley."
        assertEquals("Bennet was among the earliest of those who waited on Mr.", sentenceAt(text, "earliest"))
    }

    @Test
    fun `falls back to the whole text when there is no terminator at all`() {
        val text = "no punctuation anywhere in this line"
        assertEquals(text, sentenceAt(text, "punctuation"))
    }

    /** 区间反转时 JS 的 slice 返回空数组，Kotlin 的 subList 会抛 —— 必须挡住。 */
    @Test
    fun `answers empty for an out of range index`() {
        val tokens = Tokenizer.tokenize("Hello world.")
        assertEquals("", Sentence.forToken(tokens, 999))
        assertEquals("", Sentence.forToken(emptyList(), 0))
    }

    @Test
    fun `keeps the original casing and internal punctuation`() {
        val text = "\"Stop!\" she cried. He didn't move."
        assertEquals("\"Stop!\"", sentenceAt(text, "stop"))
        assertEquals("He didn't move.", sentenceAt(text, "didn't"))
    }
}
