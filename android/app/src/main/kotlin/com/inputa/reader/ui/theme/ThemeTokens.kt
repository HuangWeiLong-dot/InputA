package com.inputa.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.inputa.reader.domain.model.AppTheme
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.vocab.WordLevels

/**
 * 一套主题的全部颜色，逐字段对应 Web 版 `src/index.css` 里那 18 个 CSS 自定义属性。
 *
 * 三套主题的取值直接照抄 index.css，不做「优化」—— 它们已经按对比度调过
 * （尤其 `levelInk`），改一个数就可能让正文在某个主题下读不清。
 */
data class ThemeTokens(
    val theme: AppTheme,
    val bgMain: Color,
    val bgSurface: Color,
    val bgSubtle: Color,
    val bgHover: Color,
    val textStrong: Color,
    val textMain: Color,
    val textMuted: Color,
    val borderColor: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    val highlightBg: Color,
    val highlightText: Color,
    val highlightBorder: Color,
    val success: Color,
    val danger: Color,
    /**
     * 色阶用的墨色。**与 `highlight*` 是两套语义，别把它们绑在一起** ——
     * index.css 的注释说明它们曾经相等、后来分开：这里是「熟练度」，
     * 那边是「告警面 / 生词按钮」。
     */
    val levelInk: Color,
) {

    /** 按下态的底色，对应 CSS 的 `color-mix(in oklab, var(--text-strong) 18%, var(--bg-main))`。 */
    val bgActive: Color = lerp(bgMain, textStrong, 0.18f)

    /**
     * 第 N 级熟练度的正文底色。
     *
     * 对应 CSS 的 `color-mix(in oklab, var(--level-ink) N%, var(--bg-main))`，
     * N = 10 / 22 / 34 / 46 / 58。
     *
     * 值由 [levelBackgrounds] 在**首次使用时算一次**并留住，不是每次调用现算 ——
     * 正文底部那条路径是**每帧每词**调一次（一个词一个底色方块），而每次 `lerp` 都要
     * 做三趟 Oklab 转换。
     *
     * 不固化成手抄的颜色常量：那样会有一份可能与 `lerp` 漂移的第二份真相，而这里的
     * 真相始终只有 `lerp` 那一份。对比度那条不变量由 `LevelContrastTest` 直接对同一批
     * 算出来的颜色验，见 `:domain` 的 Contrast。
     */
    fun levelBackground(level: WordStatus): Color = levelBackgrounds.getValue(level)

    /**
     * 六级底色（五级熟练度 + `MASTERED` 就是页面底色）。
     *
     * 写成 `by lazy` 属性而不是构造参数：`ThemeTokens` 是 data class，把缓存表塞进构造
     * 就会牵进 `equals`/`copy` 的语义。`ThemePalettes` 那三套是单例，这张表每套只建一次。
     *
     * `when` 保持穷尽 —— 将来给 [WordStatus] 加一个状态，这里会编译不过，而不是悄悄
     * 让 `getValue` 在运行时抛异常。
     */
    private val levelBackgrounds: Map<WordStatus, Color> by lazy {
        WordStatus.entries.associateWith { level ->
            when (level) {
                WordStatus.L1 -> lerp(bgMain, levelInk, 0.10f)
                WordStatus.L2 -> lerp(bgMain, levelInk, 0.22f)
                WordStatus.L3 -> lerp(bgMain, levelInk, 0.34f)
                WordStatus.L4 -> lerp(bgMain, levelInk, 0.46f)
                WordStatus.L5 -> lerp(bgMain, levelInk, 0.58f)
                WordStatus.MASTERED -> bgMain
            }
        }
    }

    /**
     * 正文里一个词的底色：未收录按 5 级着色，已掌握不着色。
     *
     * 直接委托给领域层的 [WordLevels.highlightLevel] —— 「着色」这条规则只有一份实现，
     * 而且它在领域层是纯函数，连写词库的手段都没有。
     */
    fun wordBackground(status: WordStatus?): Color? =
        WordLevels.highlightLevel(status)?.let(::levelBackground)

    /**
     * 正文的排版。`lineHeight` 用 `em` —— CSS 的无单位 line-height 有精确对应物，
     * 而 `sp` 会随字号变化而失准。
     */
    fun readingTextStyle(fontSizeSp: Int, lineHeight: Float): TextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeight.em,
        color = textMain,
    )
}

/** 三套主题。取值逐字照抄 index.css 的 `[data-theme=...]` 块。 */
object ThemePalettes {

    val Light = ThemeTokens(
        theme = AppTheme.Light,
        bgMain = Color(0xFFFFFFFF),
        bgSurface = Color(0xFFFFFFFF),
        bgSubtle = Color(0xFFF4F4F5),
        bgHover = Color(0xFFE7E7EA),
        textStrong = Color(0xFF09090B),
        textMain = Color(0xFF18181B),
        textMuted = Color(0xFF3F3F46),
        borderColor = Color(0xFFD4D4D8),
        borderStrong = Color(0xFF71717A),
        accent = Color(0xFF1D4ED8),
        accentSoft = Color(0xFFEFF6FF),
        accentBorder = Color(0xFF93B4FD),
        highlightBg = Color(0xFFFDE68A),
        highlightText = Color(0xFF422006),
        highlightBorder = Color(0xFFB45309),
        success = Color(0xFF15803D),
        danger = Color(0xFFB91C1C),
        levelInk = Color(0xFFA68A00),
    )

    val Sepia = ThemeTokens(
        theme = AppTheme.Sepia,
        bgMain = Color(0xFFF8EED9),
        bgSurface = Color(0xFFFDF8EC),
        bgSubtle = Color(0xFFEFE2C6),
        bgHover = Color(0xFFE5D3AE),
        textStrong = Color(0xFF241A09),
        textMain = Color(0xFF2F2412),
        textMuted = Color(0xFF4D3C1F),
        borderColor = Color(0xFFCBB288),
        borderStrong = Color(0xFF7D6A48),
        accent = Color(0xFF8A4B08),
        accentSoft = Color(0xFFF6E6C8),
        accentBorder = Color(0xFFC19A55),
        highlightBg = Color(0xFFF6C445),
        highlightText = Color(0xFF3B2500),
        highlightBorder = Color(0xFF9A5B06),
        success = Color(0xFF1C6B3B),
        danger = Color(0xFFA11F1F),
        levelInk = Color(0xFF8F7500),
    )

    val Dark = ThemeTokens(
        theme = AppTheme.Dark,
        bgMain = Color(0xFF101012),
        bgSurface = Color(0xFF17171B),
        bgSubtle = Color(0xFF1E1E23),
        bgHover = Color(0xFF2C2C34),
        textStrong = Color(0xFFFFFFFF),
        textMain = Color(0xFFF2F2F4),
        textMuted = Color(0xFFBCBCC7),
        borderColor = Color(0xFF3A3A44),
        borderStrong = Color(0xFF6F6F7D),
        accent = Color(0xFF8FB4FF),
        accentSoft = Color(0xFF17223C),
        accentBorder = Color(0xFF3D5A99),
        highlightBg = Color(0xFF8A5600),
        highlightText = Color(0xFFFFF4D6),
        highlightBorder = Color(0xFFE0A92A),
        success = Color(0xFF6EE7A0),
        danger = Color(0xFFFF8F8F),
        levelInk = Color(0xFFD6AC00),
    )

    fun of(theme: AppTheme): ThemeTokens = when (theme) {
        AppTheme.Light -> Light
        AppTheme.Sepia -> Sepia
        AppTheme.Dark -> Dark
    }

    /** 供测试遍历 —— 加主题时这里会跟着报错，不会漏。 */
    val all: List<ThemeTokens> = listOf(Light, Sepia, Dark)
}
