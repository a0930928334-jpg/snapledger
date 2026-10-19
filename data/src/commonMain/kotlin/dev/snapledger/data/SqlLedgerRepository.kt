package dev.snapledger.data

import dev.snapledger.core.ledger.ImportBatchInfo
import dev.snapledger.core.ledger.ImportCommit
import dev.snapledger.core.ledger.LedgerRepository
import dev.snapledger.core.ledger.LedgerSnapshot
import dev.snapledger.core.ledger.StoredBalance
import dev.snapledger.core.ledger.StoredTransaction
import dev.snapledger.core.ledger.UserRule
import dev.snapledger.core.model.ClassificationSource
import dev.snapledger.core.model.CounterpartyType
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.TxnOrigin
import dev.snapledger.core.model.TxnType
import dev.snapledger.data.db.SnapLedgerDatabase
import dev.snapledger.data.db.Txn
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** SQLDelight implementation of the core's [LedgerRepository]. Every write runs in one database transaction. */
class SqlLedgerRepository(
    private val db: SnapLedgerDatabase,
    private val clock: () -> Long,
) : LedgerRepository {
    private val q = db.ledgerQueries

    init {
        if (q.accountById(DEFAULT_ACCOUNT_ID).executeAsOneOrNull() == null) {
            q.insertAccount(DEFAULT_ACCOUNT_ID, "HSBC UK", "HSBC UK", DEFAULT_CURRENCY, "CURRENT", clock())
        }
    }

    override fun snapshot(accountId: String): LedgerSnapshot =
        LedgerSnapshot(
            transactions = q.activeTransactions(accountId).executeAsList().map { it.toStored() },
            balances =
                q.activeBalances(accountId).executeAsList().map {
                    StoredBalance(it.account_id, LocalDate.parse(it.date), it.balance_minor, it.currency)
                },
            userRules = q.activeRules().executeAsList().map { UserRule(it.pattern, it.category_code) },
        )

    override fun commitImport(
        accountId: String,
        commit: ImportCommit,
    ): String {
        val batchId = newId()
        val now = clock()
        db.transaction {
            q.insertBatch(batchId, accountId, now, commit.extractedBy, commit.transactions.size.toLong(), commit.skippedCount.toLong(), now)
            commit.transactions.forEach { t ->
                q.insertTransaction(
                    newId(),
                    accountId,
                    t.date.toString(),
                    t.direction.name,
                    t.amountMinor,
                    t.currency,
                    t.type.name,
                    t.categoryCode,
                    t.classificationSource.name,
                    t.merchant,
                    t.counterpartyType.name,
                    t.rawDescription,
                    t.fingerprint,
                    t.note,
                    TxnOrigin.IMPORT.name,
                    batchId,
                    now,
                )
            }
            commit.balances.forEach { b ->
                val existing = q.balanceForDate(accountId, b.date.toString()).executeAsOneOrNull()
                if (existing == null) {
                    q.insertBalance(newId(), accountId, b.date.toString(), b.balanceMinor, b.currency, batchId, now)
                } else {
                    val live = existing.deleted_at == null
                    q.replaceBalance(
                        balance = b.balanceMinor,
                        currency = b.currency,
                        batchId = batchId,
                        previousBalance = if (live) existing.balance_minor else null,
                        previousBatchId = if (live) existing.import_batch_id else null,
                        now = now,
                        id = existing.id,
                    )
                }
            }
            commit.newRules.forEach { upsertRule(it, now) }
        }
        return batchId
    }

    override fun importBatches(accountId: String): List<ImportBatchInfo> =
        q.batches(accountId).executeAsList().map {
            ImportBatchInfo(it.id, it.imported_at, it.extracted_by, it.added_count.toInt(), it.skipped_count.toInt(), it.deleted_at != null)
        }

    override fun undoBatch(batchId: String) {
        val now = clock()
        db.transaction {
            q.softDeleteBatchTransactions(now = now, batchId = batchId)
            q.balancesOfBatch(batchId).executeAsList().forEach { balance ->
                if (balance.previous_balance_minor != null) {
                    q.restorePreviousBalance(now = now, id = balance.id)
                } else {
                    q.softDeleteBalance(now = now, id = balance.id)
                }
            }
            q.markBatchUndone(now = now, id = batchId)
        }
    }

    override fun updateCategory(
        transactionId: String,
        categoryCode: String?,
        source: ClassificationSource,
    ) {
        q.updateTransactionCategory(categoryCode, source.name, clock(), transactionId)
    }

    override fun deleteTransaction(transactionId: String) {
        q.softDeleteTransaction(now = clock(), id = transactionId)
    }

    override fun saveUserRule(rule: UserRule) {
        db.transaction { upsertRule(rule, clock()) }
    }

    private fun upsertRule(
        rule: UserRule,
        now: Long,
    ) {
        if (q.ruleByPattern(rule.pattern).executeAsOneOrNull() == null) {
            q.insertRule(newId(), rule.pattern, rule.categoryCode, now)
        } else {
            q.updateRule(rule.categoryCode, now, rule.pattern)
        }
    }

    private fun Txn.toStored() =
        StoredTransaction(
            id = id,
            accountId = account_id,
            date = LocalDate.parse(date),
            direction = Direction.valueOf(direction),
            amountMinor = amount_minor,
            currency = currency,
            type = TxnType.valueOf(type),
            categoryCode = category_code,
            classificationSource = ClassificationSource.valueOf(classification_source),
            merchant = merchant,
            counterpartyType = CounterpartyType.valueOf(counterparty_type),
            rawDescription = raw_description,
            fingerprint = fingerprint,
            note = note,
            origin = TxnOrigin.valueOf(origin),
            importBatchId = import_batch_id,
        )

    companion object {
        /** The single MVP account (docs/README: one HSBC UK GBP debit account). */
        const val DEFAULT_ACCOUNT_ID = "hsbc-uk-gbp"
        const val DEFAULT_CURRENCY = "GBP"

        @OptIn(ExperimentalUuidApi::class)
        private fun newId(): String = Uuid.random().toString()
    }
}
