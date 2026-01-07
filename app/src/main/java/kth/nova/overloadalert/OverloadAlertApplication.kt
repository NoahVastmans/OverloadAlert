package kth.nova.overloadalert

import android.app.Application
import androidx.work.Configuration
import kth.nova.overloadalert.di.AppComponent
import kth.nova.overloadalert.worker.MyWorkerFactory

/**
 * The main Application class for the Overload Alert app.
 *
 * This class serves as the entry point for application-wide initialization and state management.
 * It is responsible for:
 * - Initializing the Dependency Injection container ([AppComponent]).
 * - Configuring the [androidx.work.WorkManager] with a custom [MyWorkerFactory] to support
 *   dependency injection in background workers.
 *
 * Implements [Configuration.Provider] to supply the custom WorkManager configuration on demand.
 */
class OverloadAlertApplication : Application(), Configuration.Provider {

    lateinit var appComponent: AppComponent
        private set

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(MyWorkerFactory(appComponent.runningRepository, appComponent.analysisRepository))
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        appComponent = AppComponent(this)
        
        // The background sync is now correctly scheduled in MainActivity after login.
        // WorkManager will be initialized automatically on first use with the provider above.
    }
}