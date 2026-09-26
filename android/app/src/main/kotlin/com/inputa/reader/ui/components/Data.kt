package com.inputa.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Radius
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale

/**
 * 数据行的零件：状态标签、列表行、提示条。
 *
 * 这三样原先在四处各写了一遍（释义面板的状态标签、释义面板的考试标签、生词本的等级标签、
 * 书架的四种行），同一个东西三套内边距与三种边框色。现在它们共用 [Tone]，
 * 于是「一个词的熟练度长什么样」全应用只有一个答案。
 */

/** 状态标签：一个词的熟练度、考试标签、来源之类。 */
@Composable
fun Chip(label: String, tone: Tone, modifier: Modifier = Modifier) {
    val tokens = LocalThemeTokens.current
    val colors = tokens.colorsOf(tone)
    Box(
        modifier = modifier
            .background(colors.container, Radius.chipShape)
            .border(1.dp, colors.border, Radius.chipShape)
            .padding(horizontal = Space.md, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = colors.content,
            fontSize = TypeScale.label,
            fontWeight = TypeScale.labelWeight,
            maxLines = 1,
        )
    }
}

/**
 * 列表容器：一个 1px 描边的圆角框，行与行之间由 [ListRow] 自己画分隔线。
 *
 * **列表用「一个框 + 分隔线」，不是「每行一张卡片」。** 一屏十几行、每行一张带阴影的
 * 卡片既丑又贵（每张一个 RenderNode）；而一张大卡片包住整个列表，既能看出「这是一组
 * 可点的东西」，又只有一条边框。卡片留给一屏一两个的东西。
 *
 * 这个框**不再加阴影**：它总是嵌在面板里，而面板本身已经是浮起来的一层。
 */
@Composable
fun ListBlock(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val tokens = LocalThemeTokens.current
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.controlShape)
            .border(1.dp, tokens.borderColor, Radius.controlShape),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * 列表行：左边一块可换行的内容，右边挂若干个控件。
 *
 * 四个调用点原先各写一遍 `Row(padding(vertical = 5.dp)) + Column(weight(1f))` 加一两个
 * `PanelButton`，其中两处标题忘了 `maxLines`／`overflow`，长书名会把右边的按钮挤出屏幕。
 * 统一在这里加上截断。
 *
 * **列表行不做成卡片。** 一屏十几行、每行一张带阴影的卡片，既丑又贵（每张卡片一个
 * RenderNode）。列表用「行 + 分隔线」，卡片留给一屏一两个的东西。
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleFontFamily: FontFamily = FontFamily.Default,
    titleSize: androidx.compose.ui.unit.TextUnit = TypeScale.bodyStrong,
    titleMaxLines: Int = 1,
    leading: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    /** 点标题/副标题那一块时触发。为 null 时内容区不可点（列表里只有按钮可点）。 */
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val tokens = LocalThemeTokens.current
    Column(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (leading != null) leading()
        Column(
            modifier = Modifier
                .weight(1f)
                // 整块文字区都可点，而不是只有那几个字：词表里一行的高约 56dp，
                // 手指落在词与行边之间也该算命中。
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = title,
                color = tokens.textMain,
                fontSize = titleSize,
                fontFamily = titleFontFamily,
                fontWeight = if (titleFontFamily == FontFamily.Serif) FontWeight.Normal else TypeScale.labelWeight,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = tokens.textMuted,
                    fontSize = TypeScale.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            trailing()
        }
    }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(tokens.borderColor),
            )
        }
    }
}

/**
 * 提示条：翻页收录了哪些词、跳过了几页、以及那个「撤销」。
 *
 * 整条可点（撤销是条上的主要动作），所以保留水波纹而不是自绘按下色。
 * [actionLabel] 为空时就是一条不可点的说明。
 */
@Composable
fun NoticeBar(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.AccentSoft,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = LocalThemeTokens.current
    val colors = tokens.colorsOf(tone)
    val clickable = onAction != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container, Radius.cardShape)
            .border(1.dp, colors.border, Radius.cardShape)
            .then(if (clickable) Modifier.clickable { onAction?.invoke() } else Modifier)
            .padding(horizontal = Space.xl, vertical = Space.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = colors.content,
            fontSize = TypeScale.body,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                if (actionIcon != null) {
                    AppIcon(actionIcon, contentDescription = null, tint = colors.content, size = 14.dp)
                }
                Text(
                    text = actionLabel,
                    color = colors.content,
                    fontSize = TypeScale.body,
                    fontWeight = TypeScale.labelWeight,
                )
            }
        }
    }
}
