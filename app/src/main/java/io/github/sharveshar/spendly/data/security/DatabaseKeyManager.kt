// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Thrown when an encrypted database exists but its key cannot be recovered. Data is never deleted automatically. */
class DatabaseKeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Manages the SQLCipher database key.
 *
 * - A random 256-bit database key is generated with [SecureRandom] on first launch.
 * - It is encrypted ("wrapped") with an AES-256-GCM key that lives in the Android Keystore and
 *   never leaves it. Each wrap uses a fresh Keystore-generated 96-bit IV.
 * - Only the wrapped key is written to disk, in [noBackupDir] (excluded from every backup).
 * - The key is never logged, placed in SharedPreferences/DataStore, or hardcoded.
 *
 * If the Keystore entry is lost (for example after some device resets), the database cannot be
 * decrypted. That is reported via [DatabaseKeyUnavailableException]; the UI then offers restore
 * from an encrypted backup or an explicit, user-confirmed reset.
 */
class DatabaseKeyManager(private val noBackupDir: File) {

    private val keyFile = File(noBackupDir, KEY_FILE)

    fun keyFileExists(): Boolean = keyFile.exists()

    /**
     * Returns SQLCipher key material in raw-key form (`x'<64 hex>'`) so SQLCipher skips its
     * passphrase KDF; the key is already uniformly random.
     */
    @Throws(DatabaseKeyUnavailableException::class)
    fun getOrCreateKey(databaseExists: Boolean): ByteArray {
        val raw = if (keyFile.exists()) {
            unwrap(keyFile.readBytes())
        } else {
            if (databaseExists) {
                throw DatabaseKeyUnavailableException("The database key file is missing")
            }
            val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            writeAtomically(wrap(fresh))
            fresh
        }
        try {
            return toRawKeySpec(raw)
        } finally {
            raw.fill(0)
        }
    }

    /** Deletes the wrapped key and the Keystore entry. Only called after explicit user confirmation. */
    fun destroy() {
        keyFile.delete()
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        }
    }

    private fun keystoreKey(createIfMissing: Boolean): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (!createIfMissing) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(raw: ByteArray): ByteArray {
        val key = keystoreKey(createIfMissing = true)!!
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key) // Keystore generates a unique IV
        cipher.updateAAD(AAD)
        val iv = cipher.iv
        val ct = cipher.doFinal(raw)
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + ct
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        try {
            require(blob.size > 2 && blob[0] == FORMAT_VERSION) { "Unknown key file format" }
            val ivLen = blob[1].toInt()
            require(ivLen in 12..16 && blob.size > 2 + ivLen + 16) { "Corrupt key file" }
            val iv = blob.copyOfRange(2, 2 + ivLen)
            val ct = blob.copyOfRange(2 + ivLen, blob.size)
            val key = keystoreKey(createIfMissing = false)
                ?: throw DatabaseKeyUnavailableException("The device key protecting your data is no longer available")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            return cipher.doFinal(ct)
        } catch (e: DatabaseKeyUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw DatabaseKeyUnavailableException("The database key could not be unlocked", e)
        }
    }

    private fun writeAtomically(bytes: ByteArray) {
        noBackupDir.mkdirs()
        val tmp = File(noBackupDir, "$KEY_FILE.tmp")
        tmp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!tmp.renameTo(keyFile)) {
            tmp.delete()
            throw IOException("Could not store database key")
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "spendly_db_wrapping_key_v1"
        private const val KEY_FILE = "spendly_db.key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private val AAD = "spendly-db-key-v1".toByteArray()

        internal fun toRawKeySpec(raw: ByteArray): ByteArray {
            val hex = CharArray(raw.size * 2)
            val digits = "0123456789ABCDEF"
            raw.forEachIndexed { i, b ->
                val v = b.toInt() and 0xFF
                hex[i * 2] = digits[v ushr 4]
                hex[i * 2 + 1] = digits[v and 0x0F]
            }
            val out = ByteArray(hex.size + 3)
            out[0] = 'x'.code.toByte()
            out[1] = '\''.code.toByte()
            hex.forEachIndexed { i, c -> out[i + 2] = c.code.toByte() }
            out[out.size - 1] = '\''.code.toByte()
            hex.fill('0')
            return out
        }
    }
}
