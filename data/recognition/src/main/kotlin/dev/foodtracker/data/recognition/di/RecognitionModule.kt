package dev.foodtracker.data.recognition.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.foodtracker.data.recognition.gemini.GeminiFoodRecognizer
import dev.foodtracker.data.recognition.OfflineReanalysisQueue
import dev.foodtracker.data.recognition.local.OnDeviceFoodRecognizer
import dev.foodtracker.domain.recognition.CloudFoodRecognizer
import dev.foodtracker.domain.recognition.LocalFoodRecognizer
import dev.foodtracker.domain.recognition.RecognitionMerger
import dev.foodtracker.domain.recognition.ReanalysisQueue
import dev.foodtracker.domain.recognition.RecognitionOrchestrator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecognitionBindsModule {

    @Binds
    @Singleton
    abstract fun bindsCloudRecognizer(impl: GeminiFoodRecognizer): CloudFoodRecognizer

    @Binds
    @Singleton
    abstract fun bindsLocalRecognizer(impl: OnDeviceFoodRecognizer): LocalFoodRecognizer

    @Binds
    @Singleton
    abstract fun bindsReanalysisQueue(impl: OfflineReanalysisQueue): ReanalysisQueue
}

@Module
@InstallIn(SingletonComponent::class)
object RecognitionModule {

    @Provides
    @Singleton
    fun providesMerger(): RecognitionMerger = RecognitionMerger()

    @Provides
    @Singleton
    fun providesOrchestrator(
        local: LocalFoodRecognizer,
        cloud: CloudFoodRecognizer,
        merger: RecognitionMerger,
        reanalysisQueue: ReanalysisQueue,
    ): RecognitionOrchestrator = RecognitionOrchestrator(
        localRecognizer = local,
        cloudRecognizer = cloud,
        merger = merger,
        reanalysisQueue = reanalysisQueue,
    )
}
