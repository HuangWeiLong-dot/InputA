package com.inputa.reader.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 这批测试是 Web 版没有的 —— 因为 JS 端不存在这个分歧。
 * 它钉住的是「Java 的空白集合比 JS 少」这个平台差异，见 TextNormalize 的类注释。
 *
 * 空白字符一律用 `cp()` 从码点构造，不写字面量：这些字符在编辑器里不可见，
 * 一旦被误「清理」成普通空格，测试会静默变成空转 —— 比失败更糟。
 * 顺带的好处是码点写在调用处，本身就是文档。
 */
class TextNormalizeTest {

    /** 由一个码点构造单字符字符串，避免在源码里放不可见字符。 */
    private fun cp(codePoint: Int): String = codePoint.toChar().toString()

    @Test
    fun `collapses JS-only whitespace to plain spaces`() {
        assertEquals("a b", TextNormalize.normalizeContent("a" + cp(0x00A0) + "b")) // NBSP
        assertEquals("a b", TextNormalize.normalizeContent("a" + cp(0x2028) + "b")) // 行分隔符
        assertEquals("a b", TextNormalize.normalizeContent("a" + cp(0x2029) + "b")) // 段分隔符
        assertEquals("a b", TextNormalize.normalizeContent("a" + cp(0x3000) + "b")) // 全角空格
        assertEquals("a b", TextNormalize.normalizeContent("a" + cp(0xFEFF) + "b")) // BOM
        assertEquals("a b", TextNormalize.normalizeContent("a" + cp(0x2003) + "b")) // em space
        assertEquals("a b", TextNormalize.normalizeContent("a" + cp(0x1680) + "b")) // ogham space
        assertEquals("a b", TextNormalize.normalizeContent("a" + cp(0x202F) + "b")) // narrow NBSP
    }

    @Test
    fun `leaves ordinary whitespace alone`() {
        assertEquals("a b", TextNormalize.normalizeContent("a b"))
        assertEquals("a\tb", TextNormalize.normalizeContent("a\tb"))
        assertEquals("a\nb", TextNormalize.normalizeContent("a\nb"))
    }

    @Test
    fun `normalizes CRLF and lone CR to LF`() {
        assertEquals("a\nb", TextNormalize.normalizeContent("a\r\nb"))
        assertEquals("a\nb", TextNormalize.normalizeContent("a\rb"))
    }

    @Test
    fun `trim matches JavaScript trim`() {
        // Kotlin 的 trim() 不会去掉 NBSP/BOM，JS 的会。
        assertEquals("x", TextNormalize.trim(cp(0x00A0) + "x" + cp(0xFEFF)))
        assertEquals("", TextNormalize.trim(cp(0x00A0)))
        assertEquals("", TextNormalize.trim(cp(0x3000)))
        assertEquals("", TextNormalize.trim("   "))
    }

    @Test
    fun `cleanToken strips edge apostrophes and hyphens, then lowercases`() {
        assertEquals("twas", TextNormalize.cleanToken("'twas"))
        assertEquals("well-known", TextNormalize.cleanToken("-well-known-"))
        assertEquals("alice", TextNormalize.cleanToken("ALICE"))
        assertEquals("dont", TextNormalize.cleanToken("dont" + cp(0x2019))) // 右单引号
    }

    /**
     * 只去**右**单引号（U+2019）与直引号（U+0027），不去左单引号（U+2018）——
     * 逐字沿用了 Web 版的 `/^['’-]+|['’-]+$/g`。
     *
     * 实践中够不到：U+2018 不在 `\p{L}\p{N}` 里，分词器根本不会把它并进词元。
     * 把行为写下来，是为了让将来想「顺手补齐」的人先看到这里。
     */
    @Test
    fun `only strips the right single quote, not the left one`() {
        assertEquals(cp(0x2018) + "dont", TextNormalize.cleanToken(cp(0x2018) + "dont" + cp(0x2019)))
    }

    @Test
    fun `stripEdgePunctuation keeps the original casing`() {
        assertEquals("Alice", TextNormalize.stripEdgePunctuation("'Alice'"))
    }

    /**
     * 土耳其语区域设置下 `"I".toLowerCase()` 得到 `"ı"`，会静默毁掉所有词库键。
     * `lowercase()` 不受默认 Locale 影响。这条测试跑在默认 Locale 下，
     * 所以真正的保护来自「全仓库只用 lowercase()」这个约定本身 —— 这里把行为记下来。
     */
    @Test
    fun `lowercasing is locale independent`() {
        assertEquals("i", TextNormalize.cleanToken("I"))
        assertEquals("title", TextNormalize.cleanToken("TITLE"))
    }

    @Test
    fun `containsLetter uses unicode classes`() {
        assertEquals(true, TextNormalize.containsLetter("café"))
        assertEquals(true, TextNormalize.containsLetter("Привет"))
        assertEquals(false, TextNormalize.containsLetter("42"))
        assertEquals(false, TextNormalize.containsLetter("---"))
    }
}
