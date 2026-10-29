package com.vortexa.core.database.di

import android.content.Context
import androidx.room.Room
import com.vortexa.core.database.VortexaDatabase
import com.vortexa.core.database.dao.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VortexaDatabase =
        Room.databaseBuilder(context, VortexaDatabase::class.java, VortexaDatabase.NAME)
            .addMigrations(VortexaDatabase.MIGRATION_1_2)
            // Only a downgrade may wipe data; an upgrade never does.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideDownloadDao(database: VortexaDatabase): DownloadDao = database.downloadDao()
}
