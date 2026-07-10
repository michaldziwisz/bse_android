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
import eu.blueseaeye.bse.ui.common.NumericSettingRow
import eu.blueseaeye.bse.ui.common.SectionHeaderText
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
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        state.lastCrashReason?.let { reason ->
            CrashReportSection(
                reason = reason,
                onCopy = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("BSE — opis błędu", reason)
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

        // Status
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeaderText(
                title = "Bieżący status",
                description = "Widok stale odświeża dane z urządzenia BlueSeaEye."
            )
            CompassCard(snapshot = state.snapshot, settings = settings)
            val snapshot = state.snapshot
            if (snapshot != null) {
                Text(
                    text = "Ostatnia aktualizacja: ${timeFormat.format(snapshot.fetchedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Brak odczytów. Połącz telefon z siecią Wi-Fi „BlueSeaEye” i sprawdź adres urządzenia w Ustawieniach.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ControlsSection(monitor = monitor, settingsStore = settingsStore, settings = settings, state = state)

        // Ostatni komunikat (live region — czytnik odczyta zmiany)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeaderText(
                title = "Ostatni komunikat",
                description = if (settings.readingOutput == ReadingOutputMode.ARIA)
                    "Treść komunikatu jest wysyłana jako ogłoszenie dla czytnika ekranu."
                else
                    "Treść odpowiada ostatniemu odczytowi wypowiedzianemu przez syntezator."
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.lastAnnouncement.ifEmpty { "Brak komunikatu." },
                    modifier = Modifier
                        .padding(16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }

        // Urządzenie
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderText(
                title = "Urządzenie",
                description = "Połącz telefon z siecią Wi-Fi „BlueSeaEye”. Adres urządzenia zmienisz w Ustawieniach."
            )
            Text(
                text = settings.deviceBaseUrl(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
        }
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
            SectionHeaderText(
                title = "Sterowanie odczytem",
                description = "Możesz czytać pełny kurs albo odchyłkę od zapamiętanego kursu."
            )

            val readingLabel = if (state.isReadingEnabled) "Zatrzymaj odczyt" else "Uruchom odczyt"
            Button(
                onClick = monitor::toggleReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .semanticButton("$readingLabel. Włącza lub wyłącza komunikaty głosowe oraz sygnały.")
            ) {
                Text(readingLabel)
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TargetMode.entries.forEachIndexed { index, mode ->
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
                                    TargetMode.WIND -> s.copy(
                                        target = mode,
                                        targetWind = s.targetWind
                                            ?: HelmMath.normalizedCourse(state.snapshot?.wind ?: 0.0)
                                    )
                                }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, TargetMode.entries.size),
                        label = { Text(mode.title) }
                    )
                }
            }

            if (settings.target == TargetMode.COURSE) {
                NumericSettingRow(
                    title = "Zadany kurs",
                    valueText = String.format("%03.0f°", settings.targetCourse ?: 0.0),
                    decrementLabel = "Zmniejsz zadany kurs",
                    incrementLabel = "Zwiększ zadany kurs",
                    hint = "Zmiana co 1 stopień.",
                    onDecrement = { adjustTargetCourse(settingsStore, state, -1.0) },
                    onIncrement = { adjustTargetCourse(settingsStore, state, 1.0) }
                )
                FilledTonalButton(
                    onClick = monitor::holdCurrentCourse,
                    enabled = state.snapshot?.course != null,
                    modifier = Modifier.semanticButton("Ustaw aktualny kurs. Zapisuje aktualny kurs jako docelowy.")
                ) {
                    Text("Ustaw aktualny kurs")
                }
            }

            if (settings.target == TargetMode.WIND) {
                NumericSettingRow(
                    title = "Zadany kąt do wiatru",
                    valueText = String.format("%03.0f°", settings.targetWind ?: 0.0),
                    decrementLabel = "Zmniejsz zadany kąt do wiatru",
                    incrementLabel = "Zwiększ zadany kąt do wiatru",
                    hint = "Zmiana co 1 stopień.",
                    onDecrement = { adjustTargetWind(settingsStore, state, -1.0) },
                    onIncrement = { adjustTargetWind(settingsStore, state, 1.0) }
                )
                FilledTonalButton(
                    onClick = monitor::holdCurrentWind,
                    enabled = state.snapshot?.wind != null,
                    modifier = Modifier.semanticButton("Ustaw aktualny kąt do wiatru. Zapisuje aktualny kąt do wiatru jako docelowy.")
                ) {
                    Text("Ustaw aktualny kąt do wiatru")
                }
            }
        }
    }
}

private fun adjustTargetCourse(
    settingsStore: SettingsStore,
    state: eu.blueseaeye.bse.monitor.MonitorState,
    delta: Double
) {
    settingsStore.update { s ->
        val current = s.targetCourse ?: HelmMath.normalizedCourse(state.snapshot?.course ?: 0.0)
        s.copy(targetCourse = HelmMath.normalizedCourse(current + delta))
    }
}

private fun adjustTargetWind(
    settingsStore: SettingsStore,
    state: eu.blueseaeye.bse.monitor.MonitorState,
    delta: Double
) {
    settingsStore.update { s ->
        val current = s.targetWind ?: HelmMath.normalizedCourse(state.snapshot?.wind ?: 0.0)
        s.copy(targetWind = HelmMath.normalizedCourse(current + delta))
    }
}
