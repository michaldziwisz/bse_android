package eu.blueseaeye.bse

import android.content.Context
import eu.blueseaeye.bse.audio.TonePlayer
import eu.blueseaeye.bse.audio.TtsSpeaker
import eu.blueseaeye.bse.data.SettingsStore
import eu.blueseaeye.bse.monitor.HelmMonitor
import eu.blueseaeye.bse.network.HelmApiClient
import eu.blueseaeye.bse.service.SafetyNotifications

/**
 * Ręczny kontener zależności (bez frameworka DI), analogicznie do wzorca z
 * projektu TyfloCentrum Android. Trzyma jedną instancję monitora i usług na
 * cały cykl życia procesu.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(appContext)
    val speaker = TtsSpeaker(appContext)
    private val tonePlayer = TonePlayer()
    private val apiClient = HelmApiClient()
    val notifications = SafetyNotifications(appContext)

    val monitor = HelmMonitor(
        settingsStore = settingsStore,
        apiClient = apiClient,
        tonePlayer = tonePlayer,
        speaker = speaker,
        onConnectionLostAlert = { message ->
            notifications.scheduleConnectionLostAlert(
                "Sprawdź połączenie lub zakłócenia transmisji. Aplikacja nadal ponawia odczyt."
            )
        },
        onConnectionRecovered = {
            notifications.clearConnectionLostAlert()
        }
    )

    fun initializeSpeech() {
        speaker.initialize()
    }
}
