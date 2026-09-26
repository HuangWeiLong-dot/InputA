package com.inputa.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Radius
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.ThemeTokens
import com.inputa.reader.ui.theme.TypeScale

/**
 * 按钮，以及「按钮长什么样」的唯一一套定义。
 *
 * **一条规矩：色调是一个完整、自洽的集合，不存在第二个样式轴。**
 *
 * Web 版 `ui.ts` 顶部要警告的是 Tailwind 的解析顺序 —— 两个设置同一属性的工具类谁生效
 * 取决于样式表顺序，所以只能「一个尺寸 + 一个配色」。Compose 没有那个坑，但有更糟的一个：
 * 一个组件同时收 `color` 与 `filled` 两个独立参数时，`(success, filled = true)` 这种
 * 组合是编译得过的、而且没有任何东西能说明它该是什么样子。原先的 `PanelButton` 就是这样。
 *
 * 所以这里把「长什么样」收进 [Tone]：一个 tone 同时给出底、字、边三种颜色，
 * 调用点只能选一个，**不可能**拼出一个谁也没设计过的组合。
 */

/**
 * 按钮（以及其他小控件）的色调。
 *
 * [Neutral] 起，其余按用途：实心的 [Invert] / [Accent] 用于每个界面的主操作，
 * 描边的 [Success] / [Danger] 用于「标为已掌握」「移出词库」这类明确动作，
 * [DangerGhost] 是列表里那个平时很安静、只在按下时变红的删除。
 *
 * [OnHighlight] 是特例，值得说明：它画在 [Highlight] 那个琥珀色告警面**里面**，
 * 所以底色必须是页面底色而不是透明 —— 透明会让它整个消失在告警面里。
 *
 * [Level] 带一个熟练度，于是「词的状态」这一种外观在三处（释义面板的状态标签、
 * 释义面板的 5 段选择器、生词本的等级标签）共用同一个实现 —— 原先它们各写各的内边距，
 * 却算的是同一个颜色。
 */
sealed interface Tone {
    data object Neutral : Tone
    data object Invert : Tone
    data object Accent : Tone
    data object AccentSoft : Tone
    data object Success : Tone
    data object Danger : Tone

    /** 实心的危险色：破坏性操作**按下确认**的那一下，比如「确认清空词汇库」。 */
    data object DangerSolid : Tone
    data object DangerGhost : Tone
    data object Highlight : Tone
    data object OnHighlight : Tone

    /**
     * 熟练度。等级可以是 `null` —— 领域层用 null 表示「未收录」，而徽章上写的是
     * 「未标记」而不是色阶里的任何一级（见释义面板 StatusBar 的说明）。
     */
    data class Level(val level: WordStatus?) : Tone
}

/** 一个 tone 的三种颜色。 */
internal data class ToneColors(val container: Color, val content: Color, val border: Color)

/**
 * tone → 颜色。**唯一一处**。
 *
 * 注意 [Tone.Level] 对「已掌握」的处理：它给的是 `bgSubtle` 而不是页面底色。
 * 已掌握的词在释义面板里没有色阶（那是设计），但当它作为一个**标签**出现时，
 * 总得有个能看出边界的底 —— 页面底色在卡片上等于没有底色。
 */
internal fun ThemeTokens.colorsOf(tone: Tone): ToneColors = when (tone) {
    Tone.Neutral -> ToneColors(Color.Transparent, textMain, borderColor)
    Tone.Invert -> ToneColors(textStrong, bgSurface, textStrong)
    Tone.Accent -> ToneColors(accent, bgMain, accent)
    Tone.AccentSoft -> ToneColors(accentSoft, accent, accentBorder)
    Tone.Success -> ToneColors(Color.Transparent, success, success)
    Tone.Danger -> ToneColors(Color.Transparent, danger, danger)
    Tone.DangerSolid -> ToneColors(danger, bgMain, danger)
    Tone.DangerGhost -> ToneColors(Color.Transparent, textMuted, borderColor)
    Tone.Highlight -> ToneColors(highlightBg, highlightText, highlightBorder)
    Tone.OnHighlight -> ToneColors(bgSurface, highlightText, highlightBorder)
    is Tone.Level -> {
        // 未收录（null）与已掌握都给中性底色：徽章上写的是「未标记」/「已掌握」这两个词，
        // 配生词底色会自相矛盾 —— 读者会以为词库里已经有了记录。
        val level = tone.level
        ToneColors(
            container = if (level == null || level == WordStatus.MASTERED) bgSubtle else levelBackground(level),
            content = textMain,
            border = borderColor,
        )
    }
}

/** 与 Web 版一致的控件高度：Sm 只用于重复的列表行。 */
enum class ButtonSize(val height: Dp, val padding: Dp) {
    Sm(40.dp, Space.lg),
    Md(44.dp, Space.xl),
    Lg(48.dp, Space.xxl),
}

/**
 * 描边按钮，可选一个前置图标。
 *
 * **不给内容槽**（`content: @Composable RowScope.() -> Unit`）：那等于把上面那条规矩作废 ——
 * 调用点就能往里塞任何东西，按钮的外观又变得无法预料。真需要别的形态时再加具名参数。
 */
@Composable
fun PanelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    size: ButtonSize = ButtonSize.Md,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val tokens = LocalThemeTokens.current
    val colors = tokens.colorsOf(tone)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = Radius.controlShape

    val solid = colors.container != Color.Transparent
    // 按下反馈：透明底的按钮换成按下色；实心底的按钮整体压暗一点。
    // Web 版正是这么做的（`active:bg-[var(--bg-active)]` 对分段控件，`active:opacity-85` 对实心按钮）——
    // 触摸设备上 `hover:` 永远不触发，按下反馈只能靠这一下。
    val container = when {
        !enabled -> Color.Transparent
        pressed && !solid -> tokens.bgActive
        else -> colors.container
    }
    val content = if (enabled) colors.content else tokens.textMuted
    val border = if (enabled) colors.border else tokens.textMuted

    Row(
        modifier = modifier
            .height(size.height)
            .clip(shape)
            .background(container, shape)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                // 边框自己会表达按下态，再叠一层矩形水波纹就冲突了。
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (pressed && solid) 0.85f else 1f)
            .padding(horizontal = size.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (icon != null) {
            AppIcon(icon, contentDescription = null, tint = content, size = IconInButton)
        }
        Text(
            text = label,
            color = content,
            fontSize = TypeScale.body,
            fontWeight = TypeScale.labelWeight,
            maxLines = 1,
            softWrap = false,
        )
        if (trailingIcon != null) {
            AppIcon(trailingIcon, contentDescription = null, tint = content, size = IconInButton)
        }
    }
}

/**
 * 只有图标的按钮。
 *
 * **视觉 44dp，可点区域 48dp。** 这不是随手写的两个数：Android 的最小触摸目标是 48dp，
 * 而 44dp 是 Web 版 `BTN_GHOST` 的尺寸。做法是外层 48dp 的节点负责可点与语义、
 * 内层 44dp 的盒子负责画底与边 —— 注意**不能**写成 `Modifier.size(44.dp).sizeIn(min = 48.dp)`，
 * 精确尺寸会赢，那个 48 一点作用都没有。
 */
@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    size: ButtonSize = ButtonSize.Md,
    enabled: Boolean = true,
) {
    val tokens = LocalThemeTokens.current
    val colors = tokens.colorsOf(tone)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = Radius.controlShape
    val visual = size.height
    val solid = colors.container != Color.Transparent
    val content = if (enabled) colors.content else tokens.textMuted

    Box(
        modifier = modifier
            .size(TouchTarget)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visual)
                .clip(shape)
                .background(
                    when {
                        !enabled -> Color.Transparent
                        pressed && !solid -> tokens.bgActive
                        else -> colors.container
                    },
                    shape,
                )
                .border(1.dp, if (enabled) colors.border else tokens.textMuted, shape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = contentDescription, tint = content, size = IconInButton)
        }
    }
}

/** 按钮里的图标尺寸：与 12sp 的标签视觉重量相当。 */
private val IconInButton: Dp = 16.dp

/** Android 的最小触摸目标。所有只有图标的控件都按它兜底。 */
internal val TouchTarget: Dp = 48.dp
