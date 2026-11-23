package com.inputa.next.feature.playlist

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.PlaylistEntry
import com.inputa.next.core.design.DurationLabel
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqEmptyState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLevelBadge
import com.inputa.next.core.design.LqProgressBar
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.RawText
import com.inputa.next.core.design.LqType
import com.inputa.next.core.design.RemoteImage
import com.inputa.next.core.media.LessonPlayer

/** 本地播放队列 + 应用内播放器。 */
@Composable
fun PlaylistScreen(
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = LqSpace.xl, end = LqSpace.xl, top = LqSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Playlist",
                    color = LqColors.textStrong,
                    fontSize = LqType.headline,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (items.isEmpty()) "No lessons queued" else "${items.size} lessons",
                    color = LqColors.muted,
                    fontSize = 12.sp,
                )
            }
            if (items.isNotEmpty()) {
                Text(
                    text = "Clear",
                    color = LqColors.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .clickable { viewModel.clear() }
                        .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
                )
            }
        }
        Spacer(Modifier.height(LqSpace.md))

        if (items.isEmpty()) {
            LqEmptyState(
                title = "Your playlist is empty",
                message = "Add lessons from a lesson preview. They stay on this device and play from LingQ's audio.",
                icon = LqIcons.Headphones,
                actionLabel = "Browse lessons",
                onAction = onExplore,
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = LqSpace.xl,
                end = LqSpace.xl,
                bottom = 120.dp,
            ),
        ) {
            player.entry?.let { entry ->
                item(key = "now-playing") {
                    NowPlayingCard(
                        entry = entry,
                        playing = player.playing,
                        positionMs = player.positionMs,
                        durationMs = player.durationMs,
                        error = player.error,
                        onToggle = viewModel::toggle,
                        onPrevious = viewModel::playPrevious,
                        onNext = { viewModel.playNext() },
                        onStop = viewModel::stop,
                        onDismissError = viewModel::clearError,
                    )
                    Spacer(Modifier.height(LqSpace.lg))
                }
            }
            items(items, key = { it.lessonId }) { entry ->
                PlaylistRow(
                    entry = entry,
                    current = player.entry?.lessonId == entry.lessonId,
                    playing = player.playing && player.entry?.lessonId == entry.lessonId,
                    onPlay = { viewModel.play(entry) },
                    onRemove = { viewModel.remove(entry.lessonId) },
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    entry: PlaylistEntry,
    playing: Boolean,
    positionMs: Int,
    durationMs: Int,
    error: String?,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.card))
            .background(LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.card))
            .padding(LqSpace.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(LqRadius.cover)),
            ) {
                RemoteImage(entry.imageUrl, entry.title, entry.level, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(LqSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                RawText(
                    text = entry.title,
                    color = LqColors.textStrong,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!entry.collectionTitle.isNullOrBlank()) {
                    RawText(
                        text = entry.collectionTitle,
                        color = LqColors.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LqIcons.X, contentDescription = "Stop", tint = LqColors.muted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(LqSpace.md))
        LqProgressBar(progress = progress, height = 4.dp)
        Spacer(Modifier.height(LqSpace.xs))
        Row {
            Text(formatMs(positionMs), color = LqColors.faint, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text(formatMs(durationMs), color = LqColors.faint, fontSize = 10.sp)
        }
        Spacer(Modifier.height(LqSpace.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButtonSmall(LqIcons.SkipBack, "Previous", onPrevious)
            Spacer(Modifier.width(LqSpace.lg))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(LqColors.blueStrong)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playing) LqIcons.Pause else LqIcons.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(LqSpace.lg))
            IconButtonSmall(LqIcons.SkipForward, "Next", onNext)
        }
        error?.let {
            Spacer(Modifier.height(LqSpace.md))
            Text(
                text = it,
                color = LqColors.red,
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onDismissError),
            )
        }
    }
}

@Composable
private fun IconButtonSmall(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(LqColors.surfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = LqColors.text, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlaylistRow(
    entry: PlaylistEntry,
    current: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.control))
            .background(if (current) LqColors.blueSoft.copy(alpha = 0.4f) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(vertical = LqSpace.sm, horizontal = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            RemoteImage(entry.imageUrl, entry.title, entry.level, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(LqSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            RawText(
                text = entry.title,
                color = if (current) LqColors.blue else LqColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LqLevelBadge(entry.level, compact = true)
                Spacer(Modifier.width(LqSpace.sm))
                DurationLabel(entry.durationSec)
            }
        }
        Icon(
            imageVector = if (playing) LqIcons.Pause else LqIcons.Play,
            contentDescription = null,
            tint = if (current) LqColors.blue else LqColors.muted,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlay)
                .padding(8.dp),
        )
        Spacer(Modifier.width(LqSpace.xs))
        Icon(
            imageVector = LqIcons.X,
            contentDescription = "Remove",
            tint = LqColors.faint,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(8.dp),
        )
    }
}

private fun formatMs(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
