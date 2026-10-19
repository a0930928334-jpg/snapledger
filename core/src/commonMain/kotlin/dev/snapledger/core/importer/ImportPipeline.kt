package dev.snapledger.core.importer

import dev.snapledger.core.category.CategoryCatalog
import dev.snapledger.core.category.Classification
import dev.snapledger.core.category.ClassificationInput
import dev.snapledger.core.category.Classifier
import dev.snapledger.core.ledger.ImportCommit
import dev.snapledger.core.ledger.LedgerSnapshot
import dev.snapledger.core.ledger.NewTransaction
import dev.snapledger.core.ledger.StoredBalance
import dev.snapledger.core.ledger.UserRule
import dev.snapledger.core.model.ClassificationSource
import dev.snapledger.core.model.CounterpartyType
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.Money
import dev.snapledger.core.model.TxnOrigin
import dev.snapledger.core.model.TxnType
import dev.snapledger.core.model.fingerprintOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.math.absoluteValue

enum class RowStatus { NEW, DUPLICATE, SUSPECTED_DUPLICATE, INCOMPLETE }

/** A row of the import preview; everything here is editable by the user before confirming. */
data class PreviewRow(
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
    val type: TxnType,
    val categoryCode: String?,
    val classificationSource: ClassificationSource,
    val uncertainFields: Set<String>,
    val note: String?,
    /** Null until the user ticks or unticks the row; then it overrides the computed default. */
    val includeOverride: Boolean? = null,
    val categoryEdited: Boolean = false,
    /** Looks like a repeat of another screenshot that did not line up. */
    val alignmentSuspect: Boolean = false,
) {
    val isComplete: Boolean get() = date != null && direction != null && amountMinor != null && currency != null
}

data class ImportPreview(
    val accountId: String,
    val extractedBy: String,
    val rows: List<PreviewRow>,
    val balances: List<ImportedBalance>,
    /** Messages from the app about the batch (overlap removed, gaps, skipped pending rows). */
    val notices: List<String>,
    /** Warnings written by the AI. */
    val aiWarnings: List<String>,
)

data class RowEvaluation(
    val row: PreviewRow,
    val status: RowStatus,
    val include: Boolean,
    val fingerprint: String?,
    val messages: List<String>,
)

data class PreviewEvaluation(
    val rows: List<RowEvaluation>,
    val days: List<DayReconciliation>,
) {
    val newCount: Int get() = rows.count { it.include && it.status != RowStatus.INCOMPLETE }
    val skippedCount: Int get() = rows.count { !it.include }
    val suspectCount: Int get() = rows.count { it.status == RowStatus.SUSPECTED_DUPLICATE }
    val blockingCount: Int get() = rows.count { it.include && it.status == RowStatus.INCOMPLETE }
    val canCommit: Boolean get() = blockingCount == 0 && newCount + days.size > 0
}

sealed interface PrepareResult {
    data class Ready(
        val preview: ImportPreview,
    ) : PrepareResult

    data class Failure(
        val issues: List<ImportIssue>,
    ) : PrepareResult
}

/**
 * The import pipeline from docs/02 section 3: parse -> align -> classify -> (evaluate: dedupe, flag
 * suspects, reconcile) -> commit. [evaluate] is pure and cheap, so the UI re-runs it after every edit.
 */
class ImportPipeline(
    private val catalog: CategoryCatalog = CategoryCatalog.DEFAULT,
) {
    fun prepare(
        text: String,
        snapshot: LedgerSnapshot,
        accountId: String,
    ): PrepareResult {
        val parsed =
            when (val result = ImportParser.parse(text)) {
                is ParseResult.Failure -> return PrepareResult.Failure(result.issues)
                is ParseResult.Success -> result.import
            }
        val aligned = ScreenshotAligner.align(parsed.rows)
        val classifier = classifier(snapshot)
        val rows =
            aligned.rows.map { row ->
                val type = Classifier.resolveType(row.suggestedType, row.direction)
                val classification = classifier.classify(row.toInput(type))
                PreviewRow(
                    id = row.id,
                    screenshot = row.screenshot,
                    position = row.position,
                    date = row.date,
                    direction = row.direction,
                    amountMinor = row.amountMinor,
                    currency = row.currency,
                    rawDescription = row.rawDescription,
                    merchant = row.merchant,
                    counterpartyType = row.counterpartyType,
                    type = type,
                    categoryCode = classification.categoryCode,
                    classificationSource = classification.source,
                    uncertainFields = row.uncertainFields,
                    note = row.note,
                    alignmentSuspect = row.id in aligned.suspectedRepeats,
                )
            }
        val notices =
            buildList {
                if (aligned.overlapRemoved > 0) add("截图之间重叠的 ${aligned.overlapRemoved} 条已合并，只保留一份。")
                aligned.screenshotsWithoutOverlap.forEach { shot ->
                    add("截图 ${shot - 1} 和 $shot 之间没有重叠，可能漏了交易或日期标题，请核对这两张截图交界处的日期。")
                }
                if (parsed.skippedPending > 0) add("忽略了 ${parsed.skippedPending} 条待入账（pending）交易。")
            }
        return PrepareResult.Ready(
            ImportPreview(accountId, parsed.extractedBy, rows, dedupeBalances(parsed.balances), notices, parsed.warnings),
        )
    }

    /** Re-classifies a row after the user changed its type, unless they picked the category themselves. */
    fun reclassify(
        row: PreviewRow,
        snapshot: LedgerSnapshot,
    ): PreviewRow {
        if (row.categoryEdited) return row
        val classification: Classification =
            classifier(snapshot).classify(
                ClassificationInput(row.date, row.direction, row.type, row.merchant, row.rawDescription, row.counterpartyType, null),
            )
        return row.copy(categoryCode = classification.categoryCode, classificationSource = classification.source)
    }

    @Suppress("CyclomaticComplexMethod") // One readable decision table; splitting it hides the order of the checks.
    fun evaluate(
        preview: ImportPreview,
        snapshot: LedgerSnapshot,
    ): PreviewEvaluation {
        val accountId = preview.accountId
        val existingImported =
            snapshot.transactions
                .filter { it.accountId == accountId && it.origin == TxnOrigin.IMPORT }
                .groupingBy { it.fingerprint }
                .eachCount()
        val used = mutableMapOf<String, Int>()

        val firstPass =
            preview.rows.map { row ->
                val fingerprint = row.fingerprint(accountId)
                val messages = mutableListOf<String>()
                val status =
                    when {
                        fingerprint == null -> {
                            messages += "缺少${row.missingFields().joinToString("、")}，填上后才能导入"
                            RowStatus.INCOMPLETE
                        }

                        (used[fingerprint] ?: 0) < (existingImported[fingerprint] ?: 0) -> {
                            used[fingerprint] = (used[fingerprint] ?: 0) + 1
                            messages += "已经导入过，自动跳过"
                            RowStatus.DUPLICATE
                        }

                        else -> {
                            val similar = similarExisting(row, fingerprint, snapshot, accountId)
                            if (similar != null) messages += "和已有记录「$similar」日期、金额、方向都相同，可能是重复"
                            if (row.alignmentSuspect) messages += "可能和另一张截图里的同一笔重复"
                            if (similar != null || row.alignmentSuspect) RowStatus.SUSPECTED_DUPLICATE else RowStatus.NEW
                        }
                    }
                RowEvaluation(row, status, row.includeOverride ?: (status != RowStatus.DUPLICATE), fingerprint, messages)
            }

        var days = reconcile(preview, firstPass, snapshot)
        // A suspected duplicate defaults to "skip" when leaving it out is exactly what balances its day.
        val adjusted =
            firstPass.map { eval ->
                if (eval.status != RowStatus.SUSPECTED_DUPLICATE || eval.row.includeOverride != null) return@map eval
                val day = days.dayCovering(eval.row.date) ?: return@map eval
                val signed = eval.row.signedAmount() ?: return@map eval
                if (day.status == ReconStatus.MISMATCH && day.differenceMinor == -signed) {
                    eval.copy(include = false, messages = eval.messages + "去掉这一笔当天正好对平，默认跳过")
                } else {
                    eval
                }
            }
        if (adjusted != firstPass) days = reconcile(preview, adjusted, snapshot)
        return PreviewEvaluation(adjusted, days)
    }

    fun commit(
        preview: ImportPreview,
        evaluation: PreviewEvaluation,
    ): ImportCommit {
        val toWrite = evaluation.rows.filter { it.include && it.fingerprint != null }
        val transactions =
            toWrite.map { eval ->
                val row = eval.row
                NewTransaction(
                    date = row.date!!,
                    direction = row.direction!!,
                    amountMinor = row.amountMinor!!,
                    currency = row.currency!!,
                    type = row.type,
                    categoryCode = if (row.type == TxnType.TRANSFER) null else row.categoryCode,
                    classificationSource = if (row.categoryEdited) ClassificationSource.MANUAL else row.classificationSource,
                    merchant = row.merchant,
                    counterpartyType = row.counterpartyType,
                    rawDescription = row.rawDescription,
                    fingerprint = eval.fingerprint!!,
                    note = null,
                )
            }
        val rules =
            evaluation.rows
                .map { it.row }
                .filter { it.categoryEdited && it.categoryCode != null }
                .map { UserRule(Classifier.ruleKey(it.merchant, it.rawDescription), it.categoryCode!!) }
                .distinctBy { it.pattern }
        val balances = preview.balances.map { StoredBalance(preview.accountId, it.date, it.balanceMinor, it.currency) }
        return ImportCommit(preview.extractedBy, transactions, balances, evaluation.skippedCount, rules)
    }

    private fun classifier(snapshot: LedgerSnapshot) = Classifier(catalog, userRules = snapshot.userRules, history = snapshot.transactions)

    private fun reconcile(
        preview: ImportPreview,
        rows: List<RowEvaluation>,
        snapshot: LedgerSnapshot,
    ): List<DayReconciliation> {
        val batchPoints = preview.balances.map { BalancePoint(it.date, it.currency, it.balanceMinor) }
        val batchKeys = batchPoints.map { it.date to it.currency }.toSet()
        val existingPoints =
            snapshot.balances
                .filter { it.accountId == preview.accountId && (it.date to it.currency) !in batchKeys }
                .map { BalancePoint(it.date, it.currency, it.balanceMinor) }
        val movements =
            snapshot.transactions
                .filter { it.accountId == preview.accountId }
                .map { Movement(it.date, it.direction, it.amountMinor, it.currency) } +
                rows
                    .filter { it.include && it.row.date != null && it.row.currency != null }
                    .map { Movement(it.row.date!!, it.row.direction, it.row.amountMinor, it.row.currency!!, it.row.id) }
        return Reconciler.reconcile(batchPoints.sortedByDescending { it.date }, batchPoints + existingPoints, movements)
    }

    private fun similarExisting(
        row: PreviewRow,
        fingerprint: String,
        snapshot: LedgerSnapshot,
        accountId: String,
    ): String? =
        snapshot.transactions
            .firstOrNull {
                it.accountId == accountId &&
                    it.fingerprint != fingerprint &&
                    it.direction == row.direction &&
                    it.amountMinor == row.amountMinor &&
                    it.currency == row.currency &&
                    it.date.daysUntil(row.date!!).absoluteValue <= 1
            }?.rawDescription

    private fun dedupeBalances(balances: List<ImportedBalance>): List<ImportedBalance> = balances.distinctBy { it.date to it.currency }

    private fun ImportedRow.toInput(type: TxnType) =
        ClassificationInput(date, direction, type, merchant, rawDescription, counterpartyType, suggestedCategory)

    private fun PreviewRow.fingerprint(accountId: String): String? =
        if (isComplete) fingerprintOf(accountId, date!!, direction!!, amountMinor!!, currency!!, rawDescription) else null

    private fun PreviewRow.missingFields(): List<String> =
        buildList {
            if (date == null) add("日期")
            if (direction == null) add("方向")
            if (amountMinor == null) add("金额")
            if (currency == null) add("币种")
        }

    private fun PreviewRow.signedAmount(): Long? {
        val amount = amountMinor ?: return null
        return when (direction) {
            Direction.IN -> amount
            Direction.OUT -> -amount
            null -> null
        }
    }

    private fun List<DayReconciliation>.dayCovering(date: LocalDate?): DayReconciliation? {
        if (date == null) return null
        return filter { it.previousDate != null && it.previousDate < date && it.date >= date }.minByOrNull { it.date }
    }

    companion object {
        /** Formats the "probably £3.20" hint shown next to an unreadable amount. */
        fun likelyAmountHint(
            likely: LikelyAmount,
            currency: String,
        ): String = "金额很可能是 ${Money.formatMinor(likely.amountMinor, currency)}"
    }
}
