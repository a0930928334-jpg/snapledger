package dev.snapledger.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * `.slbackup` container (docs/02 section 6):
 *
 * ```
 * "SLBK" | version u8 | kdf u8 | iterations i32 | saltLen u8 | salt | nonceLen u8 | nonce | AES-256-GCM(ciphertext ‖ tag)
 * ```
 *
 * The key comes from the password via PBKDF2-HMAC-SHA256. The whole plaintext header is authenticated as
 * GCM associated data, so a wrong password, a flipped byte anywhere or edited KDF parameters all fail to open.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1

    /** OWASP's 2023+ recommendation for PBKDF2-HMAC-SHA256. */
    const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 1_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class BackupFormatException(
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    class WrongPasswordException : Exception("wrong password or modified file")

    fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        salt: ByteArray = random(SALT_BYTES),
        nonce: ByteArray = random(NONCE_BYTES),
    ): ByteArray {
        val header =
            ByteBuffer
                .allocate(MAGIC.size + 2 + 4 + 1 + salt.size + 1 + nonce.size)
                .put(MAGIC)
                .put(VERSION)
                .put(KDF_PBKDF2_SHA256)
                .putInt(iterations)
                .put(salt.size.toByte())
                .put(salt)
                .put(nonce.size.toByte())
                .put(nonce)
                .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    @Suppress("ThrowsCount") // Each check names a different way the file can be unusable.
    fun decrypt(
        file: ByteArray,
        password: CharArray,
    ): ByteArray {
        val buffer = ByteBuffer.wrap(file)
        val magic = ByteArray(MAGIC.size)
        if (file.size < MAGIC.size + 2 || !buffer.get(magic).let { magic.contentEquals(MAGIC) }) {
            throw BackupFormatException("不是 SnapLedger 备份文件")
        }
        val version = buffer.get()
        val kdf = buffer.get()
        if (version != VERSION || kdf != KDF_PBKDF2_SHA256) throw BackupFormatException("备份文件版本 $version 不受支持，请升级 App")
        try {
            val iterations = buffer.int
            if (iterations < MIN_ITERATIONS) throw BackupFormatException("备份文件的加密参数异常")
            val salt = ByteArray(buffer.get().toInt()).also { buffer.get(it) }
            val nonce = ByteArray(buffer.get().toInt()).also { buffer.get(it) }
            val headerLength = buffer.position()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(file, 0, headerLength)
            return cipher.doFinal(file, headerLength, file.size - headerLength)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException().initCause(e)
        } catch (e: java.nio.BufferUnderflowException) {
            throw BackupFormatException("备份文件不完整", e)
        } catch (e: NegativeArraySizeException) {
            throw BackupFormatException("备份文件已损坏", e)
        }
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
}
