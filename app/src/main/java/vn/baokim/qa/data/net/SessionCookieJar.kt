package vn.baokim.qa.data.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the server's `qa_session` cookie across requests (spec §4 auth note,
 * decision D2 option A). In-memory for now — persist to DataStore once the
 * auth mechanism is finalized. If D2 resolves to Bearer, this is replaced by an
 * Authorization-header interceptor instead.
 */
@Singleton
class SessionCookieJar @Inject constructor() : CookieJar {

    private val store = ConcurrentHashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store[it.name] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.values.filter { it.matches(url) }

    fun clear() = store.clear()
}
