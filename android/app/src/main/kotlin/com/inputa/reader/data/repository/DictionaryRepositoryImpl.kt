package com.inputa.reader.data.repository

import com.inputa.reader.domain.dict.DictionaryLookup
import com.inputa.reader.domain.model.LookupResult
import com.inputa.reader.domain.repository.BackendHealthRepository
import com.inputa.reader.domain.repository.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 词典查询。**刻意是一个薄适配器** —— 降级链本身（试哪个来源、404 与故障的区别、
 * 缓存什么）全在领域层的 [DictionaryLookup] 里，那里用假的 requester 就能把每条分支
 * 测到。这里只做一件事：把当前的健康状态翻译成链需要的两个布尔量。
 *
 * [DictionaryLookup] 通过注入而不是就地构造，是为了让测试能换掉来源列表 ——
 * 否则「后端路由失败后去试直连上游」这一步会打出真实的网络请求，测试就不密封了。
 */
@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    private val chain: DictionaryLookup,
    private val health: BackendHealthRepository,
) : DictionaryRepository {

    override suspend fun lookup(word: String, refreshBackend: Boolean): LookupResult {
        val healthState = if (refreshBackend) health.refresh() else health.current()
        val backendUp = healthState.ok
        // 后端在跑、且它没说「词典库不在」时才试离线词库。`null` 表示老服务器没报这个
        // 字段 —— 那要照常试，而不是当成没有。
        val localDictionaryReady = backendUp && healthState.dictionaryAvailable != false
        return chain.lookup(word, backendUp, localDictionaryReady)
    }
}
