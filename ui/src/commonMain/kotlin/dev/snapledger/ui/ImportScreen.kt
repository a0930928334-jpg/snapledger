package dev.snapledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.snapledger.core.importer.DayReconciliation
import dev.snapledger.core.importer.ImportIssue
import dev.snapledger.core.importer.ImportPipeline
import dev.snapledger.core.importer.ImportPreview
import dev.snapledger.core.importer.PrepareResult
import dev.snapledger.core.importer.PreviewEvaluation
import dev.snapledger.core.importer.PreviewRow
import dev.snapledger.core.importer.ReconStatus
import dev.snapledger.core.importer.RowEvaluation
import dev.snapledger.core.importer.RowStatus
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.Money
import dev.snapledger.core.model.TxnType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Holds the import flow: pasted text -> preview (editable) -> commit. */
class ImportState(
    private val ledger: LedgerState,
) {
    private val pipeline = ImportPipeline(ledger.catalog)

    var text by mutableStateOf("")
    var issues by mutableStateOf<List<ImportIssue>>(emptyList())
        private set
    var preview by mutableStateOf<ImportPreview?>(null)
        private set
    var evaluation by mutableStateOf<PreviewEvaluation?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    fun analyse() {
        message = null
        when (val result = pipeline.prepare(text, ledger.snapshot, ledger.accountId)) {
            is PrepareResult.Failure -> {
                issues = result.issues
                preview = null
                evaluation = null
            }

            is PrepareResult.Ready -> {
                issues = emptyList()
                showPreview(result.preview)
            }
        }
    }

    fun updateRow(
        id: Int,
        change: (PreviewRow) -> PreviewRow,
    ) {
        val current = preview ?: return
        showPreview(current.copy(rows = current.rows.map { if (it.id == id) change(it) else it }))
    }

    fun changeType(
        id: Int,
        type: TxnType,
    ) = updateRow(id) { pipeline.reclassify(it.copy(type = type), ledger.snapshot) }

    fun commit() {
        val currentPreview = preview ?: return
        val currentEvaluation = evaluation ?: return
        val commit = pipeline.commit(currentPreview, currentEvaluation)
        ledger.repository.commitImport(ledger.accountId, commit)
        ledger.refresh()
        val rules = if (commit.newRules.isNotEmpty()) "，记住了 ${commit.newRules.size} 条分类规则" else ""
        message = "已导入 ${commit.transactions.size} 条，跳过 ${commit.skippedCount} 条$rules。"
        cancel()
    }

    fun cancel() {
        preview = null
        evaluation = null
        issues = emptyList()
        text = ""
    }

    private fun showPreview(value: ImportPreview) {
        preview = value
        evaluation = pipeline.evaluate(value, ledger.snapshot)
    }
}

@Composable
fun ImportScreen(
    ledger: LedgerState,
    state: ImportState,
) {
    val preview = state.preview
    val evaluation = state.evaluation
    if (preview != null && evaluation != null) {
        PreviewPane(ledger, state, preview, evaluation)
    } else {
        InputPane(ledger, state)
    }
}

@Composable
private fun InputPane(
    ledger: LedgerState,
    state: ImportState,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("导入流水", style = MaterialTheme.typography.headlineSmall)
            Text(
                "把 ChatGPT 或 Claude 返回的 JSON 粘贴到下面（可以连同代码块标记和前后的说明文字一起粘贴）。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.message?.let { message -> item { Banner(message, StatusColors.green) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    state.text = clipboard.getText()?.text.orEmpty()
                    state.analyse()
                }) { Text("从剪贴板导入") }
                OutlinedButton(onClick = state::analyse, enabled = state.text.isNotBlank()) { Text("解析粘贴的内容") }
            }
        }
        item {
            OutlinedTextField(
                value = state.text,
                onValueChange = { state.text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                label = { Text("JSON") },
            )
        }
        if (state.issues.isNotEmpty()) {
            item { Text("无法导入，请修正后重试：", color = StatusColors.bad, fontWeight = FontWeight.Bold) }
            items(state.issues) { issue -> Banner("${issue.path}：${issue.message}", StatusColors.red) }
        }
        item { HistorySection(ledger) }
    }
}

@Composable
private fun HistorySection(ledger: LedgerState) {
    // Keyed on the snapshot so the list refreshes after every import or undo.
    val batches = remember(ledger.snapshot) { ledger.repository.importBatches(ledger.accountId) }
    if (batches.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.padding(4.dp))
        Text("导入历史", style = MaterialTheme.typography.titleMedium)
        batches.take(20).forEach { batch ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMillis(batch.importedAtMillis)} · ${batch.extractedBy} · 新增 ${batch.addedCount} 条，跳过 ${batch.skippedCount} 条" +
                        if (batch.undone) "（已撤销）" else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!batch.undone) {
                    TextButton(onClick = {
                        ledger.repository.undoBatch(batch.id)
                        ledger.refresh()
                    }) { Text("撤销整批") }
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(
    ledger: LedgerState,
    state: ImportState,
    preview: ImportPreview,
    evaluation: PreviewEvaluation,
) {
    val likelyByRow = evaluation.days.mapNotNull { day -> day.likelyAmount?.let { it.rowId to (it to day.currency) } }.toMap()
    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("导入预览", style = MaterialTheme.typography.headlineSmall)
            Text(
                "新增 ${evaluation.newCount} 条 · 跳过 ${evaluation.skippedCount} 条 · 疑似重复 ${evaluation.suspectCount} 条" +
                    if (evaluation.blockingCount > 0) " · 待补全 ${evaluation.blockingCount} 条" else "",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = state::commit, enabled = evaluation.canCommit) { Text("确认导入") }
                OutlinedButton(onClick = state::cancel) { Text("取消") }
                if (!evaluation.canCommit && evaluation.blockingCount > 0) {
                    Text("有 ${evaluation.blockingCount} 条缺少必填信息：补全，或者取消勾选", color = StatusColors.bad)
                }
            }
        }
        items(preview.notices) { Banner(it, StatusColors.yellow) }
        if (preview.aiWarnings.isNotEmpty()) {
            item { Text("AI 的说明", style = MaterialTheme.typography.titleSmall) }
            items(preview.aiWarnings) { Banner(it, StatusColors.gray) }
        }
        if (evaluation.days.isNotEmpty()) {
            item { Text("日终余额对账", style = MaterialTheme.typography.titleMedium) }
            items(evaluation.days) { day -> ReconciliationLine(day, evaluation) }
        }
        item {
            HorizontalDivider()
            Text("交易（勾选的会被导入）", style = MaterialTheme.typography.titleMedium)
        }
        items(evaluation.rows, key = { it.row.id }) { row ->
            val likely = likelyByRow[row.row.id]
            PreviewRowCard(ledger, state, row, likely?.let { ImportPipeline.likelyAmountHint(it.first, it.second) })
        }
    }
}

@Composable
private fun ReconciliationLine(
    day: DayReconciliation,
    evaluation: PreviewEvaluation,
) {
    val text =
        when (day.status) {
            ReconStatus.OK -> {
                "✓ ${dateLabel(day.date)}  余额 ${money(day.actualMinor, day.currency)}  已对平"
            }

            ReconStatus.UNVERIFIABLE -> {
                "– ${dateLabel(day.date)}  余额 ${money(day.actualMinor, day.currency)}  无法校验（没有更早的余额）"
            }

            ReconStatus.MISMATCH -> {
                val diff = day.differenceMinor ?: 0
                val hint =
                    day.likelyAmount?.let { likely ->
                        val row = evaluation.rows.firstOrNull { it.row.id == likely.rowId }?.row
                        "：${row?.merchant ?: row?.rawDescription ?: "某一笔"}的${ImportPipeline.likelyAmountHint(likely, day.currency)}"
                    } ?: if (diff < 0) "：可能漏了一笔支出，或者某笔金额识别错了" else "：可能漏了一笔收入，或者多算了一笔支出"
                val since = day.previousDate?.let { "（和 ${it.month.number}月${it.day}日 的余额相比，中间的交易都已计入）" }.orEmpty()
                "⚠ ${dateLabel(day.date)}  余额 ${money(day.actualMinor, day.currency)}  差 ${money(diff, day.currency)}$hint$since"
            }
        }
    val color =
        when (day.status) {
            ReconStatus.OK -> StatusColors.green
            ReconStatus.MISMATCH -> StatusColors.red
            ReconStatus.UNVERIFIABLE -> StatusColors.gray
        }
    Banner(text, color)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewRowCard(
    ledger: LedgerState,
    state: ImportState,
    evaluation: RowEvaluation,
    likelyHint: String?,
) {
    val row = evaluation.row
    val background =
        when {
            !evaluation.include -> StatusColors.gray
            evaluation.status == RowStatus.INCOMPLETE -> StatusColors.red
            evaluation.status == RowStatus.SUSPECTED_DUPLICATE || row.uncertainFields.isNotEmpty() -> StatusColors.yellow
            else -> StatusColors.green
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = evaluation.include,
                onCheckedChange = { checked -> state.updateRow(row.id) { it.copy(includeOverride = checked) } },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(row.merchant ?: row.rawDescription, fontWeight = FontWeight.SemiBold)
                Text(row.rawDescription, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                signedMoney(row.direction, row.amountMinor, row.currency),
                fontWeight = FontWeight.Bold,
                color = if (row.direction == Direction.IN) StatusColors.inflow else StatusColors.outflow,
            )
        }
        // Wraps onto several lines on a phone instead of pushing the pickers off screen.
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DateField(row) { date -> state.updateRow(row.id) { it.copy(date = date, uncertainFields = it.uncertainFields - "date") } }
            AmountField(row) { minor ->
                state.updateRow(row.id) {
                    it.copy(
                        amountMinor = minor,
                        uncertainFields =
                            it.uncertainFields - "amount",
                    )
                }
            }
            OptionPicker(
                label =
                    if (row.direction == Direction.IN) {
                        "收入方向"
                    } else if (row.direction == Direction.OUT) {
                        "支出方向"
                    } else {
                        "方向？"
                    },
                options = listOf(Direction.OUT, Direction.IN),
                optionLabel = { if (it == Direction.IN) "钱进来（收入方向）" else "钱出去（支出方向）" },
            ) { direction -> state.updateRow(row.id) { it.copy(direction = direction) } }
            OptionPicker(typeLabel(row.type), TxnType.entries, ::typeLabel) { type -> state.changeType(row.id, type) }
            CategoryPicker(
                label = "${sourceBadge(row.classificationSource)} ${ledger.categoryName(row.categoryCode)}",
                kind = kindFor(row.type),
                catalog = ledger.catalog,
                enabled = row.type != TxnType.TRANSFER,
            ) { code -> state.updateRow(row.id) { it.copy(categoryCode = code, categoryEdited = true) } }
        }
        val notes =
            buildList {
                addAll(evaluation.messages)
                likelyHint?.let { add(it) }
                if (row.uncertainFields.isNotEmpty()) add("AI 不确定：${row.uncertainFields.joinToString("、")}")
                row.note?.let { add("AI：$it") }
                if (row.categoryEdited) add("导入后会记住这个分类，以后同一商户自动套用")
            }
        notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DateField(
    row: PreviewRow,
    onValid: (LocalDate) -> Unit,
) {
    var text by remember(row.id, row.date) { mutableStateOf(row.date?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value
            runCatching { LocalDate.parse(value) }.getOrNull()?.let(onValid)
        },
        label = { Text("日期") },
        singleLine = true,
        isError = runCatching { LocalDate.parse(text) }.isFailure,
        modifier = Modifier.width(150.dp),
    )
}

@Composable
private fun AmountField(
    row: PreviewRow,
    onValid: (Long) -> Unit,
) {
    val currency = row.currency ?: "GBP"
    var text by remember(row.id, row.amountMinor) {
        mutableStateOf(row.amountMinor?.let { Money.formatMinor(it, currency).filter { c -> c.isDigit() || c == '.' } }.orEmpty())
    }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value
            Money.parseMinor(value.trim(), currency)?.takeIf { it >= 0 }?.let(onValid)
        },
        label = { Text("金额 ($currency)") },
        singleLine = true,
        isError = Money.parseMinor(text.trim(), currency) == null,
        modifier = Modifier.width(130.dp),
    )
}

@Composable
fun Banner(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .padding(8.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalTime::class)
fun formatMillis(millis: Long): String {
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.date} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
