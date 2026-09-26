package com.inputa.reader.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 移植自 Web 版的 src/__tests__/reader.test.ts（Tokenizer 部分），逐条对应。 */
class TokenizerTest {

    private fun wordsIn(text: String): List<String> =
        Tokenizer.tokenize(text).filter { it.isWord }.map { it.cleanWord }

    @Test
    fun `splits words, punctuation and contractions`() {
        val words = wordsIn("Alice didn't like the well-known rabbit-hole, did she?")

        assertTrue(words.contains("alice"))
        assertTrue(words.contains("didn't"))
        assertTrue(words.contains("like"))
        assertTrue(words.contains("the"))
        assertTrue(words.contains("well-known"))
        assertTrue(words.contains("rabbit-hole"))
        assertTrue(words.contains("did"))
        assertTrue(words.contains("she"))
    }

    @Test
    fun `extracts unique lowercase words from tokens`() {
        val unique = Tokenizer.extractWords(Tokenizer.tokenize("The rabbit and the other rabbit were running."))

        assertTrue(unique.contains("the"))
        assertTrue(unique.contains("rabbit"))
        assertTrue(unique.contains("and"))
        assertTrue(unique.contains("other"))
        assertTrue(unique.contains("were"))
        assertTrue(unique.contains("running"))
        // 大小写无关且去重。
        assertEquals(1, unique.count { it == "the" })
        assertEquals(1, unique.count { it == "rabbit" })
    }

    /**
     * 词元正则曾经是 `[a-zA-Z0-9]`，会把 café 切成 caf + é，于是法文书里几乎没有
     * 能整词点击的词，点击还会把碎片当成生词收录。
     */
    @Test
    fun `keeps accented Latin words whole`() {
        assertEquals(listOf("le", "café", "était", "naïf"), wordsIn("Le café était naïf."))
        assertEquals(listOf("über", "die", "brücke"), wordsIn("Über die Brücke."))
    }

    @Test
    fun `keeps other spaced scripts whole too`() {
        assertEquals(listOf("привет", "мир"), wordsIn("Привет мир"))
        assertEquals(listOf("καλημέρα", "κόσμε"), wordsIn("Καλημέρα κόσμε"))
    }

    /**
     * 中日韩没有空格，没有真正的分词器就不存在诚实的「词」边界 —— 而翻页规则会把
     * 整句当成一个词收进词库。让它们不可点击是有意选择的取舍。
     */
    @Test
    fun `does not treat CJK runs as clickable words`() {
        assertEquals(listOf("hello", "world"), wordsIn("hello 你好 world"))
        assertEquals(emptyList<String>(), wordsIn("日本語のテキスト"))
        assertEquals(emptyList<String>(), wordsIn("한국어 텍스트"))
    }

    /**
     * 把所有 raw 按顺序拼回去必须精确还原原文 —— Compose 侧按词元渲染正文，
     * 少了或多了任何字符都会让正文与书对不上。
     */
    @Test
    fun `tokens reconstruct the original text exactly`() {
        val text = "Alice didn't like the well-known rabbit-hole, did she?  She fell  down."
        assertEquals(text, Tokenizer.tokenize(text).joinToString("") { it.raw })
    }

    @Test
    fun `pure numbers are not clickable words`() {
        // 42 没有字母，不算词；但 COVID19 这类含字母的算。
        assertEquals(emptyList<String>(), wordsIn("42"))
        assertEquals(listOf("covid19"), wordsIn("COVID19"))
    }

    @Test
    fun `empty input yields no tokens`() {
        assertEquals(emptyList<Token>(), Tokenizer.tokenize(""))
    }
}
