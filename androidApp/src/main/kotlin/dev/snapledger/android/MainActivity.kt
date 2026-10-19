package dev.snapledger.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.snapledger.data.AndroidDatabase
import dev.snapledger.data.SqlLedgerRepository
import dev.snapledger.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = AndroidDatabase.open(applicationContext)
        setContent { App(repository, SqlLedgerRepository.DEFAULT_ACCOUNT_ID, SqlLedgerRepository.DEFAULT_CURRENCY) }
    }
}
