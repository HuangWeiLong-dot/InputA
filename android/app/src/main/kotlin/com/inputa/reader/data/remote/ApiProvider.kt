package com.inputa.reader.data.remote

import com.inputa.reader.di.ApiClient
import com.inputa.reader.di.DownloadClient
import com.inputa.reader.di.HealthClient
import com.inputa.reader.di.SearchClient
import com.inputa.reader.domain.repository.ServerBaseUrlProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按当前的服务器地址提供 Retrofit 实例。
 *
 * 这层之所以存在：**服务器地址是用户可改的设置**，而 Retrofit 的 baseUrl 是不可变的。
 * Web 版没这个问题（永远打同源的相对路径），所以这是移植新增的一层。
 *
 * 每次取用都拿当前地址比一次，地址没变就复用已建好的实例 —— 建 Retrofit 不贵但也不该
 * 每次请求都建。三个 client 各缓存一份，因为同一个地址要配三种超时（见 NetworkModule）。
 */
@Singleton
class ApiProvider @Inject constructor(
    private val baseUrlProvider: ServerBaseUrlProvider,
    // 显式写 @param: 而不是裸注解：Kotlin 2.x 会警告裸注解「当前只作用于值参数、
    // 将来也会作用于属性」，写出用途就没有歧义了。
    @param:ApiClient private val apiClient: OkHttpClient,
    @param:HealthClient private val healthClient: OkHttpClient,
    @param:DownloadClient private val downloadClient: OkHttpClient,
    @param:SearchClient private val searchClient: OkHttpClient,
    private val json: Json,
) {

    private class Cached(val baseUrl: String, val api: InputaApi)

    private val apiRef = AtomicReference<Cached?>(null)
    private val healthRef = AtomicReference<Cached?>(null)
    private val downloadRef = AtomicReference<Cached?>(null)
    private val searchRef = AtomicReference<Cached?>(null)

    /** 常规请求：词典。 */
    suspend fun api(): InputaApi = obtain(apiRef, apiClient)

    /** 启动时的健康探测，超时短得多。 */
    suspend fun healthApi(): InputaApi = obtain(healthRef, healthClient)

    /** 整本书下载，超时长得多。 */
    suspend fun downloadApi(): InputaApi = obtain(downloadRef, downloadClient)

    /** Gutendex 搜索。后端等上游 15 秒，所以它需要比词典那套更长的超时。 */
    suspend fun searchApi(): InputaApi = obtain(searchRef, searchClient)

    /** 设置页改地址后调用，丢掉缓存让下次取用时重建。 */
    fun invalidate() {
        apiRef.set(null)
        healthRef.set(null)
        downloadRef.set(null)
        searchRef.set(null)
    }

    private suspend fun obtain(ref: AtomicReference<Cached?>, client: OkHttpClient): InputaApi {
        val baseUrl = baseUrlProvider.current()
        ref.get()?.let { if (it.baseUrl == baseUrl) return it.api }

        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(APPLICATION_JSON))
            .build()
            .create(InputaApi::class.java)

        ref.set(Cached(baseUrl, api))
        return api
    }

    private companion object {
        val APPLICATION_JSON = "application/json".toMediaType()
    }
}
