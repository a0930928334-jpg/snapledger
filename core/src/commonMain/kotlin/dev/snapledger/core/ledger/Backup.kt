package dev.snapledger.core.ledger

/** What merging a backup into this device's ledger changed (docs/02 section 7). */
data class MergeReport(
    val transactionsAdded: Int,
    val transactionsUpdated: Int,
    /** Rows the other device imported independently from the same screenshots; matched by fingerprint, not copied. */
    val transactionsMatched: Int,
    val balancesChanged: Int,
    val rulesChanged: Int,
) {
    val totalChanges: Int get() = transactionsAdded + transactionsUpdated + balancesChanged + rulesChanged
}

sealed interface BackupResult {
    data class Merged(
        val report: MergeReport,
    ) : BackupResult

    /** [message] is shown to the user as is. */
    data class Failed(
        val message: String,
    ) : BackupResult
}

/** Password-encrypted export and import of the whole ledger; the MVP's way to sync two devices. */
interface BackupPort {
    fun export(password: CharArray): ByteArray

    fun import(
        file: ByteArray,
        password: CharArray,
    ): BackupResult
}
