package com.vortexa.feature.downloader.engine

import java.net.InetSocketAddress
import java.net.Proxy

/** Which protocol the system speaks to the proxy. */
enum class ProxyKind {
    HTTP,
    SOCKS,
}

/**
 * A usable proxy endpoint. Constructed only through [from], so a half-filled
 * settings form can never reach the engine: an unusable proxy is null and the
 * download simply connects directly.
 *
 * The password is passed in memory and only ever attached to the
 * `Proxy-Authorization` header; nothing here is persisted.
 */
data class ProxySettings(
    val kind: ProxyKind,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    val requiresAuth: Boolean get() = username.isNotBlank()

    fun toJavaProxy(): Proxy = Proxy(
        if (kind == ProxyKind.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP,
        InetSocketAddress(host, port),
    )

    companion object {
        val VALID_PORTS = 1..65535

        /** Null when the proxy is switched off or the host/port pair cannot work. */
        fun from(
            enabled: Boolean,
            kind: ProxyKind,
            host: String,
            port: Int,
            username: String = "",
            password: String = "",
        ): ProxySettings? {
            if (!enabled) return null
            val cleanHost = host.trim()
            if (cleanHost.isEmpty() || port !in VALID_PORTS) return null
            return ProxySettings(
                kind = kind,
                host = cleanHost,
                port = port,
                username = username.trim(),
                password = password,
            )
        }
    }
}
