package dev.snapledger.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.snapledger.data.JvmDatabase
import dev.snapledger.data.SqlLedgerRepository
import dev.snapledger.ui.App
import java.io.File

fun main() {
    // SNAPLEDGER_DB lets you point the app at a throwaway database, e.g. for trying the samples.
    val file = System.getenv("SNAPLEDGER_DB")?.let { File(it) } ?: JvmDatabase.defaultFile()
    val store = JvmDatabase.open(file)
    val files = DesktopFiles()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "SnapLedger",
            icon = AppIcon,
            state = rememberWindowState(width = 1200.dp, height = 820.dp),
        ) {
            App(store.repository, store.backup, files, SqlLedgerRepository.DEFAULT_ACCOUNT_ID, SqlLedgerRepository.DEFAULT_CURRENCY)
        }
    }
}
