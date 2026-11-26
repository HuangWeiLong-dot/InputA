package com.inputa.next.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import com.inputa.next.core.design.LocalAppLanguage
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqDot
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqProgressBar
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSectionHeader
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.LqType
import com.inputa.next.core.design.Text
import com.inputa.next.core.design.translate

/** 统计页：Known Words、每日目标、本机词库分布。 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    LaunchedEffect(Unit) { viewModel.load() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = LqSpace.xl,
            end = LqSpace.xl,
            top = LqSpace.sm,
            bottom = LqSpace.xxxl,
        ),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LqColors.surface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LqIcons.ArrowLeft,
                        contentDescription = LocalAppLanguage.current.translate("Back"),
                        tint = LqColors.text,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(LqSpace.md))
                Column {
                    Text(
                        text = "Stats",
                        color = LqColors.textStrong,
                        fontSize = LqType.headline,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Your local learning progress",
                        color = LqColors.muted,
                        fontSize = LqType.label,
                    )
                }
            }
            Spacer(Modifier.height(LqSpace.xl))
        }

        if (state.loading || stats == null) {
            item { LqLoading(label = "Loading your stats…") }
        } else {
            item {
                KnownWordsHero(
                    knownWords = stats.knownWords ?: 0,
                    totalWords = state.totalWords,
                )
                Spacer(Modifier.height(LqSpace.lg))
                DailyGoalCard(goal = state.dailyGoal, learnedToday = state.learnedToday)
                Spacer(Modifier.height(LqSpace.xxl))
            }

            item {
                LqSectionHeader(title = "Word Library")
                Spacer(Modifier.height(LqSpace.md))
                WordLibraryCard(
                    knownWords = stats.knownWords ?: 0,
                    inputaWords = stats.lingqs ?: 0,
                    ignoredWords = state.ignoredWords,
                    totalWords = state.totalWords,
                )
                Spacer(Modifier.height(LqSpace.md))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = LqIcons.Info,
                        contentDescription = null,
                        tint = LqColors.faint,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(LqSpace.sm))
                    Text(
                        text = "Stats are calculated from word statuses saved on this device.",
                        color = LqColors.faint,
                        fontSize = LqType.label,
                    )
                }
            }
        }
    }
}

@Composable
private fun KnownWordsHero(knownWords: Int, totalWords: Int) {
    val progress = if (totalWords <= 0) 0f else knownWords.toFloat() / totalWords
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(
                Brush.horizontalGradient(listOf(LqColors.knownStart, LqColors.knownEnd)),
            )
            .padding(LqSpace.xl),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Known Words",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = LqType.caption,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                    )
                    Text(
                        text = formatNumber(knownWords),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LqIcons.BookOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(Modifier.height(LqSpace.lg))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(LqRadius.pill))
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(7.dp)
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .background(Color.White),
                )
            }
            Spacer(Modifier.height(LqSpace.sm))
            Text(
                text = "${formatNumber(totalWords)} words tracked",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = LqType.label,
            )
        }
    }
}

@Composable
private fun DailyGoalCard(goal: Int, learnedToday: Int) {
    val progress = if (goal <= 0) 0f else learnedToday.toFloat() / goal
    val remaining = (goal - learnedToday).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.card))
            .padding(LqSpace.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Goal",
                    color = LqColors.textStrong,
                    fontSize = LqType.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (progress >= 1f) "Goal reached today." else "${formatNumber(remaining)} words remaining",
                    color = LqColors.muted,
                    fontSize = LqType.label,
                )
            }
            Text(
                text = "${formatNumber(learnedToday)} / ${formatNumber(goal)}",
                color = LqColors.text,
                fontSize = LqType.caption,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(LqSpace.md))
        LqProgressBar(progress = progress, height = 7.dp)
    }
}

@Composable
private fun WordLibraryCard(
    knownWords: Int,
    inputaWords: Int,
    ignoredWords: Int,
    totalWords: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.card))
            .padding(LqSpace.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Total tracked",
                color = LqColors.muted,
                fontSize = LqType.caption,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatNumber(totalWords),
                color = LqColors.textStrong,
                fontSize = LqType.title,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(LqSpace.md))
        WordDistributionBar(
            knownWords = knownWords,
            inputaWords = inputaWords,
            ignoredWords = ignoredWords,
            totalWords = totalWords,
        )
        Spacer(Modifier.height(LqSpace.lg))
        WordLibraryLegend("Known Words", knownWords, totalWords, LqColors.green)
        Spacer(Modifier.height(LqSpace.md))
        WordLibraryLegend("Inputa", inputaWords, totalWords, LqColors.yellow)
        Spacer(Modifier.height(LqSpace.md))
        WordLibraryLegend("Ignored", ignoredWords, totalWords, LqColors.faint)
    }
}

@Composable
private fun WordDistributionBar(
    knownWords: Int,
    inputaWords: Int,
    ignoredWords: Int,
    totalWords: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(LqColors.surfaceHigh),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (totalWords <= 0) return@Row
        listOf(
            knownWords to LqColors.green,
            inputaWords to LqColors.yellow,
            ignoredWords to LqColors.faint,
        ).forEach { (count, color) ->
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .weight(count.toFloat())
                        .height(10.dp)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun WordLibraryLegend(label: String, count: Int, totalWords: Int, color: Color) {
    val percent = if (totalWords <= 0) 0 else count * 100 / totalWords
    Row(verticalAlignment = Alignment.CenterVertically) {
        LqDot(color = color, size = 8.dp)
        Spacer(Modifier.width(LqSpace.sm))
        Text(
            text = label,
            color = LqColors.text,
            fontSize = LqType.body,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatNumber(count),
            color = LqColors.textStrong,
            fontSize = LqType.body,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(LqSpace.md))
        Text(
            text = "$percent%",
            color = LqColors.muted,
            fontSize = LqType.caption,
        )
    }
}

private fun formatNumber(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 10_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        value >= 1_000 -> String.format(Locale.US, "%,d", value)
        else -> value.toString()
    }
}
