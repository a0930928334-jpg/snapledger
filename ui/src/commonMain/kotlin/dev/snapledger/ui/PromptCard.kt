package dev.snapledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.snapledger.core.prompt.AiPrompt
import dev.snapledger.ui.theme.SnapShapes
import dev.snapledger.ui.theme.SnapTheme

/** Copies the screenshot-transcription prompt (with the current category table) to the clipboard. */
@Composable
fun rememberCopyPrompt(ledger: LedgerState): () -> Unit {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    return { clipboard.setText(AnnotatedString(AiPrompt.build(ledger.catalog))) }
}

/** Settings card: how to use the prompt, a copy button and the full text on demand. */
@Composable
fun PromptCard(ledger: LedgerState) {
    val copy = rememberCopyPrompt(ledger)
    var copied by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    SnapCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Text("AI 提示词", style = SnapTheme.type.title)
        Text(
            "复制后粘贴到 ChatGPT 或 Claude 的「项目指令」里，只需设置一次。以后每次开一个新对话，按顺序发截图，" +
                "再发一句 extract，然后把它返回的 JSON 复制回 SnapLedger 导入。提示词里的分类表来自 App 当前的分类，" +
                "改了分类后重新复制一次即可。",
            style = SnapTheme.type.body,
            color = SnapTheme.colors.body,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CtaButton("复制 AI 提示词", onClick = {
                copy()
                copied = true
            })
            SecondaryButton(if (showText) "收起" else "查看全文", onClick = { showText = !showText })
        }
        if (copied) Text("已复制到剪贴板", color = SnapTheme.colors.positive, style = SnapTheme.type.caption)
        if (showText) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(SnapShapes.md)
                    .background(SnapTheme.colors.neutralSoft)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                SelectionContainer {
                    Text(AiPrompt.build(ledger.catalog), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SnapTheme.colors.body)
                }
            }
        }
    }
}
