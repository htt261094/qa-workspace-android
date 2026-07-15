package vn.baokim.qa.data.buglog

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import vn.baokim.qa.data.local.BugDao
import vn.baokim.qa.data.local.BugEntity
import vn.baokim.qa.domain.buglog.Bug
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a bug-log network refresh. On [Error] the Room cache (and thus the UI) is kept. */
sealed interface BugLogRefresh {
    /** [editable] mirrors backend `editable` (false for DEV); [syncedAt] is the Drive sync stamp. */
    data class Success(val editable: Boolean, val syncedAt: String) : BugLogRefresh
    data object Error : BugLogRefresh
}

/** Result of a bug↔task link add/remove (E8.4). */
sealed interface LinkResult {
    data object Success : LinkResult
    data object Error : LinkResult
}

/** Result of an Excel export (E8.5): a shareable content [uri] pointing at the saved `.xlsx`. */
sealed interface ExportResult {
    data class Success(val uri: android.net.Uri, val filename: String) : ExportResult
    data object Error : ExportResult
}

/**
 * Single source of truth for the Bug Log (E8, #13). Offline-first like "Việc của tôi" (E4):
 * the UI observes [observeBugs] (backed by Room) and [refresh] pulls `/api/bug-log` and
 * replaces the cache (E8.6). Linking goes through `/link-task` (E8.4) and export streams the
 * `.xlsx` from `/export-bug-log` into a cache file exposed via FileProvider (E8.5).
 *
 * Nothing sensitive is cached or logged (OPSEC §7): only public bug metadata, never the PAT
 * or session token.
 */
@Singleton
class BugLogRepository @Inject constructor(
    private val api: BugLogApi,
    private val dao: BugDao,
    @ApplicationContext private val context: Context,
) {

    fun observeBugs(): Flow<List<Bug>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Fetches fresh bugs and replaces the cache. On any failure the cache is kept. */
    suspend fun refresh(): BugLogRefresh = try {
        val response = api.bugLog()
        if (!response.ok) {
            BugLogRefresh.Error
        } else {
            dao.replaceAll(response.bugs.toEntities())
            BugLogRefresh.Success(editable = response.editable, syncedAt = response.syncedAt)
        }
    } catch (e: Exception) {
        BugLogRefresh.Error
    }

    /** Add or remove one bug↔Jira-task link (E8.4). A successful call should be followed by a refresh. */
    suspend fun link(bugKey: String, taskKey: String, add: Boolean): LinkResult = try {
        val response = api.linkTask(
            LinkTaskRequest(keys = listOf(bugKey), task = taskKey, op = if (add) "add" else "remove"),
        )
        if (response.body()?.ok == true) LinkResult.Success else LinkResult.Error
    } catch (e: Exception) {
        LinkResult.Error
    }

    /**
     * Exports [rows] (7 columns each) to an `.xlsx` and returns a shareable content Uri (E8.5).
     * The backend fixes the header and builds the file; the app just streams the bytes into a
     * cache file and wraps it with FileProvider so a share/open intent can read it.
     */
    suspend fun export(rows: List<List<String>>, filename: String): ExportResult = try {
        val response = api.exportBugLog(ExportRequest(rows, filename))
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            ExportResult.Error
        } else withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safe = filename.ifBlank { "bug-log" }.let {
                if (it.endsWith(".xlsx", ignoreCase = true)) it else "$it.xlsx"
            }
            val file = File(dir, safe)
            body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            ExportResult.Success(uri, safe)
        }
    } catch (e: Exception) {
        ExportResult.Error
    }
}

// --- mappers -----------------------------------------------------------------

private fun BugDto.toDomain(): Bug = Bug(
    key = key,
    id = id,
    summary = summary,
    module = module,
    severity = severity,
    status = status,
    project = project,
    month = month,
    tester = qa,
    dev = dev,
    created = created,
    tasks = tasks,
)

private fun List<BugDto>.toEntities(): List<BugEntity> = mapIndexed { index, dto ->
    BugEntity(
        key = dto.key,
        rowOrder = index,
        id = dto.id,
        summary = dto.summary,
        module = dto.module,
        severity = dto.severity,
        status = dto.status,
        project = dto.project,
        month = dto.month,
        tester = dto.qa,
        dev = dto.dev,
        created = dto.created,
        tasks = dto.tasks.joinToString(","),
    )
}

private fun BugEntity.toDomain(): Bug = Bug(
    key = key,
    id = id,
    summary = summary,
    module = module,
    severity = severity,
    status = status,
    project = project,
    month = month,
    tester = tester,
    dev = dev,
    created = created,
    tasks = if (tasks.isBlank()) emptyList() else tasks.split(","),
)
