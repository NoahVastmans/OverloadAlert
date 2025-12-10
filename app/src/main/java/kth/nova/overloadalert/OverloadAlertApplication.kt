package kth.nova.overloadalert

import android.app.Application
import kth.nova.overloadalert.di.AppComponent

class OverloadAlertApplication : Application() {

    lateinit var appComponent: AppComponent

    override fun onCreate() {
        super.onCreate()
        appComponent = AppComponent(this)
    }
}