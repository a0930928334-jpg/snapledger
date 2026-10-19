package dev.snapledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.snapledger.core.ledger.BackupPort
import dev.snapledger.core.ledger.LedgerRepository
import kotlinx.coroutines.launch

private enum class Tab(
    val label: String,
    val icon: String,
) {
    OVERVIEW("概览", "◎"),
    LEDGER("流水", "☰"),
    IMPORT("导入", "⇩"),
    SETTINGS("设置", "⚙"),
}

/** Root composable shared by the Android and desktop entry points. */
@Composable
fun App(
    repository: LedgerRepository,
    backup: BackupPort,
    files: PlatformFiles,
    accountId: String,
    currency: String,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val ledger = remember(repository) { LedgerState(repository, accountId, currency) }
    val importState = remember(ledger) { ImportState(ledger) }
    var tab by remember { mutableStateOf(Tab.OVERVIEW) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    // Text shared from another app (Android share sheet) goes straight to the import preview.
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            importState.text = sharedText
            importState.analyse()
            tab = Tab.IMPORT
            onSharedTextConsumed()
        }
    }

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints {
                val wide = maxWidth > 700.dp
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (!wide) {
                            NavigationBar {
                                Tab.entries.forEach { item ->
                                    NavigationBarItem(
                                        selected = tab == item,
                                        onClick = { tab = item },
                                        icon = { Text(item.icon) },
                                        label = { Text(item.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    Row(Modifier.padding(padding)) {
                        if (wide) {
                            NavigationRail {
                                Tab.entries.forEach { item ->
                                    NavigationRailItem(
                                        selected = tab == item,
                                        onClick = { tab = item },
                                        icon = { Text(item.icon) },
                                        label = { Text(item.label) },
                                    )
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            Column {
                                when (tab) {
                                    Tab.OVERVIEW -> OverviewScreen(ledger, onImport = { tab = Tab.IMPORT })
                                    Tab.LEDGER -> LedgerScreen(ledger, showMessage)
                                    Tab.IMPORT -> ImportScreen(ledger, importState)
                                    Tab.SETTINGS -> SettingsScreen(ledger, backup, files)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
