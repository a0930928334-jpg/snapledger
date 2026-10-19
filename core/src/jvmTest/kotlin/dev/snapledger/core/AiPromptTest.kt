package dev.snapledger.core

import dev.snapledger.core.category.Category
import dev.snapledger.core.category.CategoryCatalog
import dev.snapledger.core.category.CategoryKind
import dev.snapledger.core.prompt.AiPrompt
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiPromptTest {
    /** The prompt the app copies must be exactly the one documented (and tested by hand) in docs/04. */
    @Test
    fun matchesTheDocumentedPrompt() {
        val doc = File(System.getProperty("snapledger.docsDir"), "04-ai-prompt.md").readText().replace("\r\n", "\n")
        val documented = doc.substringAfter("````text\n").substringBefore("````")
        assertEquals(documented, AiPrompt.build())
    }

    @Test
    fun listsEveryLeafCategoryOnce() {
        val prompt = AiPrompt.build()
        CategoryCatalog.DEFAULT.categories.filter { it.isLeaf }.forEach { category ->
            assertEquals(1, Regex("^${Regex.escape(category.code)} — ", RegexOption.MULTILINE).findAll(prompt).count(), category.code)
        }
    }

    @Test
    fun customCategoriesFallBackToTheirEnglishName() {
        val custom =
            CategoryCatalog(
                CategoryCatalog.DEFAULT.categories +
                    listOf(Category("dining.hotpot", "火锅", "Hot pot", CategoryKind.EXPENSE)),
            )
        assertTrue("dining.hotpot — Hot pot" in AiPrompt.build(custom))
    }
}
