package dev.snapledger.core.importer

import kotlinx.serialization.Serializable

// Wire format of import format v1 (schemas/import-v1.schema.json).
// Enum-like fields stay strings here so validation can report friendly, field-level errors.

@Serializable
internal data class ImportDocumentDto(
    val schemaVersion: String,
    val source: SourceDto,
    val transactions: List<TransactionDto>,
    val dailyBalances: List<DailyBalanceDto>,
    val warnings: List<String>,
)

@Serializable
internal data class SourceDto(
    val extractedBy: String,
    val institution: String? = null,
    val screenshotCount: Int,
)

@Serializable
internal data class TransactionDto(
    val screenshot: Int,
    val position: Int,
    val date: String?,
    val direction: String?,
    val amount: String?,
    val currency: String?,
    val rawDescription: String,
    val merchant: String?,
    val counterpartyType: String,
    val suggestedType: String?,
    val suggestedCategory: String?,
    val status: String,
    val uncertainFields: List<String>,
    val note: String?,
)

@Serializable
internal data class DailyBalanceDto(
    val date: String,
    val endOfDayBalance: String,
    val currency: String,
    val screenshot: Int,
)
