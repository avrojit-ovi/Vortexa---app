package com.vortexa.core.common.preferences

/** App wide theme selection. [AMOLED] is a true-black variant of [DARK]. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}
