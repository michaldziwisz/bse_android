package eu.blueseaeye.bse.ui.settings

import android.text.InputType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.blueseaeye.bse.data.SettingsStore
import eu.blueseaeye.bse.model.AdministrationAction
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.monitor.HelmMonitor
import eu.blueseaeye.bse.ui.common.AdjustableSettingRow
import eu.blueseaeye.bse.ui.common.LabeledTextField
import eu.blueseaeye.bse.ui.common.SectionHeaderText
import eu.blueseaeye.bse.ui.common.degreesPolish
import eu.blueseaeye.bse.ui.common.semanticButton
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    monitor: HelmMonitor,
    modifier: Modifier = Modifier
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val monitorState by monitor.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        DeviceSection(settingsStore, settings)
        HorizontalDivider()
        ReadingSection(settingsStore, settings)
        HorizontalDivider()
        ToneSection(settingsStore, settings)
        // Sekcję „Zaawansowane” (wychylenie steru) pokazujemy tylko wtedy, gdy
        // urządzenie faktycznie dostarcza dane o wychyleniu płetwy steru (rsa) —
        // analogicznie jak przy wietrze.
        if (monitorState.snapshot?.rudder != null) {
            HorizontalDivider()
            AdvancedSection(settingsStore, settings)
        }
        HorizontalDivider()
        DeviceActionsSection(monitor)
    }
}

@Composable
private fun DeviceSection(store: SettingsStore, settings: AppSettings) {
    SettingsSection("Urządzenie") {
        ToggleRow("Tryb demonstracyjny", settings.demoMode) { v ->
            store.update { it.copy(demoMode = v) }
        }
        if (!settings.demoMode) {
            LabeledTextField(
                value = settings.deviceHost,
                onValueChange = { store.update { s -> s.copy(deviceHost = it) } },
                label = "Adres urządzenia BlueSeaEye",
                singleLine = true,
                keyboardType = InputType.TYPE_TEXT_VARIATION_URI
            )
            OutlinedButton(
                enabled = settings.deviceHost != AppSettings.DEFAULT_DEVICE_HOST,
                onClick = { store.update { it.copy(deviceHost = AppSettings.DEFAULT_DEVICE_HOST) } },
                modifier = Modifier.semanticButton("Przywróć domyślny adres")
            ) {
                Text("Przywróć domyślny adres")
            }
        }
        Text(
            text = if (settings.demoMode)
                "Tryb demonstracyjny jest włączony. Aplikacja pobiera dane z serwera ${AppSettings.DEMO_BASE_URL} przez internet. Wyłącz go, aby łączyć się ze sprzętem w sieci Wi-Fi „BlueSeaEye”."
            else
                "Połącz telefon z siecią Wi-Fi „BlueSeaEye” (hasło blueseaeye). Domyślny adres ${AppSettings.DEFAULT_DEVICE_HOST} odpowiada urządzeniu w trybie access pointa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!settings.demoMode) {
            ToggleRow("Trzymaj się sieci urządzenia", settings.keepDeviceWifi) { v ->
                store.update { it.copy(keepDeviceWifi = v) }
            }
            Text(
                text = "Gdy włączone, w trakcie odczytu aplikacja przypina swój ruch do sieci Wi-Fi „BlueSeaEye”, aby telefon nie przełączył się na inną sieć (np. statkowy internet), gdy ta chwilowo złapie lepszy zasięg. Przy pierwszym użyciu system poprosi o zgodę na połączenie z siecią urządzenia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadingSection(store: SettingsStore, settings: AppSettings) {
    SettingsSection("Odczyt") {
        AdjustableSettingRow(
            label = "Mów co",
            value = settings.readingInterval,
            min = 1.0,
            max = 45.0,
            step = 1.0,
            valueLabel = { "${it.roundToInt()} s" },
            onValueChange = { store.update { s -> s.copy(readingInterval = it) } }
        )
    }
}

@Composable
private fun ToneSection(store: SettingsStore, settings: AppSettings) {
    SettingsSection("Sygnały dźwiękowe") {
        ToggleRow("Odtwarzaj sygnały dźwiękowe", settings.soundSignalsEnabled) { v ->
            store.update { it.copy(soundSignalsEnabled = v) }
        }
        ToggleRow("Odtwarzaj ton na zadanym kursie", settings.toneOnCourse) { v ->
            store.update { it.copy(toneOnCourse = v) }
        }
        AdjustableSettingRow(
            label = "Głośność sygnałów",
            value = settings.toneVolume,
            min = 0.0,
            max = 100.0,
            step = 5.0,
            valueLabel = { "${it.roundToInt()}%" },
            onValueChange = { store.update { s -> s.copy(toneVolume = it) } }
        )
        AdjustableSettingRow(
            label = "Odstęp między sygnałami",
            value = settings.toneDelay,
            min = 0.5,
            max = 5.0,
            step = 0.5,
            valueLabel = { decimalText(it) + " s" },
            onValueChange = { store.update { s -> s.copy(toneDelay = it) } }
        )
        AdjustableSettingRow(
            label = "Tolerancja zadanego kursu",
            value = settings.errorThreshold,
            min = 1.0,
            max = 5.0,
            step = 1.0,
            valueLabel = { degreesPolish(it.roundToInt()) },
            onValueChange = { store.update { s -> s.copy(errorThreshold = it) } }
        )
    }
}

@Composable
private fun AdvancedSection(store: SettingsStore, settings: AppSettings) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val stateLabel = if (expanded) "rozwinięte" else "zwinięte"
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "Zaawansowane"
                    role = Role.Button
                    stateDescription = stateLabel
                }
        ) {
            Text("Zaawansowane")
        }
        if (expanded) {
            ToggleRow("Odczytuj wychylenie steru", settings.announceRudderAngle) { v ->
                store.update { it.copy(announceRudderAngle = v) }
            }
            ToggleRow("Odwróć wychylenie steru", settings.invertRudderAngle) { v ->
                store.update { it.copy(invertRudderAngle = v) }
            }
            AdjustableSettingRow(
                label = "Poprawka wychylenia steru",
                value = settings.rudderAngleCorrection,
                min = -90.0,
                max = 90.0,
                step = 1.0,
                valueLabel = { "${it.roundToInt()}°" },
                onValueChange = { store.update { s -> s.copy(rudderAngleCorrection = it) } }
            )
        }
    }
}

@Composable
private fun DeviceActionsSection(monitor: HelmMonitor) {
    SettingsSection("Czynności urządzenia") {
        ConfirmableActionRow(
            title = "Kalibracja żyroskopu",
            warning = "Kalibrację żyroskopu należy przeprowadzić po ostatecznym zamocowaniu urządzenia do stałej części statku i gdy statek jest stabilny. Najlepiej w porcie na cumach.",
            confirmLabel = "Kalibruj",
            onConfirm = { monitor.runAdministrationAction(AdministrationAction.CALIBRATE) }
        )
        ConfirmableActionRow(
            title = "Restart urządzenia",
            warning = "Urządzenie uruchomi się ponownie. Po restarcie zwykle łączy się z powrotem samo. Jeśli w pobliżu jest inna zapamiętana sieć Wi-Fi, ponownie włącz odczyt na ekranie Ster, aby aplikacja wróciła do sieci „BlueSeaEye”.",
            confirmLabel = "Restart",
            onConfirm = { monitor.runAdministrationAction(AdministrationAction.REBOOT) }
        )
    }
}

@Composable
private fun ConfirmableActionRow(
    title: String,
    warning: String,
    confirmLabel: String,
    onConfirm: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .semanticButton("$title. Pokazuje ostrzeżenie i przycisk potwierdzenia.")
        ) {
            Text(title)
        }
        if (expanded) {
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    onConfirm()
                    expanded = false
                },
                modifier = Modifier.semanticButton("$confirmLabel. $warning")
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeaderText(title = title)
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val stateLabel = if (checked) "włączone" else "wyłączone"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .clearAndSetSemantics {
                contentDescription = title
                role = Role.Switch
                stateDescription = stateLabel
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun decimalText(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)
}
