package com.vortexa.core.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vortexa.core.database.dao.DownloadDao
import com.vortexa.core.database.entity.DownloadEntity

@Database(
    entities = [DownloadEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VortexaDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "vortexa.db"

        /**
         * v1 -> v2: per-range progress, so a resumed download continues each
         * range at its own offset instead of trusting one aggregate number.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE downloads ADD COLUMN segments TEXT")
            }
        }
    }
}
