package com.inputa.next.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.design.AppLanguage
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.translate
import com.inputa.next.feature.common.ProfileSheet

private enum class SettingsSheet {
    DailyGoal,
    Review,
    WordPopup,
    Lesson,
    Notifications,
    Theme,
    Interface,
    Profile,
}

/** 设置页。所有行都接到真实存储或页面，不再有「下一阶段」的占位提示。 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenMethod: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val lineHeight by viewModel.lineHeight.collectAsStateWithLifecycle()
    val autoTts by viewModel.autoTts.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val uiLanguage = com.inputa.next.core.design.LocalAppLanguage.current
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val avatarPath by viewModel.avatarPath.collectAsStateWithLifecycle()
    val dailyGoal by viewModel.dailyGoal.collectAsStateWithLifecycle()
    val reviewSize by viewModel.reviewSessionSize.collectAsStateWithLifecycle()
    val reviewRandom by viewModel.reviewRandom.collectAsStateWithLifecycle()
    val pronounceOnTap by viewModel.pronounceOnTap.collectAsStateWithLifecycle()
    val autoCreateLingq by viewModel.autoCreateLingq.collectAsStateWithLifecycle()
    val autoTranslate by viewModel.autoTranslate.collectAsStateWithLifecycle()
    val translationLanguage by viewModel.translationLanguage.collectAsStateWithLifecycle()
    val readerMode by viewModel.readerMode.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderHour by viewModel.reminderHour.collectAsStateWithLifecycle()
    val reminderMinute by viewModel.reminderMinute.collectAsStateWithLifecycle()

    var sheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshProfile() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setReminderEnabled(true)
        } else {
            Toast.makeText(
                context,
                uiLanguage.translate("Notification permission is required for reminders."),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val toggleReminder: (Boolean) -> Unit = { enabled ->
        val needsPermission = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminderEnabled(enabled)
        }
    }

    BackHandler(enabled = sheet != null) { sheet = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
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
                text = "Settings",
                color = LqColors.textStrong,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        SettingsGroup("LEARNING") {
            SettingRow(
                label = "Daily Goal",
                value = "${formatGoal(dailyGoal)} words",
                icon = LqIcons.TrendingUp,
                onClick = { sheet = SettingsSheet.DailyGoal },
            )
            SettingRow(
                label = "Review Settings",
                value = if (reviewSize <= 0) "All cards" else "$reviewSize cards",
                icon = LqIcons.Cards,
                onClick = { sheet = SettingsSheet.Review },
            )
            SettingRow(
                label = "Word Popup",
                value = translationLanguageLabel(translationLanguage),
                icon = LqIcons.Layers,
                onClick = { sheet = SettingsSheet.WordPopup },
            )
            SettingRow(
                label = "Lesson Settings",
                value = readerMode,
                icon = LqIcons.BookOpen,
                onClick = { sheet = SettingsSheet.Lesson },
            )
            SettingRow("Stats", null, LqIcons.BarChart, onOpenStats)
            SettingRow("Import Lesson", null, LqIcons.Plus, onOpenImport)
            SettingRow("AI Tutor", null, LqIcons.Bot, onOpenChat)
        }

        SettingsGroup("AI") {
            SettingRow(
                label = "Provider",
                value = viewModel.aiBaseUrl.takeIf { it.isNotBlank() } ?: "Not configured",
                icon = LqIcons.Globe,
                onClick = onOpenChat,
            )
            SettingRow(
                label = "Model",
                value = viewModel.aiModel.takeIf { it.isNotBlank() } ?: "—",
                icon = LqIcons.Layers,
                onClick = onOpenChat,
            )
            SettingRow(
                label = if (viewModel.aiConfigured) "Edit AI settings" else "Connect your AI",
                value = null,
                icon = LqIcons.Settings,
                onClick = onOpenChat,
            )
        }

        SettingsGroup("READER") {
            StepperRow(
                label = "Font Size",
                value = fontSize.toInt().toString(),
                icon = LqIcons.Type,
                onMinus = viewModel::decreaseFont,
                onPlus = viewModel::increaseFont,
            )
            StepperRow(
                label = "Line Spacing",
                value = String.format("%.1fx", lineHeight),
                icon = LqIcons.List,
                onMinus = viewModel::decreaseLineHeight,
                onPlus = viewModel::increaseLineHeight,
            )
            ToggleRow(
                label = "Auto Text-to-Speech",
                checked = autoTts,
                icon = LqIcons.Volume,
                onToggle = viewModel::toggleAutoTts,
            )
        }

        SettingsGroup("LANGUAGES") {
            SettingRow(
                label = "Active Language",
                value = viewModel.language.uppercase(),
                icon = LqIcons.Globe,
                onClick = onOpenLanguages,
            )
            SettingRow(
                label = "Interface Language",
                value = AppLanguage.fromCode(appLanguage).label,
                icon = LqIcons.Type,
                onClick = { sheet = SettingsSheet.Interface },
            )
        }

        SettingsGroup("APP") {
            SettingRow(
                label = "Theme",
                value = "Dark",
                icon = LqIcons.Settings,
                onClick = { sheet = SettingsSheet.Theme },
            )
            SettingRow(
                label = "Notifications",
                value = if (reminderEnabled) {
                    "Daily at ${reminderTimeLabel(reminderHour, reminderMinute)}"
                } else {
                    "Off"
                },
                icon = LqIcons.Bell,
                onClick = { sheet = SettingsSheet.Notifications },
            )
        }

        SettingsGroup("HELP") {
            SettingRow("The LingQ Method", null, LqIcons.BookOpen, onOpenMethod)
            SettingRow("Help & Support", null, LqIcons.Info, onOpenHelp)
        }

        SettingsGroup("ACCOUNT") {
            SettingRow(
                label = "Signed in as",
                value = displayName,
                icon = LqIcons.Check,
                onClick = { sheet = SettingsSheet.Profile },
            )
            SettingRow(
                label = "Sign Out",
                value = null,
                icon = LqIcons.LogOut,
                danger = true,
                onClick = {
                    viewModel.signOut()
                    onSignedOut()
                },
            )
        }
        Spacer(Modifier.height(LqSpace.xxxl))
    }

    when (sheet) {
        SettingsSheet.DailyGoal -> DailyGoalSheet(
            goal = dailyGoal,
            learnedToday = viewModel.learnedToday,
            onIncrease = viewModel::increaseDailyGoal,
            onDecrease = viewModel::decreaseDailyGoal,
            onDismiss = { sheet = null },
        )

        SettingsSheet.Review -> ReviewSettingsSheet(
            sessionSize = reviewSize,
            random = reviewRandom,
            onSessionSize = viewModel::setReviewSessionSize,
            onRandom = viewModel::setReviewRandom,
            onDismiss = { sheet = null },
        )

        SettingsSheet.WordPopup -> WordPopupSheet(
            pronounceOnTap = pronounceOnTap,
            autoCreateLingq = autoCreateLingq,
            autoTranslate = autoTranslate,
            translationLanguage = translationLanguage,
            onPronounceOnTap = viewModel::setPronounceOnTap,
            onAutoCreateLingq = viewModel::setAutoCreateLingq,
            onAutoTranslate = viewModel::setAutoTranslate,
            onTranslationLanguage = viewModel::setTranslationLanguage,
            onDismiss = { sheet = null },
        )

        SettingsSheet.Lesson -> LessonSettingsSheet(
            mode = readerMode,
            onMode = viewModel::setReaderMode,
            onDismiss = { sheet = null },
        )

        SettingsSheet.Notifications -> NotificationsSheet(
            enabled = reminderEnabled,
            hour = reminderHour,
            minute = reminderMinute,
            onToggle = toggleReminder,
            onTime = viewModel::setReminderTime,
            onDismiss = { sheet = null },
        )

        SettingsSheet.Theme -> ThemeSheet(onDismiss = { sheet = null })

        SettingsSheet.Interface -> InterfaceLanguageSheet(
            current = appLanguage,
            onSelect = viewModel::setAppLanguage,
            onDismiss = { sheet = null },
        )

        SettingsSheet.Profile -> ProfileSheet(
            username = displayName,
            avatarPath = avatarPath,
            onPickAvatar = viewModel::saveAvatar,
            onSave = { name ->
                viewModel.saveDisplayName(name)
                sheet = null
            },
            onDismiss = { sheet = null },
            bottomPadding = LqSpace.xxxl,
        )

        null -> Unit
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = LqSpace.xl)) {
        Text(
            text = title,
            color = LqColors.faint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = LqSpace.xl, vertical = LqSpace.sm),
        )
        Column(
            modifier = Modifier
                .padding(horizontal = LqSpace.xl)
                .clip(RoundedCornerShape(LqRadius.card))
                .background(LqColors.surface),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = LqSpace.lg, vertical = LqSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) LqColors.red else LqColors.muted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(LqSpace.md))
        Text(
            text = label,
            color = if (danger) LqColors.red else LqColors.text,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (!value.isNullOrBlank()) {
            Text(value, color = LqColors.muted, fontSize = 13.sp)
            Spacer(Modifier.width(LqSpace.xs))
        }
        if (!danger) {
            Icon(
                LqIcons.ChevronRight,
                contentDescription = null,
                tint = LqColors.faint,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    icon: ImageVector,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LqSpace.lg, vertical = LqSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = LqColors.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(LqSpace.md))
        Text(label, color = LqColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = LqColors.muted, fontSize = 13.sp)
        Spacer(Modifier.width(LqSpace.md))
        StepButton("-", onMinus)
        Spacer(Modifier.width(LqSpace.xs))
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(LqColors.surfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = LqColors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    icon: ImageVector,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = LqSpace.lg, vertical = LqSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = LqColors.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(LqSpace.md))
        Text(label, color = LqColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
