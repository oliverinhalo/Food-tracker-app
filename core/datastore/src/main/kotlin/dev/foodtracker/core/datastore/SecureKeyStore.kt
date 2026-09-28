package dev.foodtracker.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _keyPresent = MutableStateFlow(false)

    /** Cheap presence signal so callers can branch without decrypting on every recomposition. */
    val keyPresent: StateFlow<Boolean> = _keyPresent.asStateFlow()

    init {
        _keyPresent.value = !geminiApiKey().isNullOrBlank()
    }

    fun geminiApiKey(): String? = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    fun setGeminiApiKey(key: String?) {
        prefs.edit().apply {
            if (key.isNullOrBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, key.trim())
        }.apply()
        _keyPresent.value = !key.isNullOrBlank()
    }

    private companion object {
        const val PREFS_NAME = "secure_keys"
        const val KEY_GEMINI = "gemini_api_key"
    }
}
