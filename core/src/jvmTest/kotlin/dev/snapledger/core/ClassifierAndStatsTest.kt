package dev.snapledger.core

import dev.snapledger.core.category.Classifier
import dev.snapledger.core.importer.ImportPipeline
import dev.snapledger.core.ledger.LedgerSnapshot
import dev.snapledger.core.ledger.LedgerViews
import dev.snapledger.core.model.TxnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClassifierAndStatsTest {
    private val classifier = Classifier()

    @Test
    fun longestKeywordWins() {
        assertEquals("dining.takeaway", classifier.matchKeyword("UBER* EATS | LONDON"))
        assertEquals("transport.taxi", classifier.matchKeyword("UBER *TRIP | HELP.UBER.COM"))
    }

    @Test
    fun keywordsMatchWholeWordsOnly() {
        assertEquals("groceries.supermarket", classifier.matchKeyword("SAINSBURYS S/MKTS | LEEDS"))
        assertEquals("groceries.supermarket", classifier.matchKeyword("MARKS&SPENCER PLC | LEEDS"))
        assertEquals("housing.internet_phone", classifier.matchKeyword("EE LIMITED | BRISTOL"))
        assertNull(classifier.matchKeyword("STEAMED BUN HOUSE | LEEDS"))
        assertNull(classifier.matchKeyword("FREEDOM CAFE | LEEDS"))
        assertNull(classifier.matchKeyword("REVUE BAR | LEEDS"))
    }

    @Test
    fun monthSummaryNetsRefundsAndIgnoresTransfers() {
        val pipeline = ImportPipeline()
        var snapshot = LedgerSnapshot.EMPTY
        listOf("example-1-basic.json", "example-2-overlap.json", "example-3-special.json").forEach { name ->
            val preview = pipeline.prepareOrFail(sample(name), snapshot)
            val filled = preview.copy(rows = preview.rows.map { if (it.amountMinor == null) it.copy(amountMinor = 320) else it })
            snapshot = snapshot.apply(pipeline.commit(filled, pipeline.evaluate(filled, snapshot)))
        }
        // Turn the Alex Chen payment into a transfer: it must vanish from spending.
        snapshot =
            snapshot.copy(
                transactions =
                    snapshot.transactions.map {
                        if (it.rawDescription.startsWith("Alex Chen")) it.copy(type = TxnType.TRANSFER, categoryCode = null) else it
                    },
            )

        val summary = LedgerViews.monthSummary(snapshot, ACCOUNT, 2026, 9, "GBP")
        // Expenses: 12.35 + 2.55 + 3.50 + 3.50 + 9.80 + 18.20 + 3.20 + 11.99 (+ the 4th example-1 row); refund −25.00.
        val expenses = snapshot.transactions.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinor }
        assertEquals(expenses - 2500, summary.spendingMinor)
        assertEquals(1500L, summary.incomeMinor)
        // The M&S refund follows the built-in M&S rule (supermarket) and reduces that category.
        val refund = snapshot.transactions.single { it.type == TxnType.REFUND }
        assertEquals("groceries.supermarket", refund.categoryCode)
        val groceries = snapshot.transactions.filter { it.type == TxnType.EXPENSE && it.categoryCode?.startsWith("groceries") == true }
        assertEquals(groceries.sumOf { it.amountMinor } - 2500, summary.byCategory.single { it.code == "groceries" }.amountMinor)
    }
}
