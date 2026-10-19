package dev.snapledger.core.model

import kotlinx.datetime.LocalDate

private val NON_WORD = Regex("[^\\p{L}\\p{N} ]")
private val SPACES = Regex(" +")

/**
 * Upper-cases, turns every character other than letters, digits and spaces into a space,
 * collapses runs of spaces and trims. "MARKS&SPENCER PLC | LEEDS" -> "MARKS SPENCER PLC LEEDS".
 */
fun normalizeText(text: String): String =
    text
        .uppercase()
        .replace(NON_WORD, " ")
        .replace(SPACES, " ")
        .trim()

/** Identity of a transaction for deduplication (docs/02 section 4.1). */
fun fingerprintOf(
    accountId: String,
    date: LocalDate,
    direction: Direction,
    amountMinor: Long,
    currency: String,
    rawDescription: String,
): String =
    listOf(accountId, date.toString(), direction.name, amountMinor.toString(), currency, normalizeText(rawDescription)).joinToString("|")
