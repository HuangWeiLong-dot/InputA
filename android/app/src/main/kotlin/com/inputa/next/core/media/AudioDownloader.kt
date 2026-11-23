package com.inputa.next.core.media

import android.content.Context
import android.util.Log
import com.inputa.next.core.data.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * LingQ 的音频地址常常带鉴权/跳转，交给 MediaPlayer 直接读远端 URL 会报
 * `error -38`。这里统一用 OkHttp 下载到 cache 再播放，并顺带做磁盘缓存：
 * 同一课程/同一个词第二次播放不再走网络。
 */
@Singleton
class AudioDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val session: SessionStore,
) {
    private val directory: File by lazy { File(context.cacheDir, "audio").apply { mkdirs() } }

    suspend fun download(url: String, key: String): File? = withContext(Dispatchers.IO) {
        val safeKey = key.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
        val target = File(directory, "$safeKey-${url.hashCode()}.audio")
        if (target.length() > 0L) return@withContext target

        val temp = File(directory, "${target.name}.part")
        val host = url.toHttpUrlOrNull()?.host.orEmpty()
        // LingQ 音频在 S3 公开直链上。把 Authorization 发给 S3 会被当成 AWS 签名，
        // 直接返回 403（MediaPlayer 侧表现为 error -38），所以只给 API 域名带 Token。
        val apiHost = host == "www.lingq.com" || host == "lingq.com" || host == "api.lingq.com"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "audio/*,*/*")
            .header("User-Agent", "InputA/0.2 Android")
            .apply {
                if (apiHost) session.token()?.let { header("Authorization", "Token $it") }
            }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GET $url -> ${response.code} (${response.request.url})")
                    return@withContext null
                }
                val body = response.body
                val contentType = body.contentType()?.toString().orEmpty()
                Log.d(TAG, "GET $url -> ${response.code} $contentType len=${body.contentLength()}")
                // 登录页 / 错误页也会返回 200，别把它当音频存下来。
                if (contentType.startsWith("text/")) return@withContext null
                body.byteStream().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (temp.length() <= 0L) {
                temp.delete()
                return@withContext null
            }
            target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target
        } catch (cancellation: CancellationException) {
            temp.delete()
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "Failed $url", error)
            temp.delete()
            null
        }
    }

    private companion object {
        const val TAG = "AudioDownloader"
    }
}
