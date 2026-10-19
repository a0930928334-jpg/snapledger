package dev.snapledger.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.snapledger.data.AndroidDatabase
import dev.snapledger.data.SqlLedgerRepository
import dev.snapledger.ui.App
import dev.snapledger.ui.PlatformFiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Text received from the share sheet, waiting to be shown in the import preview. */
    private var sharedText by mutableStateOf<String?>(null)

    private var pendingSave: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    // Activity-result launchers must be registered before the activity starts, hence fields.
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { pendingSave?.complete(it) }
    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { pendingOpen?.complete(it) }

    private val files =
        object : PlatformFiles {
            override suspend fun saveBackup(
                bytes: ByteArray,
                suggestedName: String,
            ): String? {
                val uri =
                    CompletableDeferred<Uri?>().also { pendingSave = it }.also { createDocument.launch(suggestedName) }.await()
                        ?: return null
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                return uri.lastPathSegment ?: suggestedName
            }

            override suspend fun openBackup(): ByteArray? {
                val uri =
                    CompletableDeferred<Uri?>().also { pendingOpen = it }.also { openDocument.launch(arrayOf("*/*")) }.await()
                        ?: return null
                return withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = AndroidDatabase.open(applicationContext)
        if (savedInstanceState == null) sharedText = intent.sharedText()
        setContent {
            App(
                repository = store.repository,
                backup = store.backup,
                files = files,
                accountId = SqlLedgerRepository.DEFAULT_ACCOUNT_ID,
                currency = SqlLedgerRepository.DEFAULT_CURRENCY,
                sharedText = sharedText,
                onSharedTextConsumed = { sharedText = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.sharedText()?.let { sharedText = it }
    }

    private fun Intent.sharedText(): String? =
        if (action == Intent.ACTION_SEND && type?.startsWith("text/") == true) getStringExtra(Intent.EXTRA_TEXT) else null
}
