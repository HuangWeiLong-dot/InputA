package com.inputa.reader.ui.reader

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.text.Tokenizer
import com.inputa.reader.ui.theme.ThemePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import android.graphics.Rect as AndroidRect

/**
 * 熟练度底色的**几何**。
 *
 * `WordParagraphTest` 里说几何「只能靠设备上的目视验证」—— 那是当时的判断，现在不成立了：
 * Robolectric 在 NATIVE 图形模式下会加载真字体、走真实排版，`TextLayoutResult` 是能在这里
 * 造出来的。底色的位置错了在设备上只表现为「色块有点飘」，很难一眼归因，所以这些不变量
 * 值得由测试盯着。
 *
 * 这批测试之所以能成立，全靠参照物取自**字体自己**（`Paint.fontMetrics` / `getTextBounds`），
 * 而不是复述实现里的公式：字号 20 在密度 1 下就是 20px，断言里的数字可以直接和字体度量比。
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 默认的 legacy 图形模式不加载真字体，量出来的 ascent/descent 全是 0 ——
// 那样这批测试验的就是一组假数字（写这组测试时先踩过一次）。NATIVE 才是真排版。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WordTintGeometryTest {

    private val tokens = ThemePalettes.Sepia
    private val fontSizeSp = 20

    /** 密度取 1，于是 `fontSizeSp` 与像素 1:1 —— 断言里的数字就是字号本身。 */
    private val fontSizePx = fontSizeSp.toFloat()

    private val tolerance = 1f

    /** 墨迹那条测试单独用一个更紧的容差：盒子对了就是**不差分毫**地盖住字形。 */
    private val inkTolerance = 0.5f

    private lateinit var measurer: TextMeasurer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        measurer = TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
    }

    /** 与正文同一个字体，用来量字形的墨迹 —— 一个**独立于 Compose** 的参照物。 */
    private fun serifPaint() = Paint().apply {
        typeface = Typeface.SERIF
        textSize = fontSizePx
    }

    /**
     * 排一段单行文本，返回排版结果与「词 → 它的底色矩形」。
     *
     * 段落里每个词都标成 3 级，于是每个词恰好一块底色。
     */
    private fun layoutWithTints(
        text: String,
        status: WordStatus = WordStatus.L3,
        lineHeight: Float = 1.8f,
        width: Int = 4000,
    ): Pair<TextLayoutResult, Map<String, Rect>> {
        val style = tokens.readingTextStyle(fontSizeSp, lineHeight)
        val layout = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = width))

        val lexed = Tokenizer.tokenize(text)
        val words = lexed.filter { it.isWord }
        val statuses = words.associate { it.cleanWord to status }
        val spans = buildParagraph(lexed, false).spans
        // 几何覆盖**所有**词元（颜色是绘制时现取的，见 wordBoxesOf），所以这里按颜色
        // 筛出该有底色的那些再对位 —— 与画面上的取舍同一条规则。
        val boxes = wordBoxesOf(layout, spans, measureFontBox(measurer, style, fontSizePx))
            .filter { wordFill(it.span.token, statuses[it.span.token.cleanWord], null, tokens) != null }

        // 键用**原文**而不是 cleanWord：墨迹要按词在正文里的真实大小写去量。
        return layout to boxes.map { it.span.token.raw }.zip(boxes.map { it.rect }).toMap()
    }

    /** 一个折成多行的长词，以及它每一行的底色矩形（按行序）。 */
    private data class WrappedWord(val layout: TextLayoutResult, val rects: List<Rect>)

    /**
     * 排一个**折行**的长词。列宽窄到它必须断成好几行 —— 「色块跟行距无关」这条只有折行
     * 才验得出来（单行段落的行盒会被首末两端的裁剪抵掉）。
     */
    private fun wrappedWord(lineHeight: Float, width: Int = 60): WrappedWord {
        val text = "antidisestablishmentarianism"
        val style = tokens.readingTextStyle(fontSizeSp, lineHeight)
        val layout = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = width))
        assertTrue("这个列宽下这个词必须折行，否则测试没验到东西", layout.lineCount >= 2)

        val lexed = Tokenizer.tokenize(text)
        val boxes = wordBoxesOf(
            layout = layout,
            spans = buildParagraph(lexed, false).spans,
            fontBox = measureFontBox(measurer, style, fontSizePx),
        )

        // 这个词标了 3 级，所以它的每一块都会真的画出来 —— 行数就是块数。
        assertEquals("每一行都该有自己的那一块", layout.lineCount, boxes.size)
        return WrappedWord(layout, boxes.map { it.rect })
    }

    /**
     * 色块必须盖住单词的墨迹 —— 这就是「色块与单词的实际大小对齐」这条要求的字面意思。
     *
     * 参照物是 `Paint.getTextBounds` 量出来的字形外框，与 Compose 的排版实现无关。
     * 老实现（把行盒上下各收掉半个行距）在这条上直接失败：色块整体偏上，下缘离基线还有
     * 一小截，字母的下半部分落在色块外面。
     */
    @Test
    fun `every tint box covers its word's ink`() {
        val (layout, boxes) = layoutWithTints("Alice was beginning to get very worried")
        val paint = serifPaint()
        val baseline = layout.getLineBaseline(0)

        boxes.forEach { (word, rect) ->
            val ink = AndroidRect()
            paint.getTextBounds(word, 0, word.length, ink)

            // 容差紧到 0.5px：盒子等于字体的内容盒时，墨迹按定义就落在里面，不该有富余。
            assertTrue(
                "「$word」的上缘露在色块外面：色块顶 ${rect.top}，字形顶 ${baseline + ink.top}",
                rect.top <= baseline + ink.top + inkTolerance,
            )
            assertTrue(
                "「$word」的下缘露在色块外面：色块底 ${rect.bottom}，字形底 ${baseline + ink.bottom}",
                rect.bottom >= baseline + ink.bottom - inkTolerance,
            )
        }
    }

    /**
     * 色块跟着**基线**走，不跟着行盒走。
     *
     * 要让行距真正起作用，文本必须**折行**：Compose 会把首行的上缘、末行的下缘都修掉，
     * 于是单行段落怎么改行距都不动 —— 这一点是写这组测试时量出来的，也正因为如此，
     * 拿单行文本测「色块不随行距移动」是测不出东西的。折行之后行与行的间距几乎翻倍，
     * 而每块底色到自己那行的基线距离必须一模一样。老实现按行距对半收，行距一改这块就
     * 跟着挪半行，这条会失败。
     */
    @Test
    fun `the tint box keeps its distance to the baseline when the line height changes`() {
        val tight = wrappedWord(lineHeight = 1.2f)
        val loose = wrappedWord(lineHeight = 2.6f)

        val tightSpacing = tight.layout.getLineBaseline(1) - tight.layout.getLineBaseline(0)
        val looseSpacing = loose.layout.getLineBaseline(1) - loose.layout.getLineBaseline(0)
        assertTrue("行距没生效，这条测试就没有意义", looseSpacing > tightSpacing + 1f)

        assertEquals("行距不该改变折行结果", tight.rects.size, loose.rects.size)

        // 参照物：字体自己的 ascent，与实现里的公式无关。
        val metrics = serifPaint().fontMetrics
        val expected = -metrics.ascent

        tight.rects.forEachIndexed { line, rect ->
            val tightGap = tight.layout.getLineBaseline(line) - rect.top
            val looseGap = loose.layout.getLineBaseline(line) - loose.rects[line].top

            assertEquals("色块到基线的距离不该随行距变化", tightGap, looseGap, tolerance)
            assertEquals("色块顶该正好落在基线上方 ascent 处", expected, tightGap, tolerance)
        }
    }

    /**
     * 高度是**字体的内容盒**（ascent + descent），不是字号 —— 这就是 Web 版内联
     * `background` 的高度。同一段文字里长词短词、带不带下伸字母，色块都一样高。
     */
    @Test
    fun `every tint box is exactly the font's content box tall`() {
        val (_, boxes) = layoutWithTints("Alice jogged by")
        val metrics = serifPaint().fontMetrics
        val expected = -metrics.ascent + metrics.descent

        boxes.forEach { (word, rect) ->
            assertTrue(
                "「$word」的色块高度是 ${rect.height}，该是字体的内容盒 $expected",
                abs(rect.height - expected) <= tolerance,
            )
        }
    }

    /**
     * 一个词折行时，**每一行各给一块**，而不是跨行取并集。
     *
     * 并集会在两行之间框出一整片空白区域，把不该着色的地方也涂上。
     */
    @Test
    fun `a word that wraps gets one box per line`() {
        val wrapped = wrappedWord(lineHeight = 1.8f)
        val fontBox = measureFontBox(measurer, tokens.readingTextStyle(fontSizeSp, 1.8f), fontSizePx)

        // 每块各自贴着自己那一行的基线，且高度一致。
        wrapped.rects.forEachIndexed { line, rect ->
            assertEquals(fontBox.heightPx, rect.height, tolerance)
            assertEquals(
                "第 $line 行的色块没有贴住这一行的基线",
                wrapped.layout.getLineBaseline(line) - fontBox.ascentPx,
                rect.top,
                tolerance,
            )
        }
    }

    /**
     * 探针量到的必须**就是字体的度量**，而不是被行距带偏的某个值。
     *
     * 这条是上面所有定位的根：探针一旦带上行距，色块的位置与高度就又变成行距的函数了。
     */
    @Test
    fun `the probe measures the font, not the line height`() {
        val metrics = serifPaint().fontMetrics

        val atTight = measureFontBox(measurer, tokens.readingTextStyle(fontSizeSp, 1.0f), fontSizePx)
        val atLoose = measureFontBox(measurer, tokens.readingTextStyle(fontSizeSp, 3.0f), fontSizePx)

        assertEquals("探针该量出字体自己的 ascent", -metrics.ascent, atTight.ascentPx, tolerance)
        assertEquals("探针该量出字体自己的 descent", metrics.descent, atTight.descentPx, tolerance)
        assertEquals("探针不该受行距影响", atTight, atLoose)
    }

    /** 已掌握的词不着色 —— 顺带确认这批测试用的是真的 `wordFill`，不是自己造的颜色。 */
    @Test
    fun `a mastered word produces no box at all`() {
        val (_, boxes) = layoutWithTints("Alice", status = WordStatus.MASTERED)
        assertTrue("已掌握的词不该有底色", boxes.isEmpty())
    }

    /**
     * 几何与颜色**分开**：`wordBoxesOf` 出的是每个词的方块，一个已掌握的词也有方块，
     * 只是绘制时按状态取不到颜色、不画而已。
     *
     * 这条钉住的是「翻页后熟练度显示延迟」那个 bug 的结构性前提。词汇状态变化**不会**
     * 让文本重新排版，所以 `onTextLayout` 不会再触发；几何里若烘进了颜色，色块就会一直
     * 停在上一次排版时的状态。分开之后，颜色由绘制每帧现取，状态一变就对上 —— 而这条
     * 测试确保没人再把颜色塞回几何里（塞回去的最省事写法就是在 `wordBoxesOf` 里过滤掉
     * 没有颜色的词，那正是这条测试会失败的形态）。
     */
    @Test
    fun `geometry covers every word, tinted or not`() {
        val text = "Alice was beginning"
        val statuses = mapOf("alice" to WordStatus.MASTERED, "was" to WordStatus.L3, "beginning" to WordStatus.MASTERED)
        val style = tokens.readingTextStyle(fontSizeSp, 1.8f)
        val layout = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = 4000))
        val lexed = Tokenizer.tokenize(text)

        val boxes = wordBoxesOf(layout, buildParagraph(lexed, false).spans, measureFontBox(measurer, style, fontSizePx))

        assertEquals("三个词，一个方块都不该少", 3, boxes.size)
        val tinted = boxes.count {
            wordFill(it.span.token, statuses[it.span.token.cleanWord], null, tokens) != null
        }
        assertEquals("其中只有 was 是 3 级、该被画出来", 1, tinted)
    }
}
