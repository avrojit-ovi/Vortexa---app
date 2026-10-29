package com.vortexa.core.online.community

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Read-only JSON endpoints, one shared client.
 *
 * The client is created here rather than bound as `OkHttpClient` so this module
 * cannot collide with the downloader's own client binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object CommunityModule {

    private val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    @Provides
    @Singleton
    fun sponsorBlockClient(): SponsorBlockClient = SponsorBlockClient(client)

    @Provides
    @Singleton
    fun dislikeClient(): ReturnYouTubeDislikeClient = ReturnYouTubeDislikeClient(client)
}
