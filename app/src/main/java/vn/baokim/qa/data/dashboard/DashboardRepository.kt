package vn.baokim.qa.data.dashboard

import retrofit2.HttpException
import vn.baokim.qa.domain.dashboard.Dashboard
import vn.baokim.qa.domain.dashboard.DashboardTask
import vn.baokim.qa.domain.dashboard.Kpi
import vn.baokim.qa.domain.dashboard.WorkloadEntry
import vn.baokim.qa.domain.dashboard.WorkloadLevel
import vn.baokim.qa.domain.mywork.StatusCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a dashboard fetch. The endpoint is ADMIN-only and depends on Jira, so the
 * failure modes are distinct (D7): [Forbidden] (403, non-admin) and [Unavailable] (503,
 * Jira down) get their own message; everything else is [Error].
 */
sealed interface DashboardResult {
    data class Success(val data: Dashboard) : DashboardResult
    data object Forbidden : DashboardResult   // 403 — not an admin
    data object Unavailable : DashboardResult // 503 — Jira down upstream
    data object Error : DashboardResult       // network / parse / other
}

/**
 * Fetches the team dashboard (E6, #9). Network-only: unlike "Việc của tôi" (E4) there's no
 * Room cache here — D7 marks offline caching optional for this admin-only, heavy (~535
 * task) view, so the ViewModel holds the last snapshot in memory instead.
 *
 * The backend already counts the KPIs and bands the workload (D3), so this layer just maps
 * the DTOs across without re-deriving anything.
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val api: DashboardApi,
) {

    suspend fun load(): DashboardResult = try {
        val response = api.dashboard()
        if (response.ok) DashboardResult.Success(response.toDomain()) else DashboardResult.Error
    } catch (e: HttpException) {
        when (e.code()) {
            403 -> DashboardResult.Forbidden
            503 -> DashboardResult.Unavailable
            else -> DashboardResult.Error
        }
    } catch (e: Exception) {
        DashboardResult.Error
    }
}

// --- mappers -----------------------------------------------------------------

private fun DashboardResponse.toDomain(): Dashboard = Dashboard(
    kpi = Kpi(
        active = meta.active,
        overdue = meta.overdue,
        stuck = meta.stuck,
        createdWeek = meta.createdWeek,
        resolvedWeek = meta.resolvedWeek,
    ),
    workload = workload.map { it.toDomain() },
    tasks = tasks.map { it.toDomain() },
)

private fun WorkloadDto.toDomain(): WorkloadEntry = WorkloadEntry(
    name = name,
    initials = init,
    count = count,
    level = WorkloadLevel.fromRaw(level),
)

private fun DashTaskDto.toDomain(): DashboardTask = DashboardTask(
    key = key,
    summary = summary,
    status = jira,
    statusCategory = StatusCategory.fromStatus(jira),
    assignee = assignee?.name.orEmpty(),
    assigneeInitials = assignee?.init.orEmpty(),
    dueDate = due,
    active = active,
    overdue = overdue,
    stuck = stuck,
    isNew = isNew,
    url = jiraUrl,
)
