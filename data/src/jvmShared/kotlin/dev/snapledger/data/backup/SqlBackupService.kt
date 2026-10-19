package dev.snapledger.data.backup

import dev.snapledger.core.ledger.BackupPort
import dev.snapledger.core.ledger.BackupResult
import dev.snapledger.data.db.SnapLedgerDatabase
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class SqlBackupService(
    db: SnapLedgerDatabase,
    clock: () -> Long,
    private val iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
) : BackupPort {
    private val merger = BackupMerger(db, clock)
    private val json = Json { ignoreUnknownKeys = true }

    override fun export(password: CharArray): ByteArray {
        val plaintext = json.encodeToString(BackupPayload.serializer(), merger.payload()).encodeToByteArray()
        return BackupCrypto.encrypt(plaintext, password, iterations)
    }

    override fun import(
        file: ByteArray,
        password: CharArray,
    ): BackupResult =
        try {
            val payload = json.decodeFromString(BackupPayload.serializer(), BackupCrypto.decrypt(file, password).decodeToString())
            if (payload.formatVersion > BackupPayload.FORMAT_VERSION) {
                BackupResult.Failed("这个备份来自更新版本的 SnapLedger，请先升级 App")
            } else {
                BackupResult.Merged(merger.merge(payload))
            }
        } catch (e: BackupCrypto.WrongPasswordException) {
            BackupResult.Failed("无法打开备份：密码错误，或者文件已损坏、被修改过（${e.message}）")
        } catch (e: BackupCrypto.BackupFormatException) {
            BackupResult.Failed(e.message ?: "备份文件格式不正确")
        } catch (e: SerializationException) {
            BackupResult.Failed("备份内容无法解析：${e.message}")
        }
}
