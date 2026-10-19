package dev.snapledger.core.importer

import dev.snapledger.core.ImportFormat
import dev.snapledger.core.model.CounterpartyType
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.Money
import dev.snapledger.core.model.TxnType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Lenient in, strict out (docs/03 section 6): finds the JSON in whatever the user pasted,
 * then validates it field by field against import format v1.
 */
object ImportParser {
    private val json = Json { ignoreUnknownKeys = false }

    private val CODE_BLOCK = Regex("```(?:json|JSON)?\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    private val DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val CURRENCY = Regex("^[A-Z]{3}$")
    private val CATEGORY = Regex("^[a-z_]+\\.[a-z_]+$")
    private val UNCERTAIN_FIELDS =
        setOf(
            "date",
            "direction",
            "amount",
            "currency",
            "rawDescription",
            "merchant",
            "counterpartyType",
            "suggestedType",
            "suggestedCategory",
        )
    private val EXTRACTORS = setOf("chatgpt", "claude", "app", "other")

    /** Returns the JSON object inside [text]: a ```json block if present, otherwise the outermost braces. */
    fun extractJson(text: String): String? {
        CODE_BLOCK.find(text)?.let { block ->
            val body = block.groupValues[1].trim()
            if (body.startsWith("{")) return body
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    fun parse(text: String): ParseResult {
        val jsonText =
            extractJson(text)
                ?: return ParseResult.Failure(listOf(ImportIssue("$", "没有找到 JSON：文本里没有 { … } 结构")))
        val dto =
            try {
                json.decodeFromString(ImportDocumentDto.serializer(), jsonText)
            } catch (e: SerializationException) {
                return ParseResult.Failure(listOf(ImportIssue("$", "JSON 结构不符合导入格式 v1：${e.message}")))
            } catch (e: IllegalArgumentException) {
                return ParseResult.Failure(listOf(ImportIssue("$", "JSON 解析失败：${e.message}")))
            }
        return validate(dto)
    }

    private fun validate(dto: ImportDocumentDto): ParseResult {
        val issues = mutableListOf<ImportIssue>()
        if (!ImportFormat.isSupported(dto.schemaVersion)) {
            issues += ImportIssue("schemaVersion", "不支持的格式版本 ${dto.schemaVersion}（支持 ${ImportFormat.CURRENT_VERSION}）")
        }
        if (dto.source.extractedBy !in EXTRACTORS) {
            issues += ImportIssue("source.extractedBy", "取值必须是 ${EXTRACTORS.joinToString(" / ")}")
        }
        if (dto.source.screenshotCount < 1) issues += ImportIssue("source.screenshotCount", "必须 ≥ 1")

        val rows = mutableListOf<ImportedRow>()
        var skippedPending = 0
        dto.transactions.forEachIndexed { index, tx ->
            val row = validateTransaction(index, tx, issues)
            when {
                row == null -> Unit
                tx.status == "pending" -> skippedPending++
                else -> rows += row
            }
        }
        val balances =
            dto.dailyBalances.mapIndexedNotNull { index, balance -> validateBalance(index, balance, issues) }

        if (issues.isNotEmpty()) return ParseResult.Failure(issues)
        return ParseResult.Success(
            ParsedImport(
                extractedBy = dto.source.extractedBy,
                screenshotCount = dto.source.screenshotCount,
                rows = rows,
                balances = balances,
                warnings = dto.warnings,
                skippedPending = skippedPending,
            ),
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun validateTransaction(
        index: Int,
        tx: TransactionDto,
        issues: MutableList<ImportIssue>,
    ): ImportedRow? {
        val path = "transactions[$index]"
        val before = issues.size

        fun issue(
            field: String,
            message: String,
        ) {
            issues += ImportIssue("$path.$field", "第 ${index + 1} 条记录的 $field：$message")
        }

        if (tx.screenshot < 1) issue("screenshot", "必须 ≥ 1")
        if (tx.position < 1) issue("position", "必须 ≥ 1")
        val date =
            tx.date?.let {
                parseDate(it) ?: run {
                    issue("date", "\"$it\" 不是 YYYY-MM-DD 格式的日期")
                    null
                }
            }
        val direction =
            when (tx.direction) {
                null -> {
                    null
                }

                "out" -> {
                    Direction.OUT
                }

                "in" -> {
                    Direction.IN
                }

                else -> {
                    issue("direction", "\"${tx.direction}\" 只能是 out、in 或 null")
                    null
                }
            }
        if (tx.currency != null && !CURRENCY.matches(tx.currency)) issue("currency", "\"${tx.currency}\" 不是三位大写的币种代码")
        val amountMinor =
            tx.amount?.let { amount ->
                val parsed = if (amount.startsWith("-")) null else Money.parseMinor(amount, tx.currency ?: "GBP")
                parsed ?: run {
                    issue("amount", "\"$amount\" 不是合法金额（只能有数字和小数点，不能带货币符号、正负号或千分位）")
                    null
                }
            }
        if (tx.rawDescription.isBlank()) issue("rawDescription", "不能为空")
        val counterparty =
            when (tx.counterpartyType) {
                "merchant" -> {
                    CounterpartyType.MERCHANT
                }

                "person" -> {
                    CounterpartyType.PERSON
                }

                "unknown" -> {
                    CounterpartyType.UNKNOWN
                }

                else -> {
                    issue("counterpartyType", "\"${tx.counterpartyType}\" 只能是 merchant、person 或 unknown")
                    CounterpartyType.UNKNOWN
                }
            }
        val suggestedType =
            when (tx.suggestedType) {
                null -> {
                    null
                }

                "expense" -> {
                    TxnType.EXPENSE
                }

                "income" -> {
                    TxnType.INCOME
                }

                "refund" -> {
                    TxnType.REFUND
                }

                "transfer" -> {
                    TxnType.TRANSFER
                }

                else -> {
                    issue("suggestedType", "\"${tx.suggestedType}\" 不是合法的类型")
                    null
                }
            }
        if (tx.suggestedCategory != null && !CATEGORY.matches(tx.suggestedCategory)) {
            issue("suggestedCategory", "\"${tx.suggestedCategory}\" 不是合法的分类代码")
        }
        if (tx.status != "posted" && tx.status != "pending") issue("status", "\"${tx.status}\" 只能是 posted 或 pending")
        tx.uncertainFields.filterNot { it in UNCERTAIN_FIELDS }.forEach { issue("uncertainFields", "未知字段名 \"$it\"") }

        if (issues.size > before) return null
        return ImportedRow(
            id = index,
            screenshot = tx.screenshot,
            position = tx.position,
            date = date,
            direction = direction,
            amountMinor = amountMinor,
            currency = tx.currency,
            rawDescription = tx.rawDescription.trim(),
            merchant = tx.merchant?.trim()?.takeIf { it.isNotEmpty() },
            counterpartyType = counterparty,
            suggestedType = suggestedType,
            suggestedCategory = tx.suggestedCategory,
            uncertainFields = tx.uncertainFields.toSet(),
            note = tx.note,
        )
    }

    private fun validateBalance(
        index: Int,
        balance: DailyBalanceDto,
        issues: MutableList<ImportIssue>,
    ): ImportedBalance? {
        val path = "dailyBalances[$index]"
        val before = issues.size
        val date = parseDate(balance.date)
        if (date == null) issues += ImportIssue("$path.date", "第 ${index + 1} 个日终余额的日期 \"${balance.date}\" 不合法")
        val validCurrency = CURRENCY.matches(balance.currency)
        if (!validCurrency) issues += ImportIssue("$path.currency", "第 ${index + 1} 个日终余额的币种 \"${balance.currency}\" 不合法")
        val minor = Money.parseMinor(balance.endOfDayBalance, if (validCurrency) balance.currency else "GBP")
        if (minor == null) {
            issues += ImportIssue("$path.endOfDayBalance", "第 ${index + 1} 个日终余额 \"${balance.endOfDayBalance}\" 不是合法金额")
        }
        if (issues.size > before || date == null || minor == null) return null
        return ImportedBalance(date, minor, balance.currency, balance.screenshot)
    }

    private fun parseDate(text: String): LocalDate? =
        if (!DATE.matches(text)) {
            null
        } else {
            runCatching { LocalDate.parse(text) }.getOrNull()
        }
}
