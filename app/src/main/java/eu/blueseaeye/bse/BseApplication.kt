package eu.blueseaeye.bse

import android.app.Application

class BseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initializeSpeech()
    }
}
