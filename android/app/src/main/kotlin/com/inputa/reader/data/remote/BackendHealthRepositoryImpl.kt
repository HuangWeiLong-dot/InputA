package com.inputa.reader.data.remote

import com.inputa.reader.domain.repository.BackendHealth
import com.inputa.reader.domain.repository.BackendHealthRepository
import com.inputa.reader.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 后端健康探测。
 *
 * Web 版**每次页面加载探一次**并缓存 promise。Android 这边多一层判断：服务器地址是
 * 用户可改的设置，所以缓存的依据是「**为哪个地址探过**」而不是「探过没有」——
 * 换了地址就必须重探，否则换了地址还拿着旧后端的结论。
 *
 * [mutex] + [probedForUrl] 同时解决两件事：并发调用只探一次，以及地址变更后自动失效。
 * 这比在单例里养一个自建 `CoroutineScope` 干净 —— 没有需要手动取消的东西。
 *
 * 探测失败**不是错误状态**，只是「没有后端」：客户端的降级逻辑据此改走直连上游，
 * 所以这里照常返回一个 `ok = false` 的结果，不抛异常。
 */
@Singleton
class BackendHealthRepositoryImpl @Inject constructor(
    private val settings: SettingsRepository,
    private val apiProvider: ApiProvider,
) : BackendHealthRepository {

    private val state = MutableStateFlow(BackendHealth())
    private val mutex = Mutex()
    private var probedForUrl: String? = null

    override val health: Flow<BackendHealth> = state.asStateFlow().onStart { ensureProbed() }

    override suspend fun refresh(): BackendHealth = mutex.withLock {
        val result = probe()
        probedForUrl = settings.currentServerBaseUrl()
        state.value = result
        result
    }

    override suspend fun current(): BackendHealth = ensureProbed()

    private suspend fun ensureProbed(): BackendHealth {
        val baseUrl = settings.currentServerBaseUrl()
        return mutex.withLock {
            if (probedForUrl == baseUrl) {
                state.value
            } else {
                val result = probe()
                probedForUrl = baseUrl
                state.value = result
                result
            }
        }
    }

    private suspend fun probe(): BackendHealth = try {
        val response = apiProvider.healthApi().health()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            BackendHealth(
                ok = body.ok,
                service = body.service,
                dictionaryAvailable = body.dictionaryAvailable,
                deepseekKeyConfigured = body.deepseekKeyConfigured,
            )
        } else {
            BackendHealth(ok = false, error = "HTTP ${response.code()}")
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        BackendHealth(ok = false, error = error.message ?: error::class.simpleName)
    }
}
