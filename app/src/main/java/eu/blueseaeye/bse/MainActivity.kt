package eu.blueseaeye.bse

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import eu.blueseaeye.bse.diagnostics.CrashReporter
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.service.HelmForegroundService
import eu.blueseaeye.bse.ui.RootScreen
import eu.blueseaeye.bse.ui.theme.BseTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as BseApplication).container }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* wynik nieistotny — alert i tak spróbuje, a brak zgody tylko go wyciszy */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        // Ekran nie gaśnie w trakcie aktywnego odczytu (odpowiednik isIdleTimerDisabled)
        // oraz uruchamiamy/zatrzymujemy usługę pierwszoplanową wraz z odczytem.
        lifecycleScope.launch {
            container.monitor.state
                .map { it.isReadingEnabled }
                .distinctUntilChanged()
                .collect { reading ->
                    if (reading) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        HelmForegroundService.start(this@MainActivity)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        HelmForegroundService.stop(this@MainActivity)
                    }
                }
        }

        // Połączenie z siecią BlueSeaEye trzymamy OD RAZU po starcie (nie dopiero
        // po włączeniu odczytu), żeby transmisja była pewna od pierwszej chwili.
        // Reagujemy też na zmianę ustawień: włączenie trybu demo lub wyłączenie
        // „trzymaj sieć" zdejmuje wiązanie, powrót — przywraca. Systemowe okno
        // zgody „Połączyć z BlueSeaEye?" pojawia się raz, gdy jesteśmy na wierzchu.
        lifecycleScope.launch {
            container.settingsStore.settings
                .map { it.keepDeviceWifi && !it.demoMode }
                .distinctUntilChanged()
                .collect { shouldHold ->
                    if (shouldHold) {
                        container.deviceNetworkBinder.start(
                            AppSettings.DEVICE_WIFI_SSID,
                            AppSettings.DEVICE_WIFI_PASSPHRASE
                        )
                    } else {
                        container.deviceNetworkBinder.stop()
                    }
                }
        }

        container.monitor.start()

        // Diagnostyka: jeśli poprzednie uruchomienie się wywaliło, pokaż i
        // przeczytaj powód (do skopiowania). Potem ewentualne wznowienie odczytu
        // po ubiciu procesu — zależnie od ustawienia użytkownika.
        lifecycleScope.launch {
            val reason = CrashReporter.consumeLastCrashReason(this@MainActivity)
            container.monitor.reportPreviousCrashIfAny(reason)
            container.monitor.resumeIfNeeded(launchedFromService = false)
        }

        setContent {
            BseTheme {
                RootScreen(
                    monitor = container.monitor,
                    settingsStore = container.settingsStore
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            container.monitor.stop()
            HelmForegroundService.stop(this)
        }
    }
}
