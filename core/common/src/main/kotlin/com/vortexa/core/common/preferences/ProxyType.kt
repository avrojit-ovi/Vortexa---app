package com.vortexa.core.common.preferences

/** How the system reaches the HTTP proxy. Credentials live in the Keystore, never here. */
enum class ProxyType {
    HTTP,
    SOCKS,
    ;

    companion object {
        fun fromStorage(value: String?): ProxyType =
            entries.firstOrNull { it.name == value } ?: HTTP
    }
}
