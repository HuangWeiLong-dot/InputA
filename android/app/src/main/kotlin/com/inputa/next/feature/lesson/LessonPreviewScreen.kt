package com.inputa.next.feature.lesson

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.CurrentLesson
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqDot
import com.inputa.next.core.design.LqErrorState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLevelBadge
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.PrimaryButton
import com.inputa.next.core.design.RawText
import com.inputa.next.core.design.RemoteImage
import com.inputa.next.core.design.translate

/** 课程预览：封面、标题、词数统计、Start Lesson。 */
@Composable
fun LessonPreviewScreen(
    onBack: () -> Unit,
    onStart: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LessonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lesson: LessonSummary? = state.detail?.lesson ?: state.selected
    val detail = state.detail
    val context = LocalContext.current
    val appLanguage = com.inputa.next.core.design.LocalAppLanguage.current

    LaunchedEffect(state.notice) {
        val message = state.notice
        if (message != null) {
            Toast.makeText(context, appLanguage.translate(message), Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(LqColors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                RemoteImage(
                    url = lesson?.imageUrl,
                    title = lesson?.title.orEmpty(),
                    level = lesson?.level,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    LqColors.bg,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(LqSpace.lg)
                        .shadow(8.dp, CircleShape)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LqIcons.ArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = LqSpace.xl)
                        .padding(bottom = LqSpace.md),
                ) {
                    LqLevelBadge(level = lesson?.level)
                    Spacer(Modifier.height(LqSpace.sm))
                    RawText(
                        text = lesson?.title.orEmpty(),
                        color = LqColors.textStrong,
                        fontSize = 26.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!lesson?.collectionTitle.isNullOrBlank()) {
                        Spacer(Modifier.height(LqSpace.xs))
                        RawText(
                            text = lesson?.collectionTitle.orEmpty(),
                            color = LqColors.muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = LqSpace.xl)) {
                Spacer(Modifier.height(LqSpace.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lesson?.durationSec != null) {
                        MetaItem(LqIcons.Clock, "${(lesson.durationSec / 60).coerceAtLeast(1)} min")
                    }
                    if (lesson?.newWordsCount != null) {
                        if (lesson.durationSec != null) MetaSeparator()
                        MetaItem(LqIcons.Sparkles, "${lesson.newWordsCount} new words", LqColors.blue)
                    }
                    if (lesson?.isCompleted == true) {
                        MetaSeparator()
                        MetaItem(LqIcons.Check, "Completed", LqColors.green)
                    }
                }

                Spacer(Modifier.height(LqSpace.lg))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LqRadius.card))
                        .background(LqColors.surface)
                        .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.card))
                        .padding(vertical = LqSpace.lg),
                ) {
                    WordStat(
                        label = "Total Words",
                        value = detail?.totalWords,
                        dot = LqColors.textStrong,
                        modifier = Modifier.weight(1f),
                    )
                    StatDivider()
                    WordStat(
                        label = "Unique Words",
                        value = detail?.uniqueWords,
                        dot = LqColors.yellow,
                        modifier = Modifier.weight(1f),
                    )
                    StatDivider()
                    WordStat(
                        label = "New Words",
                        value = detail?.newWords ?: lesson?.newWordsCount,
                        dot = LqColors.blue,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(LqSpace.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LqSpace.sm),
                ) {
                    LessonAction(
                        icon = LqIcons.Bookmark,
                        label = if (state.bookmarked) "Saved" else "Bookmark",
                        active = state.bookmarked,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::toggleBookmark,
                    )
                    LessonAction(
                        icon = LqIcons.Plus,
                        label = if (state.inPlaylist) "In Playlist" else "Playlist",
                        active = state.inPlaylist,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::addToPlaylist,
                    )
                    LessonAction(
                        icon = LqIcons.Check,
                        label = "Complete",
                        active = lesson?.isCompleted == true,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::markComplete,
                    )
                    LessonAction(
                        icon = LqIcons.Cards,
                        label = "Review",
                        active = false,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenReview,
                    )
                }

                detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(LqSpace.xl))
                    Text(
                        text = "Lesson Description",
                        color = LqColors.textStrong,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(LqSpace.xs))
                    RawText(
                        text = description,
                        color = LqColors.muted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }

                Spacer(Modifier.height(LqSpace.xl))
                Text(
                    text = "Study Tools",
                    color = LqColors.textStrong,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(LqSpace.sm))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LqRadius.card))
                        .background(LqColors.surface),
                ) {
                    StudyToolRow(
                        icon = LqIcons.Cards,
                        title = "Review Inputas",
                        subtitle = "Flip through the words you are learning",
                        onClick = onOpenReview,
                    )
                    InfoDivider()
                    StudyToolRow(
                        icon = LqIcons.Bot,
                        title = "AI Tutor",
                        subtitle = "Ask about this lesson or its words",
                        onClick = onOpenChat,
                    )
                }

                Spacer(Modifier.height(LqSpace.xl))
                Text(
                    text = "Lesson Info",
                    color = LqColors.textStrong,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(LqSpace.sm))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LqRadius.card))
                        .background(LqColors.surface),
                ) {
                    InfoRow("Level", LessonSummary.levelLabel(lesson?.level))
                    InfoDivider()
                    InfoRow(
                        "Duration",
                        lesson?.durationSec?.let { "${(it / 60).coerceAtLeast(1)} min" } ?: "—",
                    )
                    InfoDivider()
                    InfoRow("Collection", lesson?.collectionTitle?.takeIf { it.isNotBlank() } ?: "—", rawValue = true)
                    InfoDivider()
                    InfoRow(
                        "Status",
                        when {
                            lesson?.isCompleted == true -> "Completed"
                            state.bookmarked -> "Saved for later"
                            else -> "Not started"
                        },
                    )
                }

                if (state.error != null) {
                    Spacer(Modifier.height(LqSpace.lg))
                    LqErrorState(message = state.error.orEmpty(), onRetry = viewModel::load)
                }
                Spacer(Modifier.height(LqSpace.xl))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, LqColors.bg.copy(alpha = 0.92f), LqColors.bg),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = LqSpace.xl, vertical = LqSpace.lg),
        ) {
            PrimaryButton(
                label = if (lesson?.isCompleted == true) "Read Again" else "Start Lesson",
                onClick = onStart,
            )
        }
    }
}

@Composable
private fun StudyToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = LqSpace.lg, vertical = LqSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(LqColors.blueSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = LqColors.blue, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(LqSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = LqColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = LqColors.muted, fontSize = 11.sp)
        }
        Icon(LqIcons.ChevronRight, contentDescription = null, tint = LqColors.faint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String, rawValue: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LqSpace.lg, vertical = LqSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = LqColors.muted,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(LqSpace.md))
        if (rawValue) {
            RawText(
                text = value,
                color = LqColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = value,
                color = LqColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfoDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LqSpace.lg)
            .height(1.dp)
            .background(LqColors.border),
    )
}

@Composable
private fun MetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = LqColors.muted,
) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
    Text(label, color = tint, fontSize = 12.sp)
}

@Composable
private fun MetaSeparator() {
    Box(
        modifier = Modifier
            .padding(horizontal = LqSpace.md)
            .size(3.dp)
            .clip(CircleShape)
            .background(LqColors.faint),
    )
}

@Composable
private fun WordStat(
    label: String,
    value: Int?,
    dot: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LqDot(dot, 5.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = value?.toString() ?: "—",
                color = LqColors.textStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = LqColors.muted, fontSize = 11.sp)
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(LqColors.border),
    )
}

@Composable
private fun LessonAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) LqColors.blueSoft else LqColors.surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) LqColors.blue else LqColors.text,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (active) LqColors.blue else LqColors.muted,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}
