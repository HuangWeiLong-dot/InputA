package com.inputa.reader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 非颜色标度的结构不变量。
 *
 * 这些断言大多看起来「测试了自己写的常量」—— 确实如此，它们的价值不在证明当前值对，
 * 而在**把决定钉住**：改动 [Radius.card] 或 [Elevation.card] 是设计决定，不是随手整理，
 * 有了这些测试，改动就得先跟一条断言吵架。用户明确选过「圆角 14dp + 极淡阴影」，
 * 那就是这里钉的两个数。
 *
 * [TypeScale] 那条额外的含义：改造把散在各处的 7 个字号收敛成角色名，**取值必须原样**。
 * 所以断言每个档位都落在改造前真实出现过的字号集合里 —— 一旦有人顺手把 body 从 12 调到
 * 13，全部文案的换行位置都会变，而那是最难回看出来的改动。
 */
class DesignTokenTest {

    /** 改造前源码里真实出现过的字号，一个不多一个不少。 */
    private val preOverhaulSizes = setOf(11, 12, 13, 14, 15, 18, 28)

    @Test
    fun `the spacing scale ascends`() {
        val values = listOf(
            Space.xs, Space.sm, Space.md, Space.lg,
            Space.xl, Space.xxl, Space.xxxl,
        ).map { it.value }
        assertEquals("间距档位不许并列或倒退", values.sorted(), values)
        assertEquals("档位不许重复", values.size, values.toSet().size)
    }

    @Test
    fun `radius ascends and the card value is the one the user chose`() {
        assertTrue(Radius.chip < Radius.control)
        assertTrue(Radius.control < Radius.card)
        assertEquals(14.dp, Radius.card)
    }

    /** 阴影只有一档，而且就是那一档。理由见 [Elevation]：夜间看不见是预期内的。 */
    @Test
    fun `there is exactly one elevation level`() {
        assertEquals(2.dp, Elevation.card)
        assertEquals(0.dp, Elevation.none)
    }

    @Test
    fun `every type scale step is a size that already existed`() {
        val steps = listOf(
            TypeScale.label, TypeScale.body, TypeScale.bodyLg,
            TypeScale.bodyStrong, TypeScale.title, TypeScale.panel, TypeScale.headword,
        )
        val sizes = steps.map { it.value.toInt() }
        assertEquals("字号档位不许并列或倒退", sizes.sorted(), sizes)
        assertTrue(
            "这些字号必须都来自改造前的那套，否则文案换行会整体变化：$sizes",
            preOverhaulSizes.containsAll(sizes),
        )
        // 反过来也要成立：改造后不该剩下没人用的档位，那说明收敛没收干净。
        assertEquals(preOverhaulSizes, sizes.toSet())
    }

    /**
     * 字距是 Web 版「看起来更完整」的一部分，本端原来一个都没有。
     * 只断言它们存在且为正 —— 具体值属于设计，不属于不变量。
     */
    @Test
    fun `the tracking values are present and positive`() {
        assertTrue(TypeScale.labelTracking.value > 0f)
        assertTrue(TypeScale.buttonTracking.value > 0f)
    }

    /** 动效时长与曲线照抄 Web 版，非零且用的是那条「快出慢收」贝塞尔。 */
    @Test
    fun `motion mirrors the web's durations and curve`() {
        val durations = listOf(
            Motion.overlayMillis, Motion.panelMillis, Motion.drawerMillis,
            Motion.bottomSheetMillis, Motion.toastMillis,
        )
        assertTrue("时长必须为正", durations.all { it > 0 })
        assertEquals("面板时长照抄 CSS 的 170ms", 170, Motion.panelMillis)
        assertTrue(Motion.curve is CubicBezierEasing)
    }
}
