package dev.foodtracker.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the user's Gemini API key in [EncryptedSharedPreferences], backed by an AES256-GCM master
 * key in the Android Keystore. The key never touches DataStore, logs, the repo, or any analytics
 * surface; the rest of the app only ever asks "is there a key" or gets it handed straight to the
 * request builder.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Lazy, and left lazy: building the master key generates a Keystore entry on first run and
    // reads an encrypted file on every later one. Touching it from the constructor would put that
    // on the main thread at startup, for an app that has no key to read until Settings is opened.
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun geminiApiKey(): String? = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    /**
     * Optional USDA FoodData Central key. There is a build-time key too, but a fresh clone and
     * every CI build have none, so without this the shipped app silently loses its source of
     * generic-food nutrition.
     */
    fun usdaApiKey(): String? = prefs.getString(KEY_USDA, null)?.takeIf { it.isNotBlank() }

    fun setUsdaApiKey(key: String?) {
        prefs.edit().apply {
            if (key.isNullOrBlank()) remove(KEY_USDA) else putString(KEY_USDA, key.trim())
        }.apply()
    }

    fun setGeminiApiKey(key: String?) {
        prefs.edit().apply {
            if (key.isNullOrBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, key.trim())
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "secure_keys"
        const val KEY_GEMINI = "gemini_api_key"
        const val KEY_USDA = "usda_api_key"
    }
}
