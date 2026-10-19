package dev.snapledger.data

import dev.snapledger.core.ledger.BackupPort

/** Everything the UI needs from one local database. */
class LocalStore(
    val repository: SqlLedgerRepository,
    val backup: BackupPort,
)
