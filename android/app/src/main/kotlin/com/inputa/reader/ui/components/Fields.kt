package com.inputa.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inputa.reader.ui.icons.InputaIcons
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Radius
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale

/**
 * 输入控件：文本框、分段控件、步进器、勾选行。
 *
 * 文本框仍然**不用** M3 的 `OutlinedTextField` —— 它的容器自带圆角与浮动标签，而这里
 * 要的是一个方正的输入框，也更贴近 Web 版那个 `FIELD` 类。这条决定与圆角改口径无关：
 * 圆角现在是设计的一部分，而浮动标签仍然不是。
 */

/** 方角文本框。 */
@Composable
fun PanelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Int = 0,
) {
    val tokens = LocalThemeTokens.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.bgMain, Radius.controlShape)
            .border(1.dp, tokens.borderColor, Radius.controlShape)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = tokens.textMuted, fontSize = TypeScale.bodyLg)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = tokens.textMain, fontSize = TypeScale.bodyLg),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier
                .fillMaxWidth()
                // 高度加在**输入框自己**身上，不是外层那个 Box 上。
                // 加在 Box 上时的后果是：框看着有 180dp 高，而真正能聚焦的只有顶部
                // 那 40dp —— 点在中下部毫无反应，文字还留在上一个字段里。
                // 设备验证时就是这么发现的。
                .then(if (minHeight > 0) Modifier.heightIn(min = minHeight.dp) else Modifier),
        )
    }
}

/**
 * 分段控件：一个 1px 外框，格与格之间一条 1px 分隔线，最后一格后面没有。
 *
 * 这是 Web 版 `SEGMENT_BOX` / `SEGMENT` 的形状（那边的 `last:border-r-0` 就是这个意思）。
 * 改造前这里是「每格各自一整圈边框，格与格之间垫 6dp」—— 那读起来是三个挨着的按钮，
 * 而不是一个控件；而且它的 `Spacer` 是在**每一格之后**都加，包括最后一格，
 * 于是整行右侧平白多出 6dp，右边缘对不齐。
 *
 * 按下反馈走 [Tone] 那套：选中格是 accentSoft，未选中格按下时是 `bgActive`。
 * 触摸设备上 `hover:` 永远不触发，这一下是分段控件唯一的「我按到了」。
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
) {
    val tokens = LocalThemeTokens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.controlShape)
            .border(1.dp, tokens.borderColor, Radius.controlShape)
            // 分隔线要撑满整格高度，所以行高由最高的那一格决定（IntrinsicSize.Min）。
            .height(IntrinsicSize.Min),
    ) {
        options.forEachIndexed { index, option ->
            val isOn = option == selected
            val interaction = remember(option) { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        when {
                            isOn -> tokens.accentSoft
                            pressed -> tokens.bgActive
                            else -> tokens.bgMain
                        },
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(option) },
                    )
                    .padding(vertical = Space.md, horizontal = Space.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.invoke(option)?.let { vector ->
                    AppIcon(
                        vector,
                        contentDescription = null,
                        tint = if (isOn) tokens.accent else tokens.textMuted,
                        size = 16.dp,
                    )
                    Spacer(Modifier.width(Space.sm))
                }
                Text(
                    text = label(option),
                    color = if (isOn) tokens.accent else tokens.textMuted,
                    fontSize = TypeScale.body,
                    fontWeight = TypeScale.labelWeight,
                    maxLines = 1,
                )
            }
            if (index != options.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(tokens.borderColor),
                )
            }
        }
    }
}

/** 「字号」「每页词数」那种加减器。原先的加减号是两个文字按钮（`"−"` 与 `"+"`）。
 *
 * 色调取 [Tone.AccentSoft]（染色）而不是实心：实心蓝的方块比它旁边的数字还抢眼，
 * 而这两个按钮只是把数字挪一格 —— 权重该低于它操作的那个值。
 */
@Composable
fun StepperRow(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    val tokens = LocalThemeTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        // 居中：这一组「− 20 +」是卡片的视觉主体，靠左会与上面的小节标题抢左对齐的线，
        // 看上去像没排完。
        horizontalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterHorizontally),
    ) {
        IconButton(
            icon = InputaIcons.Minus,
            contentDescription = "减少",
            onClick = onMinus,
            tone = Tone.AccentSoft,
            size = ButtonSize.Sm,
            enabled = minusEnabled,
        )
        Text(
            text = value,
            color = tokens.textMain,
            fontSize = TypeScale.bodyStrong,
            // 数字在**两个按钮正中间**：盒宽固定、文字居中，于是左右两侧的空白一样宽。
            // 只写 `width(48.dp)` 的话文字是靠左的 —— 两位数右边会空出一大截，
            // 看上去数字贴着减号、离加号很远。
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        IconButton(
            icon = InputaIcons.Plus,
            contentDescription = "增加",
            onClick = onPlus,
            tone = Tone.AccentSoft,
            size = ButtonSize.Sm,
            enabled = plusEnabled,
        )
    }
}

/**
 * 勾选行：一行可点的整块，左边一个自己画的方框，右边标题与说明。
 *
 * 仍然**不用** M3 的 `Switch` / `Checkbox` —— 它们的形状与内边距是 Material 的，
 * 而这里的方框要用 [Radius.chip]。勾上的标记改用图标，不再是 `Text("✓")` 那个字形。
 */
@Composable
fun CheckboxRow(
    label: String,
    hint: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalThemeTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 整行一个大目标，所以这里**保留**水波纹：边框类的控件各自画按下色，
            // 而这种通栏的行没有别的信号说明「整行都能点」。
            .clickable { onToggle(!checked) }
            .padding(vertical = Space.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(Radius.chipShape)
                .background(if (checked) tokens.accent else tokens.bgMain, Radius.chipShape)
                .border(
                    1.dp,
                    if (checked) tokens.accent else tokens.borderStrong,
                    Radius.chipShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                AppIcon(
                    InputaIcons.Check,
                    contentDescription = null,
                    tint = tokens.bgMain,
                    size = 14.dp,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(text = label, color = tokens.textMain, fontSize = TypeScale.bodyLg)
            Text(text = hint, color = tokens.textMuted, fontSize = TypeScale.label)
        }
    }
}
