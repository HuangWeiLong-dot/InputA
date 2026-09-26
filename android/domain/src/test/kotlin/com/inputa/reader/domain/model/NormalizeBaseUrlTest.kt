package com.inputa.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 服务器地址规范化。纯函数，所以放在领域层测 —— 这里出的错在真机上表现为
 * 「连不上」，而且 Retrofit 抛的 `baseUrl must end in /` 会让人以为是别的问题。
 */
class NormalizeBaseUrlTest {

    @Test
    fun `appends the trailing slash Retrofit requires`() {
        assertEquals("http://192.168.1.20:8787/", normalizeBaseUrl("http://192.168.1.20:8787"))
        assertEquals("http://10.0.2.2:8787/", normalizeBaseUrl("http://10.0.2.2:8787/"))
        assertEquals("http://example.com/", normalizeBaseUrl("http://example.com////"))
    }

    /**
     * 裸写 `192.168.1.20:8787` 会被 URI 解析成 scheme = `192.168.1.20`，
     * 于是请求发不出去且报错莫名其妙。补上 http:// 是这里最能省事的一步。
     */
    @Test
    fun `adds a scheme when the user typed none`() {
        assertEquals("http://192.168.1.20:8787/", normalizeBaseUrl("192.168.1.20:8787"))
        assertEquals("http://10.0.2.2:8787/", normalizeBaseUrl("10.0.2.2:8787"))
        assertEquals("http://localhost:8787/", normalizeBaseUrl("localhost:8787"))
    }

    @Test
    fun `keeps https`() {
        assertEquals("https://reader.example.com/", normalizeBaseUrl("https://reader.example.com"))
        assertEquals("https://reader.example.com/api/", normalizeBaseUrl("https://reader.example.com/api"))
    }

    @Test
    fun `keeps a path prefix, since the backend may be behind a reverse proxy`() {
        assertEquals("http://example.com/reader/", normalizeBaseUrl("http://example.com/reader"))
    }

    @Test
    fun `drops query strings and fragments, which are not part of a base url`() {
        assertEquals("http://example.com/", normalizeBaseUrl("http://example.com/?x=1"))
        assertEquals("http://example.com/", normalizeBaseUrl("http://example.com/#frag"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("http://10.0.2.2:8787/", normalizeBaseUrl("  http://10.0.2.2:8787  "))
    }

    @Test
    fun `rejects non-http schemes`() {
        assertNull(normalizeBaseUrl("ftp://example.com"))
        assertNull(normalizeBaseUrl("file:///etc/hosts"))
    }

    /**
     * 这一批是「补 scheme」带来的坑：`javascript:alert(1)` 不含 `://`，
     * 于是会被补成 `http://javascript:alert(1)/` —— 一个看起来合法、实际连不上的地址。
     * 用户于是看到「连不上服务」而不是「地址不合法」，排查方向被完全带偏。
     */
    @Test
    fun `rejects input whose host does not look like a host`() {
        assertNull(normalizeBaseUrl("javascript:alert(1)"))
        assertNull(normalizeBaseUrl("not a host"))
        assertNull(normalizeBaseUrl("http://foo bar"))
        assertNull(normalizeBaseUrl("http://example.com:notaport"))
        assertNull(normalizeBaseUrl("http://example.com:99999999"))
        assertNull(normalizeBaseUrl("http://.com"))
        assertNull(normalizeBaseUrl("http://-leading-dash.com"))
    }

    @Test
    fun `accepts IPv6 literals`() {
        assertEquals("http://[::1]:8787/", normalizeBaseUrl("http://[::1]:8787"))
    }

    @Test
    fun `rejects a missing or empty host`() {
        assertNull(normalizeBaseUrl(""))
        assertNull(normalizeBaseUrl("   "))
        assertNull(normalizeBaseUrl("http://"))
        assertNull(normalizeBaseUrl("http://:8787"))
        assertNull(normalizeBaseUrl("https:///path"))
    }

    @Test
    fun `the default points at the emulator host loopback`() {
        // 10.0.2.2 是模拟器里指向宿主机的地址；真机要在设置页改成局域网 IP。
        assertEquals("http://10.0.2.2:8787/", DEFAULT_SERVER_BASE_URL)
        assertEquals(DEFAULT_SERVER_BASE_URL, normalizeBaseUrl(DEFAULT_SERVER_BASE_URL))
    }
}
