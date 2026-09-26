package com.inputa.reader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.inputa.reader.domain.model.AppTheme

/**
 * 当前主题的颜色。`staticCompositionLocalOf` 而不是 `compositionLocalOf`：
 * 主题切换必然整棵树重组，不需要细粒度的读取追踪，静态版本读起来更便宜。
 */
val LocalThemeTokens = staticCompositionLocalOf<ThemeTokens> {
    error("ThemeTokens 未提供 —— 内容必须包在 InputaTheme 里")
}

/**
 * **圆角只有一套，经 `MaterialTheme.shapes` 下发。**
 *
 * 这里原来写着相反的一条规矩：全局零圆角，遇到会画圆角的 M3 组件就显式传
 * `RectangleShape`，依据是「`Shapes` 的构造函数在当前 Material3 版本里是 `internal`，
 * 所以覆盖不了 `MaterialTheme.shapes`」。**那条依据是错的** —— `Shapes()` 的无参构造与
 * 公开的 `copy(extraSmall = …)` 都能用，设备上实测覆盖生效：把 `ModalBottomSheet` 上那个
 * 显式的 `shape = RectangleShape` 去掉之后，面板上缘立刻按 [InputaShapes] 的 `extraLarge`
 * 画出了圆角。也就是说，当初那个显式参数**本身就是**覆盖看起来失效的原因。
 *
 * 所以现在的规矩是：
 *
 *   **别再写裸的 `shape = …`。** 需要圆角就从 [Radius] 取，或者干脆不写 ——
 *   `MaterialTheme.shapes` 已经下发到每个会读它的组件上。裸参数只会在某一处悄悄压掉
 *   全局设定，而这正是它上一次干的事。
 *
 * 会读 `shapes` 的 M3 组件（截至目前用到的）：`ModalBottomSheet`（`extraLarge`）、
 * `AlertDialog`、`TextField` / `OutlinedTextField`（`extraSmall`）。`HorizontalDivider`、
 * `CircularProgressIndicator` 不读；`Surface` 的默认形状本来就是矩形。
 *
 * 这条与 Web 版是**有意分歧**：那边 `index.css` 对 `*, *::before, *::after` 设了
 * `border-radius: 0`，且全局没有阴影标度。本端走卡片式，理由与边界见 [Design]、[Radius]
 * 的注释以及 `CLAUDE.md` 的「刻意分歧」一节。
 */
@Composable
fun InputaTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val tokens = remember(theme) { ThemePalettes.of(theme) }
    CompositionLocalProvider(LocalThemeTokens provides tokens) {
        MaterialTheme(
            colorScheme = tokens.toColorScheme(),
            shapes = InputaShapes,
            typography = InputaTypography,
            content = content,
        )
    }
}

/**
 * M3 的五个形状槽位全部指向 [Radius] 里的三档。
 *
 * `extraSmall` 给输入框，`small`/`medium`/`large` 给面板与对话框，`extraLarge` 给底部面板。
 * 之所以五个全填、而不是只填用得到的那一个：漏掉一个槽位，将来某个 M3 组件就会用它自己的
 * 默认值画出一个谁也没设计过的圆角 —— 那正是原来那条规矩想避免的事，只是方式选错了
 * （靠每个调用点自觉，而不是靠一处覆盖）。
 */
internal val InputaShapes: Shapes = Shapes().copy(
    extraSmall = RoundedCornerShape(Radius.control),
    small = RoundedCornerShape(Radius.control),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(Radius.card),
)

/**
 * M3 槽位里的排版。
 *
 * 说清楚它的作用范围：本应用的文字**几乎全部**走显式的 `TextStyle`，而 `Text` 读的是
 * `LocalTextStyle`、不是 `MaterialTheme.typography`，所以这里只对**由 M3 组件自己画的
 * 文字**生效（`TextButton` 的 `labelLarge` 之类）。改造后那两个 `TextButton` 都换成了
 * 自家按钮，实际上就没有读者了。
 *
 * 仍然设上，是为了将来引入任何 M3 组件时它不会突然冒出 Roboto 的默认尺寸 ——
 * 一个槽位填错，症状是「某个按钮的字比别处大一号」，很难归因。
 */
internal val InputaTypography: Typography = Typography(
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = TypeScale.labelWeight,
        fontSize = TypeScale.body,
        letterSpacing = TypeScale.buttonTracking,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = TypeScale.bodyLg,
    ),
)

/**
 * 把主题色映射到 Material3 的槽位。
 *
 * 只映射组件**实际会读**的那些：`surface`/`onSurface` 管默认文字色，
 * `primary` 管强调色，`error` 管破坏性操作。剩余槽位由 M3 的默认值兜底，
 * 而它们在本应用里基本用不到 —— 界面几乎全部由自定义色构成。
 */
private fun ThemeTokens.toColorScheme() = if (theme == AppTheme.Dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = bgMain,
        secondary = accent,
        onSecondary = bgMain,
        background = bgMain,
        onBackground = textMain,
        surface = bgSurface,
        onSurface = textMain,
        surfaceVariant = bgSubtle,
        onSurfaceVariant = textMuted,
        outline = borderColor,
        outlineVariant = borderColor,
        error = danger,
        onError = bgMain,
        scrim = Color.Black,
        // M3 默认 surfaceTint = primary，于是任何走色调高（tonal elevation）的组件都会把
        // surface 往 accent 方向混 —— 那是 LevelContrastTest 看不见的调色板漂移（它只查
        // 对比度，查不出色相挪了几个点）。设成 bgSurface，任何将来的色调叠层都退化为恒等。
        surfaceTint = bgSurface,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = bgMain,
        secondary = accent,
        onSecondary = bgMain,
        background = bgMain,
        onBackground = textMain,
        surface = bgSurface,
        onSurface = textMain,
        surfaceVariant = bgSubtle,
        onSurfaceVariant = textMuted,
        outline = borderColor,
        outlineVariant = borderColor,
        error = danger,
        onError = bgMain,
        scrim = Color.Black,
        surfaceTint = bgSurface,
    )
}

/**
 * 把 Compose 的 `Color` 取成 `0xRRGGBB` 整数，供领域层的 WCAG 计算使用
 * （那边的 `Contrast` 只认整数，所以能纯 JVM 测）。
 */
fun Color.toRgbInt(): Int = toArgb() and 0xFFFFFF
