package io.github.sharveshar.spendly.data.backup

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupFormatException(message: String) : Exception(message)
class BackupPasswordException : Exception("Wrong password, or the file was modified")

/**
 * Encrypted backup file format (".spendly"):
 *
 * ```
 * "SPENDLYB" | version:u8=1 | kdf:u8=1 (PBKDF2-HMAC-SHA256) | iterations:u32 BE
 * | saltLen:u8 | salt | nonceLen:u8 | nonce | AES-256-GCM(ciphertext ‖ 128-bit tag)
 * ```
 *
 * The whole header is authenticated as GCM associated data, so any change to the header or
 * payload makes decryption fail. The plaintext is GZIP-compressed JSON of [BackupSnapshot].
 * Salt (16 B) and nonce (12 B) are fresh from [SecureRandom] for every export.
 * Only standard platform cryptography (javax.crypto) is used; no custom algorithms.
 */
object BackupCodec {
    const val EXTENSION = "spendly"
    const val MIME_TYPE = "application/octet-stream"
    const val SCHEMA_VERSION = 1
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8

    private val MAGIC = "SPENDLYB".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024
    private const val MAX_PLAINTEXT_BYTES = 256L * 1024 * 1024

    val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
        encodeDefaults = true
    }

    fun encrypt(snapshot: BackupSnapshot, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) { "Password too short" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = ByteBuffer.allocate(MAGIC.size + 2 + 4 + 1 + salt.size + 1 + nonce.size).apply {
            put(MAGIC); put(FORMAT_VERSION); put(KDF_PBKDF2_SHA256); putInt(iterations)
            put(salt.size.toByte()); put(salt); put(nonce.size.toByte()); put(nonce)
        }.array()

        val plain = ByteArrayOutputStream().use { bos ->
            GZIPOutputStream(bos).use { it.write(json.encodeToString(BackupSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)) }
            bos.toByteArray()
        }
        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plain)
        } finally {
            key.fill(0)
            plain.fill(0)
        }
    }

    /** Reads at most [MAX_FILE_BYTES] from [input]; larger files are rejected without buffering them. */
    fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_FILE_BYTES) throw BackupFormatException("File is too large to be a Spendly backup")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    @Throws(BackupFormatException::class, BackupPasswordException::class)
    fun decrypt(file: ByteArray, password: CharArray): BackupSnapshot {
        val buf = ByteBuffer.wrap(file)
        fun need(n: Int) { if (buf.remaining() < n) throw BackupFormatException("This is not a Spendly backup file") }
        need(MAGIC.size + 2 + 4)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) throw BackupFormatException("This is not a Spendly backup file")
        val version = buf.get()
        if (version != FORMAT_VERSION) throw BackupFormatException("This backup was made by a newer version of Spendly")
        if (buf.get() != KDF_PBKDF2_SHA256) throw BackupFormatException("Unsupported backup encryption")
        val iterations = buf.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupFormatException("Unsupported backup parameters")
        need(1)
        val saltLen = buf.get().toInt() and 0xFF
        if (saltLen !in 16..64) throw BackupFormatException("Corrupt backup header")
        need(saltLen + 1)
        val salt = ByteArray(saltLen).also { buf.get(it) }
        val nonceLen = buf.get().toInt() and 0xFF
        if (nonceLen != 12) throw BackupFormatException("Corrupt backup header")
        need(nonceLen + 16)
        val nonce = ByteArray(nonceLen).also { buf.get(it) }
        val headerLen = buf.position()
        val header = file.copyOfRange(0, headerLen)
        val ct = file.copyOfRange(headerLen, file.size)

        val key = deriveKey(password, salt, iterations)
        val compressed = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            cipher.doFinal(ct)
        } catch (_: AEADBadTagException) {
            throw BackupPasswordException()
        } finally {
            key.fill(0)
        }
        val text = try {
            inflateBounded(compressed).toString(Charsets.UTF_8)
        } catch (e: BackupFormatException) {
            throw e
        } catch (_: Exception) {
            throw BackupFormatException("Backup contents are corrupt")
        }
        return try {
            json.decodeFromString(BackupSnapshot.serializer(), text)
        } catch (_: Exception) {
            throw BackupFormatException("Backup contents are not in the expected format")
        }
    }

    private fun inflateBounded(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { gz ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = gz.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_PLAINTEXT_BYTES) throw BackupFormatException("Backup contents are too large")
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
