package dev.foodtracker.core.common.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainDispatcher

/** Application-lifetime scope for work that must outlive a ViewModel, e.g. the re-analysis queue. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
