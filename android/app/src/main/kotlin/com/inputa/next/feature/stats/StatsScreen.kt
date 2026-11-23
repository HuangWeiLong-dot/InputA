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
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.ChartPoint
import com.inputa.next.core.data.StatsSnapshot
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqChip
import com.inputa.next.core.design.LqErrorState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqProgressBar
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSectionHeader
import com.inputa.next.core.design.LqSpace

/** 统计页：Known Words、本周活动、明细卡片。数字全部来自 profile / progress。 */
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
                    text = "Stats",
                    color = LqColors.textStrong,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(LqSpace.lg))
            KnownWordsHero(stats = stats)
            Spacer(Modifier.height(LqSpace.lg))
            DailyGoalCard(goal = state.dailyGoal, learnedToday = state.learnedToday)
            Spacer(Modifier.height(LqSpace.lg))
        }

        if (state.loading) {
            item { LqLoading(label = "Loading your stats…") }
        }

        if (!state.loading && state.error != null && stats == null) {
            item { LqErrorState(message = state.error.orEmpty(), onRetry = viewModel::load) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.md)) {
                MetricCard(
                    icon = LqIcons.BookOpen,
                    label = "Words Read",
                    value = formatNumber(stats?.wordsRead),
                    tint = LqColors.blue,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    icon = LqIcons.Sparkles,
                    label = "Inputa",
                    value = formatNumber(stats?.lingqs),
                    tint = LqColors.yellow,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(LqSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.md)) {
                MetricCard(
                    icon = LqIcons.Headphones,
                    label = "Hrs Listened",
                    value = (stats?.hoursListened)?.let { formatHours(it) } ?: "—",
                    tint = LqColors.green,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    icon = LqIcons.Flame,
                    label = "Day Streak",
                    value = formatNumber(stats?.streak),
                    tint = LqColors.orange,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(LqSpace.xxl))
        }

        item {
            LqSectionHeader(title = "This Week")
            Spacer(Modifier.height(LqSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
                listOf("reading" to "Words", "lingqs" to "Inputa", "listening" to "Listening").forEach { (key, label) ->
                    LqChip(
                        label = label,
                        selected = state.metric == key,
                        onClick = { viewModel.setMetric(key) },
                    )
                }
            }
            Spacer(Modifier.height(LqSpace.md))
            WeeklyChart(points = stats?.chart.orEmpty())
        }
    }
}

@Composable
private fun KnownWordsHero(stats: StatsSnapshot?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.blueStrong)
            .padding(LqSpace.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Known Words",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatNumber(stats?.knownWords),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(LqRadius.pill))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LqIcons.Flame, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${formatNumber(stats?.streak)} day streak",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DailyGoalCard(goal: Int, learnedToday: Int) {
    val progress = if (goal <= 0) 0f else learnedToday.toFloat() / goal
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .padding(LqSpace.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Daily Goal",
                color = LqColors.textStrong,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${formatNumber(learnedToday)} / ${formatNumber(goal)}",
                color = LqColors.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(LqSpace.md))
        LqProgressBar(progress = progress, height = 6.dp)
        Spacer(Modifier.height(LqSpace.sm))
        Text(
            text = if (progress >= 1f) {
                "Goal reached today. Keep the streak going."
            } else {
                "Words whose status changed today count toward this goal."
            },
            color = LqColors.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.card))
            .padding(LqSpace.lg),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(LqSpace.md))
        Text(
            text = value,
            color = LqColors.textStrong,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = label, color = LqColors.muted, fontSize = 11.sp)
    }
}

@Composable
private fun WeeklyChart(points: List<ChartPoint>) {
    if (points.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LqRadius.card))
                .background(LqColors.surface)
                .padding(LqSpace.xl),
        ) {
            Text(
                text = "No activity data was returned for this period.",
                color = LqColors.muted,
                fontSize = 13.sp,
            )
        }
        return
    }
    val max = points.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .padding(horizontal = LqSpace.md, vertical = LqSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(LqSpace.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { point ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((8f + 72f * (point.value / max)).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(LqColors.blue),
                )
                Spacer(Modifier.height(LqSpace.xs))
                Text(
                    text = point.label,
                    color = LqColors.faint,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatNumber(value: Int?): String {
    val number = value ?: return "—"
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 10_000 -> String.format("%.1fK", number / 1_000.0)
        number >= 1_000 -> String.format("%,d", number)
        else -> number.toString()
    }
}

private fun formatHours(value: Double): String =
    if (value >= 100) value.toInt().toString() else String.format("%.1f", value)
