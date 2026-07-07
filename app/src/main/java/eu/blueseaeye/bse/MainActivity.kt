package eu.blueseaeye.bse

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
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

        // Ekran nie gaśnie w trakcie aktywnego odczytu (odpowiednik isIdleTimerDisabled).
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

        container.monitor.start()

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
