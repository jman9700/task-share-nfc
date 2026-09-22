package com.taskshare.app.data.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates (once) and retrieves the passphrase used to encrypt the local SQLCipher database.
 * The passphrase itself is random and never leaves the device; it's stored in
 * EncryptedSharedPreferences, whose own key is hardware-backed via the Android Keystore
 * (MasterKey), so the passphrase is never written to disk in plaintext.
 */
object DbKeyProvider {
    private const val PREFS_NAME = "task_share_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    fun getOrCreatePassphrase(context: Context): CharArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) return existing.toCharArray()

        val generated = generatePassphrase()
        prefs.edit().putString(KEY_DB_PASSPHRASE, String(generated)).apply()
        return generated
    }

    private fun generatePassphrase(): CharArray {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }.toCharArray()
    }
}
