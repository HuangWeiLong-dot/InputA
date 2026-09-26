package com.inputa.reader.domain.color

import kotlin.math.pow

/**
 * WCAG 2.1 的相对亮度与对比度。纯数学，所以能在纯 JVM 上测。
 *
 * 存在的理由很具体：5 级熟练度色阶是这样推出来的 ——
 * `color-mix(in oklab, var(--level-ink) N%, var(--bg-main))`，N = 10/22/34/46/58。
 * Web 版在 CSS 注释里断言「暗色主题下 58% 是 5.06:1，62% 就跌破 4.5:1」，
 * **但没有任何东西强制它** —— 改一个百分比、换一个主题墨色，都可能悄悄让正文
 * 读不清，而那是最不该出问题的地方。
 *
 * 这里把那条注释变成可执行的检查。
 */
object Contrast {

    /** WCAG AA 对正文的要求。 */
    const val AA_NORMAL_TEXT = 4.5

    /** WCAG AA 对大号文字（约 18.66px 粗体或 24px）的要求。 */
    const val AA_LARGE_TEXT = 3.0

    /**
     * 相对亮度。输入是 sRGB 的 0-255 分量。
     *
     * 注意那条分段函数：sRGB 不是线性的，低值段近似除以 12.92，高值段是 2.4 次幂。
     * 直接拿 0-255 求平均会得出错误结论（这是最常见的「对比度检查」错误）。
     */
    fun relativeLuminance(red: Int, green: Int, blue: Int): Double {
        val r = channelToLinear(red)
        val g = channelToLinear(green)
        val b = channelToLinear(blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** 两个颜色的对比度，范围 1.0（相同）到 21.0（黑与白）。参数顺序无关。 */
    fun ratio(
        foreground: Int,
        background: Int,
    ): Double {
        val fg = relativeLuminanceOf(foreground)
        val bg = relativeLuminanceOf(background)
        val lighter = maxOf(fg, bg)
        val darker = minOf(fg, bg)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 从打包成 `0xRRGGBB` 的整数算相对亮度。 */
    fun relativeLuminanceOf(rgb: Int): Double = relativeLuminance(
        red = (rgb shr 16) and 0xFF,
        green = (rgb shr 8) and 0xFF,
        blue = rgb and 0xFF,
    )

    /** 这个对比度是否达到 AA 正文要求。 */
    fun meetsAaNormalText(rgbForeground: Int, rgbBackground: Int): Boolean =
        ratio(rgbForeground, rgbBackground) >= AA_NORMAL_TEXT

    private fun channelToLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
