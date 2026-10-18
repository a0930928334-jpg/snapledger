package dev.snapledger.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.snapledger.ui.App

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "SnapLedger") {
            App(platformName = "Desktop (${System.getProperty("os.name")})")
        }
    }
