package dev.snapledger.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.snapledger.data.backup.BackupCrypto
import dev.snapledger.data.backup.SqlBackupService
import dev.snapledger.data.db.SnapLedgerDatabase
import java.io.File
import java.util.Properties

object JvmDatabase {
    /**
     * Opens (creating or migrating as needed) the database file at [file]; pass null for an in-memory database.
     * [backupIterations] exists so tests can use a fast key derivation; production keeps the default.
     */
    fun open(
        file: File?,
        backupIterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    ): LocalStore {
        val url =
            if (file == null) {
                JdbcSqliteDriver.IN_MEMORY
            } else {
                file.parentFile?.mkdirs()
                "jdbc:sqlite:${file.absolutePath}"
            }
        val driver = JdbcSqliteDriver(url, Properties().apply { put("foreign_keys", "true") }, SnapLedgerDatabase.Schema)
        val db = SnapLedgerDatabase(driver)
        return LocalStore(
            SqlLedgerRepository(db, System::currentTimeMillis),
            SqlBackupService(db, System::currentTimeMillis, backupIterations),
        )
    }

    /** `%APPDATA%\SnapLedger\snapledger.db` on Windows, `~/.snapledger/snapledger.db` elsewhere. */
    fun defaultFile(): File {
        val appData = System.getenv("APPDATA")
        val dir = if (appData != null) File(appData, "SnapLedger") else File(System.getProperty("user.home"), ".snapledger")
        return File(dir, "snapledger.db")
    }
}
