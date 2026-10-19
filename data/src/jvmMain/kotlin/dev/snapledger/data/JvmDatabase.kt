package dev.snapledger.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.snapledger.data.db.SnapLedgerDatabase
import java.io.File
import java.util.Properties

object JvmDatabase {
    /** Opens (creating or migrating as needed) the database file at [file]; pass null for an in-memory database. */
    fun open(file: File?): SqlLedgerRepository {
        val url =
            if (file == null) {
                JdbcSqliteDriver.IN_MEMORY
            } else {
                file.parentFile?.mkdirs()
                "jdbc:sqlite:${file.absolutePath}"
            }
        val driver = JdbcSqliteDriver(url, Properties().apply { put("foreign_keys", "true") }, SnapLedgerDatabase.Schema)
        return SqlLedgerRepository(SnapLedgerDatabase(driver), System::currentTimeMillis)
    }

    /** `%APPDATA%\SnapLedger\snapledger.db` on Windows, `~/.snapledger/snapledger.db` elsewhere. */
    fun defaultFile(): File {
        val appData = System.getenv("APPDATA")
        val dir = if (appData != null) File(appData, "SnapLedger") else File(System.getProperty("user.home"), ".snapledger")
        return File(dir, "snapledger.db")
    }
}
