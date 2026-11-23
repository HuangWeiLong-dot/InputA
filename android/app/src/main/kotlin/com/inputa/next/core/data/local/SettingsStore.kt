package com.inputa.next.core.data.local

import android.content.Context
import com.inputa.next.core.data.LessonSort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 阅读器/界面偏好。非敏感，用普通 SharedPreferences。 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("inputa_next_settings", Context.MODE_PRIVATE)

    private val _fontSize = MutableStateFlow(prefs.getFloat(KEY_FONT, 19f))
    val fontSize = _fontSize.asStateFlow()

    private val _lineHeight = MutableStateFlow(prefs.getFloat(KEY_LINE, 1.55f))
    val lineHeight = _lineHeight.asStateFlow()

    /** 正文里句子/段落之间的间距，单位 dp。 */
    private val _paragraphSpacing = MutableStateFlow(prefs.getFloat(KEY_PARAGRAPH, 34f))
    val paragraphSpacing = _paragraphSpacing.asStateFlow()

    /** 词与词之间额外增加的间距，单位 sp，0 表示默认。 */
    private val _wordSpacing = MutableStateFlow(prefs.getFloat(KEY_WORD, 0f))
    val wordSpacing = _wordSpacing.asStateFlow()

    private val _autoTts = MutableStateFlow(prefs.getBoolean(KEY_TTS, true))
    val autoTts = _autoTts.asStateFlow()

    /** 界面语言：en / zh。 */
    private val _appLanguage = MutableStateFlow(
        prefs.getString(KEY_APP_LANGUAGE, "en")?.takeIf { it in APP_LANGUAGES } ?: "en",
    )
    val appLanguage = _appLanguage.asStateFlow()

    fun setAppLanguage(code: String) {
        val normalized = if (code in APP_LANGUAGES) code else "en"
        prefs.edit().putString(KEY_APP_LANGUAGE, normalized).apply()
        _appLanguage.value = normalized
    }

    /** AI 单词解释的输出语言；空串表示跟随界面语言。 */
    private val _aiExplainLanguage = MutableStateFlow(
        prefs.getString(KEY_AI_EXPLAIN_LANGUAGE, "").orEmpty(),
    )
    val aiExplainLanguage = _aiExplainLanguage.asStateFlow()

    fun setAiExplainLanguage(code: String) {
        val normalized = if (code in EXPLAIN_LANGUAGES) code else "en"
        prefs.edit().putString(KEY_AI_EXPLAIN_LANGUAGE, normalized).apply()
        _aiExplainLanguage.value = normalized
    }

    fun effectiveAiExplainLanguage(): String =
        aiExplainLanguage.value.ifBlank {
            if (appLanguage.value == "zh") "zh" else "en"
        }

    /** 首页/资料库带序号课程的正序或倒序。 */
    private val _lessonSort = MutableStateFlow(
        runCatching { LessonSort.valueOf(prefs.getString(KEY_SORT, null).orEmpty()) }
            .getOrDefault(LessonSort.Ascending),
    )
    val lessonSort = _lessonSort.asStateFlow()

    fun setLessonSort(value: LessonSort) {
        prefs.edit().putString(KEY_SORT, value.name).apply()
        _lessonSort.value = value
    }

    fun toggleLessonSort() {
        setLessonSort(
            if (_lessonSort.value == LessonSort.Ascending) LessonSort.Descending else LessonSort.Ascending,
        )
    }

    /** 每日目标词数。 */
    private val _dailyGoal = MutableStateFlow(prefs.getInt(KEY_DAILY_GOAL, 1000))
    val dailyGoal = _dailyGoal.asStateFlow()

    /** 单次复习卡片数；0 表示不限。 */
    private val _reviewSessionSize = MutableStateFlow(prefs.getInt(KEY_REVIEW_SIZE, 20))
    val reviewSessionSize = _reviewSessionSize.asStateFlow()

    /** 复习时是否打乱顺序。 */
    private val _reviewRandom = MutableStateFlow(prefs.getBoolean(KEY_REVIEW_RANDOM, false))
    val reviewRandom = _reviewRandom.asStateFlow()

    /** 点词时是否自动朗读。 */
    private val _pronounceOnTap = MutableStateFlow(prefs.getBoolean(KEY_PRONOUNCE_TAP, false))
    val pronounceOnTap = _pronounceOnTap.asStateFlow()

    /** 点开新词时是否自动收录为 1 级 LingQ。 */
    private val _autoCreateLingq = MutableStateFlow(prefs.getBoolean(KEY_AUTO_LINGQ, true))
    val autoCreateLingq = _autoCreateLingq.asStateFlow()

    /** 阅读器默认模式：Page / Sentence / Scroll。 */
    private val _readerMode = MutableStateFlow(prefs.getString(KEY_READER_MODE, "Page") ?: "Page")
    val readerMode = _readerMode.asStateFlow()

    // 每日阅读提醒
    private val _reminderEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER, false))
    val reminderEnabled = _reminderEnabled.asStateFlow()

    private val _reminderHour = MutableStateFlow(prefs.getInt(KEY_REMINDER_HOUR, 20))
    val reminderHour = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(prefs.getInt(KEY_REMINDER_MINUTE, 0))
    val reminderMinute = _reminderMinute.asStateFlow()

    fun setDailyGoal(value: Int) {
        val clamped = value.coerceIn(100, 10_000)
        prefs.edit().putInt(KEY_DAILY_GOAL, clamped).apply()
        _dailyGoal.value = clamped
    }

    fun setReviewSessionSize(value: Int) {
        val clamped = if (value <= 0) 0 else value.coerceIn(5, 200)
        prefs.edit().putInt(KEY_REVIEW_SIZE, clamped).apply()
        _reviewSessionSize.value = clamped
    }

    fun setReviewRandom(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REVIEW_RANDOM, enabled).apply()
        _reviewRandom.value = enabled
    }

    fun setPronounceOnTap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRONOUNCE_TAP, enabled).apply()
        _pronounceOnTap.value = enabled
    }

    fun setAutoCreateLingq(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LINGQ, enabled).apply()
        _autoCreateLingq.value = enabled
    }

    fun setReaderMode(mode: String) {
        val normalized = if (mode in READER_MODES) mode else "Page"
        prefs.edit().putString(KEY_READER_MODE, normalized).apply()
        _readerMode.value = normalized
    }

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER, enabled).apply()
        _reminderEnabled.value = enabled
    }

    fun setReminderTime(hour: Int, minute: Int) {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        prefs.edit()
            .putInt(KEY_REMINDER_HOUR, safeHour)
            .putInt(KEY_REMINDER_MINUTE, safeMinute)
            .apply()
        _reminderHour.value = safeHour
        _reminderMinute.value = safeMinute
    }

    fun setFontSize(value: Float) {
        val clamped = value.coerceIn(14f, 32f)
        prefs.edit().putFloat(KEY_FONT, clamped).apply()
        _fontSize.value = clamped
    }

    fun setLineHeight(value: Float) {
        val clamped = value.coerceIn(1.15f, 2.2f)
        prefs.edit().putFloat(KEY_LINE, clamped).apply()
        _lineHeight.value = clamped
    }

    fun setParagraphSpacing(value: Float) {
        val clamped = value.coerceIn(16f, 56f)
        prefs.edit().putFloat(KEY_PARAGRAPH, clamped).apply()
        _paragraphSpacing.value = clamped
    }

    fun setWordSpacing(value: Float) {
        val clamped = value.coerceIn(0f, 6f)
        prefs.edit().putFloat(KEY_WORD, clamped).apply()
        _wordSpacing.value = clamped
    }

    fun setAutoTts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS, enabled).apply()
        _autoTts.value = enabled
    }

    private companion object {
        const val KEY_FONT = "reader_font_size"
        const val KEY_LINE = "reader_line_height"
        const val KEY_PARAGRAPH = "reader_paragraph_spacing"
        const val KEY_WORD = "reader_word_spacing"
        const val KEY_TTS = "reader_auto_tts"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_AI_EXPLAIN_LANGUAGE = "ai_explain_language"
        const val KEY_SORT = "lesson_sort"
        const val KEY_DAILY_GOAL = "daily_goal"
        const val KEY_REVIEW_SIZE = "review_session_size"
        const val KEY_REVIEW_RANDOM = "review_random"
        const val KEY_PRONOUNCE_TAP = "pronounce_on_tap"
        const val KEY_AUTO_LINGQ = "auto_create_lingq"
        const val KEY_READER_MODE = "reader_mode"
        const val KEY_REMINDER = "reminder_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"

        val READER_MODES = setOf("Page", "Sentence", "Scroll")
        val APP_LANGUAGES = setOf("en", "zh")
        val EXPLAIN_LANGUAGES = setOf("zh", "en", "ja", "ko")
    }
}
