package dev.snapledger.core.model

import kotlin.math.absoluteValue

/** An amount in integer minor units (e.g. pence) plus its ISO 4217 currency. Never a floating-point value. */
data class Money(
    val minor: Long,
    val currency: String,
) {
    /** Human-readable form such as `£1,234.50` or `-£3.20`. */
    fun format(): String = formatMinor(minor, currency)

    companion object {
        private val DECIMAL = Regex("^(-?)(0|[1-9][0-9]{0,14})(?:\\.([0-9]{1,3}))?$")

        fun fractionDigits(currency: String): Int =
            when (currency) {
                "JPY", "KRW", "VND", "ISK" -> 0
                "BHD", "KWD", "OMR", "JOD", "TND" -> 3
                else -> 2
            }

        /**
         * Parses a decimal string ("12.4", "-3.20") into minor units for [currency].
         * Returns null when the text is not a plain decimal or has more decimals than the currency allows.
         */
        fun parseMinor(
            text: String,
            currency: String,
        ): Long? {
            val match = DECIMAL.matchEntire(text) ?: return null
            val (sign, whole, fraction) = match.destructured
            val digits = fractionDigits(currency)
            if (fraction.length > digits) return null
            var minor = whole.toLong()
            repeat(digits) { minor *= 10 }
            if (fraction.isNotEmpty()) {
                var fractionMinor = fraction.toLong()
                repeat(digits - fraction.length) { fractionMinor *= 10 }
                minor += fractionMinor
            }
            return if (sign == "-") -minor else minor
        }

        fun formatMinor(
            minor: Long,
            currency: String,
        ): String {
            val digits = fractionDigits(currency)
            var divisor = 1L
            repeat(digits) { divisor *= 10 }
            val abs = minor.absoluteValue
            val whole =
                (abs / divisor)
                    .toString()
                    .reversed()
                    .chunked(3)
                    .joinToString(",")
                    .reversed()
            val fraction = if (digits == 0) "" else "." + (abs % divisor).toString().padStart(digits, '0')
            val sign = if (minor < 0) "-" else ""
            val symbol = symbol(currency)
            return if (symbol != null) "$sign$symbol$whole$fraction" else "$sign$whole$fraction $currency"
        }

        private fun symbol(currency: String): String? =
            when (currency) {
                "GBP" -> "£"
                "EUR" -> "€"
                "USD" -> "$"
                "CNY", "JPY" -> "¥"
                else -> null
            }
    }
}
