package dev.foodtracker

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FoodTrackerApplication : Application(), Configuration.Provider {

    /**
     * WorkManager instantiates workers itself, so it needs Hilt's factory to be able to build
     * [dev.foodtracker.data.recognition.ReanalysisWorker]. Without this the re-analysis worker
     * throws on construction the first time it runs, long after the code that queued it.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictMode()
    }

    /**
     * Turns main-thread disk and network work into something visible during development.
     *
     * "Snappy" is not a thing you add at the end: it is the absence of a read, a write or a
     * request on the thread that draws. Logging rather than crashing, because the framework itself
     * touches disk during startup and a crash there would say nothing about our own code.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
