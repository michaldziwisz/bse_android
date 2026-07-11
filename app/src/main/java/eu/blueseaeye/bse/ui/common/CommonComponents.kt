package eu.blueseaeye.bse.ui.common

import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.blueseaeye.bse.R

/**
 * Nadaje klikalnemu elementowi dostępną nazwę WPROST na jego węźle. Konieczne
 * dla słabszych czytników (np. Jeshuo), które nie scalają etykiet z dzieci.
 * Bezpieczne dla przycisków (akcja kliknięcia zostaje w onClick).
 */
fun Modifier.semanticButton(label: String, buttonRole: Role = Role.Button): Modifier =
    this.clearAndSetSemantics {
        contentDescription = label
        role = buttonRole
    }

/**
 * Pole tekstowe oparte o natywny android.widget.EditText. Czysty Compose nie
 * nadaje dostępnej nazwy węzłowi EditText, który fokusuje Jeshuo — wzorzec
 * sprawdzony w projekcie TyfloCentrum Android. Nazwa idzie jako hint ORAZ
 * contentDescription wprost na EditText; widoczna etykieta i błąd są osobnymi
 * Text ukrytymi z drzewa a11y.
 */
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardType: Int = InputType.TYPE_CLASS_TEXT
) {
    val accessibleHint = if (isError && !errorText.isNullOrBlank()) "$label. $errorText" else label
    val borderColor = (if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline).toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val imeOption = if (singleLine) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NONE
    val inputTypeFlags = keyboardType or (if (!singleLine) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)

    Column(modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clearAndSetSemantics {}
        )
        AndroidView(
            modifier = fieldModifier.fillMaxWidth(),
            factory = { ctx ->
                android.widget.EditText(ctx).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 8f * resources.displayMetrics.density
                        setStroke((1f * resources.displayMetrics.density).toInt(), borderColor)
                        setColor(android.graphics.Color.TRANSPARENT)
                    }
                    val padH = (16f * resources.displayMetrics.density).toInt()
                    val padV = (14f * resources.displayMetrics.density).toInt()
                    setPadding(padH, padV, padH, padV)
                    setTextColor(textColor)
                    setHintTextColor(hintColor)
                    isSingleLine = singleLine
                    if (!singleLine) {
                        this.minLines = minLines
                        gravity = Gravity.TOP or Gravity.START
                    }
                    inputType = inputTypeFlags
                    imeOptions = imeOption
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            if (getTag(R.id.labeled_text_field_self_update) == true) return
                            onValueChange(s?.toString().orEmpty())
                        }
                    })
                }
            },
            update = { et ->
                et.hint = accessibleHint
                et.contentDescription = accessibleHint
                (et.background as? android.graphics.drawable.GradientDrawable)
                    ?.setStroke((1f * et.resources.displayMetrics.density).toInt(), borderColor)
                if (et.text.toString() != value) {
                    et.setTag(R.id.labeled_text_field_self_update, true)
                    et.setText(value)
                    et.setSelection(value.length)
                    et.setTag(R.id.labeled_text_field_self_update, false)
                }
            }
        )
        if (!errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

/** Nagłówek sekcji z opisem. */
@Composable
fun SectionHeaderText(title: String, description: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Wiersz z wartością liczbową sterowaną przyciskami minus/plus. Każdy przycisk
 * ma dostępną nazwę wprost na węźle (semanticButton).
 */
@Composable
fun NumericSettingRow(
    title: String,
    valueText: String,
    decrementLabel: String,
    incrementLabel: String,
    hint: String? = null,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.semanticButton(hintedLabel(decrementLabel, hint))
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null)
            }
            IconButton(
                onClick = onIncrement,
                modifier = Modifier.semanticButton(hintedLabel(incrementLabel, hint))
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun hintedLabel(label: String, hint: String?): String =
    if (hint.isNullOrBlank()) label else "$label. $hint"

/**
 * „Wybieracz" wartości oparty o natywny android.widget.SeekBar. Czytnik ekranu
 * (TalkBack ORAZ Jeshuo) obsługuje go gestem jednym palcem góra/dół — SeekBar to
 * standardowa kontrolka regulowana, więc oba czytniki radzą sobie z nią pewnie
 * (czysta semantyka Compose bywa zawodna pod Jeshuo, dlatego natywna kontrolka).
 *
 * Operuje na wartościach zmiennoprzecinkowych; [step] to wielkość jednego kroku.
 *
 * DWA ważne szczegóły dostępności:
 *  1. Cała fraza („Mów co 5 s") idzie jako contentDescription, a NIE dzielona na
 *     contentDescription+stateDescription — inaczej TalkBack czyta stan przed
 *     nazwą („5 s, Mów co"). stateDescription celowo NIE jest ustawiane.
 *  2. Natywny AbsSeekBar obsługuje akcje przewijania czytnika (SCROLL_FORWARD/
 *     BACKWARD) skacząc o max/20 zamiast o jeden krok — dlatego „stopnie chodzą
 *     co ileś". Podmieniamy AccessibilityDelegate i przewijamy DOKŁADNIE o 1
 *     krok (1 gest = 1 krok).
 */
@Composable
fun AdjustableSettingRow(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    valueLabel: (Double) -> String,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    allowKeyboardInput: Boolean = false
) {
    val ticks = Math.max(1, Math.round((max - min) / step).toInt())
    val currentTick = Math.round((value - min) / step).toInt().coerceIn(0, ticks)
    fun valueForTick(tick: Int): Double = min + tick * step
    // Zawijanie: krok w górę z ostatniego ticka wraca na 0, a w dół z 0 skacze
    // na ostatni. Bez zawijania — zwykłe ograniczenie do zakresu.
    fun nextTick(from: Int, forward: Boolean): Int {
        val raw = from + if (forward) 1 else -1
        return if (wrap) ((raw % (ticks + 1)) + (ticks + 1)) % (ticks + 1)
        else raw.coerceIn(0, ticks)
    }

    var showKeyboardDialog by remember { mutableStateOf(false) }
    // Tick, który sami wysłaliśmy do modelu i na którego „echo" ze store czekamy.
    // Podczas odczytu DataStore commituje asynchronicznie, a burza rekompozycji
    // (napływające snapshoty) potrafi wpisać STARĄ wartość z powrotem do suwaka —
    // dlatego dopóki echo nie dojdzie, ignorujemy przychodzące wartości.
    val pendingTick = remember { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {}
            )
            Text(
                valueLabel(value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                // Natywny SeekBar zapewnia gest czytnika góra/dół (regulacja).
                // PROBLEM: Samsung TalkBack dla suwaka z domyślnym zakresem czyta
                // wartość jako PROCENT (progress/max). Rozwiązanie: nadpisujemy
                // węzeł dostępności tak, aby zakres był typu INT z REALNĄ wartością
                // (sekundy/stopnie/procenty ustawień) — wtedy czytnik mówi liczbę,
                // nie „N procent". Nazwa (label) idzie osobno jako contentDescription.
                object : android.widget.SeekBar(ctx) {
                    override fun onInitializeAccessibilityNodeInfo(
                        info: android.view.accessibility.AccessibilityNodeInfo
                    ) {
                        super.onInitializeAccessibilityNodeInfo(info)
                        applyRange(info)
                    }

                    override fun createAccessibilityNodeInfo(): android.view.accessibility.AccessibilityNodeInfo? {
                        val info = super.createAccessibilityNodeInfo()
                        if (info != null) applyRange(info)
                        return info
                    }

                    private fun applyRange(info: android.view.accessibility.AccessibilityNodeInfo) {
                        val current = valueLabel(valueForTick(progress))
                        // Klasa ZOSTAJE SeekBar — dzięki temu działa natywny gest
                        // regulacji góra/dół (zmiana na View go zabija). Procent
                        // znika, bo: (a) rangeInfo=null, (b) stateDescription jest
                        // NIEPUSTA — Samsung TalkBack używa jej zamiast liczyć „N%".
                        // Całą frazę „Zadany kurs 359" dajemy w stateDescription
                        // (czytnik czyta ją PRZED contentDescription), a
                        // contentDescription czyścimy, żeby nie dublować i mieć
                        // kolejność „nazwa, wartość".
                        info.rangeInfo = null
                        info.contentDescription = ""
                        info.stateDescription = "$label $current"
                        // Przy wrap=true zawsze udostępniamy OBIE akcje przewijania,
                        // także na krańcach — inaczej AbsSeekBar usuwa
                        // SCROLL_FORWARD przy max i SCROLL_BACKWARD przy 0, więc
                        // czytnik nie ma czego wysłać i zawijanie nie działa.
                        if (wrap) {
                            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
                        }
                        // Gdy dozwolone wpisanie z klawiatury — dwukrotne stuknięcie
                        // (ACTION_CLICK) otwiera pole tekstowe. Podpowiedź dla
                        // czytnika, że da się kliknąć.
                        if (allowKeyboardInput) {
                            info.isClickable = true
                            info.addAction(
                                android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,
                                    "wpisz wartość z klawiatury"
                                )
                            )
                        }
                    }
                }.apply {
                    this.max = ticks
                    keyProgressIncrement = 1
                    progress = currentTick
                    // Wartość (z nazwą) idzie w stateDescription; contentDescription
                    // czyścimy — patrz applyRange (procent i kolejność).
                    contentDescription = ""
                    setStateDescriptionCompat("$label ${valueLabel(valueForTick(currentTick))}")

                    // Zatwierdza nowy tick: zapamiętuje go jako „w locie" (żeby
                    // asynchroniczne echo ze store nie cofnęło suwaka podczas
                    // odczytu), ustawia pozycję i zgłasza zmianę do modelu.
                    fun commitTick(nextTick: Int) {
                        pendingTick.value = nextTick
                        if (progress != nextTick) {
                            setTag(R.id.labeled_text_field_self_update, true)
                            progress = nextTick
                            setTag(R.id.labeled_text_field_self_update, false)
                        }
                        contentDescription = ""
                        setStateDescriptionCompat("$label ${valueLabel(valueForTick(nextTick))}")
                        onValueChange(valueForTick(nextTick))
                    }

                    // Dotyk paska przez użytkownika — aktualizujemy model i wartość.
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                            sb.contentDescription = ""
                            sb.setStateDescriptionCompat("$label ${valueLabel(valueForTick(progress))}")
                            if (fromUser && getTag(R.id.labeled_text_field_self_update) != true) {
                                pendingTick.value = progress
                                onValueChange(valueForTick(progress))
                            }
                        }
                        override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                        override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
                    })

                    // Przechwytujemy akcje przewijania czytnika, żeby przesuwać o
                    // DOKŁADNIE jeden krok (natywny AbsSeekBar skacze o max/20), a
                    // przy wrap=true zawijać przez granicę zakresu. Dwukrotne
                    // stuknięcie (ACTION_CLICK) otwiera dialog z klawiaturą.
                    androidx.core.view.ViewCompat.setAccessibilityDelegate(
                        this,
                        object : androidx.core.view.AccessibilityDelegateCompat() {
                            override fun performAccessibilityAction(
                                host: android.view.View,
                                action: Int,
                                args: android.os.Bundle?
                            ): Boolean {
                                val sb = host as android.widget.SeekBar
                                val forward = action ==
                                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD.id
                                val backward = action ==
                                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD.id
                                if (forward || backward) {
                                    commitTick(nextTick(sb.progress, forward))
                                    return true
                                }
                                if (allowKeyboardInput &&
                                    action == androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK.id
                                ) {
                                    showKeyboardDialog = true
                                    return true
                                }
                                return super.performAccessibilityAction(host, action, args)
                            }
                        }
                    )
                }
            },
            update = { sb ->
                if (sb.max != ticks) sb.max = ticks
                // Echo z modelu. Jeśli czekamy na własną zmianę (pendingTick), a
                // przyszła wartość jeszcze jej nie odzwierciedla, NIE cofamy suwaka
                // — inaczej podczas odczytu asynchroniczny zapis cofałby pozycję.
                val pending = pendingTick.value
                when {
                    pending != null && currentTick == pending -> {
                        pendingTick.value = null // echo doszło, zwalniamy blokadę
                        if (sb.progress != currentTick) {
                            sb.setTag(R.id.labeled_text_field_self_update, true)
                            sb.progress = currentTick
                            sb.setTag(R.id.labeled_text_field_self_update, false)
                        }
                    }
                    pending != null -> {
                        // wciąż czekamy na echo — zostaw pozycję użytkownika
                    }
                    else -> {
                        if (sb.progress != currentTick) {
                            sb.setTag(R.id.labeled_text_field_self_update, true)
                            sb.progress = currentTick
                            sb.setTag(R.id.labeled_text_field_self_update, false)
                        }
                    }
                }
                sb.contentDescription = ""
                sb.setStateDescriptionCompat("$label ${valueLabel(valueForTick(sb.progress))}")
            }
        )
    }

    if (showKeyboardDialog) {
        CourseInputDialog(
            label = label,
            initial = Math.round(value).toInt(),
            min = Math.round(min).toInt(),
            max = Math.round(max).toInt(),
            onConfirm = { entered ->
                showKeyboardDialog = false
                onValueChange(entered.toDouble())
            },
            onDismiss = { showKeyboardDialog = false }
        )
    }
}

@Composable
private fun CourseInputDialog(
    label: String,
    initial: Int,
    min: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in min..max

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wpisz wartość z zakresu $min–$max.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() } },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.semantics { contentDescription = label }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = valid
            ) { Text("Ustaw") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

private fun android.view.View.setStateDescriptionCompat(text: String) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        androidx.core.view.ViewCompat.setStateDescription(this, text)
    }
}
