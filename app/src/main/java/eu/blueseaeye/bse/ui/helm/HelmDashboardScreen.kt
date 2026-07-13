package eu.blueseaeye.bse.ui.helm

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.model.HelmMath
import eu.blueseaeye.bse.model.ReadingOutputMode
import eu.blueseaeye.bse.model.TargetMode
import eu.blueseaeye.bse.data.SettingsStore
import eu.blueseaeye.bse.monitor.HelmMonitor
import eu.blueseaeye.bse.ui.common.AdjustableSettingRow
import eu.blueseaeye.bse.ui.common.degreesPolish
import eu.blueseaeye.bse.ui.common.semanticButton
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HelmDashboardScreen(
    monitor: HelmMonitor,
    settingsStore: SettingsStore,
    modifier: Modifier = Modifier
) {
    val state by monitor.state.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ConnectionStatusLine(
            demoMode = settings.demoMode,
            connected = state.snapshot != null && !state.isConnectionLost
        )
        state.lastCrashReason?.let { reason ->
            CrashReportSection(
                reason = reason,
                onCopy = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("Sterigo — opis błędu", reason)
                    )
                },
                onDismiss = { monitor.clearCrashReason() }
            )
        }

        if (state.isConnectionLost) {
            ConnectionWarningSection(onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            })
        }

        // Bieżący stan — sam kafelek z odczytem (bez nagłówka i opisu)
        CompassCard(snapshot = state.snapshot, settings = settings)

        ControlsSection(monitor = monitor, settingsStore = settingsStore, settings = settings, state = state)
    }
}

@Composable
private fun ConnectionStatusLine(
    demoMode: Boolean,
    connected: Boolean
) {
    val text = when {
        demoMode && connected -> "Połączony z serwerem demo"
        demoMode && !connected -> "Brak połączenia z serwerem demo"
        !demoMode && connected -> "Połączony z siecią BlueSeaEye"
        else -> "Brak połączenia z siecią BlueSeaEye"
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (connected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (connected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun CrashReportSection(
    reason: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Aplikacja zakończyła się niespodziewanie",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "Powód (do diagnozy): $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onCopy()
                        copied = true
                    },
                    modifier = Modifier.semanticButton("Kopiuj do schowka. Kopiuje pełny opis błędu, aby wkleić go w wiadomości.")
                ) {
                    Text("Kopiuj do schowka")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.semanticButton("OK, ukryj. Ukrywa informację o poprzednim zamknięciu aplikacji.")
                ) {
                    Text("OK, ukryj")
                }
            }
            if (copied) {
                Text(
                    text = "Skopiowano do schowka.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

@Composable
private fun ConnectionWarningSection(onOpenSettings: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Połączenie utracone. Aplikacja ponawia odczyt i alarmuje użytkownika.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Jeśli połączenie nie wraca, sprawdź: po pierwsze, czy telefon jest połączony z siecią Wi-Fi BlueSeaEye. Po drugie, czy aplikacja działa i ma dostęp do sieci.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.semanticButton("Otwórz ustawienia aplikacji. Otwiera ustawienia systemowe aplikacji.")
            ) {
                Text("Otwórz ustawienia aplikacji")
            }
        }
    }
}

@Composable
private fun ControlsSection(
    monitor: HelmMonitor,
    settingsStore: SettingsStore,
    settings: AppSettings,
    state: eu.blueseaeye.bse.monitor.MonitorState
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val readingLabel = if (state.isReadingEnabled) "Stop" else "Czytaj"
            Button(
                onClick = monitor::toggleReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .semanticButton("$readingLabel. Włącza lub wyłącza komunikaty głosowe oraz sygnały.")
            ) {
                Text(readingLabel)
            }

            val targetModes = listOf(TargetMode.NONE, TargetMode.COURSE)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                targetModes.forEachIndexed { index, mode ->
                    val selected = settings.target == mode
                    SegmentedButton(
                        selected = selected,
                        onClick = {
                            settingsStore.update { s ->
                                when (mode) {
                                    TargetMode.NONE -> s.copy(target = mode, targetCourse = null, targetWind = null)
                                    TargetMode.COURSE -> s.copy(
                                        target = mode,
                                        targetCourse = s.targetCourse
                                            ?: HelmMath.normalizedCourse(state.snapshot?.course ?: 0.0)
                                    )
                                    TargetMode.WIND -> s.copy(target = mode)
                                }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, targetModes.size),
                        label = { Text(mode.title) }
                    )
                }
            }

            if (settings.target == TargetMode.COURSE) {
                // Kurs kompasowy prezentujemy jako 1–360 (360 = północ). Wewnętrznie
                // trzymamy 0–359 (0 = północ), więc 360 mapujemy na 0 przy zapisie,
                // a zapisane 0 pokazujemy jako 360.
                val displayCourse = (settings.targetCourse ?: 0.0).let { if (it == 0.0) 360.0 else it }
                AdjustableSettingRow(
                    label = "Zadany kurs",
                    value = displayCourse,
                    min = 1.0,
                    max = 360.0,
                    step = 1.0,
                    valueLabel = { degreesPolish(it.toInt()) },
                    onValueChange = { setTargetCourse(settingsStore, if (it >= 360.0) 0.0 else it) },
                    wrap = true,
                    allowKeyboardInput = true
                )
                FilledTonalButton(
                    onClick = monitor::holdCurrentCourse,
                    enabled = state.snapshot?.course != null,
                    modifier = Modifier.semanticButton("Ustaw aktualny kurs. Zapisuje aktualny kurs jako docelowy.")
                ) {
                    Text("Ustaw aktualny kurs")
                }
            }
        }
    }
}

private fun setTargetCourse(settingsStore: SettingsStore, value: Double) {
    settingsStore.update { s ->
        s.copy(targetCourse = HelmMath.normalizedCourse(value))
    }
}
