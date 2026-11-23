package com.inputa.next.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputa.next.core.design.AppLanguage
import com.inputa.next.core.design.LqChip
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqProgressBar
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace

internal fun formatGoal(value: Int): String = String.format("%,d", value)

internal fun reminderTimeLabel(hour: Int, minute: Int): String =
    String.format("%02d:%02d", hour, minute)

/** 设置页所有底部面板共用的外壳：遮罩 + 圆角面板 + 标题 + 关闭。 */
@Composable
fun SettingsSheetScaffold(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LqSpace.xl)
                .padding(top = LqSpace.md, bottom = LqSpace.xxxl),
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
                        text = title,
                        color = LqColors.textStrong,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (subtitle != null) {
                        Text(text = subtitle, color = LqColors.muted, fontSize = 12.sp)
                    }
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
            Spacer(Modifier.height(LqSpace.xl))
            content()
        }
    }
}

@Composable
fun DailyGoalSheet(
    goal: Int,
    learnedToday: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSheetScaffold(
        title = "Daily Goal",
        subtitle = "How many words you aim to learn each day",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LqRadius.card))
                .background(LqColors.bg)
                .padding(LqSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("-", onDecrease)
                Spacer(Modifier.width(LqSpace.xl))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatGoal(goal),
                        color = LqColors.textStrong,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("words per day", color = LqColors.muted, fontSize = 11.sp)
                }
                Spacer(Modifier.width(LqSpace.xl))
                StepButton("+", onIncrease)
            }
            Spacer(Modifier.height(LqSpace.xl))
            Text(
                text = "Today  ${formatGoal(learnedToday)} / ${formatGoal(goal)}",
                color = LqColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(LqSpace.sm))
            LqProgressBar(
                progress = if (goal <= 0) 0f else learnedToday.toFloat() / goal,
                height = 6.dp,
            )
        }
        Spacer(Modifier.height(LqSpace.md))
        Text(
            text = "A word counts once on the day its status changes. Adjust in steps of 100.",
            color = LqColors.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun ReviewSettingsSheet(
    sessionSize: Int,
    random: Boolean,
    onSessionSize: (Int) -> Unit,
    onRandom: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSheetScaffold(
        title = "Review Settings",
        subtitle = "Tune your flashcard sessions",
        onDismiss = onDismiss,
    ) {
        Text("Cards per session", color = LqColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(LqSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
            listOf(10, 20, 50).forEach { size ->
                LqChip(
                    label = size.toString(),
                    selected = sessionSize == size,
                    onClick = { onSessionSize(size) },
                )
            }
            LqChip(
                label = "All",
                selected = sessionSize <= 0,
                onClick = { onSessionSize(0) },
            )
        }
        Spacer(Modifier.height(LqSpace.lg))
        SheetToggle(
            label = "Shuffle cards",
            description = "Mix levels instead of reviewing lowest levels first.",
            checked = random,
            onToggle = { onRandom(!random) },
        )
    }
}

@Composable
fun WordPopupSheet(
    pronounceOnTap: Boolean,
    autoCreateLingq: Boolean,
    onPronounceOnTap: (Boolean) -> Unit,
    onAutoCreateLingq: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSheetScaffold(
        title = "Word Popup",
        subtitle = "What happens when you tap a word",
        onDismiss = onDismiss,
    ) {
        SheetToggle(
            label = "Pronounce on tap",
            description = "Play the word audio as soon as the popup opens.",
            checked = pronounceOnTap,
            onToggle = { onPronounceOnTap(!pronounceOnTap) },
        )
        Spacer(Modifier.height(LqSpace.sm))
        SheetToggle(
            label = "Add new words automatically",
            description = "Tapping a word with no status saves it as a level 1 Inputa.",
            checked = autoCreateLingq,
            onToggle = { onAutoCreateLingq(!autoCreateLingq) },
        )
    }
}

@Composable
fun LessonSettingsSheet(
    mode: String,
    onMode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSheetScaffold(
        title = "Lesson Settings",
        subtitle = "Defaults for newly opened lessons",
        onDismiss = onDismiss,
    ) {
        Text("Reading mode", color = LqColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(LqSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
            listOf("Page", "Sentence", "Scroll").forEach { item ->
                LqChip(
                    label = item,
                    selected = mode == item,
                    onClick = { onMode(item) },
                )
            }
        }
        Spacer(Modifier.height(LqSpace.md))
        Text(
            text = when (mode) {
                "Sentence" -> "Shows one sentence at a time with next/previous controls."
                "Scroll" -> "Shows the whole lesson in one continuous scroll."
                else -> "Splits the lesson into full-screen pages."
            },
            color = LqColors.muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun NotificationsSheet(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onToggle: (Boolean) -> Unit,
    onTime: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSheetScaffold(
        title = "Notifications",
        subtitle = "A daily nudge to keep reading",
        onDismiss = onDismiss,
    ) {
        SheetToggle(
            label = "Daily reading reminder",
            description = if (enabled) {
                "Reminder set for ${reminderTimeLabel(hour, minute)}."
            } else {
                "Get one local notification a day at the time you pick."
            },
            checked = enabled,
            onToggle = { onToggle(!enabled) },
        )
        Spacer(Modifier.height(LqSpace.lg))
        Text("Reminder time", color = LqColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(LqSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
            listOf(8 to 0, 12 to 0, 18 to 0, 20 to 0).forEach { (h, m) ->
                LqChip(
                    label = reminderTimeLabel(h, m),
                    selected = hour == h && minute == m,
                    onClick = { onTime(h, m) },
                )
            }
        }
        Spacer(Modifier.height(LqSpace.md))
        Text(
            text = "Notifications are scheduled on this device. Android may slightly delay them while the phone is idle.",
            color = LqColors.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun ThemeSheet(onDismiss: () -> Unit) {
    SettingsSheetScaffold(
        title = "Theme",
        subtitle = "Appearance",
        onDismiss = onDismiss,
    ) {
        LockedOption(label = "Dark", selected = true)
        Spacer(Modifier.height(LqSpace.md))
        Text(
            text = "This build is dark-only. Light and parchment themes are not available yet.",
            color = LqColors.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun InterfaceLanguageSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSheetScaffold(
        title = "Interface Language",
        subtitle = "Language of menus and buttons",
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
            AppLanguage.entries.forEach { language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LqRadius.control))
                        .background(LqColors.bg)
                        .clickable {
                            onSelect(language.code)
                            onDismiss()
                        }
                        .padding(horizontal = LqSpace.lg, vertical = LqSpace.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = language.label,
                        color = LqColors.text,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (current == language.code) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(LqColors.blueSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                LqIcons.Check,
                                contentDescription = null,
                                tint = LqColors.blue,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(LqSpace.md))
        Text(
            text = "Changes apply immediately.",
            color = LqColors.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun LockedOption(label: String, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.control))
            .background(LqColors.bg)
            .padding(horizontal = LqSpace.lg, vertical = LqSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LqColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
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

@Composable
private fun SheetToggle(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.control))
            .background(LqColors.bg)
            .clickable(onClick = onToggle)
            .padding(LqSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = LqColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(description, color = LqColors.muted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(LqSpace.md))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(LqRadius.pill))
                .background(if (checked) LqColors.greenStrong else LqColors.surfaceHigh)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(LqColors.surfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = LqColors.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}
