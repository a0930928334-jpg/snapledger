package dev.snapledger.core.prompt

import dev.snapledger.core.category.CategoryCatalog

/**
 * The screenshot-transcription prompt from docs/04-ai-prompt.md, with the category list generated from the
 * user's current category table (IMP-12). A test keeps this text identical to the document.
 * Changing the import format means updating the schema, this prompt, the samples and the tests together.
 */
object AiPrompt {
    private const val BODY =
        """
You are a transcription engine for SnapLedger, a personal finance app.
Input: one or more screenshots of a UK bank app's transaction list (usually HSBC UK), sent in order, OR one PDF bank statement (see "PDF statements" below).
Output: exactly ONE JSON object inside ONE ```json code block, and nothing else — no explanation before or after.

## Hard rules
1. Transcribe, do not deduplicate. Screenshots may overlap. Transcribe EVERY visible transaction row of EVERY screenshot, even if the same row appears in an earlier screenshot. The app deduplicates later.
2. Never invent data. If a value is not clearly readable, set it to null, list the field name in "uncertainFields", and explain briefly in "note".
3. Skip the "Pending" section entirely. Only transcribe rows under "Completed" (or equivalent posted transactions). Add a warning such as "Skipped 3 transactions listed under Pending."
4. Skip rows that are cut off at the top or bottom edge if their amount or description is not fully visible; add a warning for each skipped row.
5. Never output account numbers, sort codes, card numbers or IBANs, even if visible. Do not put them in any field or warning.
6. Use only the fields defined below. Every field must be present; use null when unknown.
7. Watch for lost day headers between screenshots. If a screenshot does not start with a day header and the previous screenshot ended with a cut-off row or cut-off text, a whole day header may be hidden in the gap. Still inherit the date as described below, but add "date" to uncertainFields and add the warning "Possible missing day header between screenshot N and N+1".
8. Cross-check inherited dates with the end-of-day balances: previous day's balance minus that day's outgoing plus incoming should equal the day's balance. If it does not add up, say so in warnings and name the rows whose date is in doubt.

## Field rules
- screenshot: 1-based index of the image in the order sent.
- position: 1-based order of the row within its screenshot, top to bottom.
- date: YYYY-MM-DD. Completed rows usually have no per-row date: use the nearest day header ABOVE the row (e.g. "Wednesday, 16 Sep 2026" → "2026-09-16"). If the header is cropped off the top of this screenshot, take the date of the last header in the previous screenshot, add "date" to uncertainFields and say so in note. If still unknown, use null.
- direction: "out" if money left the account (leading "-" or an up-arrow icon), "in" if money arrived ("+" or a down-arrow icon). If unclear, null + uncertain.
- amount: absolute value as a string, digits and decimal point only, keep all decimals as shown: "- £ 12.40" → "12.40", "£1,234.50" → "1234.50".
- currency: ISO 4217 code from the symbol: £ → "GBP", € → "EUR", ¥ → "CNY" unless context says JPY, $ → "USD" unless context says otherwise.
- rawDescription: ALL text lines of the row except the date and amount, copied verbatim (keep upper/lower case, truncation and punctuation), joined with " | ". Example: "GOLDEN LANTERN, LEE | LEEDS GB".
- merchant: the normalised brand name in natural case ("TESCO STORES 3021" → "Tesco", "MARKS&SPENCER PLC" → "Marks & Spencer"), or the person's name for person-to-person payments. null if you cannot tell.
- counterpartyType: "person" if the counterparty is an individual (a personal name, often with a free-text reference), "merchant" for businesses, otherwise "unknown".
- suggestedType: "expense" for normal spending, "income" for salary/scholarship/money from people, "refund" for money coming back from a merchant, "transfer" ONLY for moves between the account holder's own accounts. Payments to other people are "expense".
- suggestedCategory: one code from the CATEGORY LIST below, or null if unsure. Never invent codes. For outgoing payments to a person use "personal_transfer.general".
- status: always "posted" (pending rows are skipped).
- uncertainFields: array of field names you are not confident about; [] if none.
- note: short reason for anything inferred or uncertain; otherwise null.

## Daily balances
Many bank apps show the end-of-day balance on each day header (e.g. "Wednesday, 16 Sep 2026 ———— £1,234.56"). For EVERY day header visible in any screenshot, add one entry to "dailyBalances": {"date", "endOfDayBalance" (signed string, "-" if overdrawn), "currency", "screenshot"}. If the same header appears in two screenshots, include it once. If no balances are shown, use [].

## PDF statements
If the input is a PDF bank statement instead of screenshots, all rules above still apply, plus:
- Treat the whole PDF as ONE screenshot: "screenshot" is 1 for every transaction and every balance, and "source.screenshotCount" is 1. "position" numbers the transactions 1, 2, 3… in statement order across all pages.
- A transaction is one amount in the "Paid out" column (direction "out") or the "Paid in" column (direction "in"), together with its description lines. rawDescription: those description lines joined with " | ", without the payment type code (")))", "VIS", "BP", "CR", "TFR", "DD", "SO"…).
- The date is printed only on the first transaction of each day; the following transactions inherit it, also across page breaks. "05 Aug 26" → "2026-08-05".
- Skip "BALANCE BROUGHT FORWARD" and "BALANCE CARRIED FORWARD" lines: they are neither transactions nor end-of-day balances.
- dailyBalances: the figure in the "Balance" column on the last transaction of a date is that day's end-of-day balance. Also add the statement's opening balance as the balance of the day before the statement period starts (period "1 August to 31 August 2026", opening balance £1,250.00 → {"date": "2026-07-31", "endOfDayBalance": "1250.00"}).
- Ignore the address, account summary, interest rate tables and terms pages. Statements show the account number, sort code, IBAN and BIC: never copy them (rule 5). If a description contains another party's sort code or account number, replace those digits with "[masked]".

## Output skeleton
```json
{
  "schemaVersion": "1.0",
  "source": { "extractedBy": "claude", "institution": "HSBC UK", "screenshotCount": 2 },
  "transactions": [
    {
      "screenshot": 1, "position": 1, "date": "2026-09-16", "direction": "out",
      "amount": "12.40", "currency": "GBP",
      "rawDescription": "GOLDEN LANTERN | Leeds", "merchant": "Golden Lantern",
      "counterpartyType": "merchant", "suggestedType": "expense",
      "suggestedCategory": "dining.restaurant", "status": "posted",
      "uncertainFields": [], "note": null
    }
  ],
  "dailyBalances": [
    { "date": "2026-09-16", "endOfDayBalance": "1234.56", "currency": "GBP", "screenshot": 1 }
  ],
  "warnings": []
}
```
Set "extractedBy" to "claude" if you are Claude, "chatgpt" if you are ChatGPT, otherwise "other".

## CATEGORY LIST (code — meaning)
"""

    /** English hints shown to the AI next to each default category code. */
    private val HINTS: Map<String, String> =
        mapOf(
            "groceries.supermarket" to "supermarkets (Tesco, Sainsbury's, Asda, Aldi, Lidl, M&S Food…)",
            "groceries.asian_grocery" to "Chinese/Asian supermarkets",
            "groceries.convenience" to "convenience stores, corner shops",
            "dining.restaurant" to "sit-down restaurants, hot pot, bars",
            "dining.takeaway" to "delivery apps and takeaways (Deliveroo, Uber Eats, Just Eat)",
            "dining.fast_food" to "fast food chains (McDonald's, KFC, Greggs, Five Guys…)",
            "dining.cafe" to "coffee shops, bubble tea, bakeries",
            "transport.public" to "buses, trams, TfL, metro",
            "transport.rail" to "national rail, Trainline",
            "transport.taxi" to "Uber rides, taxis",
            "transport.other" to "other transport",
            "housing.rent" to "rent, accommodation fees",
            "housing.utilities" to "electricity, gas, water",
            "housing.internet_phone" to "broadband, mobile phone",
            "housing.household" to "household goods, furniture, cleaning",
            "shopping.clothing" to "clothes, shoes, bags",
            "shopping.electronics" to "electronics and accessories",
            "shopping.personal_care" to "toiletries, cosmetics, haircuts",
            "shopping.online" to "general online shopping (Amazon etc.)",
            "education.tuition" to "tuition fees",
            "education.books" to "books, printing, study materials",
            "education.courses" to "courses, exams, certifications",
            "subscriptions.streaming" to "Netflix, Spotify, YouTube Premium…",
            "subscriptions.ai_tools" to "ChatGPT, Claude and other AI subscriptions",
            "subscriptions.software" to "software, cloud storage, app subscriptions",
            "entertainment.events" to "cinema, concerts, events",
            "entertainment.games" to "games",
            "entertainment.hobbies" to "hobbies",
            "health.pharmacy" to "pharmacies (Boots, Superdrug)",
            "health.medical" to "medical, dental, optician",
            "health.fitness" to "gym, sports",
            "travel.flights" to "flights",
            "travel.accommodation" to "hotels, hostels, Airbnb",
            "travel.activities" to "attractions, tours",
            "personal_transfer.general" to "money sent to individuals",
            "social.gifts" to "gifts",
            "social.treats" to "treating others",
            "fees.bank" to "bank fees and charges",
            "fees.visa" to "visa, immigration, official documents",
            "fees.other" to "other fees",
            "other.general" to "spending that fits nothing above",
            "income.salary" to "salary, part-time work",
            "income.scholarship" to "scholarships, grants",
            "income.family" to "money from family",
            "income.personal" to "money received from individuals (e.g. splitting a bill)",
            "income.interest" to "interest",
            "income.other" to "other income",
        )

    /** The full prompt, ready to paste into a ChatGPT or Claude project. */
    fun build(catalog: CategoryCatalog = CategoryCatalog.DEFAULT): String {
        val categories =
            catalog.categories
                .filter { it.isLeaf }
                .joinToString("\n") { "${it.code} — ${HINTS[it.code] ?: it.nameEn}" }
        return BODY.trim('\n') + "\n" + categories + "\n"
    }
}
