package vn.baokim.qa

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Provides the [HiltWorkerFactory] so `@HiltWorker`s (E7 notification poll/dismiss) get their
 * dependencies injected. The default WorkManager initializer is disabled in the manifest so
 * WorkManager picks up this on-demand [Configuration].
 */
@HiltAndroidApp
class QaApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
