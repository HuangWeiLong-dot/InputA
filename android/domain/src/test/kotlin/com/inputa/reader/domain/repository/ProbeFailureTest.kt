package com.inputa.reader.domain.repository

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 探测失败原因的翻译。
 *
 * 这一批测试保护的核心是**不泄漏地址**：OkHttp 的失败消息里通常写着主机，早先的实现
 * 直接把 `error.message` 交给设置页显示（「连不上：…」），于是「界面上不出现后端地址」
 * 那条就白做了。最后一条兜底分支尤其要注意 —— 它只能给出类型名，因为异常消息里可能
 * 就是那个地址。
 */
class ProbeFailureTest {

    @Test
    fun `maps the failures a reader can act on`() {
        assertEquals("域名解析失败", probeFailureReason(UnknownHostException("no address")))
        assertEquals("连接超时", probeFailureReason(SocketTimeoutException("timeout")))
        assertEquals("无法连接", probeFailureReason(ConnectException("refused")))
        // SSLHandshakeException 是 SSLException 的子类，应当落到同一条分支。
        assertEquals("TLS 握手失败", probeFailureReason(SSLHandshakeException("bad cert")))
    }

    @Test
    fun `never leaks a host out of the exception message`() {
        // 下面这两条消息就是 OkHttp 真实会产出的形状。
        val refused = ConnectException("Failed to connect to /203.0.113.7:443")
        val unresolved = UnknownHostException(
            "Unable to resolve host \"api.example.test\": No address associated with hostname",
        )

        assertFalse(probeFailureReason(refused).contains("203.0.113.7"))
        assertFalse(probeFailureReason(unresolved).contains("example.test"))
    }

    @Test
    fun `falls back to the type name, which cannot contain an address`() {
        val reason = probeFailureReason(IllegalStateException("Failed to connect to /203.0.113.7:443"))

        assertFalse(reason.contains("203.0.113.7"))
        assertEquals("请求失败（IllegalStateException）", reason)
    }
}
