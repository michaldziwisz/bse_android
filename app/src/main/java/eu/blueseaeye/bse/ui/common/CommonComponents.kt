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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
 * (TalkBack ORAZ Jeshuo) obsługuje go gestem jednym palcem góra/dół, zmieniając
 * wartość o jeden krok — SeekBar to standardowa kontrolka regulowana, więc oba
 * czytniki radzą sobie z nią pewnie (czysta semantyka Compose bywa zawodna pod
 * Jeshuo, dlatego natywna kontrolka).
 *
 * Operuje na wartościach zmiennoprzecinkowych; [step] to wielkość jednego kroku.
 * Nazwa idzie jako contentDescription, a bieżąca wartość jako stateDescription
 * (czytnik mówi np. „Mów co, 5 sekund, suwak").
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
    modifier: Modifier = Modifier
) {
    val ticks = Math.max(1, Math.round((max - min) / step).toInt())
    val currentTick = Math.round((value - min) / step).toInt().coerceIn(0, ticks)

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
                android.widget.SeekBar(ctx).apply {
                    this.max = ticks
                    progress = currentTick
                    contentDescription = label
                    setStateDescriptionCompat(valueLabel(value))
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                            val newValue = min + progress * step
                            sb.setStateDescriptionCompat(valueLabel(newValue))
                            if (fromUser && getTag(R.id.labeled_text_field_self_update) != true) {
                                onValueChange(newValue)
                            }
                        }
                        override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                        override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
                    })
                }
            },
            update = { sb ->
                sb.contentDescription = label
                if (sb.max != ticks) sb.max = ticks
                if (sb.progress != currentTick) {
                    sb.setTag(R.id.labeled_text_field_self_update, true)
                    sb.progress = currentTick
                    sb.setTag(R.id.labeled_text_field_self_update, false)
                }
                sb.setStateDescriptionCompat(valueLabel(value))
            }
        )
    }
}

private fun android.view.View.setStateDescriptionCompat(text: String) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        stateDescription = text
    }
}
