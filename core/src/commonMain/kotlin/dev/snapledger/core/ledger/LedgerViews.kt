package dev.snapledger.core.ledger

import dev.snapledger.core.category.CategoryCatalog
import dev.snapledger.core.importer.BalancePoint
import dev.snapledger.core.importer.DayReconciliation
import dev.snapledger.core.importer.Movement
import dev.snapledger.core.importer.Reconciler
import dev.snapledger.core.model.TxnType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** One day of the transaction list, laid out like the bank app: rows plus end-of-day balance and check mark. */
data class LedgerDay(
    val date: LocalDate,
    val transactions: List<StoredTransaction>,
    val balance: StoredBalance?,
    val reconciliation: DayReconciliation?,
)

data class CategoryTotal(
    val code: String,
    val name: String,
    val amountMinor: Long,
    val count: Int,
    val children: List<CategoryTotal> = emptyList(),
)

data class MonthSummary(
    val year: Int,
    val month: Int,
    val currency: String,
    /** Expenses minus refunds; transfers never count (docs/05 section 1). */
    val spendingMinor: Long,
    val incomeMinor: Long,
    val previousSpendingMinor: Long,
    val byCategory: List<CategoryTotal>,
) {
    val netMinor: Long get() = incomeMinor - spendingMinor
}

object LedgerViews {
    const val UNCATEGORISED = "uncategorised"

    fun days(
        snapshot: LedgerSnapshot,
        accountId: String,
    ): List<LedgerDay> {
        val transactions = snapshot.transactions.filter { it.accountId == accountId }
        val balances = snapshot.balances.filter { it.accountId == accountId }
        val points = balances.map { BalancePoint(it.date, it.currency, it.balanceMinor) }
        val movements = transactions.map { Movement(it.date, it.direction, it.amountMinor, it.currency) }
        val recon = Reconciler.reconcile(points, points, movements).associateBy { it.date to it.currency }
        val byDate = transactions.groupBy { it.date }
        val balanceByDate = balances.associateBy { it.date }
        return (byDate.keys + balanceByDate.keys)
            .sortedDescending()
            .map { date ->
                val balance = balanceByDate[date]
                LedgerDay(
                    date = date,
                    transactions = byDate[date].orEmpty(),
                    balance = balance,
                    reconciliation = balance?.let { recon[date to it.currency] },
                )
            }
    }

    fun monthSummary(
        snapshot: LedgerSnapshot,
        accountId: String,
        year: Int,
        month: Int,
        currency: String,
        catalog: CategoryCatalog = CategoryCatalog.DEFAULT,
    ): MonthSummary {
        val all = snapshot.transactions.filter { it.accountId == accountId && it.currency == currency }
        val inMonth = all.filter { it.date.year == year && it.date.month.number == month }
        val (prevYear, prevMonth) = if (month == 1) year - 1 to 12 else year to month - 1
        val previous = all.filter { it.date.year == prevYear && it.date.month.number == prevMonth }

        val spendingRows = inMonth.filter { it.type == TxnType.EXPENSE || it.type == TxnType.REFUND }
        val byLeaf =
            spendingRows.groupBy { it.categoryCode ?: UNCATEGORISED }.mapValues { (_, rows) ->
                rows.sumOf { it.spendingSign() } to rows.size
            }
        val byTop =
            byLeaf.entries
                .groupBy { (code, _) -> code.substringBefore('.') }
                .map { (top, leaves) ->
                    val children =
                        leaves
                            .map { (code, totals) -> CategoryTotal(code, nameOf(catalog, code), totals.first, totals.second) }
                            .sortedByDescending { it.amountMinor }
                    CategoryTotal(top, nameOf(catalog, top), children.sumOf { it.amountMinor }, children.sumOf { it.count }, children)
                }.sortedByDescending { it.amountMinor }

        return MonthSummary(
            year = year,
            month = month,
            currency = currency,
            spendingMinor = spendingRows.sumOf { it.spendingSign() },
            incomeMinor = inMonth.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinor },
            previousSpendingMinor = previous.filter { it.type == TxnType.EXPENSE || it.type == TxnType.REFUND }.sumOf { it.spendingSign() },
            byCategory = byTop,
        )
    }

    private fun StoredTransaction.spendingSign(): Long = if (type == TxnType.REFUND) -amountMinor else amountMinor

    private fun nameOf(
        catalog: CategoryCatalog,
        code: String,
    ): String = if (code == UNCATEGORISED) "未分类" else catalog[code]?.nameZh ?: code
}
