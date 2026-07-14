package vn.baokim.qa.data.pat

import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a save attempt, carrying the server's message for the user (never the PAT). */
sealed interface SaveResult {
    data class Success(val message: String?) : SaveResult
    data class Failure(val message: String?) : SaveResult
}

/**
 * Thin repository over [PatApi] (E3, #5). Server is the source of truth for the PAT;
 * this layer only translates transport into results the ViewModel can render and makes
 * sure a failure never throws into the UI. The PAT string is passed straight through to
 * the API and is never stored, cached, or logged here (OPSEC §7).
 */
@Singleton
class PatRepository @Inject constructor(
    private val api: PatApi,
    private val json: Json,
) {

    /** True/false when the server answered; null when we couldn't determine (network/Jira error). */
    suspend fun hasPat(): Boolean? = runCatching { api.hasPat() }
        .map { it.ok && it.hasPat }
        .getOrNull()

    suspend fun savePat(pat: String): SaveResult = try {
        val response = api.savePat(SavePatRequest(pat))
        val body = response.body() ?: response.errorBody()?.string()?.let { raw ->
            runCatching { json.decodeFromString<SavePatResponse>(raw) }.getOrNull()
        }
        if (response.isSuccessful && body?.ok == true) {
            SaveResult.Success(body.msg)
        } else {
            SaveResult.Failure(body?.msg)
        }
    } catch (e: Exception) {
        // Network/parse failure — surface a generic message, never the PAT or exception detail.
        SaveResult.Failure(null)
    }

    /** True when the server confirmed deletion. */
    suspend fun deletePat(): Boolean = runCatching { api.deletePat().ok }.getOrDefault(false)
}
