package com.inputa.reader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.inputa.reader.ui.icons.InputaIcons
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale

/**
 * 首屏 / 换书时的加载动画：一本「呼吸」的书，加一行说明。
 *
 * **为什么不是转圈的进度条。** 这是一个阅读器，等待时该传达的是「安静地准备好了」，
 * 而不是「正在努力加载」。所以用应用自己的那本书（[InputaIcons.BookOpen]，与书架入口
 * 同一个字形），做很轻的缩放 + 透明度脉动 —— 与冷启动画面里那个动画
 * （`animator/splash_breathe.xml`）是同一个动作，于是从系统启动画面到应用内是连着的，
 * 不会「换了一种等待的样子」。
 *
 * 时长 700ms、来回无限、`FastOutSlowInEasing`：与启动画面的 700ms 对齐。
 *
 * 用 `graphicsLayer` 而不是 `Modifier.scale()`：只是把已经画好的东西缩放，
 * 不必重新布局，也不必重新绘制字形。动画本身走 Compose 的 `InfiniteTransition`，
 * 与窗口的帧同步。
 */
@Composable
fun LoadingIndicator(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalThemeTokens.current
    val pulse = rememberInfiniteTransition(label = "loading")

    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BreathingMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    // 缩放之外再叠一点透明度：只缩放的话，在静止的那一瞬间看起来像卡住了。
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BreathingMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(
            icon = InputaIcons.BookOpen,
            contentDescription = null,
            tint = tokens.textMuted,
            size = 40.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        )
        Spacer(Modifier.height(Space.xl))
        Text(text = text, color = tokens.textMuted, fontSize = TypeScale.bodyLg)
    }
}

/**
 * 一次「呼吸」的时长。与 `animator/splash_breathe.xml` 里的 700ms 是同一个数 ——
 * 两边不一致的话，从启动画面切进应用时节奏会突然变一下。
 */
private const val BreathingMillis = 700
