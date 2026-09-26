package com.inputa.reader.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.inputa.reader.ui.icons.InputaIcons
import com.inputa.reader.ui.theme.LocalThemeTokens

/**
 * 应用内唯一的图标出口。
 *
 * **颜色永远来自 [LocalThemeTokens]**，默认取正文色；强调、危险、次要各有其色，由调用点
 * 显式传。这样「某个图标是什么颜色」只有一个答案的来源，而 [ImageVector] 里那份描边色
 * 永远被忽略（`Icon` 用 `ColorFilter` 上色）。
 *
 * `contentDescription` 是必填的：图标按钮没有文字，读屏软件只能靠它。确实纯装饰、
 * 旁边已有文字的图标传 `null`。
 */
@Composable
fun AppIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalThemeTokens.current.textMain,
    size: Dp = InputaIcons.defaultSize,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
