package vn.baokim.qa.data.auth

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads claims out of the server session token (D2 hướng C):
 *
 *     token = base64url( json{email, iat, exp} ) + "." + hmac_sig
 *
 * The app does NOT verify the HMAC signature — the server re-checks it on every
 * request and answers 401 on tampering (→ [AuthManager.logout]). We only read the
 * `email` claim to derive [Role], so the payload is treated as untrusted display
 * data, never as an authorization decision the backend doesn't also make.
 */
object SessionToken {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Email from the token's payload, or null if the token is absent/malformed. Never throws. */
    fun emailOf(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val payload = token.substringBefore('.', missingDelimiterValue = "")
        if (payload.isEmpty()) return null
        return runCatching {
            val bytes = Base64.decode(normalize(payload), Base64.NO_WRAP)
            val obj = json.parseToJsonElement(bytes.decodeToString()).jsonObject
            obj["email"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Normalises to padded standard base64 so a single decoder handles both base64url
     * (`-_`, likely — the token rides in an App Link fragment) and standard (`+/`)
     * encodings, with or without `=` padding.
     */
    private fun normalize(b64: String): String {
        val std = b64.replace('-', '+').replace('_', '/')
        val pad = (4 - std.length % 4) % 4
        return std + "=".repeat(pad)
    }
}
