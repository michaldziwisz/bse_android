package eu.blueseaeye.bse

import android.app.Application
import eu.blueseaeye.bse.diagnostics.CrashReporter

class BseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
        container.initializeSpeech()
    }
}
