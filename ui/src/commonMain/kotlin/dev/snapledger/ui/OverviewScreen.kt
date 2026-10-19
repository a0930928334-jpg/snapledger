package dev.snapledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.snapledger.core.ledger.CategoryTotal
import dev.snapledger.core.ledger.LedgerViews
import dev.snapledger.core.ledger.MonthSummary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Home screen: this month's spending, income and where the money went. */
@OptIn(ExperimentalTime::class)
@Composable
fun OverviewScreen(
    ledger: LedgerState,
    onImport: () -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.month.number) }
    val summary =
        remember(ledger.snapshot, year, month) { LedgerViews.monthSummary(ledger.snapshot, ledger.accountId, year, month, ledger.currency) }
    var expanded by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (month == 1) {
                        year--
                        month = 12
                    } else {
                        month--
                    }
                }) { Text("‹ 上个月") }
                Text("${year}年${month}月", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (month == 12) {
                        year++
                        month = 1
                    } else {
                        month++
                    }
                }) { Text("下个月 ›") }
            }
        }
        item { Button(onClick = onImport) { Text("＋ 导入流水") } }
        item { Totals(summary) }
        item { Text("钱花在哪", style = MaterialTheme.typography.titleMedium) }
        if (summary.byCategory.isEmpty()) {
            item { Text("这个月还没有支出记录。") }
        }
        items(summary.byCategory, key = { it.code }) { top ->
            CategoryBar(top, summary, expanded == top.code) { expanded = if (expanded == top.code) null else top.code }
        }
    }
}

@Composable
private fun Totals(summary: MonthSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("本月支出", money(summary.spendingMinor, summary.currency), Modifier.weight(1f))
        StatCard("本月收入", money(summary.incomeMinor, summary.currency), Modifier.weight(1f))
        StatCard("结余", money(summary.netMinor, summary.currency), Modifier.weight(1f))
        StatCard("上月支出", money(summary.previousSpendingMinor, summary.currency), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryBar(
    total: CategoryTotal,
    summary: MonthSummary,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val max = summary.byCategory.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1
    val share = if (summary.spendingMinor > 0) total.amountMinor * 100 / summary.spendingMinor else 0
    Column(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${if (expanded) "▾" else "▸"} ${total.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${money(total.amountMinor, summary.currency)}  ·  $share%  ·  ${total.count} 笔")
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((total.amountMinor.toFloat() / max).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (expanded) {
            total.children.forEach { child ->
                Row(Modifier.padding(start = 24.dp, top = 4.dp)) {
                    Text(child.name, modifier = Modifier.weight(1f))
                    Text("${money(child.amountMinor, summary.currency)}  ·  ${child.count} 笔")
                }
            }
        }
    }
}
