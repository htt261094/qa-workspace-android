package vn.baokim.qa.data.buglog

import retrofit2.HttpException
import vn.baokim.qa.domain.buglog.Analytics
import vn.baokim.qa.domain.buglog.BacklogSummary
import vn.baokim.qa.domain.buglog.CrossMetrics
import vn.baokim.qa.domain.buglog.DevBugs
import vn.baokim.qa.domain.buglog.DevReopen
import vn.baokim.qa.domain.buglog.MonthMetrics
import vn.baokim.qa.domain.buglog.ReopenSummary
import vn.baokim.qa.domain.buglog.ValidRate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of an analytics fetch (E8.3). The endpoint depends on the bug-log cache and is
 * dev-forbidden, so failure modes are distinct (D10): [Forbidden] (403, dev), [Unavailable]
 * (503, bug-log source down) get their own message; everything else is [Error].
 */
sealed interface AnalyticsResult {
    data class Success(val data: Analytics) : AnalyticsResult
    data object Forbidden : AnalyticsResult    // 403 — dev role
    data object Unavailable : AnalyticsResult  // 503 — bug-log source unavailable
    data object Error : AnalyticsResult
}

/**
 * Fetches analytics (E8.3, #13). Network-only — the numbers are cheap KPIs the backend
 * computes on demand (D3), and DEV can't reach the endpoint at all, so there's no offline
 * cache here (unlike the bug list, E8.6). Every rate arrives pre-computed; this layer only
 * reshapes the dynamic-keyed maps into ordered lists for the UI.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val api: BugLogApi,
) {

    suspend fun load(): AnalyticsResult = try {
        val response = api.analytics()
        if (response.ok) AnalyticsResult.Success(response.toDomain()) else AnalyticsResult.Error
    } catch (e: HttpException) {
        when (e.code()) {
            403 -> AnalyticsResult.Forbidden
            503 -> AnalyticsResult.Unavailable
            else -> AnalyticsResult.Error
        }
    } catch (e: Exception) {
        AnalyticsResult.Error
    }
}

// --- mappers -----------------------------------------------------------------

private fun AnalyticsResponse.toDomain(): Analytics = Analytics(
    syncedAt = syncedAt,
    months = months,
    cross = crossMetrics.toDomain(),
    byMonth = metrics.mapValues { (_, m) -> m.toDomain() },
)

private fun CrossMetricsDto.toDomain(): CrossMetrics = CrossMetrics(
    coveragePct = coverage.pct,
    coverageTasksWithTc = coverage.tasksWithTc,
    coverageTotalTasks = coverage.totalTasks,
    densityValue = density.value,
    densityTotalBugs = density.totalBugs,
    executionPct = execution.execPct,
    passRate = execution.passRate,
    execPass = execution.pass,
    execFail = execution.fail,
    execTotal = execution.total,
)

private fun MonthMetricsDto.toDomain(): MonthMetrics = MonthMetrics(
    grand = grand,
    valid = ValidRate(valid.total, valid.reject, valid.closed, valid.validPct, valid.rejectPct),
    reopen = ReopenSummary(
        totalBugs = reopen.totalBugs,
        distinctReopened = reopen.distinctTotal,
        devs = reopen.devs.map { (name, d) -> DevReopen(name, d.nb, d.fx, d.denom) }
            .sortedByDescending { it.reopened },
    ),
    backlog = BacklogSummary(
        prevMonth = backlog.prev,
        total = backlog.total,
        resolved = backlog.resolved,
        stillOpen = backlog.stillOpen,
        newCount = backlog.newCount,
        hasSnapshot = backlog.hasSnapshot,
    ),
    // sum each dev's per-project fractions into one total, biggest first (bar chart order)
    bugsPerDev = chart.map { (dev, byProj) -> DevBugs(dev, byProj.values.sum()) }
        .sortedByDescending { it.count },
)
