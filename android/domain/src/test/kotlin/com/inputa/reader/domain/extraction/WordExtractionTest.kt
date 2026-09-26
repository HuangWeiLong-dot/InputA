package com.inputa.reader.domain.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 移植自 Web 版的 src/__tests__/wordExtraction.test.ts，逐条对应。 */
class WordExtractionTest {

    private fun clean(text: String): List<String> = WordExtraction.extract(text).map { it.clean }

    @Test
    fun `splits a sentence into words and drops the punctuation`() {
        assertEquals(
            listOf("screens", "are", "ubiquitous", "and", "that", "is"),
            clean("Screens are ubiquitous, and that is that."),
        )
    }

    @Test
    fun `de-duplicates case-insensitively but keeps the first spelling`() {
        val words = WordExtraction.extract("Alice saw alice and ALICE again.")
        assertEquals(1, words.count { it.clean == "alice" })
        // 面板该显示 "Alice"，不是小写版本。
        assertEquals("Alice", words.first { it.clean == "alice" }.raw)
    }

    @Test
    fun `keeps contractions and hyphenated words whole`() {
        val result = clean("He didn't like the well-known rabbit-hole.")
        assertTrue(result.contains("didn't"))
        assertTrue(result.contains("well-known"))
        assertTrue(result.contains("rabbit-hole"))
    }

    @Test
    fun `drops single letters, bare numbers and symbols`() {
        // "a" 与 "I" 是语法不是词汇；42 与 --- 根本不是词。
        assertEquals(listOf("saw", "cat", "times", "really"), clean("I saw a cat, 42 times --- really!"))
    }

    @Test
    fun `keeps accented words intact`() {
        val result = clean("Le café était naïve à Paris.")
        assertTrue(result.contains("café"))
        assertTrue(result.contains("naïve"))
    }

    /**
     * 分词器在收录时会去掉首尾撇号，所以提取也必须去掉，否则查不到自己的状态。
     */
    @Test
    fun `strips surrounding quotes so the word still matches its vocabulary key`() {
        assertTrue(clean("'twas the night").contains("twas"))
    }

    @Test
    fun `answers nothing for blank input`() {
        assertEquals(emptyList<String>(), clean(""))
        assertEquals(emptyList<String>(), clean("   "))
        assertEquals(emptyList<String>(), clean("..."))
    }

    /**
     * 这条是 Web 版没有的：面板列出的词必须与阅读器收录的词是同一套单位，
     * 否则点「已掌握」会往词库塞进正文里永远不会出现的键。
     */
    @Test
    fun `uses the same word units as the reader, not a unicode segmenter`() {
        // Intl.Segmenter 会把 well-known 拆成 well + known。
        assertEquals(listOf("well-known"), clean("well-known"))
        assertEquals(listOf("didn't"), clean("didn't"))
    }
}
