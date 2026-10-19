package dev.snapledger.data

import dev.snapledger.core.importer.ImportPipeline
import dev.snapledger.core.importer.PrepareResult
import dev.snapledger.data.SqlLedgerRepository.Companion.DEFAULT_ACCOUNT_ID
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SqlLedgerRepositoryTest {
    private val pipeline = ImportPipeline()

    private fun sample(name: String) = File(System.getProperty("snapledger.samplesDir"), name).readText()

    private fun importSample(
        repo: SqlLedgerRepository,
        name: String,
    ): Pair<String, Int> {
        val snapshot = repo.snapshot(DEFAULT_ACCOUNT_ID)
        val preview =
            when (val r = pipeline.prepare(sample(name), snapshot, DEFAULT_ACCOUNT_ID)) {
                is PrepareResult.Ready -> r.preview
                is PrepareResult.Failure -> fail(r.issues.toString())
            }
        val commit = pipeline.commit(preview, pipeline.evaluate(preview, snapshot))
        return repo.commitImport(DEFAULT_ACCOUNT_ID, commit) to commit.transactions.size
    }

    @Test
    fun importTwiceThenUndo() {
        val repo = JvmDatabase.open(null).repository
        val (firstBatch, firstAdded) = importSample(repo, "example-1-basic.json")
        assertEquals(4, firstAdded)
        assertEquals(4, repo.snapshot(DEFAULT_ACCOUNT_ID).transactions.size)
        assertEquals(2, repo.snapshot(DEFAULT_ACCOUNT_ID).balances.size)

        val (_, secondAdded) = importSample(repo, "example-1-basic.json")
        assertEquals(0, secondAdded)
        assertEquals(4, repo.snapshot(DEFAULT_ACCOUNT_ID).transactions.size)

        repo.undoBatch(firstBatch)
        val after = repo.snapshot(DEFAULT_ACCOUNT_ID)
        assertTrue(after.transactions.isEmpty())
        // The second batch re-wrote the same balances, so they survive until that batch is undone too.
        assertEquals(2, after.balances.size)
        assertTrue(repo.importBatches(DEFAULT_ACCOUNT_ID).first { it.id == firstBatch }.undone)
    }

    @Test
    fun undoRestoresOverwrittenBalance() {
        val repo = JvmDatabase.open(null).repository
        importSample(repo, "example-1-basic.json")
        val changed = sample("example-1-basic.json").replace("\"1509.50\"", "\"1500.00\"")
        val snapshot = repo.snapshot(DEFAULT_ACCOUNT_ID)
        val preview = (pipeline.prepare(changed, snapshot, DEFAULT_ACCOUNT_ID) as PrepareResult.Ready).preview
        val batch = repo.commitImport(DEFAULT_ACCOUNT_ID, pipeline.commit(preview, pipeline.evaluate(preview, snapshot)))
        assertTrue(repo.snapshot(DEFAULT_ACCOUNT_ID).balances.any { it.balanceMinor == 150000L })

        repo.undoBatch(batch)
        val balances = repo.snapshot(DEFAULT_ACCOUNT_ID).balances
        assertTrue(balances.any { it.balanceMinor == 150950L })
        assertTrue(balances.none { it.balanceMinor == 150000L })
    }
}
