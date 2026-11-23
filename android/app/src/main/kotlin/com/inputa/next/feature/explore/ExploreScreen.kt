package com.inputa.next.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.LessonSort
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.ShelfSection
import com.inputa.next.core.data.lessonLevelLabel
import com.inputa.next.core.design.DurationLabel
import com.inputa.next.core.design.LqChip
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqDot
import com.inputa.next.core.design.LqEmptyState
import com.inputa.next.core.design.LqErrorState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLevelBadge
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSearchField
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.LqType
import com.inputa.next.core.design.RemoteImage

/** 资料库：搜索 + 等级/来源筛选 + 书架分组卡片；搜索时切换成结果网格。 */
@Composable
fun ExploreScreen(
    onOpenLesson: (LessonSummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lessonSort by viewModel.lessonSort.collectAsStateWithLifecycle()
    var levelMenu by remember { mutableStateOf(false) }
    var expandedSections by remember(state.shelves) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = LqSpace.xl, end = LqSpace.md, top = LqSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Explore",
                color = LqColors.textStrong,
                fontSize = LqType.headline,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(LqColors.surface)
                    .clickable { viewModel.refresh() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    LqIcons.Refresh,
                    contentDescription = "Refresh",
                    tint = LqColors.text,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(LqSpace.md))
        LqSearchField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = "What interests you?",
            onSubmit = viewModel::refresh,
            modifier = Modifier.padding(horizontal = LqSpace.xl),
        )
        Spacer(Modifier.height(LqSpace.md))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LqSpace.sm),
            contentPadding = PaddingValues(horizontal = LqSpace.xl),
        ) {
            item {
                Box {
                    ExploreFilterChip(
                        label = lessonLevelLabel(state.level),
                        active = state.level != null,
                        onClick = { levelMenu = true },
                    )
                    DropdownMenu(
                        expanded = levelMenu,
                        onDismissRequest = { levelMenu = false },
                        containerColor = LqColors.surfaceHigh,
                    ) {
                        LevelOption("All levels", state.level == null) {
                            levelMenu = false
                            viewModel.setLevel(null)
                        }
                        (1..6).forEach { level ->
                            LevelOption(lessonLevelLabel(level), state.level == level) {
                                levelMenu = false
                                viewModel.setLevel(level)
                            }
                        }
                    }
                }
            }
            item {
                LqChip(
                    label = "All resources",
                    selected = state.providerId == null,
                    onClick = { viewModel.setProvider(null) },
                )
            }
            item {
                LqChip(
                    label = if (lessonSort == LessonSort.Ascending) "1→9" else "9→1",
                    selected = false,
                    onClick = viewModel::toggleLessonSort,
                )
            }
            items(state.providers, key = { it.id }) { provider ->
                LqChip(
                    label = provider.title,
                    selected = state.providerId == provider.id,
                    onClick = { viewModel.setProvider(provider.id) },
                )
            }
        }
        Spacer(Modifier.height(LqSpace.sm))

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loading && !state.hasLoaded -> LqLoading(
                    modifier = Modifier.align(Alignment.TopCenter),
                    label = "Loading library…",
                )

                state.loading && !state.showingShelves && state.lessons.isEmpty() -> LqLoading(
                    modifier = Modifier.align(Alignment.TopCenter),
                    label = "Searching…",
                )

                state.error != null && state.shelves.isEmpty() && state.lessons.isEmpty() -> LqErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::refresh,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                state.showingShelves && state.shelves.isEmpty() -> LqEmptyState(
                    title = "We could not find any lessons!",
                    message = "Try another level or keyword.",
                    icon = LqIcons.BookOpen,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                !state.showingShelves && state.lessons.isEmpty() -> LqEmptyState(
                    title = "No results",
                    message = if (state.providerId != null) {
                        "No lessons from this source yet. Try another source or level."
                    } else {
                        "Try another keyword or choose a different level."
                    },
                    icon = LqIcons.Search,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                state.showingShelves -> ShelvesContent(
                    shelves = state.shelves,
                    expanded = expandedSections,
                    onToggleSection = { title ->
                        expandedSections = if (title in expandedSections) {
                            expandedSections - title
                        } else {
                            expandedSections + title
                        }
                    },
                    onOpenLesson = onOpenLesson,
                )

                else -> ResultsContent(
                    lessons = state.lessons,
                    onOpenLesson = onOpenLesson,
                )
            }
        }
    }
}

@Composable
private fun ShelvesContent(
    shelves: List<ShelfSection>,
    expanded: Set<String>,
    onToggleSection: (String) -> Unit,
    onOpenLesson: (LessonSummary) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = LqSpace.xl,
            end = LqSpace.xl,
            bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(LqSpace.lg),
    ) {
        items(shelves, key = { it.title }) { shelf ->
            SectionBlock(
                shelf = shelf,
                expanded = shelf.title in expanded,
                onToggle = { onToggleSection(shelf.title) },
                onOpenLesson = onOpenLesson,
            )
        }
    }
}

@Composable
private fun SectionBlock(
    shelf: ShelfSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenLesson: (LessonSummary) -> Unit,
) {
    val visible = if (expanded) shelf.lessons else shelf.lessons.take(SECTION_PREVIEW)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                LqIcons.List,
                contentDescription = null,
                tint = LqColors.muted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(LqSpace.sm))
            Text(
                text = shelf.title,
                color = LqColors.textStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (shelf.lessons.size > SECTION_PREVIEW) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .clickable(onClick = onToggle)
                        .padding(start = LqSpace.sm, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "Show less" else "See all",
                        color = LqColors.blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        LqIcons.ChevronRight,
                        contentDescription = null,
                        tint = LqColors.blue,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(LqSpace.sm))
        LessonCardGrid(lessons = visible, onOpenLesson = onOpenLesson)
    }
}

@Composable
private fun ResultsContent(
    lessons: List<LessonSummary>,
    onOpenLesson: (LessonSummary) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = LqSpace.xl,
            end = LqSpace.xl,
            top = LqSpace.xs,
            bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(LqSpace.md),
    ) {
        item {
            Text(
                text = "${lessons.size} lessons",
                color = LqColors.faint,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = LqSpace.xs),
            )
        }
        items(lessons.chunked(2), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
                row.forEach { lesson ->
                    ExploreLessonCard(
                        lesson = lesson,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenLesson(lesson) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LessonCardGrid(
    lessons: List<LessonSummary>,
    onOpenLesson: (LessonSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LqSpace.md)) {
        lessons.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
                row.forEach { lesson ->
                    ExploreLessonCard(
                        lesson = lesson,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenLesson(lesson) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExploreLessonCard(
    lesson: LessonSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .graphicsLayer { alpha = if (pressed) 0.86f else 1f }
            .clip(RoundedCornerShape(LqRadius.card))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(LqRadius.card)),
        ) {
            RemoteImage(
                url = lesson.imageUrl,
                title = lesson.title,
                level = lesson.level,
                modifier = Modifier.fillMaxSize(),
            )
            LqLevelBadge(
                level = lesson.level,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                compact = true,
            )
            if (lesson.durationSec != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "${(lesson.durationSec / 60).coerceAtLeast(1)}m",
                        color = Color.White,
                        fontSize = 9.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(LqSpace.sm))
        Text(
            text = lesson.title,
            color = LqColors.text,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 36.dp),
        )
        if (!lesson.collectionTitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = lesson.collectionTitle.orEmpty(),
                color = LqColors.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(LqSpace.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LqLevelBadge(level = lesson.level, compact = true)
            lesson.newWordsCount?.let {
                Spacer(Modifier.width(LqSpace.xs))
                LqDot(LqColors.blue, 5.dp)
                Spacer(Modifier.width(3.dp))
                Text("$it new", color = LqColors.muted, fontSize = 10.sp, maxLines = 1)
            }
            if (lesson.durationSec != null) {
                Spacer(Modifier.width(LqSpace.xs))
                Icon(
                    LqIcons.Clock,
                    contentDescription = null,
                    tint = LqColors.faint,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.width(2.dp))
                DurationLabel(lesson.durationSec, Modifier)
            }
        }
    }
}

/** 等级筛选胶囊：左侧漏斗图标 + 当前等级文案。 */
@Composable
private fun ExploreFilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(if (active) LqColors.textStrong else LqColors.surface)
            .border(
                1.dp,
                if (active) Color.Transparent else LqColors.border,
                RoundedCornerShape(LqRadius.pill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = LqSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            LqIcons.Filter,
            contentDescription = null,
            tint = if (active) Color(0xFF111113) else LqColors.text,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(LqSpace.xs))
        Text(
            text = label,
            color = if (active) Color(0xFF111113) else LqColors.text,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun LevelOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (selected) LqColors.blue else LqColors.text,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        onClick = onClick,
    )
}

private const val SECTION_PREVIEW = 4
