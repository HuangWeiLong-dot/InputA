package com.inputa.reader.domain.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 对比度数学。
 *
 * 这批断言不只是在验公式 —— 它们先钉住几个**已知的参考值**，这样公式一旦写错
 * （最常见的错误是跳过 sRGB 的伽马分段，直接拿 0-255 求平均），下面的色阶检查
 * 就不会给出一个看起来合理的错误结论。
 */
class ContrastTest {

    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21.0, Contrast.ratio(0x000000, 0xFFFFFF), 0.01)
    }

    @Test
    fun `a colour against itself is the minimum ratio`() {
        assertEquals(1.0, Contrast.ratio(0x123456, 0x123456), 0.001)
    }

    @Test
    fun `ratio does not depend on argument order`() {
        assertEquals(
            Contrast.ratio(0x101012, 0xF2F2F4),
            Contrast.ratio(0xF2F2F4, 0x101012),
            0.0001,
        )
    }

    /**
     * 这条能抓住「跳过伽马校正」的实现：中灰对白在实际 sRGB 感知下是 3.95:1，
     * 而按线性分量平均会算出约 5.3:1 —— 一个会让人误以为达标的错值。
     */
    @Test
    fun `mid grey on white matches the published value`() {
        // #767676 on #FFFFFF 是 WCAG 文档里常引的「恰好达到 4.5:1 的最浅灰」。
        assertEquals(4.54, Contrast.ratio(0x767676, 0xFFFFFF), 0.02)
        // #949494 对白约 3.03:1，达不到正文要求。
        assertFalse(Contrast.meetsAaNormalText(0x949494, 0xFFFFFF))
    }

    @Test
    fun `relative luminance endpoints`() {
        assertEquals(0.0, Contrast.relativeLuminance(0, 0, 0), 0.0001)
        assertEquals(1.0, Contrast.relativeLuminance(255, 255, 255), 0.0001)
    }

    @Test
    fun `the packed helper agrees with the explicit one`() {
        assertEquals(
            Contrast.relativeLuminance(0x2F, 0x24, 0x12),
            Contrast.relativeLuminanceOf(0x2F2412),
            0.0001,
        )
    }

    /**
     * 传输函数是**分段**的，不是一条纯幂曲线。
     *
     * 这条盯住最常见的实现错误：整段都用 `((c+0.055)/1.055)^2.4`。那样算出来的
     * 相对亮度在暗端会偏小，于是暗色主题的对比度被高估 —— 恰好是本应用最吃紧的地方。
     */
    @Test
    fun `the transfer function is piecewise, not a single power curve`() {
        // 黑必须是精确的 0；纯幂函数在这里给出约 0.0003。
        assertEquals(0.0, Contrast.relativeLuminance(0, 0, 0), 1e-9)

        // 低值段走线性分支：sRGB 10 → 10/255/12.92。
        assertEquals(10 / 255.0 / 12.92, Contrast.relativeLuminance(10, 10, 10), 1e-9)

        // 一个公开的参考值把整条曲线钉住：sRGB 128 的相对亮度是 0.2159。
        // （三个通道相等，而权重之和为 1，所以中灰的亮度就是这个数。）
        assertEquals(0.2159, Contrast.relativeLuminanceOf(0x808080), 0.0005)

        // 两个分支在阈值处必须接得上，否则「分段」就成了一个可见的台阶。
        val threshold = 0.03928
        val linear = threshold / 12.92
        val power = ((threshold + 0.055) / 1.055).pow(2.4)
        assertEquals(linear, power, 0.0001)
    }
}
