package dev.snapledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.snapledger.core.ledger.BackupPort
import dev.snapledger.core.ledger.BackupResult
import dev.snapledger.ui.theme.SnapShapes
import dev.snapledger.ui.theme.SnapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Reading and writing backup files is platform-specific (Windows file dialog, Android document picker). */
interface PlatformFiles {
    /** Returns a short description of where the file went, or null if the user cancelled. */
    suspend fun saveBackup(
        bytes: ByteArray,
        suggestedName: String,
    ): String?

    /** Returns the chosen file's bytes, or null if the user cancelled. */
    suspend fun openBackup(): ByteArray?
}

private const val MIN_PASSWORD_LENGTH = 8

@OptIn(ExperimentalTime::class)
@Composable
fun SettingsScreen(
    ledger: LedgerState,
    backup: BackupPort,
    files: PlatformFiles,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    fun run(
        label: String,
        block: suspend () -> Pair<String, Boolean>?,
    ) {
        scope.launch {
            busy = label
            result = null
            result =
                try {
                    block()
                } finally {
                    busy = null
                }
        }
    }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()).widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = SnapTheme.type.pageTitle)
        PromptCard(ledger)
        Text("加密备份 · 两台设备之间同步", style = SnapTheme.type.title)
        Text(
            "导出：把这台设备上的全部数据加密成一个 .slbackup 文件。导入：把另一台设备的备份合并进来，" +
                "同一条记录以较新的修改为准，两边各自导入的同一批截图不会重复。" +
                "文件用你设置的密码加密（PBKDF2 + AES-256-GCM），忘记密码就无法打开。",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("备份密码（至少 $MIN_PASSWORD_LENGTH 位）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("再输入一次（导出时需要）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = confirm.isNotEmpty() && confirm != password,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                shape = SnapShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = SnapTheme.colors.cta, contentColor = SnapTheme.colors.onCta),
                enabled = busy == null && password.length >= MIN_PASSWORD_LENGTH && confirm == password,
                onClick = {
                    run("正在加密…") {
                        val bytes = withContext(Dispatchers.Default) { backup.export(password.toCharArray()) }
                        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                        val where = files.saveBackup(bytes, "snapledger-$today.slbackup")
                        where?.let { "已导出：$it" to true }
                    }
                },
            ) { Text("导出备份…") }
            OutlinedButton(
                shape = SnapShapes.pill,
                enabled = busy == null && password.length >= MIN_PASSWORD_LENGTH,
                onClick = {
                    run("正在解密并合并…") {
                        val bytes = files.openBackup() ?: return@run null
                        when (val r = withContext(Dispatchers.Default) { backup.import(bytes, password.toCharArray()) }) {
                            is BackupResult.Failed -> {
                                r.message to false
                            }

                            is BackupResult.Merged -> {
                                ledger.refresh()
                                val m = r.report
                                "合并完成：新增 ${m.transactionsAdded} 条交易，更新 ${m.transactionsUpdated} 条，" +
                                    "识别出两边重复导入的 ${m.transactionsMatched} 条，余额变化 ${m.balancesChanged} 天，" +
                                    "分类规则变化 ${m.rulesChanged} 条。" to true
                            }
                        }
                    }
                },
            ) { Text("导入并合并备份…") }
            busy?.let {
                CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                Text(it)
            }
        }
        result?.let { (message, ok) -> Banner(message, if (ok) StatusColors.good else StatusColors.problem) }
        Text(
            "提示：备份文件可以通过 U 盘、数据线、网盘或邮件在电脑和手机之间传递。文件是加密的，但请不要和密码放在一起。",
            style = MaterialTheme.typography.bodySmall,
            color = SnapTheme.colors.mute,
        )
    }
}
