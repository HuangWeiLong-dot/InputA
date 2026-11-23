package com.inputa.next.core.design

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LingQ 7 深色令牌。只做深色，避免两套色板互相污染。
 *
 * 底色/卡片来自截图，品牌色取自 APK 资源表：
 * blue #2E75CD、green #02A622、indigo #091A2F。
 */
object LqColors {
    val bg = Color(0xFF111113)
    val bgSoft = Color(0xFF17171A)
    val surface = Color(0xFF1C1C1E)
    val surfaceHigh = Color(0xFF2C2C2E)
    val border = Color(0xFF2E2E32)
    val scrim = Color(0xCC000000)

    val textStrong = Color(0xFFFFFFFF)
    val text = Color(0xFFF2F2F7)
    val muted = Color(0xFFA1A1A8)
    val faint = Color(0xFF6E6E76)

    val blue = Color(0xFF4D9BFF)
    val blueStrong = Color(0xFF2E75CD)
    val blueDeep = Color(0xFF1B4B8F)
    val blueSoft = Color(0xFF14263C)

    val green = Color(0xFF33C759)
    val greenStrong = Color(0xFF02A622)
    val greenSoft = Color(0xFF12301C)

    val yellow = Color(0xFFF5C451)
    val yellowSoft = Color(0xFF392E15)

    val orange = Color(0xFFFF9F0A)
    val red = Color(0xFFFF453A)
    val purple = Color(0xFFB08CFF)

    /** Known Words 蓝青渐变。 */
    val knownStart = Color(0xFF2E75CD)
    val knownEnd = Color(0xFF19A974)
}

object LqSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 6.dp
    val md = 10.dp
    val lg = 14.dp
    val xl = 18.dp
    val xxl = 22.dp
    val xxxl = 30.dp
}

object LqRadius {
    val chip = 10.dp
    val control = 14.dp
    val cover = 14.dp
    val card = 20.dp
    val sheet = 26.dp
    val pill = 999.dp
}

/** 界面字号。阅读器正文另有自己的排版。 */
object LqType {
    val micro = 10.sp
    val label = 11.sp
    val caption = 12.sp
    val body = 14.sp
    val bodyLarge = 15.sp
    val title = 17.sp
    val section = 19.sp
    val headline = 24.sp
    val hero = 30.sp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LqColors.blue,
            onPrimary = Color(0xFF08131F),
            secondary = LqColors.green,
            background = LqColors.bg,
            onBackground = LqColors.text,
            surface = LqColors.surface,
            onSurface = LqColors.text,
            surfaceVariant = LqColors.surfaceHigh,
            onSurfaceVariant = LqColors.muted,
            outline = LqColors.border,
            error = LqColors.red,
        ),
        typography = lingqTypography(),
    ) {
        // 放在 MaterialTheme 内层，确保覆盖它自带的 ripple 指示。
        CompositionLocalProvider(
            LocalIndication provides EmptyIndication,
            LocalRippleConfiguration provides null,
        ) {
            content()
        }
    }
}

/** 取消默认的点击涟漪；按钮是否做按下反馈由各组件自己决定。 */
private class NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other is NoIndication
    override fun hashCode(): Int = 0
}

private val EmptyIndication = NoIndication()

private fun lingqTypography(): Typography {
    val base = Typography()
    fun style(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
        letterSpacing: Double = 0.0,
    ) = TextStyle(
        fontSize = size.sp,
        lineHeight = (size * 1.28).sp,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
    )
    return base.copy(
        displayLarge = style(34, FontWeight.Bold, -0.6),
        headlineLarge = style(28, FontWeight.Bold, -0.4),
        headlineMedium = style(24, FontWeight.Bold, -0.3),
        titleLarge = style(19, FontWeight.SemiBold, -0.2),
        titleMedium = style(16, FontWeight.SemiBold),
        bodyLarge = style(15),
        bodyMedium = style(14),
        bodySmall = style(12),
        labelLarge = style(14, FontWeight.SemiBold),
        labelMedium = style(12, FontWeight.Medium),
        labelSmall = style(10, FontWeight.Medium, 0.4),
    )
}
