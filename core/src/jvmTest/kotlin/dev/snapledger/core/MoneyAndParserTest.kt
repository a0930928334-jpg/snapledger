package dev.snapledger.core

import dev.snapledger.core.importer.ImportParser
import dev.snapledger.core.importer.ParseResult
import dev.snapledger.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyAndParserTest {
    @Test
    fun parsesDecimalStringsIntoMinorUnits() {
        assertEquals(1480L, Money.parseMinor("14.8", "GBP"))
        assertEquals(1480L, Money.parseMinor("14.80", "GBP"))
        assertEquals(-1240L, Money.parseMinor("-12.40", "GBP"))
        assertEquals(1500L, Money.parseMinor("1500", "JPY"))
        assertNull(Money.parseMinor("12.405", "GBP"))
        assertNull(Money.parseMinor("£12.40", "GBP"))
        assertNull(Money.parseMinor("1,234.50", "GBP"))
    }

    @Test
    fun formatsWithSymbolAndThousands() {
        assertEquals("£1,234.50", Money.formatMinor(123450, "GBP"))
        assertEquals("-£3.20", Money.formatMinor(-320, "GBP"))
        assertEquals("£0.05", Money.formatMinor(5, "GBP"))
    }

    @Test
    fun extractsJsonFromChatTextWithCodeBlock() {
        val chat = "Here you go:\n```json\n${sample("example-1-basic.json")}\n```\nLet me know if anything is off."
        val result = ImportParser.parse(chat)
        assertIs<ParseResult.Success>(result)
        assertEquals(4, result.import.rows.size)
        assertEquals(2, result.import.balances.size)
    }

    @Test
    fun reportsFieldLevelErrorForCurrencySymbolInAmount() {
        val bad = sample("example-1-basic.json").replaceFirst("\"12.35\"", "\"£12.35\"")
        val result = ImportParser.parse(bad)
        assertIs<ParseResult.Failure>(result)
        val issue = result.issues.single()
        assertEquals("transactions[0].amount", issue.path)
        assertTrue("第 1 条记录的 amount" in issue.message)
    }

    @Test
    fun rejectsUnknownFields() {
        val bad = sample("example-1-basic.json").replaceFirst("\"note\": null", "\"note\": null, \"sortCode\": \"12-34-56\"")
        assertIs<ParseResult.Failure>(ImportParser.parse(bad))
    }

    @Test
    fun skipsPendingRows() {
        val withPending = sample("example-1-basic.json").replaceFirst("\"status\": \"posted\"", "\"status\": \"pending\"")
        val result = ImportParser.parse(withPending)
        assertIs<ParseResult.Success>(result)
        assertEquals(3, result.import.rows.size)
        assertEquals(1, result.import.skippedPending)
    }

    @Test
    fun failsWhenThereIsNoJson() {
        assertIs<ParseResult.Failure>(ImportParser.parse("sorry, I could not read the screenshot"))
    }
}
