package com.inputa.reader.domain.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 移植自 Web 版的 src/__tests__/languageDetect.test.ts（语言检测部分），逐条对应。
 * 该文件里另含一个仿生阅读套件，在 BionicTest 里。
 */
class LanguageDetectTest {

    private fun lang(text: String): String = LanguageDetect.detect(text).language

    @Test
    fun `tells Japanese, Chinese and Korean apart`() {
        assertEquals("ja", lang("これは日本語の文章です。"))
        assertEquals("zh", lang("这是一段中文文本。"))
        assertEquals("ko", lang("이것은 한국어 문장입니다."))
    }

    /**
     * 日文正文必然带假名，所以只有汉字时才算中文。先查假名，是日文句子不落进中文桶的原因。
     */
    @Test
    fun `reads mixed kanji-and-kana text as Japanese, not Chinese`() {
        assertEquals("ja", lang("彼は本を読んでいる。"))
    }

    @Test
    fun `is confident about the scripts it recognises`() {
        assertTrue(LanguageDetect.detect("これは日本語です").confidence > 0.9)
    }

    @Test
    fun `recognises Cyrillic, Greek, Arabic and Thai`() {
        assertEquals("ru", lang("Привет, как дела?"))
        assertEquals("el", lang("Καλημέρα, τι κάνεις;"))
        assertEquals("ar", lang("مرحبا كيف حالك"))
        assertEquals("th", lang("สวัสดีครับ"))
    }

    @Test
    fun `separates Latin-script languages by their function words`() {
        assertEquals(
            "en",
            lang("The rabbit was not in the garden, and it was his hat that she took from the table."),
        )
        assertEquals(
            "de",
            lang("Der Mann ist nicht mit dem Hund in das Haus gegangen, und ich habe auch nicht die Frau gesehen."),
        )
        assertEquals(
            "fr",
            lang("Le chat est dans la maison et il ne pas vous voir, mais nous sommes sur la table avec une lampe."),
        )
        assertEquals(
            "es",
            lang("El libro está en la mesa y no se puede ver, pero los niños con su madre para una tarde."),
        )
    }

    @Test
    fun `reports low confidence for a short or ambiguous sample`() {
        // 一句话不足以给一本书贴标签。
        assertTrue(LanguageDetect.detect("Hello there.").confidence < 0.9)
        assertEquals(0.0, LanguageDetect.detect("").confidence, 0.0)
    }

    @Test
    fun `always answers a usable language, even for text it cannot place`() {
        assertEquals("en", lang("xyzzy plugh"))
        assertEquals("en", lang("   "))
        assertEquals("en", lang("1234 5678"))
    }

    /** NBSP 之类的 JS 空白也要被 trim 掉，否则全空白的输入会走错分支。 */
    @Test
    fun `treats JS-only whitespace as blank`() {
        assertEquals("en", lang(0x00A0.toChar().toString()))
        assertEquals(0.0, LanguageDetect.detect(0x3000.toChar().toString()).confidence, 0.0)
    }
}
