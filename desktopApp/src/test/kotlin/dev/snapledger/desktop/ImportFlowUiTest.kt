package dev.snapledger.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.snapledger.data.JvmDatabase
import dev.snapledger.data.SqlLedgerRepository
import dev.snapledger.ui.App
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals

/** Drives the real UI end to end: paste -> preview -> confirm -> ledger and overview. */
class ImportFlowUiTest {
    @get:Rule
    val rule = createComposeRule()

    private fun sample(name: String) = File(System.getProperty("snapledger.samplesDir"), name).readText()

    private fun screenshot(name: String) {
        val dir = File(System.getProperty("snapledger.screenshotDir")).apply { mkdirs() }
        ImageIO.write(rule.onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun paste(text: String) {
        rule.onNode(hasSetTextAction()).performTextReplacement(text)
        rule.onNodeWithText("解析粘贴的内容").performClick()
        rule.waitForIdle()
    }

    @Test
    fun screenshotsLightTheme() = screenshotEveryScreen(dark = false)

    @Test
    fun screenshotsDarkTheme() = screenshotEveryScreen(dark = true)

    /** Renders every screen with sample data, for checking the design by eye. */
    private fun screenshotEveryScreen(dark: Boolean) {
        run {
            val store = JvmDatabase.open(null)
            val theme = if (dark) "dark" else "light"
            var tab = 0
            rule.setContent {
                App(store.repository, store.backup, DesktopFiles(), SqlLedgerRepository.DEFAULT_ACCOUNT_ID, "GBP", darkTheme = dark)
            }
            rule.onNodeWithText("导入").performClick()
            paste(sample("example-3-special.json"))
            screenshot("theme-$theme-${++tab}-preview")
            rule.onNodeWithText("取消").performClick()
            paste(sample("example-1-basic.json"))
            rule.onNodeWithText("确认导入").performClick()
            paste(sample("example-2-overlap.json"))
            rule.onNodeWithText("确认导入").performClick()
            rule.onNodeWithText("流水").performClick()
            screenshot("theme-$theme-${++tab}-ledger")
            rule.onNodeWithText("概览").performClick()
            rule.onNodeWithText("‹ 上个月").performClick()
            rule.waitForIdle()
            screenshot("theme-$theme-${++tab}-overview")
            rule.onNodeWithText("设置").performClick()
            screenshot("theme-$theme-${++tab}-settings")
        }
    }

    @Test
    fun pasteConfirmReimportAndBrowse() {
        val store = JvmDatabase.open(null)
        val repository = store.repository
        rule.setContent {
            App(repository, store.backup, DesktopFiles(), SqlLedgerRepository.DEFAULT_ACCOUNT_ID, SqlLedgerRepository.DEFAULT_CURRENCY)
        }

        rule.onNodeWithText("导入").performClick()
        screenshot("1-import-empty")
        paste(sample("example-1-basic.json"))
        rule.onNodeWithText("新增 4 条", substring = true).assertExists()
        screenshot("2-preview-example1")
        rule.onNodeWithText("确认导入").performClick()
        rule.onNodeWithText("已导入 4 条", substring = true).assertExists()
        assertEquals(4, repository.snapshot(SqlLedgerRepository.DEFAULT_ACCOUNT_ID).transactions.size)

        paste(sample("example-1-basic.json"))
        rule.onNodeWithText("新增 0 条 · 跳过 4 条", substring = true).assertExists()
        rule.onNodeWithText("取消").performClick()

        paste(sample("example-3-special.json"))
        // Shown both on the reconciliation line and on the Costa row.
        assertEquals(2, rule.onAllNodesWithText("金额很可能是 £3.20", substring = true).fetchSemanticsNodes().size)
        screenshot("3-preview-example3")
        rule.onNodeWithText("取消").performClick()

        rule.onNodeWithText("流水").performClick()
        rule.onNodeWithText("Tesco").assertExists()
        screenshot("4-ledger")

        rule.onNodeWithText("概览").performClick()
        rule.onNodeWithText("‹ 上个月").performClick()
        rule.onNodeWithText("2026年9月").assertExists()
        screenshot("5-overview-september")

        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("导出备份…").assertExists()
        screenshot("6-settings")
    }
}
