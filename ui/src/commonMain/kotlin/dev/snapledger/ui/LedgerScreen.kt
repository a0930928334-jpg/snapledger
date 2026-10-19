package dev.snapledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.snapledger.core.importer.ReconStatus
import dev.snapledger.core.ledger.LedgerDay
import dev.snapledger.core.ledger.LedgerViews
import dev.snapledger.core.ledger.StoredTransaction
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.TxnType

/** The transaction list, grouped by day like the bank app, with each day's balance and check mark. */
@Composable
fun LedgerScreen(
    ledger: LedgerState,
    onMessage: (String) -> Unit,
) {
    val days = remember(ledger.snapshot) { LedgerViews.days(ledger.snapshot, ledger.accountId) }
    if (days.isEmpty()) {
        Column(Modifier.padding(16.dp)) {
            Text("还没有交易", style = MaterialTheme.typography.headlineSmall)
            Text("去「导入」页粘贴 AI 返回的 JSON。")
        }
        return
    }
    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        days.forEach { day ->
            item(key = "day-${day.date}") { DayHeader(day) }
            items(day.transactions, key = { it.id }) { txn -> TransactionRow(ledger, txn, onMessage) }
        }
    }
}

@Composable
private fun DayHeader(day: LedgerDay) {
    val recon = day.reconciliation
    val mark =
        when (recon?.status) {
            ReconStatus.OK -> "✓"
            ReconStatus.MISMATCH -> "⚠ 差 ${money(recon.differenceMinor ?: 0, recon.currency)}"
            ReconStatus.UNVERIFIABLE, null -> "–"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateLabel(day.date), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        day.balance?.let { Text("余额 ${money(it.balanceMinor, it.currency)}  ", style = MaterialTheme.typography.bodyMedium) }
        Text(
            mark,
            color = if (day.reconciliation?.status == ReconStatus.MISMATCH) StatusColors.bad else StatusColors.ok,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TransactionRow(
    ledger: LedgerState,
    txn: StoredTransaction,
    onMessage: (String) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(txn.merchant ?: txn.rawDescription, fontWeight = FontWeight.SemiBold)
                Text(txn.rawDescription, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    signedMoney(txn.direction, txn.amountMinor, txn.currency),
                    fontWeight = FontWeight.Bold,
                    color = if (txn.direction == Direction.IN) StatusColors.inflow else StatusColors.outflow,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (txn.type == TxnType.TRANSFER) {
                        Text("转账（不计收支）", style = MaterialTheme.typography.bodySmall)
                    } else {
                        CategoryPicker(
                            label = "${sourceBadge(txn.classificationSource)} ${ledger.categoryName(txn.categoryCode)}",
                            kind = kindFor(txn.type),
                            catalog = ledger.catalog,
                        ) { code -> onMessage(ledger.recategorise(txn, code)) }
                    }
                    TextButton(onClick = {
                        ledger.delete(txn)
                        onMessage("已删除：${txn.merchant ?: txn.rawDescription}")
                    }) { Text("删除") }
                }
            }
        }
        HorizontalDivider()
    }
}
