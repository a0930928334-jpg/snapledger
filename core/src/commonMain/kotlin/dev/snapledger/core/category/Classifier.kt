package dev.snapledger.core.category

import dev.snapledger.core.ledger.StoredTransaction
import dev.snapledger.core.ledger.UserRule
import dev.snapledger.core.model.ClassificationSource
import dev.snapledger.core.model.CounterpartyType
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.TxnType
import dev.snapledger.core.model.normalizeText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

data class Classification(
    val categoryCode: String?,
    val source: ClassificationSource,
)

/** Facts about a transaction that classification looks at. */
data class ClassificationInput(
    val date: LocalDate?,
    val direction: Direction?,
    val type: TxnType,
    val merchant: String?,
    val rawDescription: String,
    val counterpartyType: CounterpartyType,
    val aiCategory: String?,
)

/** Priority chain from docs/05 section 3.1: user rule > built-in keyword > person default > AI > none. */
class Classifier(
    private val catalog: CategoryCatalog = CategoryCatalog.DEFAULT,
    private val keywordRules: List<KeywordRule> = BuiltinRules.DEFAULT,
    userRules: List<UserRule> = emptyList(),
    private val history: List<StoredTransaction> = emptyList(),
) {
    private val userRulesByPattern = userRules.associate { it.pattern to it.categoryCode }

    @Suppress("CyclomaticComplexMethod") // The priority chain reads top to bottom on purpose.
    fun classify(input: ClassificationInput): Classification {
        if (input.type == TxnType.TRANSFER) return Classification(null, ClassificationSource.NONE)
        val wanted = if (input.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

        fun usable(code: String?) = code != null && catalog.isLeaf(code) && catalog[code]?.kind == wanted

        userRulesByPattern[ruleKey(input.merchant, input.rawDescription)]
            ?.takeIf(::usable)
            ?.let { return Classification(it, ClassificationSource.USER_RULE) }

        if (input.type == TxnType.REFUND) refundCategory(input)?.let { return Classification(it, ClassificationSource.BUILTIN_RULE) }

        if (wanted == CategoryKind.EXPENSE) {
            matchKeyword(input.rawDescription)?.takeIf(::usable)?.let { return Classification(it, ClassificationSource.BUILTIN_RULE) }
        }

        if (input.counterpartyType == CounterpartyType.PERSON) {
            val personal = if (input.direction == Direction.IN) "income.personal" else "personal_transfer.general"
            if (usable(personal)) return Classification(personal, ClassificationSource.BUILTIN_RULE)
        }

        if (usable(input.aiCategory)) return Classification(input.aiCategory, ClassificationSource.AI_SUGGESTION)
        return Classification(null, ClassificationSource.NONE)
    }

    /**
     * Longest keyword found as whole words. Keywords longer than four letters may also carry a trailing
     * "S" ("SAINSBURY" matches "SAINSBURYS"), while short ones such as "EE" or "VUE" must match exactly.
     */
    fun matchKeyword(rawDescription: String): String? {
        val text = " ${normalizeText(rawDescription)} "
        return keywordRules
            .firstOrNull { rule ->
                " ${rule.keyword} " in text || (rule.keyword.length > 4 && " ${rule.keyword}S " in text)
            }?.categoryCode
    }

    /** Refunds offset the category of the most recent expense at the same merchant within 90 days. */
    private fun refundCategory(input: ClassificationInput): String? {
        val key = ruleKey(input.merchant, input.rawDescription)
        return history
            .filter { it.type == TxnType.EXPENSE && it.categoryCode != null && ruleKey(it.merchant, it.rawDescription) == key }
            .filter { input.date == null || it.date.daysUntil(input.date) in 0..REFUND_LOOKBACK_DAYS }
            .maxByOrNull { it.date }
            ?.categoryCode
    }

    companion object {
        private const val REFUND_LOOKBACK_DAYS = 90

        /** The key a user rule is stored under: the normalised merchant, or the description when there is none. */
        fun ruleKey(
            merchant: String?,
            rawDescription: String,
        ): String = normalizeText(merchant ?: rawDescription)

        /** Accounting type from the AI's suggestion, falling back to the money's direction. */
        fun resolveType(
            suggested: TxnType?,
            direction: Direction?,
        ): TxnType =
            when {
                suggested == TxnType.TRANSFER -> TxnType.TRANSFER
                direction == Direction.OUT && suggested == TxnType.EXPENSE -> TxnType.EXPENSE
                direction == Direction.IN && (suggested == TxnType.INCOME || suggested == TxnType.REFUND) -> suggested
                direction == Direction.IN -> TxnType.INCOME
                direction == Direction.OUT -> TxnType.EXPENSE
                else -> suggested ?: TxnType.EXPENSE
            }
    }
}
