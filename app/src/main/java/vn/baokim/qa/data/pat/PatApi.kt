package vn.baokim.qa.data.pat

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Personal Jira PAT endpoints (E3, #5). The PAT lives **server-side** — the backend
 * stores it encrypted keyed by the logged-in user's email (`pat_store`), so the app
 * never persists the raw PAT locally; it only drives save / status / delete here.
 * Auth is the Bearer session token attached by `AuthInterceptor` (D2 hướng C).
 *
 * Contract mirrors `qa_dashboard.py` (`_get_has_pat` / `_post_save_pat` / `_post_delete_pat`).
 */
interface PatApi {

    /** `{ok, hasPat}` — whether the server has a PAT for this user. `ok=false` on Jira error. */
    @GET("has-pat")
    suspend fun hasPat(): HasPatResponse

    /**
     * Saves this user's PAT. Backend returns 200 `{ok:true, msg}` on success or
     * 400 `{ok:false, msg}` on failure — so we take the raw [Response] and read the
     * error body to surface the server's Vietnamese [SavePatResponse.msg] to the user.
     */
    @POST("save-pat")
    suspend fun savePat(@Body body: SavePatRequest): Response<SavePatResponse>

    /** Deletes this user's PAT. `{ok}`. */
    @POST("delete-pat")
    suspend fun deletePat(): OkResponse
}

@Serializable
data class HasPatResponse(val ok: Boolean = false, val hasPat: Boolean = false)

@Serializable
data class SavePatRequest(val pat: String)

@Serializable
data class SavePatResponse(val ok: Boolean = false, val msg: String? = null)

@Serializable
data class OkResponse(val ok: Boolean = false)
