package vn.baokim.qa.data.net

import okhttp3.Interceptor
import okhttp3.Response
import vn.baokim.qa.data.auth.AuthManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the server-brokered session token as `Authorization: Bearer` (D2 hướng C),
 * replacing the old cookie jar. Handles the optional sliding refresh (server returns
 * a rotated token in `X-Session-Token` once past half-life) and drops the session on
 * 401 so the UI falls back to login (E2.5).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authManager.token
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        response.header(HEADER_REFRESH)?.let { authManager.refreshToken(it) }
        if (response.code == 401) authManager.logout()

        return response
    }

    private companion object {
        const val HEADER_REFRESH = "X-Session-Token"
    }
}
