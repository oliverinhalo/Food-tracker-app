package dev.foodtracker.core.network.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.foodtracker.core.network.BuildConfig
import dev.foodtracker.core.network.ConnectivityNetworkMonitor
import dev.foodtracker.core.network.NetworkMonitor
import dev.foodtracker.core.network.RetryInterceptor
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor())
        .apply {
            if (BuildConfig.VERBOSE_HTTP_LOGS) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        // BODY would dump base64 image payloads into logcat; HEADERS is enough.
                        level = HttpLoggingInterceptor.Level.HEADERS
                    },
                )
            }
        }
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindsModule {

    @Binds
    @Singleton
    abstract fun bindsNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}
