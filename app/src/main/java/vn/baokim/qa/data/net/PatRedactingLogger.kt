package vn.baokim.qa.data.net

import okhttp3.logging.HttpLoggingInterceptor

/**
 * OkHttp logging that scrubs any PAT / bearer token before it reaches logcat
 * (spec §7: never log PAT, even partially).
 */
object PatRedactingLogger {

    private val SENSITIVE = Regex(
        """(?i)(authorization:\s*bearer\s+|"?pat"?\s*[:=]\s*"?)([A-Za-z0-9._\-]+)""",
    )

    fun create(): HttpLoggingInterceptor {
        val logger = HttpLoggingInterceptor { message ->
            android.util.Log.d("QaHttp", SENSITIVE.replace(message) { "${it.groupValues[1]}«REDACTED»" })
        }
        // Never BODY in release; headers only, and even those are scrubbed above.
        logger.level = HttpLoggingInterceptor.Level.HEADERS
        logger.redactHeader("Authorization")
        logger.redactHeader("Cookie")
        logger.redactHeader("Set-Cookie")
        logger.redactHeader("X-Session-Token") // sliding-refresh session token (D2)
        return logger
    }
}
