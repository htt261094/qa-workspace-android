package vn.baokim.qa.domain.buglog

/**
 * UI-facing model for the analytics screen (E8.3, #13).
 *
 * Backend `/api/analytics` (D10) does **all** the math — Valid/Rejected Bug Rate, Reopen
 * quality, cross Task×TC×Bug metrics, per-dev/per-project bug counts (D3, one source of
 * truth to avoid a Python↔Kotlin parity trap). The app only picks a month and renders; it
 * never re-derives a rate. Percentages arrive pre-computed and nullable (backend sends
 * `null` when the denominator is 0) — the UI shows "—" for those.
 *
 * DEV role can't reach this (backend answers 403, mirroring the web) — the Bugs screen hides
 * the Analytics tab for dev (E2.4 / D5).
 */
data class Analytics(
    val syncedAt: String,
    val months: List<String>,             // newest-first (month picker)
    val cross: CrossMetrics,              // NOT month-specific
    val byMonth: Map<String, MonthMetrics>,
) {
    companion object {
        val EMPTY = Analytics("", emptyList(), CrossMetrics(), emptyMap())
    }

    fun metrics(month: String): MonthMetrics? = byMonth[month]
}

/** Task×TestCase×Bug coverage snapshot (not per-month). `pct`/`value` null when N/A. */
data class CrossMetrics(
    val coveragePct: Double? = null,      // % task có test case
    val coverageTasksWithTc: Int = 0,
    val coverageTotalTasks: Int = 0,
    val densityValue: Double? = null,     // bug / task
    val densityTotalBugs: Int = 0,
    val executionPct: Double? = null,     // (pass+fail)/total
    val passRate: Double? = null,         // pass/(pass+fail)
    val execPass: Int = 0,
    val execFail: Int = 0,
    val execTotal: Int = 0,
)

data class MonthMetrics(
    val grand: Int,                       // real bug count (fp-deduped) for the month
    val valid: ValidRate,
    val reopen: ReopenSummary,
    val backlog: BacklogSummary,
    /** Per-dev total bug count (summed over projects; backend splits multi-dev bugs 1/n). */
    val bugsPerDev: List<DevBugs>,
)

/** Valid Bug Rate = Closed / (Total − Reject); Rejected Rate = Reject / Total. */
data class ValidRate(
    val total: Int,
    val reject: Int,
    val closed: Int,
    val validPct: Double?,
    val rejectPct: Double?,
)

data class ReopenSummary(
    val totalBugs: Int,
    val distinctReopened: Int,
    val devs: List<DevReopen>,            // sorted by reopen count desc
)

/**
 * Per-dev reopen quality. `reopened` = distinct bugs the dev had reopened; `fixes` = total
 * fix attempts (reopen+1 heuristic, backend-computed); `bugs` = the dev's bug denominator.
 */
data class DevReopen(
    val name: String,
    val reopened: Double,
    val fixes: Double,
    val bugs: Double,
)

/** Carry-over from month T-1 vs newly-created this month (backend `computeBacklog`). */
data class BacklogSummary(
    val prevMonth: String,
    val total: Int,
    val resolved: Int,
    val stillOpen: Int,
    val newCount: Int,
    val hasSnapshot: Boolean,
)

data class DevBugs(val name: String, val count: Double)
