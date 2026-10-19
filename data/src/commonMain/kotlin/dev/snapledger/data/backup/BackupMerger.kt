package dev.snapledger.data.backup

import dev.snapledger.core.ledger.MergeReport
import dev.snapledger.data.db.SnapLedgerDatabase

/**
 * Builds and merges backup payloads (docs/02 section 7, ADR-012).
 *
 * Rows are matched by id and the newer `updated_at` wins, deletions included. Two extra rules handle
 * data that both devices created independently:
 * - Daily balances are matched by (account, date) and rules by pattern, since each device mints its own ids.
 * - A transaction with an unknown id is skipped when this device already has an active transaction with the
 *   same fingerprint that the backup does not know about: both devices imported the same screenshot.
 *   Matching is count-based, so two genuine identical purchases still need two rows on each side.
 */
class BackupMerger(
    private val db: SnapLedgerDatabase,
    private val clock: () -> Long,
) {
    private val q = db.ledgerQueries

    fun payload(): BackupPayload =
        BackupPayload(
            exportedAt = clock(),
            accounts = q.allAccounts().executeAsList().map { it.toRow() },
            transactions = q.allTransactions().executeAsList().map { it.toRow() },
            balances = q.allBalances().executeAsList().map { it.toRow() },
            rules = q.allRules().executeAsList().map { it.toRow() },
            batches = q.allBatches().executeAsList().map { it.toRow() },
        )

    fun merge(payload: BackupPayload): MergeReport {
        var report = MergeReport(0, 0, 0, 0, 0)
        db.transaction {
            mergeAccounts(payload)
            mergeBatches(payload)
            report = mergeTransactions(payload)
            report = report.copy(balancesChanged = mergeBalances(payload), rulesChanged = mergeRules(payload))
        }
        return report
    }

    private fun mergeAccounts(payload: BackupPayload) {
        val local = q.allAccounts().executeAsList().associateBy { it.id }
        payload.accounts.forEach { incoming ->
            val existing = local[incoming.id]
            when {
                existing == null -> {
                    q.insertAccountFull(incoming.toDb())
                }

                incoming.updatedAt > existing.updated_at -> {
                    q.updateAccountFull(
                        incoming.name,
                        incoming.institution,
                        incoming.currency,
                        incoming.type,
                        incoming.updatedAt,
                        incoming.deletedAt,
                        incoming.id,
                    )
                }
            }
        }
    }

    private fun mergeBatches(payload: BackupPayload) {
        payload.batches.forEach { incoming ->
            val existing = q.batchById(incoming.id).executeAsOneOrNull()
            when {
                existing == null -> q.insertBatchFull(incoming.toDb())
                incoming.updatedAt > existing.updated_at -> q.updateBatchFull(incoming.updatedAt, incoming.deletedAt, incoming.id)
            }
        }
    }

    private fun mergeTransactions(payload: BackupPayload): MergeReport {
        val local = q.allTransactions().executeAsList()
        val localById = local.associateBy { it.id }
        val incomingIds = payload.transactions.map { it.id }.toSet()
        val unmatched =
            local
                .filter { it.deleted_at == null && it.id !in incomingIds }
                .groupingBy { it.fingerprint }
                .eachCount()
                .toMutableMap()
        var added = 0
        var updated = 0
        var matched = 0
        payload.transactions.forEach { incoming ->
            val existing = localById[incoming.id]
            when {
                existing != null -> {
                    if (incoming.updatedAt > existing.updated_at) {
                        q.updateTransactionFull(
                            incoming.accountId,
                            incoming.date,
                            incoming.direction,
                            incoming.amountMinor,
                            incoming.currency,
                            incoming.type,
                            incoming.categoryCode,
                            incoming.classificationSource,
                            incoming.merchant,
                            incoming.counterpartyType,
                            incoming.rawDescription,
                            incoming.fingerprint,
                            incoming.note,
                            incoming.origin,
                            incoming.importBatchId,
                            incoming.updatedAt,
                            incoming.deletedAt,
                            incoming.id,
                        )
                        updated++
                    }
                }

                incoming.deletedAt == null && (unmatched[incoming.fingerprint] ?: 0) > 0 -> {
                    unmatched[incoming.fingerprint] = unmatched.getValue(incoming.fingerprint) - 1
                    matched++
                }

                else -> {
                    q.insertTransactionFull(incoming.toDb())
                    if (incoming.deletedAt == null) added++
                }
            }
        }
        return MergeReport(added, updated, matched, 0, 0)
    }

    private fun mergeBalances(payload: BackupPayload): Int {
        val local = q.allBalances().executeAsList().associateBy { it.account_id to it.date }
        var changed = 0
        payload.balances.forEach { incoming ->
            val existing = local[incoming.accountId to incoming.date]
            when {
                existing == null -> {
                    q.insertBalanceFull(incoming.toDb())
                    changed++
                }

                incoming.updatedAt > existing.updated_at -> {
                    q.updateBalanceFull(
                        incoming.balanceMinor,
                        incoming.currency,
                        incoming.importBatchId,
                        incoming.previousBalanceMinor,
                        incoming.previousBatchId,
                        incoming.updatedAt,
                        incoming.deletedAt,
                        existing.id,
                    )
                    changed++
                }
            }
        }
        return changed
    }

    private fun mergeRules(payload: BackupPayload): Int {
        val local = q.allRules().executeAsList().associateBy { it.pattern }
        var changed = 0
        payload.rules.forEach { incoming ->
            val existing = local[incoming.pattern]
            when {
                existing == null -> {
                    q.insertRuleFull(incoming.toDb())
                    changed++
                }

                incoming.updatedAt > existing.updated_at -> {
                    q.updateRuleFull(incoming.categoryCode, incoming.updatedAt, incoming.deletedAt, existing.id)
                    changed++
                }
            }
        }
        return changed
    }
}
