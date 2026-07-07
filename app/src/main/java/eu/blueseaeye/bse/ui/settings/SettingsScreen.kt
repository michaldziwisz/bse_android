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
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.model.CourseSource
import eu.blueseaeye.bse.model.ReadingOutputMode
import eu.blueseaeye.bse.model.ToneWaveform
import eu.blueseaeye.bse.ui.common.LabeledTextField
import eu.blueseaeye.bse.ui.common.NumericSettingRow
import eu.blueseaeye.bse.ui.common.SectionHeaderText
import eu.blueseaeye.bse.ui.common.semanticButton
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    modifier: Modifier = Modifier
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        DeviceSection(settingsStore, settings)
        Divider()
        ReadingSection(settingsStore, settings)
        Divider()
        ToneSection(settingsStore, settings)
        Divider()
        DataSourceSection(settingsStore, settings)
        Divider()
        AuxiliarySection(settingsStore, settings)
    }
}

@Composable
private fun DeviceSection(store: SettingsStore, settings: AppSettings) {
    SettingsSection("Urządzenie") {
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
        Text(
            text = "Połącz telefon z siecią Wi-Fi „BlueSeaEye” (hasło blueseaeye). Domyślny adres ${AppSettings.DEFAULT_DEVICE_HOST} odpowiada urządzeniu w trybie access pointa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadingSection(store: SettingsStore, settings: AppSettings) {
    SettingsSection("Odczyt") {
        PickerRow(
            label = "Sposób odczytu",
            options = ReadingOutputMode.entries,
            selected = settings.readingOutput,
            optionTitle = { it.title },
            onSelected = { mode -> store.update { it.copy(readingOutput = mode) } }
        )

        if (settings.readingOutput == ReadingOutputMode.ARIA) {
            NumericSettingRow(
                title = "Odstęp między aktualizacjami",
                valueText = secondsText(settings.readingInterval),
                decrementLabel = "Skróć odstęp między aktualizacjami",
                incrementLabel = "Wydłuż odstęp między aktualizacjami",
                hint = "Zakres od 1 do 45 sekund.",
                onDecrement = { store.update { it.copy(readingInterval = it.readingInterval - 1) } },
                onIncrement = { store.update { it.copy(readingInterval = it.readingInterval + 1) } }
            )
        } else {
            NumericSettingRow(
                title = "Głośność odczytu",
                valueText = percentText(settings.readingVolume),
                decrementLabel = "Zmniejsz głośność odczytu",
                incrementLabel = "Zwiększ głośność odczytu",
                hint = "Zakres od 0 do 100 procent.",
                onDecrement = { store.update { it.copy(readingVolume = it.readingVolume - 5) } },
                onIncrement = { store.update { it.copy(readingVolume = it.readingVolume + 5) } }
            )
            NumericSettingRow(
                title = "Odstęp między odczytami",
                valueText = secondsText(settings.readingDelay),
                decrementLabel = "Skróć odstęp między odczytami",
                incrementLabel = "Wydłuż odstęp między odczytami",
                hint = "Zakres od 0 do 30 sekund.",
                onDecrement = { store.update { it.copy(readingDelay = it.readingDelay - 0.5) } },
                onIncrement = { store.update { it.copy(readingDelay = it.readingDelay + 0.5) } }
            )
            NumericSettingRow(
                title = "Prędkość odczytu",
                valueText = percentText(settings.readingRate),
                decrementLabel = "Zmniejsz prędkość odczytu",
                incrementLabel = "Zwiększ prędkość odczytu",
                hint = "Zakres od 50 do 400 procent.",
                onDecrement = { store.update { it.copy(readingRate = it.readingRate - 10) } },
                onIncrement = { store.update { it.copy(readingRate = it.readingRate + 10) } }
            )
            VoicePickerRow(store, settings)
        }
        Text(
            text = "Tryb czytnika ekranu najlepiej działa z aktywnym TalkBack.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToneSection(store: SettingsStore, settings: AppSettings) {
    SettingsSection("Sygnały dźwiękowe") {
        ToggleRow("Odtwarzaj sygnały dźwiękowe", settings.soundSignalsEnabled) { v ->
            store.update { it.copy(soundSignalsEnabled = v) }
        }
        ToggleRow("Odtwarzaj ton referencyjny", settings.referenceTone) { v ->
            store.update { it.copy(referenceTone = v) }
        }
        ToggleRow("Odtwarzaj ton na zadanym kursie", settings.toneOnCourse) { v ->
            store.update { it.copy(toneOnCourse = v) }
        }
        ToggleRow("Szeroka rozpiętość tonów", settings.broadTonalSpread) { v ->
            store.update { it.copy(broadTonalSpread = v) }
        }
        PickerRow(
            label = "Typ dźwięku",
            options = ToneWaveform.entries,
            selected = settings.toneType,
            optionTitle = { it.title },
            onSelected = { w -> store.update { it.copy(toneType = w) } }
        )
        NumericSettingRow(
            title = "Głośność sygnałów",
            valueText = percentText(settings.toneVolume),
            decrementLabel = "Zmniejsz głośność sygnałów",
            incrementLabel = "Zwiększ głośność sygnałów",
            hint = "Zakres od 0 do 100 procent.",
            onDecrement = { store.update { it.copy(toneVolume = it.toneVolume - 5) } },
            onIncrement = { store.update { it.copy(toneVolume = it.toneVolume + 5) } }
        )
        NumericSettingRow(
            title = "Odstęp między sygnałami",
            valueText = secondsText(settings.toneDelay),
            decrementLabel = "Skróć odstęp między sygnałami",
            incrementLabel = "Wydłuż odstęp między sygnałami",
            hint = "Zakres od 0,5 do 5 sekund.",
            onDecrement = { store.update { it.copy(toneDelay = it.toneDelay - 0.1) } },
            onIncrement = { store.update { it.copy(toneDelay = it.toneDelay + 0.1) } }
        )
        NumericSettingRow(
            title = "Bazowy odstęp od tonu na kursie",
            valueText = decimalText(settings.toneBaseOffset),
            decrementLabel = "Zmniejsz bazowy odstęp",
            incrementLabel = "Zwiększ bazowy odstęp",
            hint = "Zakres od 0 do 6 półtonów.",
            onDecrement = { store.update { it.copy(toneBaseOffset = it.toneBaseOffset - 1) } },
            onIncrement = { store.update { it.copy(toneBaseOffset = it.toneBaseOffset + 1) } }
        )
        NumericSettingRow(
            title = "Dozwolona odchyłka",
            valueText = degreesText(settings.errorThreshold),
            decrementLabel = "Zmniejsz dozwoloną odchyłkę",
            incrementLabel = "Zwiększ dozwoloną odchyłkę",
            hint = "Zakres od 1 do 15 stopni.",
            onDecrement = { store.update { it.copy(errorThreshold = it.errorThreshold - 0.5) } },
            onIncrement = { store.update { it.copy(errorThreshold = it.errorThreshold + 0.5) } }
        )
        NumericSettingRow(
            title = "Zakres sygnalizowanej odchyłki",
            valueText = degreesText(settings.errorRange),
            decrementLabel = "Zmniejsz zakres sygnalizacji",
            incrementLabel = "Zwiększ zakres sygnalizacji",
            hint = "Zakres od 15 do 60 stopni.",
            onDecrement = { store.update { it.copy(errorRange = it.errorRange - 1) } },
            onIncrement = { store.update { it.copy(errorRange = it.errorRange + 1) } }
        )
        if (settings.readingOutput != ReadingOutputMode.ARIA) {
            ToggleRow("Unikaj sygnalizowania w trakcie odczytu", settings.avoidSignalsOverlap) { v ->
                store.update { it.copy(avoidSignalsOverlap = v) }
            }
        }
    }
}

@Composable
private fun DataSourceSection(store: SettingsStore, settings: AppSettings) {
    SettingsSection("Źródło danych") {
        PickerRow(
            label = "Źródło kursu",
            options = CourseSource.entries,
            selected = settings.courseSource,
            optionTitle = { it.title },
            onSelected = { src -> store.update { it.copy(courseSource = src) } }
        )
        NumericSettingRow(
            title = "Okno uśredniania",
            valueText = "${settings.averageWindow} s",
            decrementLabel = "Zmniejsz okno uśredniania",
            incrementLabel = "Zwiększ okno uśredniania",
            hint = "Zakres od 1 do 5 sekund.",
            onDecrement = { store.update { it.copy(averageWindow = it.averageWindow - 1) } },
            onIncrement = { store.update { it.copy(averageWindow = it.averageWindow + 1) } }
        )
    }
}

@Composable
private fun AuxiliarySection(store: SettingsStore, settings: AppSettings) {
    SettingsSection("Ustawienia pomocnicze") {
        ToggleRow("Odwróć wychylenie steru", settings.invertRudderAngle) { v ->
            store.update { it.copy(invertRudderAngle = v) }
        }
        NumericSettingRow(
            title = "Poprawka wychylenia steru",
            valueText = degreesText(settings.rudderAngleCorrection),
            decrementLabel = "Zmniejsz poprawkę wychylenia steru",
            incrementLabel = "Zwiększ poprawkę wychylenia steru",
            hint = "Zakres od minus 90 do 90 stopni.",
            onDecrement = { store.update { it.copy(rudderAngleCorrection = it.rudderAngleCorrection - 1) } },
            onIncrement = { store.update { it.copy(rudderAngleCorrection = it.rudderAngleCorrection + 1) } }
        )
    }
}

@Composable
private fun VoicePickerRow(store: SettingsStore, settings: AppSettings) {
    val voices = remember { SettingsStore.voices() }
    var expanded by remember { mutableStateOf(false) }
    val selectedName = voices.firstOrNull { it.id == settings.readingVoiceIdentifier }?.title ?: "Domyślny"
    Column {
        Text("Głos", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics {})
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .semanticButton("Głos: $selectedName. Rozwiń listę głosów.")
            ) {
                Text(selectedName)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Domyślny") },
                    onClick = {
                        store.update { it.copy(readingVoiceIdentifier = null) }
                        expanded = false
                    }
                )
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice.title) },
                        onClick = {
                            store.update { it.copy(readingVoiceIdentifier = voice.id) }
                            expanded = false
                        }
                    )
                }
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
private fun <T> PickerRow(
    label: String,
    options: List<T>,
    selected: T,
    optionTitle: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics {})
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .semanticButton("$label: ${optionTitle(selected)}. Rozwiń listę.")
            ) {
                Text(optionTitle(selected))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionTitle(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
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

private fun percentText(value: Double): String = "${value.roundToInt()}%"
private fun secondsText(value: Double): String = "${decimalText(value)} s"
private fun degreesText(value: Double): String = "${decimalText(value)}°"
private fun decimalText(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)
}
