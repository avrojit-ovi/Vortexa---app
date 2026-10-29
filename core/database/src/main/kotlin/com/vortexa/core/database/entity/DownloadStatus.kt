package com.vortexa.core.database.entity

/**
 * Lifecycle of a download. Stored as its name, so the order of the entries can
 * change without a migration.
 */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED,
    ;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELED

    companion object {
        fun fromStorage(value: String?): DownloadStatus =
            entries.firstOrNull { it.name == value } ?: QUEUED
    }
}
