package vn.baokim.qa.domain.buglog

/**
 * UI-facing model for the Bug Log (E8, #13).
 *
 * Backend `/api/bug-log` (D10) returns a **flat** list of bugs sourced from the QA team's
 * Excel-on-Drive cache plus the app-side task links — no server-side month bucketing. So the
 * app groups by [month] client-side for the month tabs and derives the tester/dev filter
 * option lists from the rows (thin logic, kept in one place). The KPI/analytics math is NOT
 * done here — that lives entirely on the backend `/api/analytics` (D3), see [Analytics].
 */
data class Bug(
    val key: String,              // bug row key (Excel row id) — used for `/link-task`
    val id: String,               // display id, e.g. "PSIT-BE-123"
    val summary: String,
    val module: String,
    val severity: String,
    val status: String,           // raw bug status text (Vietnamese/English, free-form)
    val project: String,
    val month: String,            // sheet-month "yyyy-MM" (drives the month tabs)
    val tester: String,           // QA PIC (backend `qa`, normalised)
    val dev: String,              // dev in charge (backend `dev`)
    val created: String,          // yyyy-MM-dd
    val tasks: List<String>,      // linked Jira task keys (app-side links, E8.4)
) {
    val isLinked: Boolean get() = tasks.isNotEmpty()
}

/**
 * A month tab's worth of bugs. Months are newest-first (backend order); each holds the bugs
 * whose [Bug.month] matches, in backend order.
 */
data class BugMonth(
    val month: String,
    val bugs: List<Bug>,
)

/**
 * Filter over the bugs of the currently-selected month (E8.2). All three are AND-combined;
 * blank/[LinkFilter.ALL] means "don't filter on this axis". The option lists shown in the UI
 * are derived from the month's bugs (see `BugLogViewModel`).
 */
data class BugFilter(
    val tester: String = "",
    val dev: String = "",
    val link: LinkFilter = LinkFilter.ALL,
) {
    fun apply(bugs: List<Bug>): List<Bug> = bugs.filter { bug ->
        (tester.isBlank() || bug.tester == tester) &&
            (dev.isBlank() || bug.dev == dev) &&
            link.matches(bug)
    }

    val isActive: Boolean get() = tester.isNotBlank() || dev.isNotBlank() || link != LinkFilter.ALL
}

enum class LinkFilter(val matches: (Bug) -> Boolean) {
    ALL({ true }),
    LINKED({ it.isLinked }),
    UNLINKED({ !it.isLinked });
}
