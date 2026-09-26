package com.inputa.reader.data.remote

import com.inputa.reader.domain.dict.ProviderOutcome
import com.inputa.reader.domain.dict.ProviderRequester
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 HTTP 响应翻译成降级链要的三态。
 *
 * 这几行就是 Web 版 `requestProvider` 的等价物，语义必须一致：
 *
 *   - **404 是「词典里没这个词」这一确定结论**，会被缓存，不再问别的来源
 *   - 其余非 2xx、连接失败、超时、以及**无法解析的响应体**都是临时故障，不会被缓存
 *
 * 最后一条值得展开：后端对上游是状态码与响应体**原样透传**的，包括把上游返回的
 * HTML 错误页也标成 `application/json`。所以反序列化失败是正常会发生的路径，
 * 必须归为故障 —— 若把它当成「没这个词」，读者会看到一个错误的结论并被缓存下来。
 */
@Singleton
class OkHttpProviderRequester @Inject constructor(
    private val apiProvider: ApiProvider,
) : ProviderRequester {

    override suspend fun request(url: String): ProviderOutcome = try {
        val response = apiProvider.api().json(url)
        when {
            response.code() == 404 -> ProviderOutcome.Missing
            !response.isSuccessful -> ProviderOutcome.Failure("HTTP ${response.code()}")
            else -> response.body()?.let(::asPayload)
                ?: ProviderOutcome.Failure("empty response body")
        }
    } catch (cancellation: CancellationException) {
        // 协程取消必须原样抛出，否则会被当成一次「传输失败」而污染降级结果。
        throw cancellation
    } catch (error: Exception) {
        ProviderOutcome.Failure(error.message ?: error::class.simpleName ?: "request failed")
    }

    /**
     * 一个响应体算不算「有效载荷」。
     *
     * **这里必须挡一道**，因为 Retrofit 的 kotlinx-serialization 转换器对裸文本是宽松的：
     * 一段 `<html><body>522</body></html>` 配着 `application/json` 回来时，它不会抛异常，
     * 而是解成一个字符串标量。若不挡，链会把它当成「答了但没释义」，于是读者在
     * **代理或上游坏掉**时看到的是「词典均未收录该词」—— 一个错误的结论，而且会被缓存。
     *
     * Web 版没有这个洞：`res.json()` 对 HTML 抛异常，自然归为传输故障。这条就是它的等价物。
     *
     * 判据是「对象或数组」而不是检查 `Content-Type` —— 后者没用：后端对上游是
     * **响应体原样透传**的，包括把上游的 HTML 标成 `application/json`。
     * 而这四个上游一律以对象或数组作答，所以标量只可能来自坏掉的中间层。
     */
    private fun asPayload(element: JsonElement): ProviderOutcome =
        if (element is JsonObject || element is JsonArray) {
            ProviderOutcome.Payload(element)
        } else {
            ProviderOutcome.Failure("response is not a JSON object or array")
        }
}
