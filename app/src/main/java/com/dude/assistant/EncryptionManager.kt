package com.dude.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage using AES256-GCM.
 * Contacts aur settings securely store hote hain.
 */
class EncryptionManager(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "dude_secure_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ─── Contacts ────────────────────────────────────────────

    fun saveContact(name: String, phone: String) {
        prefs.edit()
            .putString("c_${name.lowercase().trim()}", phone.trim())
            .apply()
    }

    fun getContact(name: String): String? =
        prefs.getString("c_${name.lowercase().trim()}", null)

    fun deleteContact(name: String) {
        prefs.edit().remove("c_${name.lowercase().trim()}").apply()
    }

    fun getAllContacts(): Map<String, String> =
        prefs.all
            .filter { it.key.startsWith("c_") }
            .mapKeys { it.key.removePrefix("c_") }
            .mapValues { it.value as? String ?: "" }

    // ─── Settings ────────────────────────────────────────────

    fun saveSetting(key: String, value: String) {
        prefs.edit().putString("s_$key", value).apply()
    }

    fun getSetting(key: String, default: String = ""): String =
        prefs.getString("s_$key", default) ?: default
}
