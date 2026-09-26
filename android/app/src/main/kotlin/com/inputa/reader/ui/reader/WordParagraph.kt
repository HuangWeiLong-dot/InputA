package com.inputa.reader.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputa.reader.domain.bionic.Bionic
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.text.Sentence
import com.inputa.reader.domain.text.Token
import com.inputa.reader.domain.text.Tokenizer
import com.inputa.reader.ui.theme.ThemeTokens

/**
 * 一个词元在 [AnnotatedString] 里占的字符区间。点按命中测试与当前词描边都要用它。
 *
 * 之所以不让渲染组件自己算这些：区间是纯粹的「字符偏移」，而它恰好是最容易
 * 算错又最难一眼看出的部分（差一个字符，描边就会框住隔壁的词）。
 */
data class TokenSpan(val token: Token, val start: Int, val end: Int)

data class ParagraphContent(
    val text: AnnotatedString,
    val spans: List<TokenSpan>,
)

/**
 * 把一个段落渲染成「可点击、带熟练度底色、可仿生加粗」的文本。
 *
 * **做法是一个段落一个 `Text`**，内部用 [AnnotatedString]，配一个 `pointerInput`
 * 做命中测试。否掉的两个替代方案：
 *
 *   - **每个词元一个 composable（`FlowRow`）**：唯一的好处是每词的 `Modifier.background`
 *     好写。代价是放弃平台文本排版引擎（没有断词、没有真正的两端对齐，词间距要手工
 *     处理），而一页约 1300 个词元会变成 1300 个 composable —— 而翻页器还会预组合邻居。
 *   - **每个词一个 `LinkAnnotation.Clickable`**：它给点击但不给独立的**长按**，
 *     `TextLinkStyles` 也无法同时表达底色和描边而不与链接自身的颜色处理打架，
 *     而且会引入第二个「底色真相源」—— 那个真相源还得与翻页规则保持一致。
 *
 * 下面的 `pointerInput` + 偏移→词元映射的 API 面更小，也更贴近 Web 版那个裸
 * `<span onClick>` 模型（渲染与命中是一一对应的）。
 *
 * **底色是自己画的，不走 `SpanStyle(background=)`** —— 那两者填的高度不一样：
 * Compose 的 span 底色填满整个行盒（含 `lineHeight` 带出的行距），于是相邻两行的
 * 色块会贴成一整片；CSS 的内联 `background` 只覆盖 em 盒，行距不参与绘制。这就是
 * 两端观感不同的全部原因。底色与当前词的描边共用同一个方块，见 [wordRect] 与
 * [wordBoxesOf]。
 *
 * @param dimmed 段落聚焦标尺用。第一阶段恒为 false，但接缝先留出来。
 */
@Composable
fun WordParagraph(
    text: String,
    paragraphIndex: Int,
    statuses: Map<String, WordStatus>,
    activeWord: String?,
    bionic: Boolean,
    tokens: ThemeTokens,
    fontSizeSp: Int,
    lineHeight: Float,
    onWordTap: (word: String, sentence: String) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val lexed = remember(text, paragraphIndex) { Tokenizer.tokenize(text, paragraphIndex) }

    // 键里只有词元与仿生开关，**没有词库状态**：正文的文本与样式本来就不随词汇状态变化
    // （底色是自己画的，见 [WordBox]）。写成 `remember(lexed, bionic)` 是让这件事在
    // 类型上就成立，而不是靠「反正结果一样」。
    val content = remember(lexed, bionic) { buildParagraph(lexed, bionic) }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 熟练度底色的**几何**：每个词元在每一行的方块。它只由排版决定，所以在排版时算一次
    // 并缓存 —— 重算要逐字符调 `getBoundingBox`，一页 120 个词就是七百多次调用，
    // 而拖动滚动时 draw 每帧都会跑。
    //
    // **颜色不在这里面。** 颜色是每帧照着当前词库状态现取的，见下面 draw 的说明。
    var wordBoxes by remember { mutableStateOf<List<WordBox>>(emptyList()) }

    val outlineOffset = 3.dp
    val outlineWidth = 2.dp
    val fontSizePx = with(LocalDensity.current) { fontSizeSp.sp.toPx() }

    val measurer = rememberTextMeasurer()
    // 键里刻意没有 lineHeight：结果本来就与它无关（见 [measureFontBox]），
    // 键上它只会让每次调整行距都白量一次。
    val fontBox = remember(measurer, tokens, fontSizeSp) {
        measureFontBox(measurer, tokens.readingTextStyle(fontSizeSp, lineHeight), fontSizePx)
    }

    Text(
        text = content.text,
        onTextLayout = { result ->
            layout = result
            val next = wordBoxesOf(result, content.spans, fontBox)
            // 结构相等时不动状态，避免「排版 → 写状态 → 重组 → 再排版」的空转。
            if (next != wordBoxes) wordBoxes = next
        },
        style = tokens.readingTextStyle(fontSizeSp, lineHeight),
        modifier = modifier
            .fillMaxWidth()
            .then(if (dimmed) Modifier.alpha(0.25f) else Modifier)
            .drawWithContent {
                // 顺序是：底色 → 字形 → 描边。
                // 底色必须先画，否则会盖住文字；描边必须最后画，否则会被底色的边缘压掉。
                //
                // **颜色在这里现取，不在排版时烘进矩形。** 这是修一个真实的延迟：词汇状态
                // 变化**不会**让文本重新排版（`buildParagraph` 不依赖状态），所以
                // `onTextLayout` 不会再触发 —— 把颜色烘在排版那一趟里，色块就会一直停在上一次
                // 排版时的状态，翻页后要等别的原因（提示条出现/消失改变约束）逼出一次重排才
                // 对上。几何随排版走、颜色随状态走，两者各归其位，这类延迟就不存在了。
                wordBoxes.forEach { box ->
                    val color = wordFill(box.span.token, statuses[box.span.token.cleanWord], activeWord, tokens)
                        ?: return@forEach
                    drawRect(
                        color = color,
                        topLeft = Offset(box.rect.left, box.rect.top),
                        size = Size(box.rect.width, box.rect.height),
                    )
                }

                drawContent()

                val result = layout ?: return@drawWithContent
                content.spans
                    .filter { it.token.isWord && it.token.cleanWord == activeWord }
                    .forEach { span ->
                        lineBoxes(result, span).forEach { box ->
                            // 与底色用**同一个**方块 —— 描边和色块必须同心，否则
                            // 有底色的当前词会是「一块底色 + 一个错位的框」，
                            // 比没有描边还难看（见 [wordRect]）。
                            val rect = wordRect(result, box, fontBox)
                            drawRect(
                                color = tokens.accent,
                                topLeft = Offset(
                                    rect.left - outlineOffset.toPx(),
                                    rect.top - outlineOffset.toPx(),
                                ),
                                size = Size(
                                    rect.width + outlineOffset.toPx() * 2,
                                    rect.height + outlineOffset.toPx() * 2,
                                ),
                                style = Stroke(width = outlineWidth.toPx()),
                            )
                        }
                    }
            }
            .pointerInput(lexed) {
                detectTapGestures { position ->
                    val result = layout ?: return@detectTapGestures
                    val offset = result.getOffsetForPosition(position)
                    val span = content.spans.spanAt(offset) ?: return@detectTapGestures
                    if (!span.token.isWord) return@detectTapGestures
                    onWordTap(span.token.cleanWord, Sentence.forToken(lexed, content.spans.indexOf(span)))
                }
            },
    )
}

/**
 * 生成段落文本与词元区间。抽成普通函数（不是 composable）是为了能单测 ——
 * 区间偏移对不对很难靠肉眼 verify。
 *
 * **这里刻意不设任何底色。** 底色由 [wordBoxesOf] 算出方块、在 `drawWithContent`
 * 里按当前词库状态上色，理由见 [WordBox] 的说明。加粗（仿生阅读）仍然走 `SpanStyle` ——
 * 那是字形属性，本来就该由排版引擎处理。
 *
 * 参数里**没有词库状态**：正文的文本与样式本来就不随它变（底色是自己画的）。这不是
 * 巧合而是要求 —— 一旦这里依赖状态，文本就会随状态变化，而「排版一变就重算几何」
 * 的那条路径会顺带把颜色也刷新，于是颜色随状态这一条就变得多余而难以察觉了。
 */
internal fun buildParagraph(lexed: List<Token>, bionic: Boolean): ParagraphContent {
    val spans = ArrayList<TokenSpan>(lexed.size)
    val builder = AnnotatedString.Builder()

    for (token in lexed) {
        val start = builder.length

        if (!token.isWord) {
            builder.append(token.raw)
        } else if (bionic && Bionic.isEligible(token.raw)) {
            // 仿生阅读只加粗词首，所以必须分两次 append：`SpanStyle` 作用于
            // 整段 append 进去的文本，一次 append 会让整个词都变粗。
            val parts = Bionic.split(token.raw)
            if (parts.bold.isNotEmpty()) {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(parts.bold) }
            }
            if (parts.rest.isNotEmpty()) {
                builder.append(parts.rest)
            }
        } else {
            builder.append(token.raw)
        }

        spans += TokenSpan(token, start, builder.length)
    }

    return ParagraphContent(builder.toAnnotatedString(), spans)
}

/**
 * 一个词该涂什么底色；null 表示不涂。
 *
 * `status` 与「该涂什么颜色」是两个不同的问题，这里刻意分开：
 * `status` 回答这个词**是什么**，底色回答它该**长什么样**。未收录（status 为 null）
 * 会被涂成生词，但词库里没有任何记录。
 *
 * 当前词：有底色时保留自己的底色（位置由描边提示），无底色时才补一块 accent-soft。
 * Web 版是因为两个 Tailwind 背景工具类按样式表顺序解析才需要这个区分；Compose 没有
 * 这个歧义，但照搬以保持与 Web 的视觉可比。
 */
internal fun wordFill(
    token: Token,
    status: WordStatus?,
    activeWord: String?,
    tokens: ThemeTokens,
): Color? {
    // 守卫放在这里而不是调用方：标点与空白永远没有底色，这是判定的性质，
    // 不是某个调用点的选择。
    if (!token.isWord) return null

    val tint = tokens.wordBackground(status)
    val isActive = activeWord != null && activeWord == token.cleanWord
    return when {
        isActive && tint == null -> tokens.accentSoft
        tint != null -> tint
        else -> null
    }
}

/** 一个词元在某一行的方块。**只有几何，没有颜色** —— 理由见 [wordBoxesOf]。 */
internal data class WordBox(val span: TokenSpan, val rect: Rect)

/**
 * 算出这一段里**每个词元**在每一行的方块。
 *
 * **为什么不用 `SpanStyle(background=)`。** Compose 的 span 底色填满的是**整个行盒**，
 * 包括 `lineHeight` 带出来的行距 —— 于是相邻两行的色块会贴在一起，整段看起来像一块
 * 连续的色板。而 Web 版没有这个问题：CSS 的内联 `background` 只覆盖字体的内容盒，
 * `line-height: 1.8` 多出来的行距不参与绘制。这就是两端观感不同的全部原因。
 *
 * **纵向也不再「对半收」。** 早先的做法是把行盒上下各收掉半个行距，以为这样就收到了
 * 内容盒 —— 但那只在行距**上下对半分**时才成立。行距实际摊在基线上下的比例由字体自己的
 * ascent/descent 决定（衬线体的 ascent 明显大于 descent），所以对半收出来的盒子整体
 * 偏上：色块压住字的上半截，下缘却离字很远。现在改成**由基线定位**（见 [wordRect]）——
 * 位置的依据从「行盒的几何中心」换成「基线 + 字体度量」，与行距设置彻底无关。
 *
 * **这里刻意不含颜色。** 几何只由排版决定（排版时算一次并缓存，拖动滚动时 `draw`
 * 每帧都跑，而这里每个词要对每个字符调一次 `getBoundingBox`，不能放进每帧的路径），
 * 而颜色只由词库状态决定。两者变化的原因不同，绑在一起就会出问题：词汇状态变化
 * **不会**触发重新排版，于是只在排版那一趟里取色的实现会一直停在旧状态上 —— 翻页后
 * 色块要等别的原因逼出一次重排才追上来。颜色由 draw 每帧现取，见 [WordParagraph]。
 *
 * 也因此这里返回的是**所有**词元的方块，不只是当前有底色的那些：一个已掌握的词
 * 被点开时会补一块 accent-soft（见 [wordFill]），而且有底色的词随时可能变成没底色。
 */
internal fun wordBoxesOf(
    layout: TextLayoutResult,
    spans: List<TokenSpan>,
    /** 字体自身的内容盒，由调用方量出来后传进来（见 [measureFontBox]）。 */
    fontBox: FontBox,
): List<WordBox> {
    val result = ArrayList<WordBox>(spans.size)

    for (span in spans) {
        // 标点与空白永远没有方块。这是判定的性质，不是某个调用点的选择。
        if (!span.token.isWord) continue
        for (box in lineBoxes(layout, span)) {
            result += WordBox(span, wordRect(layout, box, fontBox))
        }
    }

    return result
}

/** 一个词元在**某一行**上占的横向区间。纵向由 [wordRect] 按这一行的基线补出来。 */
internal data class LineBox(val line: Int, val left: Float, val right: Float)

/**
 * 一个词元占据的每一行。
 *
 * 一个词**可能跨行**，所以要按行拆开：`getBoundingBox` 返回的是单个字符的盒，直接对
 * 首尾字符取并集会在跨行时框住整个方块区域 —— 两行之间那片空白也会被涂上。
 *
 * 不用 `getPathForRange`：它更简洁，但 Path 没法向外生长，而当前词的描边要向外扩 3dp
 * （对应 Web 的 `outline-offset: 3px`），所以还是得要矩形。
 */
internal fun lineBoxes(layout: TextLayoutResult, span: TokenSpan): List<LineBox> {
    if (span.start >= span.end) return emptyList()

    val boxes = ArrayList<LineBox>(1)
    var line = -1
    var left = 0f
    var right = 0f

    for (offset in span.start until span.end) {
        val box = layout.getBoundingBox(offset)
        // 换行处的零尺寸盒会带一个假的行号，跳过。
        if (box.width == 0f && box.height == 0f) continue

        val offsetLine = layout.getLineForOffset(offset)
        if (offsetLine != line) {
            if (line >= 0) boxes += LineBox(line, left, right)
            line = offsetLine
            left = box.left
            right = box.right
        } else {
            left = minOf(left, box.left)
            right = maxOf(right, box.right)
        }
    }
    if (line >= 0) boxes += LineBox(line, left, right)

    return boxes
}

/**
 * 底色与描边共用的方块：**横向**取词元自己的字符盒（左右不收 —— 词与词之间的空格本来
 * 就不着色，间隔已经存在），**纵向**由这一行的基线定，上下各取字体的 ascent / descent。
 *
 * 以基线为锚点而不是以行盒为中心，是这块色块能贴住单词的全部原因：基线是排版引擎对
 * 齐字形的那条线，字体的 ascent/descent 是字形相对它的真实伸展。行盒的中心只是行距的
 * 几何中心，行距一改它就跟着跑。
 *
 * **盒子大小就是 Web 版那个。** CSS 里行内元素的 `background` 覆盖的是「内容区」，
 * 浏览器拿字体自己的 ascent + descent 当它的高度 —— 于是 `outline-offset` 描出来的框、
 * 底色、字形三者严丝合缝，行距怎么调都不散。这里照搬同一条规则，两端才是同一个观感。
 * （字体文件不同、渲染后端也不同，跨平台的像素不会真的逐点相等；相等的是这条规则。）
 */
internal fun wordRect(layout: TextLayoutResult, box: LineBox, fontBox: FontBox): Rect {
    val baseline = layout.getLineBaseline(box.line)
    return Rect(box.left, baseline - fontBox.ascentPx, box.right, baseline + fontBox.descentPx)
}

/** 字体自身的内容盒：相对基线，上方 [ascentPx]、下方 [descentPx]。像素，不是比例。 */
internal data class FontBox(val ascentPx: Float, val descentPx: Float) {
    val heightPx: Float get() = ascentPx + descentPx
}

/** 探针文本。行盒高度由字体度量决定、与这几个字母无关；带上上下伸展的字母只是为了
 *  万一将来改成按字形量取时仍然是对的。 */
private const val FONT_PROBE_TEXT = "Hg"

/**
 * 量出**字体自身**的内容盒。底色的纵向定位与高度都靠它。
 *
 * 探针**刻意不带 lineHeight**：带上之后行盒里就混进了行距，而这份行距摊在基线上下的
 * 比例取决于排版引擎的策略（按 ascent/descent 等比摊、还是上下各一半），从带行距的
 * 盒子里反推不出字体自己的度量。去掉 lineHeight，量到的就是字体的 ascent / descent ——
 * 与行距设置无关，所以调整行距时色块不会跟着跑。
 *
 * [fontSizePx] 只用在**兜底**上：排版结果异常（量出来的盒子高度为 0）时退回「一个字号、
 * 按 0.8 / 0.2 分」的盒子 —— 宁可位置略偏，也不要画出零高度的色块。
 *
 * 单独抽成函数是为了能被测试直接量：几何那一层只有真实排版才能算，
 * 而 Robolectric 里这件事做得到（见 WordTintGeometryTest）。
 */
internal fun measureFontBox(measurer: TextMeasurer, style: TextStyle, fontSizePx: Float): FontBox {
    val probe = measurer.measure(
        AnnotatedString(FONT_PROBE_TEXT),
        style.copy(lineHeight = TextUnit.Unspecified),
    )
    val top = probe.getLineTop(0)
    val baseline = probe.getLineBaseline(0)
    val descent = probe.getLineBottom(0) - baseline
    val ascent = baseline - top

    return if (ascent + descent > 0f) {
        FontBox(ascent, descent)
    } else {
        FontBox(fontSizePx * FALLBACK_ASCENT_SHARE, fontSizePx * (1f - FALLBACK_ASCENT_SHARE))
    }
}

/** 兜底时 ascent 占一个字号的比例。0.8 是常见衬线体 ascent/(ascent+descent) 附近的值。 */
private const val FALLBACK_ASCENT_SHARE = 0.8f

/** 命中测试：这个字符偏移落在哪个词元里。 */
internal fun List<TokenSpan>.spanAt(offset: Int): TokenSpan? =
    firstOrNull { offset >= it.start && offset < it.end }

