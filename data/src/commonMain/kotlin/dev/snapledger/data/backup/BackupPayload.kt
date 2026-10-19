package dev.snapledger.data.backup

import dev.snapledger.data.db.Account
import dev.snapledger.data.db.Daily_balance
import dev.snapledger.data.db.Import_batch
import dev.snapledger.data.db.Txn
import dev.snapledger.data.db.User_rule
import kotlinx.serialization.Serializable

/** Plaintext inside a `.slbackup` file: every row of every table, soft-deleted ones included. */
@Serializable
data class BackupPayload(
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: Long,
    val accounts: List<AccountRow>,
    val transactions: List<TxnRow>,
    val balances: List<BalanceRow>,
    val rules: List<RuleRow>,
    val batches: List<BatchRow>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class AccountRow(
    val id: String,
    val name: String,
    val institution: String?,
    val currency: String,
    val type: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
data class TxnRow(
    val id: String,
    val accountId: String,
    val date: String,
    val direction: String,
    val amountMinor: Long,
    val currency: String,
    val type: String,
    val categoryCode: String?,
    val classificationSource: String,
    val merchant: String?,
    val counterpartyType: String,
    val rawDescription: String,
    val fingerprint: String,
    val note: String?,
    val origin: String,
    val importBatchId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
data class BalanceRow(
    val id: String,
    val accountId: String,
    val date: String,
    val balanceMinor: Long,
    val currency: String,
    val importBatchId: String?,
    val previousBalanceMinor: Long?,
    val previousBatchId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
data class RuleRow(
    val id: String,
    val pattern: String,
    val categoryCode: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
data class BatchRow(
    val id: String,
    val accountId: String,
    val importedAt: Long,
    val extractedBy: String,
    val addedCount: Long,
    val skippedCount: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

internal fun Account.toRow() = AccountRow(id, name, institution, currency, type, updated_at, deleted_at)

internal fun AccountRow.toDb() = Account(id, name, institution, currency, type, updatedAt, deletedAt)

internal fun Txn.toRow() =
    TxnRow(
        id,
        account_id,
        date,
        direction,
        amount_minor,
        currency,
        type,
        category_code,
        classification_source,
        merchant,
        counterparty_type,
        raw_description,
        fingerprint,
        note,
        origin,
        import_batch_id,
        updated_at,
        deleted_at,
    )

internal fun TxnRow.toDb() =
    Txn(
        id,
        accountId,
        date,
        direction,
        amountMinor,
        currency,
        type,
        categoryCode,
        classificationSource,
        merchant,
        counterpartyType,
        rawDescription,
        fingerprint,
        note,
        origin,
        importBatchId,
        updatedAt,
        deletedAt,
    )

internal fun Daily_balance.toRow() =
    BalanceRow(
        id,
        account_id,
        date,
        balance_minor,
        currency,
        import_batch_id,
        previous_balance_minor,
        previous_batch_id,
        updated_at,
        deleted_at,
    )

internal fun BalanceRow.toDb() =
    Daily_balance(id, accountId, date, balanceMinor, currency, importBatchId, previousBalanceMinor, previousBatchId, updatedAt, deletedAt)

internal fun User_rule.toRow() = RuleRow(id, pattern, category_code, updated_at, deleted_at)

internal fun RuleRow.toDb() = User_rule(id, pattern, categoryCode, updatedAt, deletedAt)

internal fun Import_batch.toRow() = BatchRow(id, account_id, imported_at, extracted_by, added_count, skipped_count, updated_at, deleted_at)

internal fun BatchRow.toDb() = Import_batch(id, accountId, importedAt, extractedBy, addedCount, skippedCount, updatedAt, deletedAt)
