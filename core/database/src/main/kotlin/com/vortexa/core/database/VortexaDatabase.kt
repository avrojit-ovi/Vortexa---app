package com.vortexa.core.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vortexa.core.database.dao.BrowserEntryDao
import com.vortexa.core.database.dao.DownloadDao
import com.vortexa.core.database.entity.BrowserEntryEntity
import com.vortexa.core.database.entity.DownloadEntity

@Database(
    entities = [DownloadEntity::class, BrowserEntryEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VortexaDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun browserEntryDao(): BrowserEntryDao

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

        /** v2 -> v3: browser bookmarks and history share one table, told apart by `kind`. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS browser_entries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "url TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "visited_at INTEGER NOT NULL)",
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_browser_entries_kind ON browser_entries (kind)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_browser_entries_url ON browser_entries (url)")
            }
        }
    }
}
