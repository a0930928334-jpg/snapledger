package dev.snapledger.core.importer

import dev.snapledger.core.model.Direction
import kotlinx.datetime.LocalDate

/** One money movement as far as reconciliation is concerned; unknown fields are allowed. */
data class Movement(
    val date: LocalDate,
    val direction: Direction?,
    val amountMinor: Long?,
    val currency: String,
    /** Preview row id when the movement comes from the batch being imported. */
    val rowId: Int? = null,
)

data class BalancePoint(
    val date: LocalDate,
    val currency: String,
    val balanceMinor: Long,
)

enum class ReconStatus { OK, MISMATCH, UNVERIFIABLE }

/** "Row [rowId] is probably [amountMinor]": the single unreadable amount that would make the day balance. */
data class LikelyAmount(
    val rowId: Int,
    val amountMinor: Long,
)

data class DayReconciliation(
    val date: LocalDate,
    val currency: String,
    val actualMinor: Long,
    val previousDate: LocalDate?,
    val expectedMinor: Long?,
    val status: ReconStatus,
    /** actual − expected; negative means more money went out than the rows explain. */
    val differenceMinor: Long?,
    val likelyAmount: LikelyAmount?,
)

/** End-of-day balance check from docs/02 section 4.5. */
object Reconciler {
    fun reconcile(
        check: List<BalancePoint>,
        allBalances: List<BalancePoint>,
        movements: List<Movement>,
    ): List<DayReconciliation> =
        check.map { point ->
            val previous =
                allBalances
                    .filter { it.currency == point.currency && it.date < point.date }
                    .maxByOrNull { it.date }
            if (previous == null) {
                DayReconciliation(point.date, point.currency, point.balanceMinor, null, null, ReconStatus.UNVERIFIABLE, null, null)
            } else {
                val inRange = movements.filter { it.currency == point.currency && it.date > previous.date && it.date <= point.date }
                val expected =
                    previous.balanceMinor +
                        inRange.sumOf { m ->
                            when {
                                m.amountMinor == null || m.direction == null -> 0L
                                m.direction == Direction.IN -> m.amountMinor
                                else -> -m.amountMinor
                            }
                        }
                val difference = point.balanceMinor - expected
                val status = if (difference == 0L) ReconStatus.OK else ReconStatus.MISMATCH
                DayReconciliation(
                    date = point.date,
                    currency = point.currency,
                    actualMinor = point.balanceMinor,
                    previousDate = previous.date,
                    expectedMinor = expected,
                    status = status,
                    differenceMinor = difference,
                    likelyAmount = if (status == ReconStatus.MISMATCH) likelyAmount(inRange, difference) else null,
                )
            }
        }

    private fun likelyAmount(
        inRange: List<Movement>,
        difference: Long,
    ): LikelyAmount? {
        val unknown = inRange.filter { it.amountMinor == null && it.direction != null && it.rowId != null }
        val only = unknown.singleOrNull() ?: return null
        val amount = if (only.direction == Direction.OUT) -difference else difference
        return if (amount > 0) LikelyAmount(only.rowId!!, amount) else null
    }
}
