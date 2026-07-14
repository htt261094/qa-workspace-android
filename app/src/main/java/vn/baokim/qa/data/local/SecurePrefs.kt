package vn.baokim.qa.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted at-rest storage for the user's personal Jira PAT (spec §7 OPSEC).
 * Backed by Android Keystore via EncryptedSharedPreferences. Never logged.
 */
@Singleton
class SecurePrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var pat: String?
        get() = prefs.getString(KEY_PAT, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_PAT) else putString(KEY_PAT, value)
        }.apply()

    val hasPat: Boolean get() = !prefs.getString(KEY_PAT, null).isNullOrBlank()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val FILE_NAME = "qa_secure_prefs"
        const val KEY_PAT = "jira_pat"
    }
}
