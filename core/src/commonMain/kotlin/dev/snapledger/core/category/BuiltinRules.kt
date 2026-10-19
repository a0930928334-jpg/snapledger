package dev.snapledger.core.category

/** A keyword that, when found in a normalised raw description, implies a category. */
data class KeywordRule(
    val keyword: String,
    val categoryCode: String,
)

/**
 * Built-in keyword rules (docs/05 section 3.3). Keywords are already normalised.
 * Matching (see [Classifier]) is word-based, and the longest matching keyword wins,
 * so "UBER EATS" beats "UBER".
 */
object BuiltinRules {
    private val table: Map<String, List<String>> =
        mapOf(
            "groceries.supermarket" to
                listOf(
                    "TESCO",
                    "SAINSBURY",
                    "ASDA",
                    "ALDI",
                    "LIDL",
                    "MORRISONS",
                    "CO OP",
                    "WAITROSE",
                    "ICELAND",
                    "MARKS SPENCER",
                    "M S SIMPLY FOOD",
                ),
            "groceries.asian_grocery" to listOf("WING YIP", "SEE WOO", "HANG SANG"),
            "dining.takeaway" to listOf("DELIVEROO", "UBER EATS", "JUST EAT"),
            "dining.fast_food" to listOf("MCDONALDS", "KFC", "GREGGS", "BURGER KING", "SUBWAY", "NANDOS", "FIVE GUYS"),
            "dining.cafe" to listOf("STARBUCKS", "COSTA", "PRET A MANGER", "CAFFE NERO", "GAIL S"),
            "transport.public" to listOf("TFL", "FIRST BUS", "FIRST WEST YORKSHIRE", "ARRIVA", "STAGECOACH"),
            "transport.rail" to listOf("TRAINLINE", "NORTHERN", "LNER", "CROSSCOUNTRY", "AVANTI"),
            "transport.taxi" to listOf("UBER", "BOLT", "FREENOW"),
            "housing.utilities" to listOf("OCTOPUS ENERGY", "BRITISH GAS", "EDF", "YORKSHIRE WATER"),
            "housing.internet_phone" to listOf("VODAFONE", "GIFFGAFF", "VOXI", "LEBARA", "EE LIMITED", "THREE", "VIRGIN MEDIA"),
            "housing.household" to listOf("IKEA", "WILKO", "B M", "HOME BARGAINS", "DUNELM"),
            "shopping.clothing" to listOf("PRIMARK", "UNIQLO", "ZARA", "H M", "JD SPORTS"),
            "shopping.electronics" to listOf("CURRYS", "ARGOS"),
            "shopping.online" to listOf("AMAZON", "AMZN", "EBAY"),
            "subscriptions.streaming" to listOf("NETFLIX", "SPOTIFY", "DISNEY PLUS", "YOUTUBE PREMIUM"),
            "subscriptions.ai_tools" to listOf("OPENAI", "CHATGPT", "ANTHROPIC", "CLAUDE AI"),
            "subscriptions.software" to listOf("APPLE COM BILL", "GOOGLE ONE", "MICROSOFT", "ICLOUD"),
            "entertainment.events" to listOf("ODEON", "CINEWORLD", "VUE", "TICKETMASTER"),
            "entertainment.games" to listOf("STEAM", "PLAYSTATION", "NINTENDO"),
            "health.pharmacy" to listOf("BOOTS", "SUPERDRUG"),
            "health.fitness" to listOf("PUREGYM", "THE GYM GROUP"),
            "travel.flights" to listOf("RYANAIR", "EASYJET", "JET2", "BRITISH AIRWAYS"),
            "travel.accommodation" to listOf("AIRBNB", "BOOKING COM", "PREMIER INN", "TRAVELODGE"),
            "education.tuition" to listOf("UNIVERSITY OF LEEDS"),
            "fees.visa" to listOf("UKVI", "HOME OFFICE"),
            "fees.bank" to listOf("ACCOUNT FEE", "OVERDRAFT FEE", "NON STERLING TRANSACTION FEE"),
        )

    val DEFAULT: List<KeywordRule> =
        table
            .flatMap { (code, keywords) -> keywords.map { KeywordRule(it, code) } }
            .sortedByDescending { it.keyword.length }
}
