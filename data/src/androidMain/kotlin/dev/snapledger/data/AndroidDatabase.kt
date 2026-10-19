package dev.snapledger.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.snapledger.data.backup.SqlBackupService
import dev.snapledger.data.db.SnapLedgerDatabase

object AndroidDatabase {
    fun open(context: Context): LocalStore {
        val db = SnapLedgerDatabase(AndroidSqliteDriver(SnapLedgerDatabase.Schema, context, "snapledger.db"))
        return LocalStore(
            SqlLedgerRepository(db, System::currentTimeMillis),
            SqlBackupService(db, System::currentTimeMillis),
        )
    }
}
