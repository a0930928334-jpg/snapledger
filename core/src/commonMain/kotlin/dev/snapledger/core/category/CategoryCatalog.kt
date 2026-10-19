package dev.snapledger.core.category

enum class CategoryKind { EXPENSE, INCOME }

/** A category; level-1 codes have no dot ("dining"), level-2 codes do ("dining.cafe"). */
data class Category(
    val code: String,
    val nameZh: String,
    val nameEn: String,
    val kind: CategoryKind,
) {
    val parentCode: String? get() = code.substringBefore('.', "").ifEmpty { null }
    val isLeaf: Boolean get() = '.' in code
}

/** The default two-level category table from docs/05-categories-and-rules.md section 2. */
class CategoryCatalog(
    val categories: List<Category>,
) {
    private val byCode = categories.associateBy { it.code }

    operator fun get(code: String?): Category? = code?.let { byCode[it] }

    fun isLeaf(code: String?): Boolean = get(code)?.isLeaf == true

    fun leaves(kind: CategoryKind): List<Category> = categories.filter { it.isLeaf && it.kind == kind }

    fun topLevelOf(code: String): Category? = byCode[code.substringBefore('.')]

    companion object {
        val DEFAULT: CategoryCatalog by lazy { CategoryCatalog(buildDefault()) }

        @Suppress("LongMethod")
        private fun buildDefault(): List<Category> {
            val list = mutableListOf<Category>()

            fun group(
                code: String,
                zh: String,
                en: String,
                kind: CategoryKind,
                vararg children: Triple<String, String, String>,
            ) {
                list += Category(code, zh, en, kind)
                children.forEach { (child, childZh, childEn) -> list += Category("$code.$child", childZh, childEn, kind) }
            }
            val e = CategoryKind.EXPENSE
            group(
                "groceries",
                "买菜超市",
                "Groceries",
                e,
                Triple("supermarket", "超市", "Supermarket"),
                Triple("asian_grocery", "亚洲超市", "Asian Grocery"),
                Triple("convenience", "便利店", "Convenience"),
            )
            group(
                "dining",
                "餐饮",
                "Eating Out",
                e,
                Triple("restaurant", "堂食", "Restaurant"),
                Triple("takeaway", "外卖", "Takeaway"),
                Triple("fast_food", "快餐", "Fast Food"),
                Triple("cafe", "咖啡奶茶", "Café & Drinks"),
            )
            group(
                "transport",
                "交通",
                "Transport",
                e,
                Triple("public", "公交地铁", "Public Transport"),
                Triple("rail", "火车", "Rail"),
                Triple("taxi", "打车", "Taxi"),
                Triple("other", "其他交通", "Other"),
            )
            group(
                "housing",
                "住房",
                "Housing",
                e,
                Triple("rent", "房租", "Rent"),
                Triple("utilities", "水电燃气", "Utilities"),
                Triple("internet_phone", "网络话费", "Internet & Phone"),
                Triple("household", "家居日用", "Household"),
            )
            group(
                "shopping",
                "购物",
                "Shopping",
                e,
                Triple("clothing", "服饰鞋包", "Clothing"),
                Triple("electronics", "数码电子", "Electronics"),
                Triple("personal_care", "个护美妆", "Personal Care"),
                Triple("online", "网购其他", "Online & Other"),
            )
            group(
                "education",
                "学习",
                "Education",
                e,
                Triple("tuition", "学费", "Tuition"),
                Triple("books", "书籍资料", "Books & Materials"),
                Triple("courses", "课程考试", "Courses & Exams"),
            )
            group(
                "subscriptions",
                "订阅",
                "Subscriptions",
                e,
                Triple("streaming", "影音", "Streaming"),
                Triple("ai_tools", "AI 工具", "AI Tools"),
                Triple("software", "软件云服务", "Software & Cloud"),
            )
            group(
                "entertainment",
                "娱乐",
                "Entertainment",
                e,
                Triple("events", "演出电影", "Events & Cinema"),
                Triple("games", "游戏", "Games"),
                Triple("hobbies", "兴趣爱好", "Hobbies"),
            )
            group(
                "health",
                "健康",
                "Health",
                e,
                Triple("pharmacy", "药店", "Pharmacy"),
                Triple("medical", "医疗", "Medical"),
                Triple("fitness", "运动健身", "Fitness"),
            )
            group(
                "travel",
                "旅行",
                "Travel",
                e,
                Triple("flights", "机票", "Flights"),
                Triple("accommodation", "住宿", "Accommodation"),
                Triple("activities", "景点活动", "Activities"),
            )
            group("personal_transfer", "个人转账", "Personal Transfers", e, Triple("general", "个人转账", "Personal Transfers"))
            group("social", "人情", "Gifts & Social", e, Triple("gifts", "礼物", "Gifts"), Triple("treats", "请客", "Treats"))
            group(
                "fees",
                "费用",
                "Fees & Admin",
                e,
                Triple("bank", "银行费用", "Bank Fees"),
                Triple("visa", "签证证件", "Visa & Documents"),
                Triple("other", "其他费用", "Other Fees"),
            )
            group("other", "其他", "Other", e, Triple("general", "其他支出", "Other"))
            group(
                "income",
                "收入",
                "Income",
                CategoryKind.INCOME,
                Triple("salary", "工资兼职", "Salary"),
                Triple("scholarship", "奖学金", "Scholarship"),
                Triple("family", "家庭汇款", "Family"),
                Triple("personal", "个人转入", "From Individuals"),
                Triple("interest", "利息", "Interest"),
                Triple("other", "其他收入", "Other"),
            )
            return list
        }
    }
}
