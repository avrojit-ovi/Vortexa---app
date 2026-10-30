package com.vortexa.core.database

import androidx.room.TypeConverter
import com.vortexa.core.database.entity.BrowserEntryKind
import com.vortexa.core.database.entity.DownloadStatus

class Converters {

    @TypeConverter
    fun statusToString(status: DownloadStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String?): DownloadStatus = DownloadStatus.fromStorage(value)

    @TypeConverter
    fun browserKindToString(kind: BrowserEntryKind): String = kind.name

    @TypeConverter
    fun stringToBrowserKind(value: String?): BrowserEntryKind =
        value?.let { runCatching { BrowserEntryKind.valueOf(it) }.getOrNull() } ?: BrowserEntryKind.HISTORY
}
