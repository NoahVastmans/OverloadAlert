package kth.nova.overloadalert

import android.app.Application
import androidx.work.Configuration
import kth.nova.overloadalert.di.AppComponent
import kth.nova.overloadalert.worker.MyWorkerFactory

class OverloadAlertApplication : Application(), Configuration.Provider {

    lateinit var appComponent: AppComponent
        private set

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(MyWorkerFactory(appComponent.runningRepository, appComponent.analyzeRunData))
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        appComponent = AppComponent(this)
        
        // The background sync is now correctly scheduled in MainActivity after login.
        // WorkManager will be initialized automatically on first use with the provider above.
    }
}