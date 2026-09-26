package com.inputa.reader.ui.theme

import androidx.compose.ui.graphics.lerp
import com.inputa.reader.domain.color.Contrast
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.vocab.WordLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 5 级色阶的**无障碍不变量**。
 *
 * Web 版的 `index.css` 在这件事上只留下一句注释：「暗色主题下 58% 是 5.06:1，
 * 62% 就跌破 4.5:1」，**而没有任何东西强制它**。改一个百分比、换一个主题墨色，
 * 都可能悄悄让正文读不清 —— 而那是最不该出问题的地方（这套阅读器的全部意义就是
 * 让人把字看清楚）。
 *
 * 这里把那句注释变成可执行的检查。用的是 `:domain` 里那份纯 JVM 的 WCAG 数学，
 * 所以公式本身另有快速测试；这里只负责把真实的主题色喂进去。
 *
 * 之所以要 Robolectric：颜色是用 Compose 的 `lerp` 现算的，而它在 Oklab 空间插值
 * （与 CSS 的 `color-mix(in oklab, ...)` 同一个算法）—— 那比手抄 15 个常量可靠，
 * 不会漂移。
 */
@RunWith(RobolectricTestRunner::class)
class LevelContrastTest {

    private fun ratioOf(theme: ThemeTokens, level: WordStatus): Double =
        Contrast.ratio(theme.textMain.toRgbInt(), theme.levelBackground(level).toRgbInt())

    /**
     * 每一级底色上正文都必须达到 AA。这是整个色阶唯一的硬约束 ——
     * 底色再好看，字读不清就没有意义。
     */
    @Test
    fun `every level keeps body text at AA or better, in every theme`() {
        val failures = mutableListOf<String>()

        for (theme in ThemePalettes.all) {
            for (level in WordLevels.LEVELS) {
                val ratio = ratioOf(theme, level)
                if (ratio < Contrast.AA_NORMAL_TEXT) {
                    failures += "${theme.theme.key}/${level.name} = ${"%.2f".format(ratio)}:1"
                }
            }
        }

        assertTrue("这些色阶低于 AA 4.5:1：$failures", failures.isEmpty())
    }

    /**
     * 58% 这个上限有实在的余量 —— 而边界比 Web 版注释里写的宽一个百分点。
     *
     * Web 版 `index.css` 写着「暗色主题下 62% 是 4.59:1，**跌破 AA**，所以 58% 是硬上限」。
     * 在这套实现里实测（同一批颜色、同一个 Oklab 插值）：
     *
     *   58% → 5.06:1    62% → 4.59:1    63% → 第一个跌破 4.5:1
     *
     * 注释里那个**数字是准的**（说明 Compose 的 `lerp` 与 Chrome 的
     * `color-mix(in oklab, …)` 是同一个算法，精确到小数点后两位），但 4.59 高于 4.5，
     * 所以「62% 就跌破」这个结论差了一个百分点。
     *
     * 这不改变任何决定 —— 58% 仍然安全，而且色阶的视觉分档是调过的，不该动。
     * 但**测试不去钉那个信念**，而是自己测出边界：颜色或公式任何一边动了，
     * 失败信息都会给出新的边界值。
     */
    @Test
    fun `the chosen ceiling leaves real headroom before AA breaks`() {
        val dark = ThemePalettes.Dark

        val ratioAt = { percent: Int ->
            Contrast.ratio(
                dark.textMain.toRgbInt(),
                lerp(dark.bgMain, dark.levelInk, percent / 100f).toRgbInt(),
            )
        }

        val breaking = (1..99).firstOrNull { ratioAt(it) < Contrast.AA_NORMAL_TEXT }

        assertTrue(
            "预期边界在 62% 之后，实际 $breaking% 就跌破了 —— 色阶或对比度公式已被改动",
            breaking != null && breaking >= 62,
        )
        // 第 5 级用的是 58%，它到边界之间必须留得下余量。
        val headroom = (breaking ?: 100) - 58
        assertTrue("58% 离跌破 AA 只剩 $headroom 个百分点，太薄了", headroom >= 4)

        // 第 5 级是整套色阶里最紧的一档，把它自己的余量也钉住 ——
        // 只验「不低于 4.5」的话，改到 4.51 也算通过，而那已经没有余量可言。
        val atFive = ratioAt(58)
        assertTrue(
            "第 5 级只有 ${"%.2f".format(atFive)}:1（Web 版注释记的是 5.06:1），余量太薄",
            atFive >= 4.9,
        )
    }

    /**
     * 色阶必须在明度上单调，否则「颜色越深 = 越不熟」这个视觉规则就断了 ——
     * 读者会看到 4 级比 5 级还深，而那个顺序是整个界面的语义。
     */
    @Test
    fun `the scale is monotonic in luminance within each theme`() {
        for (theme in ThemePalettes.all) {
            val luminances = WordLevels.LEVELS.map {
                Contrast.relativeLuminanceOf(theme.levelBackground(it).toRgbInt())
            }

            val ascending = luminances.zipWithNext().all { (a, b) -> b > a }
            val descending = luminances.zipWithNext().all { (a, b) -> b < a }

            assertTrue(
                "${theme.theme.key} 的色阶非单调：$luminances",
                ascending || descending,
            )
        }
    }

    /**
     * 没有底色的词（已掌握）与整页正文都落在 `bgMain` 上 —— 那条路径也必须达标，
     * 否则「掌握了的词」和普通文字反而比生词更难读。
     */
    @Test
    fun `plain body text on the page background passes AA in every theme`() {
        for (theme in ThemePalettes.all) {
            val ratio = Contrast.ratio(theme.textMain.toRgbInt(), theme.bgMain.toRgbInt())
            assertTrue(
                "${theme.theme.key} 的正文对页面底色只有 ${"%.2f".format(ratio)}:1",
                ratio >= Contrast.AA_NORMAL_TEXT,
            )
        }
    }

    /**
     * 未收录按 5 级着色、已掌握不着色 —— 这条规则由领域层的纯函数保证，
     * 这里只确认主题这一层**老老实实转发**了它，没有自己另写一套。
     */
    @Test
    fun `highlight levels pass through the domain rule unchanged`() {
        for (theme in ThemePalettes.all) {
            assertEquals(theme.levelBackground(WordStatus.L5), theme.wordBackground(null))
            assertEquals(theme.levelBackground(WordStatus.L2), theme.wordBackground(WordStatus.L2))
            assertNull(theme.wordBackground(WordStatus.MASTERED))
        }
    }
}
