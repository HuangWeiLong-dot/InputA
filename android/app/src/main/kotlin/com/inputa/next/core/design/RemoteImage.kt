package com.inputa.next.core.design

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.inputa.next.core.data.absoluteUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 课程封面加载。只用 OkHttp + BitmapFactory，避免为了几张封面再引入图片库。
 * 内存里保留最近 48 张；失败时按等级色渐变兜底。
 */
@Composable
fun RemoteImage(
    url: String?,
    title: String,
    level: Int?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val resolved = remember(url) { url?.absoluteUrl() }
    val bitmap by produceState(
        initialValue = resolved?.let { ImageMemory.get(it) },
        key1 = resolved,
    ) {
        if (resolved == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) { ImageMemory.load(resolved) }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = title,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val colors = when {
                level == null -> listOf(LqColors.surfaceHigh, LqColors.surface)
                level <= 2 -> listOf(Color(0xFF274A73), Color(0xFF16283F))
                level <= 4 -> listOf(Color(0xFF5A4A20), Color(0xFF2E2717))
                else -> listOf(Color(0xFF5A2A28), Color(0xFF2E1B1A))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.trim().take(1).uppercase(),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private object ImageMemory {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    fun get(url: String): ImageBitmap? = cache.get(url)

    fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return runCatching {
            val request = Request.Builder().url(url).header("User-Agent", "InputA/0.2 Android").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val stream = response.body?.byteStream() ?: return null
                val bitmap = BitmapFactory.decodeStream(stream) ?: return null
                val image = bitmap.asImageBitmap()
                cache.put(url, image)
                image
            }
        }.getOrNull()
    }
}

/**
 * 本机头像：从应用私有目录读图，按需降采样，避免相册原图撑爆内存。
 * 没设置头像时显示名字首字母。
 *
 * 解码结果按路径缓存在内存里：首页是 LazyColumn，滚出首屏后 header 会被回收，
 * 再滚回来若重新异步解码，头像会先消失一下（只剩首字母）。缓存后立即回显。
 */
@Composable
fun AvatarImage(
    path: String?,
    fallback: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = path?.let { AvatarMemory.get(it) },
        key1 = path,
    ) {
        if (path == null) {
            value = null
            return@produceState
        }
        AvatarMemory.get(path)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) { decodeAvatar(path) }
        decoded?.let { AvatarMemory.put(path, it) }
        value = decoded
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = fallback.trim().take(1).uppercase().ifBlank { "I" },
                color = LqColors.textStrong,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private object AvatarMemory {
    private val cache = object : LruCache<String, ImageBitmap>(8) {}

    fun get(path: String): ImageBitmap? = cache.get(path)

    fun put(path: String, image: ImageBitmap) {
        cache.put(path, image)
    }
}

private fun decodeAvatar(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) {
            sample *= 2
        }
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()
