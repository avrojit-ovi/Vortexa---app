package com.vortexa.feature.onlinevideo

import com.vortexa.core.online.BuiltInSourceProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** One NewPipe initialisation for the whole process. */
@Singleton
class NewPipeInitializerHolder @Inject constructor() {
    private val initializer = NewPipeInitializer()

    fun get(): NewPipeInitializer = initializer
}

/** Contributes layer (a) to the online registry. */
class YouTubeSourceProvider(
    private val initializer: NewPipeInitializer,
) : BuiltInSourceProvider {
    override fun extractors() = listOf(YouTubeExtractor(initializer))
}

@Module
@InstallIn(SingletonComponent::class)
object OnlineVideoModule {

    @Provides
    @Singleton
    fun newPipeInitializer(holder: NewPipeInitializerHolder): NewPipeInitializer = holder.get()

    @Provides
    @Singleton
    @IntoSet
    fun youtubeSource(initializer: NewPipeInitializer): BuiltInSourceProvider =
        YouTubeSourceProvider(initializer)
}
