package com.inputa.next.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 底栏四个 tab；＋ 是动作，不是 tab。 */
enum class NextTab(val label: String, val icon: ImageVector) {
    Home("Home", LqIcons.Home),
    Explore("Explore", LqIcons.Search),
    Playlist("Playlist", LqIcons.MusicNote),
    Vocabulary("Vocabulary", LqIcons.LingqBubble),
}

/**
 * LingQ 7 底栏：没有整块实心底，只有一层半透明模糊胶囊，
 * 内容会从下面透出来；[backdrop] 是页面录制的模糊图层。
 */
@Composable
fun FrostedBottomBar(
    tab: NextTab,
    onTab: (NextTab) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null,
) {
    val shape = RoundedCornerShape(30.dp)
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { origin = it.positionInRoot() }
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .height(74.dp)
                .drawBehind {
                    val layer = backdrop ?: return@drawBehind
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(0f, 0f, size.width, size.height, CornerRadius(30.dp.toPx())),
                        )
                    }
                    clipPath(path) {
                        translate(left = -origin.x, top = -origin.y) {
                            drawLayer(layer)
                        }
                    }
                }
                .clip(shape)
                .background(Color(0xFF101014).copy(alpha = 0.40f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabSlot(NextTab.Home, tab, onTab)
            TabSlot(NextTab.Explore, tab, onTab)
            CreateSlot(onCreate)
            TabSlot(NextTab.Vocabulary, tab, onTab)
            TabSlot(NextTab.Playlist, tab, onTab)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabSlot(
    target: NextTab,
    current: NextTab,
    onTab: (NextTab) -> Unit,
) {
    val active = target == current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "tab-press")
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null) { onTab(target) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) LqColors.blueDeep else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = target.icon,
                contentDescription = target.label,
                tint = when {
                    active -> LqColors.blue
                    pressed -> Color.White
                    else -> LqColors.text
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CreateSlot(onCreate: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "create-press")
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(48.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onCreate),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LqIcons.Plus,
                contentDescription = "Create",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 主按钮：LingQ 蓝、白色文字、胶囊。 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(if (enabled) LqColors.blueStrong else LqColors.surfaceHigh)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .graphicsLayer { alpha = if (pressed) 0.86f else 1f },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = label,
                color = if (enabled) Color.White else LqColors.muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(if (pressed) LqColors.surfaceHigh else LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.pill))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = LqSpace.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = LqColors.text, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(LqSpace.sm))
        }
        Text(label, color = LqColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LqIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = LqColors.text,
    background: Color = LqColors.surface,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) LqColors.surfaceHigh else background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun LqSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = LqColors.textStrong,
            fontSize = LqType.section,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(LqRadius.pill))
                    .clickable(onClick = onAction)
                    .padding(horizontal = LqSpace.md, vertical = LqSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, color = LqColors.blue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Icon(
                    LqIcons.ChevronRight,
                    contentDescription = null,
                    tint = LqColors.blue,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * 课程等级徽章。LingQ 用 1–6 级，这里按 Beginner / Intermediate / Advanced
 * 三档着色，与筛选器文案一致。
 */
@Composable
fun LqLevelBadge(level: Int?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val (tint, background) = when {
        level == null -> LqColors.muted to LqColors.surfaceHigh
        level <= 2 -> LqColors.blue to LqColors.blueSoft
        level <= 4 -> LqColors.yellow to LqColors.yellowSoft
        else -> LqColors.red to LqColors.red.copy(alpha = 0.14f)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(background)
            .padding(
                horizontal = if (compact) LqSpace.sm else LqSpace.md,
                vertical = if (compact) 1.dp else 3.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (level == null) "—" else level.toString(),
            color = tint,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
        )
        if (!compact) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = LessonLevelLabel(level),
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun LessonLevelLabel(level: Int?): String = when {
    level == null -> ""
    level <= 2 -> "Beg."
    level <= 4 -> "Int."
    else -> "Adv."
}

@Composable
fun LqProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    brush: Brush = Brush.horizontalGradient(listOf(LqColors.greenStrong, LqColors.blue)),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(LqColors.surfaceHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(LqRadius.pill))
                .background(brush),
        )
    }
}

@Composable
fun LqDot(color: Color, size: Dp = 6.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun LqChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(if (selected) LqColors.textStrong else LqColors.surface)
            .border(
                1.dp,
                if (selected) Color.Transparent else LqColors.border,
                RoundedCornerShape(LqRadius.pill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = LqSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(LqSpace.xs))
        }
        Text(
            text = label,
            color = if (selected) Color(0xFF111113) else LqColors.text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun LqEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = LqIcons.BookOpen,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LqSpace.xxxl, horizontal = LqSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(LqColors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = LqColors.muted, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(LqSpace.lg))
        Text(
            text = title,
            color = LqColors.textStrong,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(LqSpace.xs))
        Text(
            text = message,
            color = LqColors.muted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(LqSpace.lg))
            SecondaryButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LqErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LqSpace.xxxl, horizontal = LqSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = LqColors.muted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(LqSpace.lg))
        SecondaryButton(label = "Try again", onClick = onRetry, icon = LqIcons.Refresh)
    }
}

@Composable
fun LqLoading(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(LqSpace.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = LqColors.blue,
            strokeWidth = 2.dp,
            modifier = Modifier.size(26.dp),
        )
        if (label != null) {
            Spacer(Modifier.height(LqSpace.md))
            Text(label, color = LqColors.muted, fontSize = 12.sp)
        }
    }
}

/** 课程封面。图片加载失败时用等级色渐变兜底，不留白块。 */
@Composable
fun LessonCoverFallback(
    level: Int?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = when {
        level == null -> listOf(LqColors.surfaceHigh, LqColors.surface)
        level <= 2 -> listOf(Color(0xFF274A73), Color(0xFF16283F))
        level <= 4 -> listOf(Color(0xFF5A4A20), Color(0xFF2E2717))
        else -> listOf(Color(0xFF5A2A28), Color(0xFF2E1B1A))
    }
    Box(
        modifier = modifier.background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().take(1).uppercase(),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun DurationLabel(
    seconds: Int?,
    modifier: Modifier = Modifier,
    color: Color = LqColors.muted,
) {
    if (seconds == null || seconds <= 0) return
    val minutes = seconds / 60
    val text = if (minutes >= 60) {
        "${minutes / 60}h ${minutes % 60}m"
    } else {
        "${minutes.coerceAtLeast(1)}m"
    }
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** 胶囊搜索框：Explore 与 Vocabulary 共用。 */
@Composable
fun LqSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.pill))
            .padding(horizontal = LqSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LqIcons.Search, contentDescription = null, tint = LqColors.muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(LqSpace.sm))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = LqColors.text, fontSize = 15.sp),
            cursorBrush = SolidColor(LqColors.blue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onSubmit()
                },
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = LqColors.muted, fontSize = 15.sp)
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(LqColors.surfaceHigh)
                    .clickable {
                        onValueChange("")
                        onSubmit()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(LqIcons.X, contentDescription = "Clear", tint = LqColors.muted, modifier = Modifier.size(13.dp))
            }
        }
    }
}
