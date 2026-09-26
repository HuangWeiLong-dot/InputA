package com.inputa.reader.di

import android.util.Log
import com.inputa.reader.BuildConfig
import com.inputa.reader.data.remote.BackendHealthRepositoryImpl
import com.inputa.reader.data.remote.OkHttpProviderRequester
import com.inputa.reader.data.repository.DictionaryRepositoryImpl
import com.inputa.reader.domain.dict.DictionaryLookup
import com.inputa.reader.domain.dict.LookupCache
import com.inputa.reader.domain.dict.ProviderRequester
import com.inputa.reader.domain.repository.BackendHealthRepository
import com.inputa.reader.domain.repository.DictionaryRepository
import com.inputa.reader.domain.repository.ServerBaseUrlProvider
import com.inputa.reader.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HealthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SearchClient

/**
 * HTTP 客户端。
 *
 * **三个 client，不是一个。** 一个 client 无法同时服务三种时间尺度：
 * 启动探测要 2.5 秒内给出结论（否则用户对着白屏等），词典请求对应后端 6 秒的上游
 * 超时（客户端必须比它长，才能收到后端的真实错误而不是自己先中断），
 * 整书下载可以跑 90 秒。Web 版把这些写成三个常量，这里落成三个实例。
 *
 * 超时值都对着后端 `server/upstreams.js` 的表设：**客户端一律比服务端长**，
 * 这样浏览器/手机收到的是后端的真实状态码（404 还是 502），降级逻辑才有意义。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // 后端与上游都可能加字段；忽略未知键，免得加一个字段就崩客户端。
        ignoreUnknownKeys = true
    }

    /** 把设置仓库收窄成网络层真正需要的那一个值，见 [ServerBaseUrlProvider]。 */
    @Provides
    @Singleton
    fun provideServerBaseUrlProvider(settings: SettingsRepository): ServerBaseUrlProvider =
        ServerBaseUrlProvider { settings.currentServerBaseUrl() }

    /**
     * 降级链。做成可注入的，是为了让测试能换掉来源列表 —— 否则「后端路由失败后
     * 去试直连上游」那一步会打出真实的网络请求，测试就不密封了。
     */
    @Provides
    @Singleton
    fun provideDictionaryLookup(requester: ProviderRequester): DictionaryLookup = DictionaryLookup(
        requester = requester,
        cache = LookupCache(),
        log = { message -> if (BuildConfig.DEBUG) Log.d(DICTIONARY_TAG, message) },
    )

    private const val DICTIONARY_TAG = "Dictionary"

    @Provides
    @Singleton
    @ApiClient
    fun provideApiClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        // 后端对词典上游设的是 6 秒，这里必须更长。
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // 只打 basic（方法/URL/状态码/耗时）。绝不打 body ——
                // 第二阶段 AI 请求里会带 DeepSeek 的 Authorization 头。
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    /**
     * 健康探测。用 `newBuilder()` 从 [ApiClient] 派生而不是新建一个 —— 这样它们
     * 共享连接池与线程池，衍生 client 的代价接近零。
     *
     * 2.5 秒对着 Web 版的 `PROBE_TIMEOUT_MS`：地址填错时用户不该等 8 秒才知道。
     */
    @Provides
    @Singleton
    @HealthClient
    fun provideHealthClient(@ApiClient base: OkHttpClient): OkHttpClient = base.newBuilder()
        .callTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    /** 整书下载。后端给 Gutenberg 的上游超时是 30 秒、客户端 90 秒。 */
    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Gutendex 搜索。
     *
     * **这个 client 是被一次真实故障逼出来的。** 搜索一度返回一句无意义的 `timeout`：
     * 它借用的是 [ApiClient]，而那个是照**词典**的节奏配的（总计 10 秒），
     * 后端对 Gutendex 的上游超时却是 **15 秒** —— 客户端比服务端先放弃，
     * 于是永远收不到后端那条有信息的 502，读者只看到 "timeout"。
     *
     * 规则：**客户端的超时必须长于后端的每上游超时**，这样手机拿到的是后端的真实结论。
     * Web 版三个常量（8s / 20s / 90s 对后端的 6s / 15s / 30s）就是同一条规则。
     *
     * 与 [HealthClient] 一样从 [ApiClient] 派生，共享连接池，代价接近零。
     */
    @Provides
    @Singleton
    @SearchClient
    fun provideSearchClient(@ApiClient base: OkHttpClient): OkHttpClient = base.newBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingModule {

    @Binds
    abstract fun bindBackendHealthRepository(
        impl: BackendHealthRepositoryImpl,
    ): BackendHealthRepository

    @Binds
    abstract fun bindDictionaryRepository(impl: DictionaryRepositoryImpl): DictionaryRepository

    @Binds
    abstract fun bindProviderRequester(impl: OkHttpProviderRequester): ProviderRequester
}
