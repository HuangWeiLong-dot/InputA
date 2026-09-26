package com.inputa.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inputa.reader.ui.theme.Elevation
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Radius
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale

/**
 * 面：卡片、面板、告警块，以及所有面板共用的外壳与标题。
 *
 * 这个仓库原先的规矩是「全局零圆角」，所以每处都得显式传 `RectangleShape`（41 个调用点）。
 * 现在圆角由 [Radius] 一处定义、经 `MaterialTheme.shapes` 下发，那条规矩连同它的理由
 * 都换掉了 —— 详见 `InputaTheme` 的注释。
 *
 * **卡片的外观只有 [cardSurface] 一处。** 卡片是「有边、有底、有圆角、有一点阴影」的东西，
 * 散在十几处写就会各自漂移（改造前正是这样：同一种按钮写了三遍，内边距分别是 12/8、9/6、12/8）。
 */

/**
 * 卡片的表面处理，供所有「长得像卡片」的东西复用：卡片本身、列表行、告警块。
 *
 * 顺序不能换：`shadow → background → border`。阴影画在底与边**之前**，否则它会盖住卡片
 * 自己的填充；`clip` 也必须显式加，`shadow` 的 `clip = false` 意味着子内容不会被圆角裁掉，
 * 一旦有子内容顶到角上就会露出方角。
 *
 * 顺带把「阴影在这里其实很淡」记在这儿：2dp 的阴影在浅色主题下只是边缘一点润饰，
 * 在夜间主题里**根本看不见**（`bgMain` 是 #101012，黑影落在黑底上）。真正撑起卡片的是那
 * 1px 描边。别因为「夜间看不出来」去调大它 —— 调大也还是看不见，只会把浅色主题弄脏。
 */
@Composable
internal fun Modifier.cardSurface(
    radius: Dp = Radius.card,
    fill: Color = LocalThemeTokens.current.bgSurface,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val tokens = LocalThemeTokens.current
    return this
        .shadow(Elevation.card, shape, clip = false)
        .clip(shape)
        .background(fill, shape)
        .border(1.dp, tokens.borderColor, shape)
}

/** 一张卡片。内容默认带一圈内边距；要通栏的行自己传 `padding = PaddingValues(0.dp)`。 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(contentPadding),
        content = content,
    )
}

/**
 * 告警块：底、边、内容都按 [tone] 走。
 *
 * 三个调用点原先各写各的（书架的报错、释义面板的「服务不可用」、以及一个只有边没有底的
 * 「词典未收录」），内边距 12 与 14 两种、字号 12 与 13 两种。现在它们是同一个块，
 * 差别只在 tone 与内容。
 */
@Composable
fun AlertBox(
    tone: Tone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val colors = tokens.colorsOf(tone)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container, Radius.cardShape)
            .border(1.dp, colors.border, Radius.cardShape)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (icon != null) {
            AppIcon(icon, contentDescription = null, tint = colors.content, size = 18.dp)
        }
        content()
    }
}

/**
 * 三个覆盖面板共用的外壳。
 *
 * 尺寸仍然由这里定（屏幕宽度的 94%、高度的 88%）—— 面板不是卡片，它是覆盖层，
 * 那两个比例是「看得见底下、又装得下内容」的折中，不需要进标度。
 */
@Composable
fun ModalPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    Dialog(
        onDismissRequest = onDismiss,
        // M3 的默认对话框宽度上限约 280dp，对三 tab 书库和可搜索词表都太窄。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = maxHeight)
                .cardSurface()
                .padding(Space.xl),
            content = content,
        )
    }
}

/** 面板标题，右侧可以挂一行次要信息（计数、来源之类）。 */
@Composable
fun PanelTitle(text: String, trailing: String? = null, icon: ImageVector? = null) {
    val tokens = LocalThemeTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (icon != null) {
                AppIcon(icon, contentDescription = null, tint = tokens.textStrong, size = 20.dp)
            }
            Text(
                text = text,
                color = tokens.textStrong,
                fontSize = TypeScale.panel,
                fontWeight = TypeScale.titleWeight,
            )
        }
        if (trailing != null) {
            Text(text = trailing, color = tokens.textMuted, fontSize = TypeScale.body)
        }
    }
}

/**
 * 小节标题：小字号、加粗、带字距。
 *
 * 字距（Web 版的 `tracking-[0.16em]`）不是装饰 —— 小字号的全大写/短词一眼看上去会挤在
 * 一起，字距是让它「像个标题」的那一半。本端原先完全没有字距，这是补齐。
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalThemeTokens.current
    Text(
        text = text,
        color = tokens.textMuted,
        fontSize = TypeScale.label,
        fontWeight = TypeScale.labelWeight,
        letterSpacing = TypeScale.labelTracking,
        modifier = modifier.padding(bottom = Space.sm),
    )
}

/** 面板底部那个居右的「关闭」。三个面板原先各写一遍。 */
@Composable
fun CloseFooter(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "关闭",
    /**
     * 可选的次要动作，排在「关闭」左边。
     *
     * 存在的理由：动作按钮若放在可增长内容**下面**，内容一长就会被挤出面板 ——
     * 面板带裁剪（`cardSurface` 的 `clip`），滚也滚不到。footer 永远在最后，
     * 所以它是这类动作唯一安全的位置。书架粘贴页的「载入阅读器」就是这么修的。
     */
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        // 有两个按钮时用 spacedBy + 右对齐（与设置页那对按钮同一做法）；
        // 只有一个时保持原来的右对齐。权重刻意不用：窄屏上让两个按钮各占一半
        // 反而会把「载入阅读器」压成两行。
        horizontalArrangement = if (action != null) {
            Arrangement.spacedBy(Space.md, Alignment.End)
        } else {
            Arrangement.End
        },
    ) {
        action?.invoke()
        PanelButton(label, onClick = onClose, tone = Tone.Accent)
    }
}

/** 面板里那种「一行说明」的次要文字，比如设置里的提示。 */
@Composable
fun HintText(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val tokens = LocalThemeTokens.current
    Text(
        text = text,
        color = if (tone == Tone.Neutral) tokens.textMuted else tokens.colorsOf(tone).content,
        fontSize = TypeScale.label,
        modifier = modifier,
    )
}
