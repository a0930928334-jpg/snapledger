package dev.snapledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.snapledger.core.ImportFormat

/** Root composable shared by the Android and desktop entry points. */
@Composable
fun App(platformName: String) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Hello, SnapLedger", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Running on $platformName · import format v${ImportFormat.CURRENT_VERSION}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
