package com.inputa.next.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqEmptyState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqSpace
import com.inputa.next.feature.common.LessonListRow

/** 全部本机书签课程；点右侧书签可直接移除。 */
@Composable
fun SavedScreen(
    onBack: () -> Unit,
    onOpenLesson: (LessonSummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LqSpace.xl, vertical = LqSpace.md),
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
            Text(
                text = "Saved Lessons",
                color = LqColors.textStrong,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.lessons.size}",
                color = LqColors.muted,
                fontSize = 13.sp,
            )
        }

        if (state.lessons.isEmpty()) {
            LqEmptyState(
                title = "No saved lessons",
                message = "Open a lesson and tap Bookmark to keep it here.",
                icon = LqIcons.Bookmark,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = LqSpace.xl,
                    end = LqSpace.xl,
                    bottom = LqSpace.xxxl,
                ),
            ) {
                items(state.lessons, key = { it.id }) { lesson ->
                    LessonListRow(
                        lesson = lesson,
                        onClick = { onOpenLesson(lesson) },
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.remove(lesson) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    LqIcons.Bookmarked,
                                    contentDescription = "Remove bookmark",
                                    tint = LqColors.blue,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
