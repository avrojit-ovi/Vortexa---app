package com.vortexa.core.database

import androidx.room.TypeConverter
import com.vortexa.core.database.entity.DownloadStatus

class Converters {

    @TypeConverter
    fun statusToString(status: DownloadStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String?): DownloadStatus = DownloadStatus.fromStorage(value)
}
