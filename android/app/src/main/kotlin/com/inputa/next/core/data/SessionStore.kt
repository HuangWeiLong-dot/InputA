package com.inputa.next.core.data

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录会话。token 只写入 EncryptedSharedPreferences，不落日志、不进仓库。
 *
 * LingQ 用的是 DRF Token（`Authorization: Token <token>`），长期有效；
 * 退出登录时连 token 一起清掉。
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "inputa_next_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    fun username(): String? = prefs.getString(KEY_USERNAME, null)

    fun isLoggedIn(): Boolean = !token().isNullOrBlank()

    /** 用户手动选过的语言；null 表示还没选过，由 profile 决定。 */
    fun storedLanguage(): String? =
        prefs.getString(KEY_LANGUAGE, null)?.takeIf { it.isNotBlank() }

    /** 当前学习语言；默认英语，可在头像菜单里改。 */
    fun language(): String = normalizeLanguage(storedLanguage())

    fun saveLanguage(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, normalizeLanguage(code)).apply()
    }

    /**
     * 用户为某门语言选的课程等级：1–6；0 表示“全部等级”；null 表示还没选过。
     * 首页会用 null 回落到接口返回的语言等级。
     */
    fun storedLanguageLevel(code: String): Int? {
        val key = KEY_LANGUAGE_LEVEL + normalizeLanguage(code)
        if (!prefs.contains(key)) return null
        return prefs.getInt(key, 0).coerceIn(0, 6)
    }

    fun saveLanguageLevel(code: String, level: Int?) {
        val key = KEY_LANGUAGE_LEVEL + normalizeLanguage(code)
        prefs.edit().putInt(key, (level ?: 0).coerceIn(0, 6)).apply()
    }

    fun save(token: String, username: String) {
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USERNAME, username).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        avatarDir().deleteRecursively()
    }

    // -----------------------------------------------------------------------
    // 本机自定义资料：显示名 + 头像
    // -----------------------------------------------------------------------

    /** 显示名优先用本机自定义的名字，没改过就回落到登录用户名。 */
    fun displayUsername(): String? =
        prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: username()

    fun saveDisplayUsername(name: String) {
        val trimmed = name.trim()
        prefs.edit().apply {
            if (trimmed.isBlank()) remove(KEY_DISPLAY_NAME) else putString(KEY_DISPLAY_NAME, trimmed)
        }.apply()
    }

    fun avatarPath(): String? =
        prefs.getString(KEY_AVATAR_PATH, null)
            ?.takeIf { File(it).isFile }

    /** 把相册选中的图片复制到应用私有目录，返回新路径；失败返回 null。 */
    fun saveAvatar(uri: Uri): String? {
        val dir = avatarDir().apply { mkdirs() }
        val target = File(dir, "avatar_${System.currentTimeMillis()}.img")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            target.delete()
            return null
        }
        val previous = avatarPath()
        prefs.edit().putString(KEY_AVATAR_PATH, target.absolutePath).apply()
        if (previous != null && previous != target.absolutePath) File(previous).delete()
        return target.absolutePath
    }

    private fun avatarDir(): File = File(context.filesDir, "profile")

    // -----------------------------------------------------------------------
    // 用户自己的 AI 接口（OpenAI 兼容）
    // -----------------------------------------------------------------------

    fun aiBaseUrl(): String = prefs.getString(KEY_AI_BASE, null).orEmpty()

    fun aiApiKey(): String = prefs.getString(KEY_AI_KEY, null).orEmpty()

    fun aiModel(): String = prefs.getString(KEY_AI_MODEL, null).orEmpty()

    fun hasAiConfig(): Boolean =
        aiBaseUrl().isNotBlank() && aiApiKey().isNotBlank() && aiModel().isNotBlank()

    fun saveAiConfig(baseUrl: String, apiKey: String, model: String) {
        prefs.edit()
            .putString(KEY_AI_BASE, baseUrl.trim())
            .putString(KEY_AI_KEY, apiKey.trim())
            .putString(KEY_AI_MODEL, model.trim())
            .apply()
    }

    // -----------------------------------------------------------------------
    // MiMo 语音合成（与聊天 AI 凭据分开保存）
    // -----------------------------------------------------------------------

    fun mimoApiKey(): String = prefs.getString(KEY_MIMO_KEY, null).orEmpty()

    fun hasMimoKey(): Boolean = mimoApiKey().isNotBlank()

    fun saveMimoApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        prefs.edit().apply {
            if (trimmed.isBlank()) remove(KEY_MIMO_KEY) else putString(KEY_MIMO_KEY, trimmed)
        }.apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USERNAME = "username"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_AVATAR_PATH = "avatar_path"
        const val KEY_LANGUAGE = "language"
        const val KEY_LANGUAGE_LEVEL = "language_level_"
        const val KEY_AI_BASE = "ai_base_url"
        const val KEY_AI_KEY = "ai_api_key"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_MIMO_KEY = "mimo_api_key"
    }
}
