package com.inputa.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.inputa.reader.domain.bionic.Bionic
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.text.Tokenizer
import com.inputa.reader.ui.theme.ThemePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 正文渲染。这批测试盯的是**最难肉眼发现**的一类错误：词元在 `AnnotatedString` 里
 * 的字符区间。差一个字符，当前词的描边就会框住隔壁的词 —— 而光看代码看不出来，
 * 在设备上也只表现为「偶尔框错」。
 *
 * 底色的语义同样在这里钉住：**未收录的词按生词着色，但不写词库**。这条是只关于显示的
 * 规则，而它正是翻页收录规则能正常工作的前提（见 WordLevels 的类注释）。
 */
@RunWith(RobolectricTestRunner::class)
class WordParagraphTest {

    private val tokens = ThemePalettes.Sepia

    /**
     * 注意签名里**没有词库状态** —— `buildParagraph` 本来就不依赖它（正文的文本与样式
     * 不随词汇状态变化，底色是自己画的，见 `WordBox`）。这里如实反映那个事实，而不是
     * 继续接两个用不上的参数。
     */
    private fun build(text: String, bionic: Boolean = false): ParagraphContent =
        buildParagraph(Tokenizer.tokenize(text), bionic)

    /**
     * 某个词该涂什么底色。
     *
     * 注意断言的是 `wordFill` 这个**判定**，不是渲染结果 —— 底色由 `wordBoxesOf` 出方块、
     * 绘制时再上色，方块本身在 `WordTintGeometryTest` 里验：Robolectric 在 NATIVE 图形
     * 模式下会走真实排版，几何是能在这里造出来的（这里原先写着「只能靠设备目视」，
     * 那是当时不知道这一点）。判定与几何仍然分开测 —— 这个类是判定，那个类是几何。
     */
    private fun fillOf(
        word: String,
        statuses: Map<String, WordStatus> = emptyMap(),
        active: String? = null,
    ): Color? {
        val token = Tokenizer.tokenize("Alice was beginning.")
            .first { it.isWord && it.cleanWord == word }
        return wordFill(token, statuses[word], active, tokens)
    }

    // ---------------------------------------------------------------- 区间

    @Test
    fun `the paragraph text is the input verbatim`() {
        val text = "Alice didn't like the well-known rabbit-hole, did she?"
        assertEquals(text, build(text).text.text)
    }

    /**
     * 区间必须**无缝隙无重叠**地铺满整段。任何一处错位都会让描边或底色落到错误的
     * 字符上，而那种错误在肉眼看来只是「有点怪」。
     */
    @Test
    fun `spans tile the paragraph with no gaps and no overlaps`() {
        val text = "Hello,  world! It's -- fine."
        val content = build(text)

        assertEquals(0, content.spans.first().start)
        assertEquals(text.length, content.spans.last().end)
        content.spans.zipWithNext().forEach { (a, b) ->
            assertEquals("span '${a.token.raw}' and '${b.token.raw}' are not contiguous", a.end, b.start)
        }
    }

    @Test
    fun `every span covers exactly its own raw text`() {
        val text = "Alice didn't like the well-known rabbit-hole."
        val content = build(text)

        for (span in content.spans) {
            assertEquals(
                "span for '${span.token.raw}' covers the wrong range",
                span.token.raw,
                content.text.text.substring(span.start, span.end),
            )
        }
    }

    @Test
    fun `concatenating the spans reproduces the paragraph`() {
        val text = "Über die Brücke, naïve café — 你好 world."
        val content = build(text)
        assertEquals(text, content.spans.joinToString("") { content.text.text.substring(it.start, it.end) })
    }

    // ---------------------------------------------------------------- 底色

    /**
     * 底色**不再由 `SpanStyle` 承载**，而是算成方块自己画（见 `wordBoxesOf`）。
     *
     * 这条钉住那个决定：如果哪天有人把 `background` 加回 span 样式，底色会被画两遍 ——
     * 一遍整个行盒、一遍收过的矩形 —— 而视觉上只表现为「色块又贴上了」，很难归因。
     * 另外它一旦回退，正文的 `AnnotatedString` 就会随词库状态变化，而那正是「翻页后
     * 熟练度延迟」那个 bug 的成因（状态变化逼出一次重排才刷新颜色）。
     */
    @Test
    fun `the paragraph carries no span backgrounds at all`() {
        val content = build("Alice was beginning.")

        val backgrounds = content.text.spanStyles.mapNotNull { it.item.background.takeIf { c -> c != Color.Unspecified } }
        assertEquals(emptyList<Color>(), backgrounds)
    }

    /**
     * 这条是「只关于显示」那条规则的守门人：未收录的词**看起来**像 5 级生词，
     * 但词库里没有任何记录。点击它才算「用户判断过」。
     */
    @Test
    fun `an uncollected word is painted as the newest level`() {
        assertEquals(tokens.levelBackground(WordStatus.L5), fillOf("beginning"))
    }

    @Test
    fun `a filed word uses its own level`() {
        assertEquals(
            tokens.levelBackground(WordStatus.L2),
            fillOf("beginning", statuses = mapOf("beginning" to WordStatus.L2)),
        )
    }

    @Test
    fun `a mastered word gets no tint at all`() {
        assertNull(fillOf("beginning", statuses = mapOf("beginning" to WordStatus.MASTERED)))
    }

    @Test
    fun `non-word tokens are never tinted`() {
        val punctuation = Tokenizer.tokenize("Alice, was.").first { !it.isWord }
        assertNull(wordFill(punctuation, status = null, activeWord = null, tokens = tokens))
    }

    // ---------------------------------------------------------------- 当前词

    /**
     * **只有「没有底色」的当前词才补一块 accent-soft 填充。**
     *
     * 而「没有底色」实际上只有已掌握一种情况 —— 未收录的词是有底色的（5 级），
     * 所以点开一个没收录过的词时，它保持 5 级底色，只多一圈描边。
     * 这一点容易反直觉，Web 版是靠两个 Tailwind 背景工具类的样式表顺序才区分开的。
     */
    @Test
    fun `the active word gets the accent fill only when it has no tint`() {
        assertEquals(
            tokens.accentSoft,
            fillOf("was", statuses = mapOf("was" to WordStatus.MASTERED), active = "was"),
        )
    }

    /** 有底色的当前词保留自己的熟练度底色 —— 位置由描边提示，不必再加一层填充。 */
    @Test
    fun `the active word with a tint keeps the tint`() {
        assertEquals(
            tokens.levelBackground(WordStatus.L3),
            fillOf("was", statuses = mapOf("was" to WordStatus.L3), active = "was"),
        )
    }

    /**
     * 未收录的当前词保持它的 5 级底色，而不是被换成 accent-soft ——
     * 因为「未收录」在显示上就是一个有色阶的身份（只关于显示的规则）。
     */
    @Test
    fun `an uncollected active word keeps its newest-level tint`() {
        assertEquals(tokens.levelBackground(WordStatus.L5), fillOf("was", active = "was"))
    }

    /** 当前词的标记不该外溢到相邻的词上。 */
    @Test
    fun `neighbours of the active word are unaffected`() {
        val statuses = mapOf("was" to WordStatus.MASTERED, "alice" to WordStatus.L2)

        assertEquals(tokens.accentSoft, fillOf("was", statuses = statuses, active = "was"))
        assertEquals(tokens.levelBackground(WordStatus.L2), fillOf("alice", statuses = statuses, active = "was"))
        assertEquals(tokens.levelBackground(WordStatus.L5), fillOf("beginning", statuses = statuses, active = "was"))
    }

    // ---------------------------------------------------------------- 仿生

    /**
     * 只给词首加粗，其余部分**不带任何样式**。
     *
     * 底色从 span 样式里挪走之后（见 `wordBoxesOf`），span 上就只剩字体属性了 ——
     * 所以「词尾」根本不需要单独套一个空样式，它自然是干净的。
     */
    @Test
    fun `bionic bolds only the head of a word`() {
        val content = build("reading aloud", bionic = true)
        val span = content.spans.first { it.token.cleanWord == "reading" }

        // 注意范围要限定在**这个词**的区间里：仿生对段落里每个词都加粗，
        // 所以不限定的话会把 "aloud" 的那一段也算进来。
        val bold = content.text.spanStyles.filter {
            it.item.fontWeight == FontWeight.Bold && it.start >= span.start && it.end <= span.end
        }
        assertEquals("只有词首该带样式", 1, bold.size)
        assertEquals(span.start, bold[0].start)
        assertEquals(Bionic.boldLength("reading"), bold[0].end - bold[0].start)
        // 加粗只覆盖词的一部分，否则整个词都会粗。
        assertTrue("bold must stop before the end of the word", bold[0].end < span.end)
    }

    @Test
    fun `no word is bolded when bionic is off`() {
        val content = build("reading aloud", bionic = false)
        assertTrue(content.text.spanStyles.none { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `bionic does not change the paragraph text`() {
        val text = "reading aloud is good"
        assertEquals(text, build(text, bionic = true).text.text)
        assertEquals(text, build(text, bionic = false).text.text)
    }

    @Test
    fun `bionic leaves CJK alone`() {
        // 中日韩词元根本不算「词」（isWord 为 false），所以一个加粗区间都不该出现。
        val content = build("你好 world", bionic = true)
        assertTrue(
            "CJK must not be bolded",
            content.text.spanStyles.none { it.item.fontWeight == FontWeight.Bold && it.start < 2 },
        )
    }

    // ---------------------------------------------------------------- 命中测试

    @Test
    fun `spanAt finds the word under an offset`() {
        val content = build("Alice was beginning.")
        val alice = content.spans.first { it.token.cleanWord == "alice" }

        assertEquals(alice, content.spans.spanAt(alice.start))
        assertEquals(alice, content.spans.spanAt(alice.end - 1))
    }

    @Test
    fun `spanAt reports the punctuation run, which the caller must reject`() {
        // 命中测试本身只回答「这个偏移落在哪一段」，判断「是不是词」是调用方的事 ——
        // 这样 pointerInput 里的判断只有一处。
        //
        // 注意非词元是一整段连续的非字母字符（这里是 ", "，逗号带一个空格），
        // 不是单个标点 —— 分词器把两词之间的所有非词字符合成一个词元。
        val content = build("Alice, was")
        val punctuation = content.spans.first { !it.token.isWord && it.token.raw.startsWith(",") }

        val hit = content.spans.spanAt(punctuation.start)
        assertEquals(punctuation, hit)
        assertTrue(hit?.token?.isWord == false)
    }

    @Test
    fun `spanAt returns nothing past the end of the paragraph`() {
        val content = build("Alice.")
        assertNull(content.spans.spanAt(content.text.length + 5))
    }
}
