package com.inputa.next.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.design.DurationLabel
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqDot
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLevelBadge
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.RawText
import com.inputa.next.core.design.RemoteImage

/** 资料库/搜索结果里的一行课程。 */
@Composable
fun LessonListRow(
    lesson: LessonSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (pressed) 0.86f else 1f }
            .clip(RoundedCornerShape(LqRadius.control))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(LqRadius.cover)),
        ) {
            RemoteImage(
                url = lesson.imageUrl,
                title = lesson.title,
                level = lesson.level,
                modifier = Modifier.fillMaxSize(),
            )
            if (lesson.durationSec != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "${(lesson.durationSec / 60).coerceAtLeast(1)}m",
                        color = Color.White,
                        fontSize = 9.sp,
                    )
                }
            }
        }
        Spacer(Modifier.width(LqSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            RawText(
                text = lesson.title,
                color = LqColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!lesson.collectionTitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                RawText(
                    text = lesson.collectionTitle,
                    color = LqColors.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(LqSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LqLevelBadge(level = lesson.level, compact = true)
                lesson.newWordsCount?.let {
                    Spacer(Modifier.width(LqSpace.sm))
                    LqDot(LqColors.blue, 5.dp)
                    Spacer(Modifier.width(3.dp))
                    Text("$it new", color = LqColors.muted, fontSize = 11.sp)
                }
                if (lesson.durationSec != null) {
                    Spacer(Modifier.width(LqSpace.sm))
                    Icon(LqIcons.Clock, contentDescription = null, tint = LqColors.faint, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    DurationLabel(lesson.durationSec, Modifier)
                }
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                LqIcons.ChevronRight,
                contentDescription = null,
                tint = LqColors.faint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
