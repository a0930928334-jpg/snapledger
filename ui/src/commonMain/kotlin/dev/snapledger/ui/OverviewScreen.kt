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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.snapledger.ui.theme.SnapShapes
import dev.snapledger.ui.theme.SnapTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Home screen: this month's spending (the one hero card), income and where the money went. */
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

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                TextButton(onClick = {
                    if (month == 1) {
                        year--
                        month = 12
                    } else {
                        month--
                    }
                }) { Text("‹ 上个月", color = SnapTheme.colors.accent) }
                Text("${year}年${month}月", style = SnapTheme.type.pageTitle, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (month == 12) {
                        year++
                        month = 1
                    } else {
                        month++
                    }
                }) { Text("下个月 ›", color = SnapTheme.colors.accent) }
            }
        }
        item { CtaButton("＋ 导入流水", onImport) }
        item { HeroCard(summary) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("本月收入", money(summary.incomeMinor, summary.currency), Modifier.weight(1f))
                StatCard("上月支出", money(summary.previousSpendingMinor, summary.currency), Modifier.weight(1f))
            }
        }
        item { Text("钱花在哪", style = SnapTheme.type.title, modifier = Modifier.padding(top = 4.dp)) }
        item {
            SnapCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
                if (summary.byCategory.isEmpty()) {
                    Text("这个月还没有支出记录。", color = SnapTheme.colors.mute)
                }
                summary.byCategory.forEach { top ->
                    CategoryBar(top, summary, expanded == top.code) { expanded = if (expanded == top.code) null else top.code }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(summary: MonthSummary) {
    val c = SnapTheme.colors
    SnapCard(modifier = Modifier.fillMaxWidth(), color = c.hero, padding = 24.dp) {
        Text("本月支出", color = c.onHero, style = SnapTheme.type.body)
        Text(money(summary.spendingMinor, summary.currency), color = c.onHero, style = SnapTheme.type.heroNumber)
        Text("结余 ${money(summary.netMinor, summary.currency)}", color = c.heroMute, style = SnapTheme.type.body)
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    SnapCard(modifier = modifier, padding = 16.dp) {
        Text(title, style = SnapTheme.type.caption, color = SnapTheme.colors.mute)
        Text(value, style = SnapTheme.type.statNumber)
    }
}

@Composable
private fun CategoryBar(
    total: CategoryTotal,
    summary: MonthSummary,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val c = SnapTheme.colors
    val max = summary.byCategory.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1
    val share = if (summary.spendingMinor > 0) total.amountMinor * 100 / summary.spendingMinor else 0
    Column(Modifier.clickable(onClick = onClick).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${if (expanded) "▾" else "▸"} ${total.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(
                "${money(total.amountMinor, summary.currency)} · $share% · ${total.count} 笔",
                color = c.mute,
                style = SnapTheme.type.caption,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(SnapShapes.pill)
                .background(c.surfaceSoft),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((total.amountMinor.toFloat() / max).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(SnapShapes.pill)
                    .background(c.accent),
            )
        }
        if (expanded) {
            total.children.forEach { child ->
                Row(Modifier.padding(start = 20.dp)) {
                    Text(child.name, modifier = Modifier.weight(1f), color = c.body)
                    Text("${money(child.amountMinor, summary.currency)} · ${child.count} 笔", color = c.mute, style = SnapTheme.type.caption)
                }
            }
        }
    }
}
