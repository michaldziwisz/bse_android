package eu.blueseaeye.bse.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import eu.blueseaeye.bse.data.SettingsStore
import eu.blueseaeye.bse.monitor.HelmMonitor
import eu.blueseaeye.bse.ui.admin.AdministrationScreen
import eu.blueseaeye.bse.ui.helm.HelmDashboardScreen
import eu.blueseaeye.bse.ui.settings.SettingsScreen

/** Flagi funkcji. Administracja ukryta — bieżący firmware zwraca 404 na calibrate/reboot. */
object FeatureFlags {
    const val ADMINISTRATION_ENABLED = false
}

private data class TabItem(
    val title: String,
    val icon: ImageVector,
    val content: @Composable (Modifier) -> Unit
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    monitor: HelmMonitor,
    settingsStore: SettingsStore
) {
    val tabs = remember {
        buildList {
            add(TabItem("Ster", Icons.Filled.Explore) { m ->
                HelmDashboardScreen(
                    monitor = monitor,
                    settingsStore = settingsStore,
                    modifier = m
                )
            })
            add(TabItem("Ustawienia", Icons.Filled.Settings) { m ->
                SettingsScreen(settingsStore = settingsStore, monitor = monitor, modifier = m)
            })
            if (FeatureFlags.ADMINISTRATION_ENABLED) {
                add(TabItem("Administracja", Icons.Filled.Build) { m ->
                    AdministrationScreen(monitor = monitor, modifier = m)
                })
            }
        }
    }
    var selectedIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            // Ekran „Ster" (indeks 0) bez górnego paska — zakładka na dole i tak go nazywa.
            if (selectedIndex != 0) {
                TopAppBar(title = { Text(tabs[selectedIndex].title) })
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedIndex == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedIndex = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) },
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = tab.title
                            role = Role.Tab
                            stateDescription = if (selected) "zaznaczone" else "niezaznaczone"
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        tabs[selectedIndex].content(Modifier.padding(innerPadding))
    }
}
