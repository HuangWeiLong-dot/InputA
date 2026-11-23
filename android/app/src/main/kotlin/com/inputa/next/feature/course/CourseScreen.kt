package com.inputa.next.feature.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.design.DurationLabel
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqEmptyState
import com.inputa.next.core.design.LqErrorState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLevelBadge
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.RawText
import com.inputa.next.core.design.PrimaryButton
import com.inputa.next.core.design.RemoteImage

/** 课程/合集详情：封面 + 简介 + 每集列表。 */
@Composable
fun CourseScreen(
    onBack: () -> Unit,
    onOpenLesson: (LessonSummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CourseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.detail
    val course = state.course
    val lessons = detail?.lessons.orEmpty()
    val startLesson = lessons.firstOrNull { !it.isCompleted } ?: lessons.firstOrNull()
    val totalMinutes = lessons.sumOf { it.durationSec ?: 0 } / 60

    Box(modifier = modifier.fillMaxSize().background(LqColors.bg)) {
        when {
            state.loading -> Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                CourseTopBar(onBack = onBack)
                LqLoading(label = "Loading course…")
            }

            state.error != null && detail == null -> Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            ) {
                CourseTopBar(onBack = onBack)
                LqErrorState(message = state.error.orEmpty(), onRetry = viewModel::load)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item {
                    CourseHero(
                        title = detail?.title ?: course?.title ?: "Course",
                        imageUrl = detail?.imageUrl ?: course?.imageUrl,
                        level = detail?.level ?: course?.level,
                        lessonCount = lessons.size,
                        totalMinutes = totalMinutes,
                        onBack = onBack,
                    )
                }
                item {
                    detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
                        RawText(
                            text = description,
                            color = LqColors.muted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(horizontal = LqSpace.xl, vertical = LqSpace.sm),
                        )
                    }
                    if (startLesson != null) {
                        PrimaryButton(
                            label = if (lessons.any { it.isCompleted }) "Continue Course" else "Start Course",
                            onClick = { onOpenLesson(startLesson) },
                            modifier = Modifier.padding(horizontal = LqSpace.xl, vertical = LqSpace.md),
                        )
                    }
                    Text(
                        text = "Lessons (${lessons.size})",
                        color = LqColors.textStrong,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = LqSpace.xl,
                            end = LqSpace.xl,
                            top = LqSpace.lg,
                            bottom = LqSpace.xs,
                        ),
                    )
                }
                if (lessons.isEmpty()) {
                    item {
                        LqEmptyState(
                            title = "No lessons returned",
                            message = "LingQ didn't return the lessons for this course yet.",
                            icon = LqIcons.BookOpen,
                            actionLabel = "Retry",
                            onAction = viewModel::load,
                        )
                    }
                } else {
                    itemsIndexed(lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
                        CourseLessonRow(
                            index = index,
                            lesson = lesson,
                            onClick = { onOpenLesson(lesson) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(LqColors.surface)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LqIcons.ArrowLeft, contentDescription = "Back", tint = LqColors.text, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(LqSpace.md))
        Text("Course", color = LqColors.textStrong, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CourseHero(
    title: String,
    imageUrl: String?,
    level: Int?,
    lessonCount: Int,
    totalMinutes: Int,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        RemoteImage(
            url = imageUrl,
            title = title,
            level = level,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            LqColors.bg.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(LqSpace.lg)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
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
            LqLevelBadge(level = level)
            Spacer(Modifier.height(LqSpace.sm))
            Text(
                text = title,
                color = LqColors.textStrong,
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(LqSpace.xs))
            Text(
                text = buildString {
                    append("$lessonCount lessons")
                    if (totalMinutes > 0) {
                        append(" · ")
                        append("$totalMinutes min")
                    }
                },
                color = LqColors.muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CourseLessonRow(
    index: Int,
    lesson: LessonSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = LqSpace.xl, vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(LqColors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                color = LqColors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(LqSpace.sm))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(LqRadius.cover)),
        ) {
            RemoteImage(
                url = lesson.imageUrl,
                title = lesson.title,
                level = lesson.level,
                modifier = Modifier.fillMaxSize(),
            )
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
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lesson.durationSec != null) {
                    DurationLabel(lesson.durationSec)
                }
                lesson.newWordsCount?.let {
                    Spacer(Modifier.width(LqSpace.sm))
                    Text("$it new", color = LqColors.blue, fontSize = 11.sp)
                }
                if (lesson.isCourse) {
                    Spacer(Modifier.width(LqSpace.sm))
                    Text("${lesson.lessonCount ?: 0} lessons", color = LqColors.faint, fontSize = 11.sp)
                }
            }
        }
        if (lesson.isCompleted) {
            Icon(LqIcons.Check, contentDescription = "Completed", tint = LqColors.green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(LqSpace.xs))
        }
        Icon(LqIcons.ChevronRight, contentDescription = null, tint = LqColors.faint, modifier = Modifier.size(18.dp))
    }
}
