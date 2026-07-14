package vn.baokim.qa.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.baokim.qa.data.local.SecurePrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for auth session state (E2, D2 hướng C).
 *
 * The app never runs OAuth itself — the server brokers it and hands back its
 * existing HMAC session token via an App Link. This holder persists that token
 * in [SecurePrefs] and exposes login state to the UI so it can gate to/from the
 * login screen. The token is emitted only as a boolean here; the raw value never
 * leaves [SecurePrefs] except via the Authorization header.
 */
@Singleton
class AuthManager @Inject constructor(
    private val securePrefs: SecurePrefs,
) {
    private val _isLoggedIn = MutableStateFlow(securePrefs.sessionToken != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /** Access role derived from the token's email claim (E2.4). Drives which tabs/actions show. */
    private val _role = MutableStateFlow(roleFor(securePrefs.sessionToken))
    val role: StateFlow<Role> = _role.asStateFlow()

    /** Current Bearer token, or null when logged out. Read by [AuthInterceptor] only. */
    val token: String? get() = securePrefs.sessionToken

    /** App Link delivered a fresh token from the server broker (`/app/auth#token=`). */
    fun onTokenReceived(token: String) {
        securePrefs.sessionToken = token
        _role.value = roleFor(token)
        _isLoggedIn.value = true
    }

    /** Sliding refresh — server rotated the token past half-life (X-Session-Token header). */
    fun refreshToken(token: String) {
        securePrefs.sessionToken = token
        _role.value = roleFor(token) // same user, but re-derive in case the payload changes
    }

    /** Session expired (401) or explicit logout → drop token, back to login. Keeps the PAT. */
    fun logout() {
        securePrefs.sessionToken = null
        _role.value = Role.DEFAULT
        _isLoggedIn.value = false
    }

    private fun roleFor(token: String?): Role = Role.fromEmail(SessionToken.emailOf(token))
}
