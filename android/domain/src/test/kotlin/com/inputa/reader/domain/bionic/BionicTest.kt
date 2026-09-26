package com.inputa.reader.domain.bionic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 移植自 Web 版的 src/__tests__/languageDetect.test.ts 里的 `describe('Bionic reading')`。 */
class BionicTest {

    /** LingKuma 的规则：词长的 40%，向上取整。 */
    @Test
    fun `bolds 40 percent of the word, rounded up`() {
        assertEquals(1, Bionic.boldLength("a"))
        assertEquals(2, Bionic.boldLength("the")) // 1.2 → 2，必须走浮点
        assertEquals(2, Bionic.boldLength("hello"))
        assertEquals(3, Bionic.boldLength("reader"))
        assertEquals(4, Bionic.boldLength("ubiquitous"))
    }

    @Test
    fun `splits a word into the bold head and the rest`() {
        assertEquals(BionicParts("rea", "ding"), Bionic.split("reading"))
        assertEquals(BionicParts("ca", "t"), Bionic.split("cat"))
    }

    @Test
    fun `never loses characters`() {
        for (word in listOf("a", "the", "hello", "ubiquitous", "antidisestablishmentarianism")) {
            val parts = Bionic.split(word)
            assertEquals(word, parts.bold + parts.rest)
        }
    }

    @Test
    fun `leaves CJK alone, since a bold first character means nothing there`() {
        assertEquals(BionicParts("", "日本語"), Bionic.split("日本語"))
        assertEquals(BionicParts("", "中文"), Bionic.split("中文"))
        assertTrue(Bionic.isEligible("hello"))
        assertFalse(Bionic.isEligible("日本語"))
    }

    /**
     * "café" 是四个字符（é 占一个 UTF-16 码元），所以 ceil(4 * 0.4) = 2。
     * 若按 UTF-8 字节数会算成 5，多粗一个字符。
     */
    @Test
    fun `counts accented letters as one character each, not as bytes`() {
        assertEquals(4, "café".length)
        assertEquals(BionicParts("ca", "fé"), Bionic.split("café"))
    }

    @Test
    fun `does nothing for an empty word`() {
        assertEquals(BionicParts("", ""), Bionic.split(""))
        assertFalse(Bionic.isEligible(""))
    }
}
