package com.inputa.reader.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.inputa.reader.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 调色板的**冻结**检查。
 *
 * `LevelContrastTest` 盯的是对比度与单调性，但它**看不见色相挪了几个点**：把 sepia 的墨色
 * 从 `#8F7500` 改成 `#8F7A00`，那边所有断言照过。而这批十六进制值是「逐字照抄 Web 版
 * `index.css`、并且明确禁止优化」的东西（见 [ThemePalettes] 的注释），改错一位的后果是
 * 三套主题里某一处颜色悄悄变味 —— 再加上 `android/` 目录此刻并不在版本控制里，
 * 改错了没有任何东西会响。
 *
 * **它和 `levelBackground` 那条「不要留第二份真相」的注释并不冲突**，两者的对象不同：
 * 那条说的是**派生**颜色 —— 它们必须继续由 `lerp` 算出来，才不会与公式漂移；这里冻结的是
 * 被要求原样保留的那 18 个**源值**。所以派生值这里只验「由这批源值算得出来」（见
 * `pressed mix`、`level tints`），不写死具体数字。
 */
@RunWith(RobolectricTestRunner::class)
class PaletteFreezeTest {

    private fun hex(color: androidx.compose.ui.graphics.Color): String =
        "%06X".format(color.toArgb() and 0xFFFFFF)

    private fun paletteOf(tokens: ThemeTokens): Map<String, String> = mapOf(
        "bgMain" to hex(tokens.bgMain),
        "bgSurface" to hex(tokens.bgSurface),
        "bgSubtle" to hex(tokens.bgSubtle),
        "bgHover" to hex(tokens.bgHover),
        "textStrong" to hex(tokens.textStrong),
        "textMain" to hex(tokens.textMain),
        "textMuted" to hex(tokens.textMuted),
        "borderColor" to hex(tokens.borderColor),
        "borderStrong" to hex(tokens.borderStrong),
        "accent" to hex(tokens.accent),
        "accentSoft" to hex(tokens.accentSoft),
        "accentBorder" to hex(tokens.accentBorder),
        "highlightBg" to hex(tokens.highlightBg),
        "highlightText" to hex(tokens.highlightText),
        "highlightBorder" to hex(tokens.highlightBorder),
        "success" to hex(tokens.success),
        "danger" to hex(tokens.danger),
        "levelInk" to hex(tokens.levelInk),
    )

    /** 与 Web 版 `src/index.css` 的三个主题块逐字对应。改这里之前先改那边。 */
    private val light = mapOf(
        "bgMain" to "FFFFFF", "bgSurface" to "FFFFFF", "bgSubtle" to "F4F4F5",
        "bgHover" to "E7E7EA", "textStrong" to "09090B", "textMain" to "18181B",
        "textMuted" to "3F3F46", "borderColor" to "D4D4D8", "borderStrong" to "71717A",
        "accent" to "1D4ED8", "accentSoft" to "EFF6FF", "accentBorder" to "93B4FD",
        "highlightBg" to "FDE68A", "highlightText" to "422006", "highlightBorder" to "B45309",
        "success" to "15803D", "danger" to "B91C1C", "levelInk" to "A68A00",
    )

    private val sepia = mapOf(
        "bgMain" to "F8EED9", "bgSurface" to "FDF8EC", "bgSubtle" to "EFE2C6",
        "bgHover" to "E5D3AE", "textStrong" to "241A09", "textMain" to "2F2412",
        "textMuted" to "4D3C1F", "borderColor" to "CBB288", "borderStrong" to "7D6A48",
        "accent" to "8A4B08", "accentSoft" to "F6E6C8", "accentBorder" to "C19A55",
        "highlightBg" to "F6C445", "highlightText" to "3B2500", "highlightBorder" to "9A5B06",
        "success" to "1C6B3B", "danger" to "A11F1F", "levelInk" to "8F7500",
    )

    private val dark = mapOf(
        "bgMain" to "101012", "bgSurface" to "17171B", "bgSubtle" to "1E1E23",
        "bgHover" to "2C2C34", "textStrong" to "FFFFFF", "textMain" to "F2F2F4",
        "textMuted" to "BCBCC7", "borderColor" to "3A3A44", "borderStrong" to "6F6F7D",
        "accent" to "8FB4FF", "accentSoft" to "17223C", "accentBorder" to "3D5A99",
        "highlightBg" to "8A5600", "highlightText" to "FFF4D6", "highlightBorder" to "E0A92A",
        "success" to "6EE7A0", "danger" to "FF8F8F", "levelInk" to "D6AC00",
    )

    @Test
    fun `the transcribed palette matches the web's, value for value`() {
        assertEquals("明亮主题", light, paletteOf(ThemePalettes.Light))
        assertEquals("羊皮纸主题", sepia, paletteOf(ThemePalettes.Sepia))
        assertEquals("夜间主题", dark, paletteOf(ThemePalettes.Dark))
    }

    /**
     * 按下态由 `lerp(bgMain, textStrong, 0.18)` 算出 —— 这里验证的其实是**那个 0.18**：
     * 派生值本身不写死，但混色比例改了就要在这里交代。
     */
    @Test
    fun `the pressed colour still follows the 18 percent mix`() {
        ThemePalettes.all.forEach { tokens ->
            assertEquals(
                "${tokens.theme.key} 的 bgActive",
                hex(lerp(tokens.bgMain, tokens.textStrong, 0.18f)),
                hex(tokens.bgActive),
            )
        }
    }

    /**
     * 五级底色由 `levelInk` 与 `bgMain` 混出，比例是 10/22/34/46/58。
     * 同样只验「算得出来」——具体色值由 `LevelContrastTest` 从对比度那一侧盯着。
     */
    @Test
    fun `the level tints still follow their mix ratios`() {
        val ratios = listOf(0.10f, 0.22f, 0.34f, 0.46f, 0.58f)
        ThemePalettes.all.forEach { tokens ->
            com.inputa.reader.domain.vocab.WordLevels.LEVELS.forEachIndexed { index, level ->
                assertEquals(
                    "${tokens.theme.key} 的 $level",
                    hex(lerp(tokens.bgMain, tokens.levelInk, ratios[index])),
                    hex(tokens.levelBackground(level)),
                )
            }
        }
    }

    /**
     * 启动图标的前景是手写矢量，配色另有一份在 `res/values/colors.xml` 里。
     * 那份是**镜像**（文件注释自己承认），而镜像最容易在改主题时忘掉一半。
     */
    @Test
    fun `the launcher's sepia background mirrors the theme`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            "colors.xml 的 sepia_bg 与 ThemePalettes.Sepia.bgMain 不一致",
            hex(ThemePalettes.Sepia.bgMain),
            "%06X".format(context.getColor(R.color.sepia_bg) and 0xFFFFFF),
        )
    }
}
