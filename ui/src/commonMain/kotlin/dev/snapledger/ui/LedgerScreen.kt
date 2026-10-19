package dev.snapledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
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
import dev.snapledger.ui.theme.SnapShapes
import dev.snapledger.ui.theme.SnapTheme

/** The transaction list, grouped by day like the bank app, with each day's balance and check mark. */
@Composable
fun LedgerScreen(
    ledger: LedgerState,
    onMessage: (String) -> Unit,
) {
    val days = remember(ledger.snapshot) { LedgerViews.days(ledger.snapshot, ledger.accountId) }
    if (days.isEmpty()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("还没有交易", style = SnapTheme.type.pageTitle)
            Text("去「导入」页粘贴 AI 返回的 JSON。", color = SnapTheme.colors.mute)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("流水", style = SnapTheme.type.pageTitle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
        days.forEach { day ->
            item(key = "day-${day.date}") { DayHeader(day) }
            if (day.transactions.isNotEmpty()) {
                item(key = "txns-${day.date}") {
                    SnapCard(shape = SnapShapes.lg, padding = 12.dp) {
                        day.transactions.forEachIndexed { index, txn ->
                            if (index > 0) HorizontalDivider(color = SnapTheme.colors.hairline)
                            TransactionRow(ledger, txn, onMessage)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LedgerDay) {
    val c = SnapTheme.colors
    val recon = day.reconciliation
    val (mark, color) =
        when (recon?.status) {
            ReconStatus.OK -> "✓" to c.positive
            ReconStatus.MISMATCH -> "⚠ 差 ${money(recon.differenceMinor ?: 0, recon.currency)}" to c.negative
            ReconStatus.UNVERIFIABLE, null -> "–" to c.mute
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateLabel(day.date), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        day.balance?.let { Text("余额 ${money(it.balanceMinor, it.currency)}  ", style = SnapTheme.type.caption, color = c.mute) }
        Text(mark, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TransactionRow(
    ledger: LedgerState,
    txn: StoredTransaction,
    onMessage: (String) -> Unit,
) {
    val c = SnapTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(txn.merchant ?: txn.rawDescription, style = SnapTheme.type.title)
            Text(txn.rawDescription, style = SnapTheme.type.caption, color = c.mute)
            if (txn.type == TxnType.TRANSFER) {
                Text("转账（不计收支）", style = SnapTheme.type.caption, color = c.mute)
            } else {
                CategoryPicker(
                    label = "${sourceBadge(txn.classificationSource)} ${ledger.categoryName(txn.categoryCode)}",
                    kind = kindFor(txn.type),
                    catalog = ledger.catalog,
                ) { code -> onMessage(ledger.recategorise(txn, code)) }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signedMoney(txn.direction, txn.amountMinor, txn.currency),
                style = SnapTheme.type.amount,
                color = if (txn.direction == Direction.IN) StatusColors.inflow else StatusColors.outflow,
            )
            TextButton(onClick = {
                ledger.delete(txn)
                onMessage("已删除：${txn.merchant ?: txn.rawDescription}")
            }) { Text("删除", color = c.mute, style = SnapTheme.type.caption) }
        }
    }
}
