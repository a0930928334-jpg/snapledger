package dev.snapledger.core.ledger

import dev.snapledger.core.model.ClassificationSource
import dev.snapledger.core.model.CounterpartyType
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.TxnOrigin
import dev.snapledger.core.model.TxnType
import kotlinx.datetime.LocalDate

/** A transaction already in the ledger (soft-deleted rows are never included). */
data class StoredTransaction(
    val id: String,
    val accountId: String,
    val date: LocalDate,
    val direction: Direction,
    val amountMinor: Long,
    val currency: String,
    val type: TxnType,
    val categoryCode: String?,
    val classificationSource: ClassificationSource,
    val merchant: String?,
    val counterpartyType: CounterpartyType,
    val rawDescription: String,
    val fingerprint: String,
    val note: String?,
    val origin: TxnOrigin,
    val importBatchId: String?,
)

data class StoredBalance(
    val accountId: String,
    val date: LocalDate,
    val balanceMinor: Long,
    val currency: String,
)

/** "Always file [pattern] under [categoryCode]"; [pattern] is a normalised merchant or description. */
data class UserRule(
    val pattern: String,
    val categoryCode: String,
)

/** Everything the import pipeline needs to know about the existing ledger of one account. */
data class LedgerSnapshot(
    val transactions: List<StoredTransaction>,
    val balances: List<StoredBalance>,
    val userRules: List<UserRule>,
) {
    companion object {
        val EMPTY = LedgerSnapshot(emptyList(), emptyList(), emptyList())
    }
}

/** A transaction about to be written by a confirmed import. */
data class NewTransaction(
    val date: LocalDate,
    val direction: Direction,
    val amountMinor: Long,
    val currency: String,
    val type: TxnType,
    val categoryCode: String?,
    val classificationSource: ClassificationSource,
    val merchant: String?,
    val counterpartyType: CounterpartyType,
    val rawDescription: String,
    val fingerprint: String,
    val note: String?,
)

data class ImportCommit(
    val extractedBy: String,
    val transactions: List<NewTransaction>,
    val balances: List<StoredBalance>,
    val skippedCount: Int,
    val newRules: List<UserRule>,
)

data class ImportBatchInfo(
    val id: String,
    val importedAtMillis: Long,
    val extractedBy: String,
    val addedCount: Int,
    val skippedCount: Int,
    val undone: Boolean,
)

/** Persistence port implemented by the data module; core never touches the database directly. */
interface LedgerRepository {
    fun snapshot(accountId: String): LedgerSnapshot

    /** Writes everything in one transaction and returns the new import batch id. */
    fun commitImport(
        accountId: String,
        commit: ImportCommit,
    ): String

    fun importBatches(accountId: String): List<ImportBatchInfo>

    /** Soft-deletes the batch's transactions and removes the balances it introduced. */
    fun undoBatch(batchId: String)

    fun updateCategory(
        transactionId: String,
        categoryCode: String?,
        source: ClassificationSource,
    )

    fun deleteTransaction(transactionId: String)

    fun saveUserRule(rule: UserRule)
}
