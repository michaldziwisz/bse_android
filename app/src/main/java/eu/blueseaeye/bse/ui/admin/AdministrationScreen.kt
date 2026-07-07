package eu.blueseaeye.bse.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.blueseaeye.bse.model.AdministrationAction
import eu.blueseaeye.bse.monitor.HelmMonitor
import eu.blueseaeye.bse.ui.common.SectionHeaderText
import eu.blueseaeye.bse.ui.common.semanticButton

@Composable
fun AdministrationScreen(
    monitor: HelmMonitor,
    modifier: Modifier = Modifier
) {
    val state by monitor.state.collectAsStateWithLifecycle()
    var pendingAction by remember { mutableStateOf<AdministrationAction?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Aplikacja komunikuje się z urządzeniem BlueSeaEye w jego sieci Wi-Fi. Niektóre egzemplarze mogą nie udostępniać wszystkich akcji administracyjnych — wówczas pojawi się komunikat o błędzie.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeaderText("Akcje urządzenia")
        Button(
            onClick = { pendingAction = AdministrationAction.CALIBRATE },
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .semanticButton("Skalibruj żyroskop")
        ) {
            Text("Skalibruj żyroskop")
        }
        OutlinedButton(
            onClick = { pendingAction = AdministrationAction.REBOOT },
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .semanticButton("Restartuj urządzenie")
        ) {
            Text("Restartuj urządzenie")
        }

        SectionHeaderText("Status")
        Text(
            text = state.adminMessage ?: "Brak ostatniego komunikatu administracyjnego.",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    val action = pendingAction
    if (action != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Potwierdzenie") },
            text = {
                Text(
                    when (action) {
                        AdministrationAction.CALIBRATE -> "Kalibracja może chwilowo zatrzymać odczyty. Kontynuować?"
                        AdministrationAction.REBOOT -> "Urządzenie zostanie zrestartowane. Kontynuować?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    monitor.runAdministrationAction(action)
                    pendingAction = null
                }) {
                    Text(
                        when (action) {
                            AdministrationAction.CALIBRATE -> "Uruchom kalibrację"
                            AdministrationAction.REBOOT -> "Restartuj"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("Anuluj") }
            }
        )
    }
}
