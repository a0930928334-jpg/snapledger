package dev.snapledger.core

import dev.snapledger.core.importer.ImportPipeline
import dev.snapledger.core.importer.ImportPreview
import dev.snapledger.core.importer.PrepareResult
import dev.snapledger.core.ledger.ImportCommit
import dev.snapledger.core.ledger.LedgerSnapshot
import dev.snapledger.core.ledger.StoredTransaction
import dev.snapledger.core.model.TxnOrigin
import java.io.File
import kotlin.test.fail

const val ACCOUNT = "test-account"

fun sample(name: String): String = File(System.getProperty("snapledger.samplesDir"), name).readText()

fun ImportPipeline.prepareOrFail(
    text: String,
    snapshot: LedgerSnapshot = LedgerSnapshot.EMPTY,
): ImportPreview =
    when (val result = prepare(text, snapshot, ACCOUNT)) {
        is PrepareResult.Ready -> result.preview
        is PrepareResult.Failure -> fail("Expected a valid import, got ${result.issues}")
    }

/** In-memory stand-in for the repository: applies a commit to a snapshot. */
fun LedgerSnapshot.apply(commit: ImportCommit): LedgerSnapshot {
    val batch = "batch-${transactions.size}"
    val added =
        commit.transactions.mapIndexed { i, t ->
            StoredTransaction(
                id = "$batch-$i",
                accountId = ACCOUNT,
                date = t.date,
                direction = t.direction,
                amountMinor = t.amountMinor,
                currency = t.currency,
                type = t.type,
                categoryCode = t.categoryCode,
                classificationSource = t.classificationSource,
                merchant = t.merchant,
                counterpartyType = t.counterpartyType,
                rawDescription = t.rawDescription,
                fingerprint = t.fingerprint,
                note = t.note,
                origin = TxnOrigin.IMPORT,
                importBatchId = batch,
            )
        }
    val newKeys = commit.balances.map { it.date to it.currency }.toSet()
    return LedgerSnapshot(
        transactions = transactions + added,
        balances = balances.filterNot { (it.date to it.currency) in newKeys } + commit.balances,
        userRules = (userRules.filterNot { r -> commit.newRules.any { it.pattern == r.pattern } } + commit.newRules),
    )
}
