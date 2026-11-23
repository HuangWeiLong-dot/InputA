package com.inputa.next.feature.home

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.LearningLanguage
import com.inputa.next.core.data.LessonSort
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.flagForLanguage
import com.inputa.next.core.design.AvatarImage
import com.inputa.next.core.design.DurationLabel
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqDot
import com.inputa.next.core.design.LqErrorState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLevelBadge
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqProgressBar
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSectionHeader
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.LqType
import com.inputa.next.core.design.RawText
import com.inputa.next.core.design.RemoteImage
import com.inputa.next.feature.common.ProfileSheet

/**
 * LingQ 7 首页：头像/语言 → Known Words 渐变带 → Continue Studying → Picked For You。
 * 所有数字来自真实接口；拿不到就显示「—」，不写假数据。
 */
@Composable
fun HomeScreen(
    onOpenLesson: (LessonSummary) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onExplore: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lessonSort by viewModel.lessonSort.collectAsStateWithLifecycle()
    var showProfile by rememberSaveable { mutableStateOf(false) }

    // 从阅读器返回时重新取一次，Known Words/LingQs 会立即反映刚做的词状态变更。
    LaunchedEffect(Unit) {
        val hasData = state.continueLessons.isNotEmpty() || state.picked.isNotEmpty()
        viewModel.load(refresh = hasData)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = LqSpace.xl, end = LqSpace.xl, top = LqSpace.md, bottom = 120.dp),
        ) {
        item {
            HomeHeader(
                username = state.username,
                avatarPath = state.avatarPath,
                languageName = state.languageName,
                languageCode = state.languageCode,
                languageLevel = state.languageLevel,
                onOpenProfile = { showProfile = true },
                onOpenSettings = onOpenSettings,
                onOpenLanguages = onOpenLanguages,
            )
            Spacer(Modifier.height(LqSpace.xl))
            KnownWordsCard(
                knownCount = state.knownCount,
                inputaCount = state.inputaCount,
                ignoredCount = state.ignoredCount,
                onOpenStats = onOpenStats,
            )
            state.lastLesson?.let { lesson ->
                Spacer(Modifier.height(LqSpace.md))
                LastLessonCard(lesson = lesson, onClick = { onOpenLesson(lesson) })
            }
            Spacer(Modifier.height(LqSpace.xxl))
        }

        if (state.loading) {
            item { LqLoading(label = "Loading your lessons…") }
        }

        if (state.error != null && state.continueLessons.isEmpty() && state.picked.isEmpty()) {
            item {
                LqErrorState(message = state.error.orEmpty(), onRetry = { viewModel.load() })
            }
        }

        if (state.continueLessons.isNotEmpty()) {
            item {
                LqSectionHeader(
                    title = "Continue Studying",
                    action = "View All",
                    onAction = onExplore,
                )
                Spacer(Modifier.height(LqSpace.md))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(LqSpace.md),
                    contentPadding = PaddingValues(end = LqSpace.xl),
                ) {
                    items(state.continueLessons, key = { it.id }) { lesson ->
                        ContinueCard(lesson = lesson, onClick = { onOpenLesson(lesson) })
                    }
                }
                Spacer(Modifier.height(LqSpace.xxl))
            }
        }

        if (state.savedLessons.isNotEmpty()) {
            item {
                LqSectionHeader(
                    title = "Saved Lessons",
                    action = "View All",
                    onAction = onOpenSaved,
                )
                Spacer(Modifier.height(LqSpace.md))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(LqSpace.md),
                    contentPadding = PaddingValues(end = LqSpace.xl),
                ) {
                    items(state.savedLessons, key = { "saved-${it.id}" }) { lesson ->
                        SavedCard(lesson = lesson, onClick = { onOpenLesson(lesson) })
                    }
                }
                Spacer(Modifier.height(LqSpace.xxl))
            }
        }

        if (state.pickedSections.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Picked For You",
                        color = LqColors.textStrong,
                        fontSize = LqType.section,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (lessonSort == LessonSort.Ascending) "1→9" else "9→1",
                        color = LqColors.blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(LqRadius.pill))
                            .clickable(onClick = viewModel::toggleLessonSort)
                            .padding(horizontal = LqSpace.sm, vertical = LqSpace.xs),
                    )
                    Text(
                        text = "Search",
                        color = LqColors.blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(LqRadius.pill))
                            .clickable(onClick = onExplore)
                            .padding(horizontal = LqSpace.md, vertical = LqSpace.xs),
                    )
                }
                Spacer(Modifier.height(LqSpace.xs))
            }
            state.pickedSections.forEach { section ->
                item(key = "level-${section.title}") {
                    LevelSubheader(section)
                }
                items(
                    items = section.lessons,
                    key = { lesson -> "picked-${section.title}-${lesson.id}" },
                ) { lesson ->
                    PickedRow(lesson = lesson, onClick = { onOpenLesson(lesson) })
                }
            }
            item { Spacer(Modifier.height(LqSpace.xl)) }
        }

        if (!state.loading && state.continueLessons.isEmpty() && state.picked.isEmpty() && state.error == null) {
            item {
                LqSectionHeader(title = "Picked For You")
                Spacer(Modifier.height(LqSpace.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LqRadius.card))
                        .background(LqColors.surface)
                        .padding(LqSpace.xl),
                ) {
                    Column {
                        Text(
                            "No lessons yet",
                            color = LqColors.textStrong,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Find something you want to read or listen to in Explore.",
                            color = LqColors.muted,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(LqSpace.md))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(LqRadius.pill))
                                .background(LqColors.blueSoft)
                                .clickable(onClick = onExplore)
                                .padding(horizontal = LqSpace.lg, vertical = LqSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Explore lessons", color = LqColors.blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        }

        if (showProfile) {
            ProfileSheet(
                username = state.username,
                avatarPath = state.avatarPath,
                onPickAvatar = viewModel::saveAvatar,
                onSave = { name ->
                    viewModel.saveDisplayName(name)
                    showProfile = false
                },
                onDismiss = { showProfile = false },
            )
        }
    }
}

@Composable
private fun LastLessonCard(lesson: LessonSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.card))
            .clickable(onClick = onClick)
            .padding(LqSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                LqDot(LqColors.blue, 5.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "CONTINUE WHERE YOU LEFT OFF",
                    color = LqColors.blue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
            Spacer(Modifier.height(3.dp))
            RawText(
                text = lesson.title,
                color = LqColors.textStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!lesson.collectionTitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                RawText(
                    text = lesson.collectionTitle,
                    color = LqColors.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(LqSpace.sm))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LqColors.blueStrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                LqIcons.Play,
                contentDescription = "Continue lesson",
                tint = Color.White,
                modifier = Modifier.padding(start = 2.dp).size(16.dp),
            )
        }
    }
}

@Composable
private fun SavedCard(lesson: LessonSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(190.dp)
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(104.dp)) {
            RemoteImage(
                url = lesson.imageUrl,
                title = lesson.title,
                level = lesson.level,
                modifier = Modifier.fillMaxSize(),
            )
            LqLevelBadge(
                level = lesson.level,
                modifier = Modifier.align(Alignment.TopStart).padding(LqSpace.sm),
            )
        }
        Column(modifier = Modifier.padding(LqSpace.md)) {
            RawText(
                text = lesson.title,
                color = LqColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = lesson.newWordsCount?.let { "$it new words" }
                    ?: lesson.collectionTitle
                    ?: "Saved lesson",
                color = LqColors.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeHeader(
    username: String,
    avatarPath: String?,
    languageName: String,
    languageCode: String,
    languageLevel: Int?,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLanguages: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clickable(onClick = onOpenProfile),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(LqColors.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    AvatarImage(
                        path = avatarPath,
                        fallback = username,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(LqColors.bg)
                        .border(1.dp, LqColors.surfaceHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = flagForLanguage(languageCode), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(LqSpace.md))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(LqRadius.control))
                    .clickable(onClick = onOpenLanguages)
                    .padding(vertical = 4.dp),
            ) {
                RawText(
                    text = username.takeIf { it.isNotBlank() } ?: "Inputa",
                    color = LqColors.textStrong,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(languageName, color = LqColors.muted, fontSize = 12.sp)
                    Spacer(Modifier.width(LqSpace.xs))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(LqColors.faint),
                    )
                    Spacer(Modifier.width(LqSpace.xs))
                    Text(languageCode.uppercase(), color = LqColors.faint, fontSize = 11.sp)
                    languageLevel?.let { level ->
                        Spacer(Modifier.width(LqSpace.xs))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(LqColors.faint),
                        )
                        Spacer(Modifier.width(LqSpace.xs))
                        Text("Lv.$level", color = LqColors.muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        LqIcons.ChevronDown,
                        contentDescription = "Switch language",
                        tint = LqColors.faint,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LqColors.surface)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LqIcons.Settings, contentDescription = "Settings", tint = LqColors.text, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun KnownWordsCard(
    knownCount: Int,
    inputaCount: Int,
    ignoredCount: Int,
    onOpenStats: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.blueStrong),
    ) {
        Column(modifier = Modifier.padding(LqSpace.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Known Words",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                    )
                    Text(
                        text = formatNumber(knownCount),
                        color = Color.White,
                        fontSize = LqType.hero,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onOpenStats)
                        .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("All Stats", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(
                        LqIcons.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(LqSpace.lg))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.height(LqSpace.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BandMetric("Inputa", formatNumber(inputaCount), Modifier.weight(1f))
                BandDivider()
                BandMetric("Ignored", formatNumber(ignoredCount), Modifier.weight(1f))
                BandDivider()
                BandMetric(
                    "Total",
                    formatNumber(knownCount + inputaCount + ignoredCount),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BandMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(1.dp))
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun BandDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = LqSpace.md)
            .width(1.dp)
            .height(24.dp)
            .background(Color.White.copy(alpha = 0.16f)),
    )
}

@Composable
private fun ContinueCard(lesson: LessonSummary, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .width(272.dp)
            .graphicsLayer { alpha = if (pressed) 0.9f else 1f }
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            RemoteImage(
                url = lesson.imageUrl,
                title = lesson.title,
                level = lesson.level,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 120f,
                        ),
                    ),
            )
            LqLevelBadge(
                level = lesson.level,
                modifier = Modifier.align(Alignment.TopStart).padding(LqSpace.md),
            )
            if (lesson.durationSec != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(LqSpace.md)
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = LqSpace.sm, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(LqIcons.Clock, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    DurationLabel(lesson.durationSec, color = Color.White)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(LqSpace.md)
                    .shadow(8.dp, CircleShape)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    LqIcons.Play,
                    contentDescription = "Open lesson",
                    tint = Color(0xFF111113),
                    modifier = Modifier.padding(start = 2.dp).size(16.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(LqSpace.md)) {
            RawText(
                text = lesson.title,
                color = LqColors.textStrong,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
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
            Spacer(Modifier.height(LqSpace.md))
            LqProgressBar(
                progress = if (lesson.isCompleted) 1f else 0f,
                height = 4.dp,
            )
            Spacer(Modifier.height(LqSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LqDot(LqColors.blue)
                Spacer(Modifier.width(LqSpace.xs))
                Text(
                    text = lesson.newWordsCount?.let { "$it new words" } ?: "Lesson",
                    color = LqColors.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lesson.isCompleted) {
                    Icon(LqIcons.Check, contentDescription = null, tint = LqColors.green, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Completed", color = LqColors.green, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                } else {
                    Text("Continue", color = LqColors.blue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Picked For You 里的等级小标题：Beginner / Intermediate / Advanced。 */
@Composable
private fun LevelSubheader(section: HomeViewModel.LevelSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LqSpace.lg, bottom = LqSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (section.level != null) {
            LqLevelBadge(level = section.level, compact = true)
            Spacer(Modifier.width(LqSpace.sm))
        }
        Text(
            text = section.title,
            color = LqColors.textStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(LqSpace.sm))
        Text(
            text = "${section.lessons.size} lessons",
            color = LqColors.faint,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PickedRow(lesson: LessonSummary, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (pressed) 0.86f else 1f }
            .clip(RoundedCornerShape(LqRadius.control))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
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
                        .background(Color.Black.copy(alpha = 0.6f))
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
        Icon(
            LqIcons.ChevronRight,
            contentDescription = null,
            tint = LqColors.faint,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatNumber(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 10_000 -> String.format("%.1fK", value / 1_000.0)
        value >= 1_000 -> String.format("%,d", value)
        else -> value.toString()
    }
}

@Composable
private fun LanguageSheet(
    languages: List<LearningLanguage>,
    currentCode: String,
    loading: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LqColors.scrim)
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = LqRadius.sheet, topEnd = LqRadius.sheet))
                .background(LqColors.surface)
                .clickable(interactionSource = noRipple, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = LqSpace.xl)
                .padding(top = LqSpace.md, bottom = LqSpace.lg),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .background(LqColors.surfaceHigh),
                )
            }
            Spacer(Modifier.height(LqSpace.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Learning Languages",
                        color = LqColors.textStrong,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Switch what you're studying",
                        color = LqColors.muted,
                        fontSize = 12.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LqColors.surfaceHigh)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LqIcons.X, contentDescription = "Close", tint = LqColors.muted, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(LqSpace.md))
            when {
                loading && languages.isEmpty() -> LqLoading(label = "Loading languages…")
                languages.isEmpty() -> Text(
                    text = "No languages were returned for this account.",
                    color = LqColors.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = LqSpace.xl),
                )

                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(languages, key = { it.code }) { language ->
                        LanguageRow(
                            language = language,
                            selected = language.code == currentCode,
                            onClick = { onSelect(language.code) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(LqSpace.sm))
        }
    }
}

@Composable
internal fun LanguageRow(
    language: LearningLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.control))
            .clickable(onClick = onClick)
            .padding(horizontal = LqSpace.sm, vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LqColors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(flagForLanguage(language.code), fontSize = 20.sp)
        }
        Spacer(Modifier.width(LqSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.name,
                color = if (selected) LqColors.blue else LqColors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(language.code.uppercase())
                    language.level?.let {
                        append(" · Lv.")
                        append(it)
                    }
                    language.knownWords?.let {
                        append(" · ")
                        append(formatNumber(it))
                        append(" known")
                    }
                },
                color = LqColors.muted,
                fontSize = 11.sp,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(LqColors.blueSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LqIcons.Check, contentDescription = null, tint = LqColors.blue, modifier = Modifier.size(14.dp))
            }
        }
    }
}
