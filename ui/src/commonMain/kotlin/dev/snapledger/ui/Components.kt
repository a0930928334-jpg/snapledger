package dev.snapledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.snapledger.core.category.CategoryCatalog
import dev.snapledger.core.category.CategoryKind
import dev.snapledger.core.model.ClassificationSource
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.Money
import dev.snapledger.core.model.TxnType
import dev.snapledger.ui.theme.SnapShapes
import dev.snapledger.ui.theme.SnapTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** Semantic colours (DESIGN.md "Preview row states"), resolved from the current light or dark theme. */
object StatusColors {
    val good: Color @Composable get() = SnapTheme.colors.positiveSoft
    val caution: Color @Composable get() = SnapTheme.colors.warningSoft
    val problem: Color @Composable get() = SnapTheme.colors.negativeSoft
    val neutral: Color @Composable get() = SnapTheme.colors.neutralSoft
    val newRow: Color @Composable get() = SnapTheme.colors.surface
    val ok: Color @Composable get() = SnapTheme.colors.positive
    val bad: Color @Composable get() = SnapTheme.colors.negative
    val outflow: Color @Composable get() = SnapTheme.colors.ink
    val inflow: Color @Composable get() = SnapTheme.colors.positive
}

/** The screen's main action: lavender pill with an ink label. */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = SnapShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = SnapTheme.colors.cta, contentColor = SnapTheme.colors.onCta),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

/** Secondary action: outlined pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = SnapShapes.pill,
        border = BorderStroke(1.dp, SnapTheme.colors.hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapTheme.colors.ink),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

/** White card on the canvas; the contrast is the only elevation (no shadow, no border). */
@Composable
fun SnapCard(
    modifier: Modifier = Modifier,
    shape: Shape = SnapShapes.xl,
    color: Color = SnapTheme.colors.surface,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.clip(shape).background(color).padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

fun money(
    minor: Long,
    currency: String,
): String = Money.formatMinor(minor, currency)

fun signedMoney(
    direction: Direction?,
    minor: Long?,
    currency: String?,
): String {
    if (minor == null) return "金额？"
    val text = Money.formatMinor(minor, currency ?: "GBP")
    return when (direction) {
        Direction.OUT -> "-$text"
        Direction.IN -> "+$text"
        null -> "?$text"
    }
}

fun dateLabel(date: LocalDate): String {
    val weekday =
        when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            else -> "周日"
        }
    return "${date.year}年${date.month.number}月${date.day}日 $weekday"
}

fun typeLabel(type: TxnType): String =
    when (type) {
        TxnType.EXPENSE -> "支出"
        TxnType.INCOME -> "收入"
        TxnType.REFUND -> "退款"
        TxnType.TRANSFER -> "转账（不计收支）"
    }

fun sourceBadge(source: ClassificationSource): String =
    when (source) {
        ClassificationSource.MANUAL -> "✋"
        ClassificationSource.USER_RULE -> "👤"
        ClassificationSource.BUILTIN_RULE -> "📘"
        ClassificationSource.AI_SUGGESTION -> "✨"
        ClassificationSource.NONE -> ""
    }

/** A text button showing the current category; clicking opens the list of leaf categories of [kind]. */
@Composable
fun CategoryPicker(
    label: String,
    kind: CategoryKind,
    catalog: CategoryCatalog,
    enabled: Boolean = true,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            label,
            style = SnapTheme.type.caption,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) SnapTheme.colors.accent else SnapTheme.colors.mute,
            modifier =
                Modifier
                    .clip(SnapShapes.pill)
                    .background(SnapTheme.colors.surfaceSoft)
                    .clickable(enabled = enabled) { open = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            catalog.leaves(kind).forEach { category ->
                val parent = catalog.topLevelOf(category.code)
                val name =
                    if (parent != null &&
                        parent.nameZh != category.nameZh
                    ) {
                        "${parent.nameZh} · ${category.nameZh}"
                    } else {
                        category.nameZh
                    }
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onPick(category.code)
                    },
                )
            }
        }
    }
}

/** A small dropdown for picking one of [options]. */
@Composable
fun <T> OptionPicker(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

fun kindFor(type: TxnType): CategoryKind = if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
