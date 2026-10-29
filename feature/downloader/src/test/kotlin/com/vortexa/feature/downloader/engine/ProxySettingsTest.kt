package com.vortexa.feature.downloader.engine

import java.net.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySettingsTest {

    @Test
    fun `a switched off proxy is not an endpoint`() {
        assertNull(
            ProxySettings.from(
                enabled = false,
                kind = ProxyKind.HTTP,
                host = "10.0.0.2",
                port = 8080,
            ),
        )
    }

    @Test
    fun `a half filled form never reaches the engine`() {
        assertNull(ProxySettings.from(true, ProxyKind.HTTP, host = "   ", port = 8080))
        assertNull(ProxySettings.from(true, ProxyKind.HTTP, host = "proxy.local", port = 0))
        assertNull(ProxySettings.from(true, ProxyKind.HTTP, host = "proxy.local", port = 65_536))
    }

    @Test
    fun `a complete endpoint is trimmed and kept`() {
        val proxy = ProxySettings.from(
            enabled = true,
            kind = ProxyKind.SOCKS,
            host = "  proxy.local  ",
            port = 1080,
            username = "  naveen ",
            password = "s3cret",
        )

        assertEquals("proxy.local", proxy?.host)
        assertEquals(1080, proxy?.port)
        assertEquals("naveen", proxy?.username)
        assertEquals(ProxyKind.SOCKS, proxy?.kind)
        assertTrue(proxy?.requiresAuth == true)
    }

    @Test
    fun `an endpoint without a username does not try to authenticate`() {
        val proxy = ProxySettings.from(true, ProxyKind.HTTP, "proxy.local", 3128)

        assertEquals(false, proxy?.requiresAuth)
    }

    @Test
    fun `the protocol picks the java proxy type`() {
        val http = ProxySettings.from(true, ProxyKind.HTTP, "proxy.local", 3128)!!
        val socks = ProxySettings.from(true, ProxyKind.SOCKS, "proxy.local", 1080)!!

        assertEquals(Proxy.Type.HTTP, http.toJavaProxy().type())
        assertEquals(Proxy.Type.SOCKS, socks.toJavaProxy().type())
        assertEquals(1080, (socks.toJavaProxy().address() as java.net.InetSocketAddress).port)
    }
}
