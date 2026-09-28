package dev.foodtracker.core.common.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.foodtracker.core.common.SystemTimeProvider
import dev.foodtracker.core.common.TimeProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {

    @Binds
    @Singleton
    abstract fun bindsTimeProvider(impl: SystemTimeProvider): TimeProvider
}
