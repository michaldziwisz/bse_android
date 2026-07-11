package eu.blueseaeye.bse.ui.helm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.model.HelmSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kafelek z dużym odczytem kursu/odchyłki, sterem i wiatrem. Cały element ma
 * jedną dostępną etykietę (podsumowanie odczytu) — odpowiednik CompassCardView.
 */
@Composable
fun CompassCard(
    snapshot: HelmSnapshot?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val displayed = snapshot?.displayedValue(settings)
    val valueText = if (displayed != null) String.format("%03d", abs(displayed)) else "?"
    val prefix = if (displayed != null && displayed < 0) "-" else ""
    val summary = snapshot?.spokenReading(settings)?.takeIf { it.isNotEmpty() }
        ?: "Brak bieżących odczytów"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = summary },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "$prefix$valueText",
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val rudder = snapshot?.rudder
                if (rudder != null) {
                    Text(
                        text = "Ster ${abs(rudder.roundToInt())}° ${if (rudder >= 0) "prawo" else "lewo"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Ster nieznany",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
