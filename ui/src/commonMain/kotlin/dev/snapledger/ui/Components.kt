package dev.snapledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.snapledger.core.category.CategoryCatalog
import dev.snapledger.core.category.CategoryKind
import dev.snapledger.core.model.ClassificationSource
import dev.snapledger.core.model.Direction
import dev.snapledger.core.model.Money
import dev.snapledger.core.model.TxnType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** Status colours used across the preview and the ledger; tuned separately for dark mode. */
object StatusColors {
    val green: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E3A2A) else Color(0xFFE6F4EA)
    val yellow: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF3D3415) else Color(0xFFFFF4D6)
    val red: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4A1F1F) else Color(0xFFFCE4E4)
    val gray: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2A2A2A) else Color(0xFFEFEFEF)
    val ok: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81C995) else Color(0xFF1E8E3E)
    val bad: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF28B82) else Color(0xFFD93025)
    val outflow: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val inflow: Color @Composable get() = ok
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
        TextButton(onClick = { open = true }, enabled = enabled) { Text(label) }
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
