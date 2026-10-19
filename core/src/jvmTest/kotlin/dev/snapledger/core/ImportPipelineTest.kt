package dev.snapledger.core

import dev.snapledger.core.importer.ImportPipeline
import dev.snapledger.core.importer.ReconStatus
import dev.snapledger.core.importer.RowStatus
import dev.snapledger.core.ledger.LedgerSnapshot
import dev.snapledger.core.model.ClassificationSource
import dev.snapledger.core.model.TxnType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImportPipelineTest {
    private val pipeline = ImportPipeline()

    @Test
    fun example1ImportsEverythingAndReconciles() {
        val preview = pipeline.prepareOrFail(sample("example-1-basic.json"))
        val evaluation = pipeline.evaluate(preview, LedgerSnapshot.EMPTY)

        assertEquals(4, evaluation.newCount)
        val day15 = evaluation.days.single { it.date == LocalDate(2026, 9, 15) }
        assertEquals(ReconStatus.OK, day15.status)
        val day14 = evaluation.days.single { it.date == LocalDate(2026, 9, 14) }
        assertEquals(ReconStatus.UNVERIFIABLE, day14.status)
    }

    @Test
    fun importingTheSameJsonTwiceAddsNothing() {
        val text = sample("example-1-basic.json")
        val first = pipeline.prepareOrFail(text)
        val snapshot = LedgerSnapshot.EMPTY.apply(pipeline.commit(first, pipeline.evaluate(first, LedgerSnapshot.EMPTY)))

        val second = pipeline.prepareOrFail(text, snapshot)
        val evaluation = pipeline.evaluate(second, snapshot)

        assertEquals(0, evaluation.newCount)
        assertEquals(4, evaluation.skippedCount)
        assertTrue(evaluation.rows.all { it.status == RowStatus.DUPLICATE })
        assertEquals(0, pipeline.commit(second, evaluation).transactions.size)
    }

    @Test
    fun example2MergesOverlapAndKeepsBothCoffees() {
        val preview = pipeline.prepareOrFail(sample("example-2-overlap.json"))
        val evaluation = pipeline.evaluate(preview, LedgerSnapshot.EMPTY)

        assertEquals(4, preview.rows.size)
        assertEquals(2, preview.rows.count { it.rawDescription.startsWith("PRET") })
        assertTrue(preview.notices.any { "2 条已合并" in it })
        assertEquals(ReconStatus.OK, evaluation.days.single { it.date == LocalDate(2026, 9, 16) }.status)
    }

    @Test
    fun example3PointsAtTheUnreadableAmountAndBalancesOnceFilled() {
        val preview = pipeline.prepareOrFail(sample("example-3-special.json"))
        val evaluation = pipeline.evaluate(preview, LedgerSnapshot.EMPTY)

        val costa = evaluation.rows.single { it.row.rawDescription.startsWith("COSTA") }
        assertEquals(RowStatus.INCOMPLETE, costa.status)
        assertFalse(evaluation.canCommit)
        val day20 = evaluation.days.single { it.date == LocalDate(2026, 9, 20) }
        assertEquals(ReconStatus.MISMATCH, day20.status)
        assertEquals(-320L, day20.differenceMinor)
        val likely = assertNotNull(day20.likelyAmount)
        assertEquals(costa.row.id, likely.rowId)
        assertEquals("金额很可能是 £3.20", ImportPipeline.likelyAmountHint(likely, "GBP"))

        val filled = preview.copy(rows = preview.rows.map { if (it.id == costa.row.id) it.copy(amountMinor = 320) else it })
        val after = pipeline.evaluate(filled, LedgerSnapshot.EMPTY)
        assertEquals(ReconStatus.OK, after.days.single { it.date == LocalDate(2026, 9, 20) }.status)
        assertTrue(after.canCommit)
    }

    @Test
    fun personalTransfersAndRefundsAreClassified() {
        val preview1 = pipeline.prepareOrFail(sample("example-1-basic.json"))
        val alex = preview1.rows.single { it.rawDescription.startsWith("Alex Chen") }
        assertEquals("personal_transfer.general", alex.categoryCode)
        assertEquals(ClassificationSource.BUILTIN_RULE, alex.classificationSource)

        val preview3 = pipeline.prepareOrFail(sample("example-3-special.json"))
        val refund = preview3.rows.single { it.rawDescription.startsWith("MARKS") }
        assertEquals(TxnType.REFUND, refund.type)
        val friend = preview3.rows.single { it.rawDescription.startsWith("Jamie Li") }
        assertEquals(TxnType.INCOME, friend.type)
        assertEquals("income.personal", friend.categoryCode)
    }

    @Test
    fun screenshotCutMidDayOnlyAddsTheMissingRows() {
        // Day 16 Sep has a coffee, a second coffee and an Uber; the first import only saw one coffee.
        val full = sample("example-2-overlap.json")
        val partial =
            """
            {"schemaVersion":"1.0","source":{"extractedBy":"claude","institution":null,"screenshotCount":1},
             "transactions":[{"screenshot":1,"position":1,"date":"2026-09-16","direction":"out","amount":"3.50","currency":"GBP",
               "rawDescription":"PRET A MANGER | LEEDS GB","merchant":"Pret A Manger","counterpartyType":"merchant",
               "suggestedType":"expense","suggestedCategory":"dining.cafe","status":"posted","uncertainFields":[],"note":null}],
             "dailyBalances":[],"warnings":[]}
            """.trimIndent()
        val first = pipeline.prepareOrFail(partial)
        val snapshot = LedgerSnapshot.EMPTY.apply(pipeline.commit(first, pipeline.evaluate(first, LedgerSnapshot.EMPTY)))

        val second = pipeline.prepareOrFail(full, snapshot)
        val evaluation = pipeline.evaluate(second, snapshot)
        assertEquals(1, evaluation.rows.count { it.status == RowStatus.DUPLICATE })
        assertEquals(3, evaluation.newCount)
    }

    @Test
    fun screenshotsSentInReverseOrderStillAlign() {
        val reversed =
            sample("example-2-overlap.json")
                .replace("\"screenshot\": 1,", "\"screenshot\": 9,")
                .replace("\"screenshot\": 2,", "\"screenshot\": 1,")
                .replace("\"screenshot\": 9,", "\"screenshot\": 2,")
        val preview = pipeline.prepareOrFail(reversed)
        assertEquals(4, preview.rows.size)
        assertEquals(2, preview.rows.count { it.rawDescription.startsWith("PRET") })
    }

    @Test
    fun userCategoryEditBecomesARuleForTheNextImport() {
        val first = pipeline.prepareOrFail(sample("example-1-basic.json"))
        val edited =
            first.copy(
                rows =
                    first.rows.map {
                        if (it.rawDescription.startsWith(
                                "Alex Chen",
                            )
                        ) {
                            it.copy(categoryCode = "dining.restaurant", categoryEdited = true)
                        } else {
                            it
                        }
                    },
            )
        val commit = pipeline.commit(edited, pipeline.evaluate(edited, LedgerSnapshot.EMPTY))
        assertEquals(1, commit.newRules.size)
        val snapshot = LedgerSnapshot.EMPTY.apply(commit)

        val again = pipeline.prepareOrFail(sample("example-1-basic.json"), snapshot)
        val alex = again.rows.single { it.rawDescription.startsWith("Alex Chen") }
        assertEquals("dining.restaurant", alex.categoryCode)
        assertEquals(ClassificationSource.USER_RULE, alex.classificationSource)
    }

    @Test
    fun suspectedDuplicateIsSkippedWhenThatBalancesTheDay() {
        val first = pipeline.prepareOrFail(sample("example-1-basic.json"))
        val snapshot = LedgerSnapshot.EMPTY.apply(pipeline.commit(first, pipeline.evaluate(first, LedgerSnapshot.EMPTY)))

        // The AI re-transcribes the Tesco row with a different description on a second import.
        val retyped = sample("example-1-basic.json").replace("TESCO STORES 3021 | LEEDS GB", "TESCO STORE 3021 | LEEDS")
        val second = pipeline.prepareOrFail(retyped, snapshot)
        val evaluation = pipeline.evaluate(second, snapshot)
        val tesco = evaluation.rows.single { it.row.rawDescription.startsWith("TESCO") }
        assertEquals(RowStatus.SUSPECTED_DUPLICATE, tesco.status)
        assertFalse(tesco.include)
        assertEquals(ReconStatus.OK, evaluation.days.single { it.date == LocalDate(2026, 9, 15) }.status)
    }
}
