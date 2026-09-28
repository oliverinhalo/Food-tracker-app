package dev.foodtracker

import android.app.Application
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
