package dev.snapledger.data

import dev.snapledger.core.importer.ImportPipeline
import dev.snapledger.core.importer.PrepareResult
import dev.snapledger.core.ledger.BackupResult
import dev.snapledger.core.ledger.MergeReport
import dev.snapledger.core.model.ClassificationSource
import dev.snapledger.data.SqlLedgerRepository.Companion.DEFAULT_ACCOUNT_ID
import dev.snapledger.data.backup.BackupCrypto
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class BackupTest {
    private val pipeline = ImportPipeline()
    private val password = "correct horse battery staple".toCharArray()

    // Fast KDF for tests only; the format records the iteration count, so this is still a valid file.
    private fun store() = JvmDatabase.open(null, backupIterations = 1_000)

    private fun sample(name: String) = File(System.getProperty("snapledger.samplesDir"), name).readText()

    private fun LocalStore.importSample(name: String) {
        val snapshot = repository.snapshot(DEFAULT_ACCOUNT_ID)
        val preview =
            when (val r = pipeline.prepare(sample(name), snapshot, DEFAULT_ACCOUNT_ID)) {
                is PrepareResult.Ready -> r.preview
                is PrepareResult.Failure -> fail(r.issues.toString())
            }
        val filled = preview.copy(rows = preview.rows.map { if (it.amountMinor == null) it.copy(amountMinor = 320) else it })
        repository.commitImport(DEFAULT_ACCOUNT_ID, pipeline.commit(filled, pipeline.evaluate(filled, snapshot)))
    }

    private fun LocalStore.mergeFrom(other: LocalStore): MergeReport {
        val result = backup.import(other.backup.export(password), password)
        return assertIs<BackupResult.Merged>(result).report
    }

    private fun LocalStore.state() =
        repository.snapshot(DEFAULT_ACCOUNT_ID).let { s ->
            Triple(
                s.transactions.map { listOf(it.date, it.amountMinor, it.rawDescription, it.categoryCode) }.sortedBy { it.toString() },
                s.balances.map { it.date to it.balanceMinor }.sortedBy { it.toString() },
                s.userRules.sortedBy { it.pattern },
            )
        }

    @Test
    fun matchesIndependentReferenceImplementation() {
        // Generated with Node.js crypto (pbkdf2Sync + aes-256-gcm), not with the JDK.
        val expected =
            "534c424b0101000003e810000102030405060708090a0b0c0d0e0f0ca0a1a2a3a4a5a6a7a8a9aaab" +
                "adfedbb721776c972a9de28e73d257191df51e6840d4a43dff75ba77a80955480538d0e4ecd5"
        val salt = (0 until 16).map { it.toByte() }.toByteArray()
        val nonce = (0xA0..0xAB).map { it.toByte() }.toByteArray()
        val plaintext = "{\"hello\":\"SnapLedger\"}".encodeToByteArray()

        val file = BackupCrypto.encrypt(plaintext, password, 1_000, salt, nonce)
        assertEquals(expected, file.toHexString())
        assertContentEquals(plaintext, BackupCrypto.decrypt(file, password))
    }

    @Test
    fun roundTripsEverything() {
        val windows = store()
        windows.importSample("example-1-basic.json")
        windows.importSample("example-3-special.json")
        val tx =
            windows.repository
                .snapshot(DEFAULT_ACCOUNT_ID)
                .transactions
                .first()
        windows.repository.updateCategory(tx.id, "dining.restaurant", ClassificationSource.MANUAL)

        val android = store()
        val report = android.mergeFrom(windows)
        assertEquals(8, report.transactionsAdded)
        assertEquals(windows.state(), android.state())
    }

    @Test
    fun wrongPasswordAndTamperingAreRejected() {
        val source = store().apply { importSample("example-1-basic.json") }
        val file = source.backup.export(password)
        val target = store()

        val wrong = target.backup.import(file, "not the password".toCharArray())
        assertTrue("密码错误" in assertIs<BackupResult.Failed>(wrong).message)

        // A flipped bit in the ciphertext, in the authenticated header, and a truncated file.
        val flippedBody = file.copyOf().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 1).toByte() }
        val flippedHeader = file.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }
        listOf(flippedBody, flippedHeader, file.copyOf(20), "hello".encodeToByteArray()).forEach { broken ->
            assertIs<BackupResult.Failed>(target.backup.import(broken, password))
        }
        assertTrue(
            target.repository
                .snapshot(DEFAULT_ACCOUNT_ID)
                .transactions
                .isEmpty(),
        )
    }

    @Test
    fun independentImportsOfTheSameScreenshotsDoNotDuplicate() {
        val windows = store().apply { importSample("example-2-overlap.json") }
        val android = store().apply { importSample("example-2-overlap.json") }

        val report = android.mergeFrom(windows)
        assertEquals(0, report.transactionsAdded)
        assertEquals(4, report.transactionsMatched)
        windows.mergeFrom(android)
        assertEquals(
            4,
            windows.repository
                .snapshot(DEFAULT_ACCOUNT_ID)
                .transactions.size,
        )
        assertEquals(
            4,
            android.repository
                .snapshot(DEFAULT_ACCOUNT_ID)
                .transactions.size,
        )
    }

    @Test
    fun newerEditsWinInBothDirections() {
        val windows = store().apply { importSample("example-1-basic.json") }
        val android = store()
        android.mergeFrom(windows)

        val txns = windows.repository.snapshot(DEFAULT_ACCOUNT_ID).transactions
        Thread.sleep(5)
        windows.repository.updateCategory(txns[0].id, "social.treats", ClassificationSource.MANUAL)
        android.repository.deleteTransaction(txns[1].id)

        android.mergeFrom(windows)
        windows.mergeFrom(android)
        assertEquals(windows.state(), android.state())
        val after = windows.repository.snapshot(DEFAULT_ACCOUNT_ID).transactions
        assertEquals(3, after.size)
        assertEquals("social.treats", after.single { it.id == txns[0].id }.categoryCode)
    }

    /** Writes a real-strength backup of fictional data, used to check that Android opens files made on Windows. */
    @Test
    fun writesCrossDeviceFixture() {
        val store = JvmDatabase.open(null).apply { importSample("example-2-overlap.json") }
        val file = File(System.getProperty("snapledger.buildDir"), "backup-fixture/example-2.slbackup")
        file.parentFile.mkdirs()
        file.writeBytes(store.backup.export(password))
        val roundTrip = JvmDatabase.open(null).backup.import(file.readBytes(), password)
        assertEquals(4, assertIs<BackupResult.Merged>(roundTrip).report.transactionsAdded)
    }

    /** Manual cross-device check: opens a backup pulled from the Android app, when one has been placed there. */
    @Test
    fun opensBackupExportedOnAndroidWhenPresent() {
        val file = File(System.getProperty("snapledger.buildDir"), "backup-fixture/from-android.slbackup")
        if (!file.exists()) return
        val store = JvmDatabase.open(null)
        val report = assertIs<BackupResult.Merged>(store.backup.import(file.readBytes(), password)).report
        println("Android backup: ${report.transactionsAdded} transactions, ${report.balancesChanged} balances")
        assertEquals(8, report.transactionsAdded)
    }

    @Test
    fun mergingTheSameBackupTwiceChangesNothing() {
        val windows = store().apply { importSample("example-1-basic.json") }
        val android = store()
        android.mergeFrom(windows)
        assertEquals(0, android.mergeFrom(windows).totalChanges)
    }
}
