package dev.snapledger.core.importer

import dev.snapledger.core.model.CounterpartyType
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.TxnType
import kotlinx.datetime.LocalDate

/** One transaction row as transcribed by the AI, after validation and conversion to minor units. */
data class ImportedRow(
    /** Index in the original `transactions` array; stable identity for the preview. */
    val id: Int,
    val screenshot: Int,
    val position: Int,
    val date: LocalDate?,
    val direction: Direction?,
    val amountMinor: Long?,
    val currency: String?,
    val rawDescription: String,
    val merchant: String?,
    val counterpartyType: CounterpartyType,
    val suggestedType: TxnType?,
    val suggestedCategory: String?,
    val uncertainFields: Set<String>,
    val note: String?,
) {
    val isDateUncertain: Boolean get() = date == null || "date" in uncertainFields
}

data class ImportedBalance(
    val date: LocalDate,
    val balanceMinor: Long,
    val currency: String,
    val screenshot: Int,
)

data class ParsedImport(
    val extractedBy: String,
    val screenshotCount: Int,
    val rows: List<ImportedRow>,
    val balances: List<ImportedBalance>,
    val warnings: List<String>,
    val skippedPending: Int,
)

/** A problem found while reading the import text; [path] points at the offending field. */
data class ImportIssue(
    val path: String,
    val message: String,
)

sealed interface ParseResult {
    data class Success(
        val import: ParsedImport,
    ) : ParseResult

    data class Failure(
        val issues: List<ImportIssue>,
    ) : ParseResult
}
