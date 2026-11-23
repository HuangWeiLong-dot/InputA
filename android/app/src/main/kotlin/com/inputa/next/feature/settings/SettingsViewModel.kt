package com.inputa.next.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.local.LocalWordStore
import com.inputa.next.core.data.local.SettingsStore
import com.inputa.next.core.notify.ReadingReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: SessionStore,
    private val settings: SettingsStore,
    private val localWords: LocalWordStore,
    private val reminder: ReadingReminder,
) : ViewModel() {

    private val _displayName = MutableStateFlow(session.displayUsername() ?: "Inputa")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _avatarPath = MutableStateFlow(session.avatarPath())
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    val language: String get() = session.language()
    val learnedToday: Int get() = localWords.todayLearned(session.language())

    val aiConfigured: Boolean get() = session.hasAiConfig()
    val aiBaseUrl: String get() = session.aiBaseUrl()
    val aiModel: String get() = session.aiModel()

    val fontSize = settings.fontSize
    val lineHeight = settings.lineHeight
    val autoTts = settings.autoTts
    val appLanguage = settings.appLanguage
    val dailyGoal = settings.dailyGoal
    val reviewSessionSize = settings.reviewSessionSize
    val reviewRandom = settings.reviewRandom
    val pronounceOnTap = settings.pronounceOnTap
    val autoCreateLingq = settings.autoCreateLingq
    val readerMode = settings.readerMode
    val reminderEnabled = settings.reminderEnabled
    val reminderHour = settings.reminderHour
    val reminderMinute = settings.reminderMinute

    fun increaseFont() = settings.setFontSize(settings.fontSize.value + 1f)
    fun decreaseFont() = settings.setFontSize(settings.fontSize.value - 1f)
    fun increaseLineHeight() = settings.setLineHeight(settings.lineHeight.value + 0.1f)
    fun decreaseLineHeight() = settings.setLineHeight(settings.lineHeight.value - 0.1f)
    fun toggleAutoTts() = settings.setAutoTts(!settings.autoTts.value)
    fun setAppLanguage(code: String) = settings.setAppLanguage(code)

    fun increaseDailyGoal() = settings.setDailyGoal(settings.dailyGoal.value + 100)
    fun decreaseDailyGoal() = settings.setDailyGoal(settings.dailyGoal.value - 100)

    fun setReviewSessionSize(size: Int) = settings.setReviewSessionSize(size)
    fun setReviewRandom(enabled: Boolean) = settings.setReviewRandom(enabled)
    fun setPronounceOnTap(enabled: Boolean) = settings.setPronounceOnTap(enabled)
    fun setAutoCreateLingq(enabled: Boolean) = settings.setAutoCreateLingq(enabled)
    fun setReaderMode(mode: String) = settings.setReaderMode(mode)

    fun setReminderEnabled(enabled: Boolean) {
        if (enabled) {
            reminder.schedule(settings.reminderHour.value, settings.reminderMinute.value)
        } else {
            reminder.cancel()
        }
    }

    fun setReminderTime(hour: Int, minute: Int) = reminder.reschedule(hour, minute)

    fun saveDisplayName(name: String) {
        session.saveDisplayUsername(name)
        _displayName.value = session.displayUsername() ?: "Inputa"
    }

    fun saveAvatar(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { session.saveAvatar(uri) } ?: return@launch
            _avatarPath.value = path
        }
    }

    /** 从首页编辑过资料后，再进设置页时同步一次。 */
    fun refreshProfile() {
        _displayName.value = session.displayUsername() ?: "Inputa"
        _avatarPath.value = session.avatarPath()
    }

    fun signOut() {
        reminder.cancel()
        session.clear()
    }
}
