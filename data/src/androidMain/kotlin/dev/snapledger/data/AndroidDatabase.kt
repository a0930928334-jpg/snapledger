package dev.snapledger.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.snapledger.data.db.SnapLedgerDatabase

object AndroidDatabase {
    fun open(context: Context): SqlLedgerRepository {
        val driver = AndroidSqliteDriver(SnapLedgerDatabase.Schema, context, "snapledger.db")
        return SqlLedgerRepository(SnapLedgerDatabase(driver), System::currentTimeMillis)
    }
}
