package dev.snapledger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.snapledger.core.category.CategoryCatalog
import dev.snapledger.core.category.Classifier
import dev.snapledger.core.ledger.LedgerRepository
import dev.snapledger.core.ledger.LedgerSnapshot
import dev.snapledger.core.ledger.StoredTransaction
import dev.snapledger.core.ledger.UserRule
import dev.snapledger.core.model.ClassificationSource

/**
 * App-wide state holder (the role a ViewModel plays): the current snapshot of the ledger plus the
 * write operations. Compose re-renders whatever reads [snapshot] whenever it changes.
 * SQLite is local and small, so calls run synchronously.
 */
class LedgerState(
    val repository: LedgerRepository,
    val accountId: String,
    val currency: String,
    val catalog: CategoryCatalog = CategoryCatalog.DEFAULT,
) {
    var snapshot: LedgerSnapshot by mutableStateOf(repository.snapshot(accountId))
        private set

    fun refresh() {
        snapshot = repository.snapshot(accountId)
    }

    /** Changes a category by hand and, like the preview, remembers it for this merchant from now on. */
    fun recategorise(
        transaction: StoredTransaction,
        categoryCode: String,
    ): String {
        repository.updateCategory(transaction.id, categoryCode, ClassificationSource.MANUAL)
        val pattern = Classifier.ruleKey(transaction.merchant, transaction.rawDescription)
        repository.saveUserRule(UserRule(pattern, categoryCode))
        refresh()
        val who = transaction.merchant ?: transaction.rawDescription
        return "已记住：以后「$who」都归为「${categoryName(categoryCode)}」"
    }

    fun delete(transaction: StoredTransaction) {
        repository.deleteTransaction(transaction.id)
        refresh()
    }

    fun categoryName(code: String?): String {
        if (code == null) return "未分类"
        val category = catalog[code] ?: return code
        val parent = catalog.topLevelOf(code)
        return if (parent != null && parent.code != code &&
            parent.nameZh != category.nameZh
        ) {
            "${parent.nameZh} · ${category.nameZh}"
        } else {
            category.nameZh
        }
    }
}
