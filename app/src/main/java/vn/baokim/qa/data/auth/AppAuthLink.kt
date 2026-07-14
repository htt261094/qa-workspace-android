package vn.baokim.qa.data.auth

import android.net.Uri
import vn.baokim.qa.BuildConfig

/**
 * App Links contract with the server broker (D2a). The server finishes OAuth on
 * the web side, then redirects to a verified https App Link carrying the session
 * token in the URL **fragment** (kept out of the app-side access log). Raw custom
 * schemes are intentionally avoided — another app could claim them.
 *
 *   redirect: https://baokim-qa.com/app/auth#token=<make_session_token>
 *
 * Host/path here must stay in sync with the manifest intent-filter and the
 * backend `APP_REDIRECT` (see SIGNING.md).
 */
object AppAuthLink {

    const val HOST = "baokim-qa.com"
    const val PATH = "/app/auth"

    /** Custom Tab target: server-side login that brokers OAuth back to the app. */
    val loginUrl: String = "${BuildConfig.BASE_URL}login?go=1&app=1"

    /**
     * Extracts the session token from an incoming App Link, or null if [uri] is not
     * our auth redirect or carries no token. The fragment is `token=<value>` (may be
     * followed by other `&`-separated params). No URL-decoding beyond what [Uri]
     * already applied — the token is base64url + `.` signature, safe as-is.
     */
    fun extractToken(uri: Uri?): String? {
        if (uri == null) return null
        if (!uri.host.equals(HOST, ignoreCase = true) || uri.path != PATH) return null
        val fragment = uri.fragment ?: return null
        return fragment.split('&')
            .firstOrNull { it.startsWith("token=") }
            ?.removePrefix("token=")
            ?.takeIf { it.isNotBlank() }
    }
}
