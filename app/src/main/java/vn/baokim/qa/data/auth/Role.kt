package vn.baokim.qa.data.auth

/**
 * Access role of the logged-in user (E2.4, spec §2 team table + §3 MVP #1).
 *
 * The server-brokered session token (D2 hướng C) is HMAC-signed and self-contained
 * but carries only `{email, iat, exp}` — no role claim — and the backend exposes no
 * role endpoint yet. So the app maps email→role here, in the single place that
 * mapping lives. If the backend later adds a `role` claim to the token or an
 * `/api/me` endpoint, swap [fromEmail] for that and every UI gate below is unchanged.
 *
 * Not a security boundary: the backend still enforces authorization on every write
 * (a forged role only reshuffles which tabs render locally; mutating calls 401/403
 * server-side). This gate exists for UX — don't show a dev buttons they can't use.
 */
enum class Role {
    /** Manager / acting manager — full access, incl. the team Dashboard (spec §3.3). */
    ADMIN,

    /** QA tester — personal work + bug log with write (comment/status/link…). */
    QA,

    /** Developer — read-only lens: chỉ "Việc của tôi" + Bug Log read-only + export. */
    DEV;

    /** Team Dashboard (E6) is admin-only. */
    val canSeeDashboard: Boolean get() = this == ADMIN

    /** DEV may view the bug log but not mutate it; export stays allowed for everyone. */
    val bugLogReadOnly: Boolean get() = this == DEV

    /** Whether the user may write to Jira / bug log at all (comment, transition, link, custom-status). */
    val canWrite: Boolean get() = this != DEV

    companion object {
        // Team table, spec §2. Managers get the Dashboard; the dev gets the read-only lens.
        // Everyone else who passed the `@baokim.vn` OAuth gate is a QA member.
        private val ADMIN_EMAILS = setOf("thanhht1@baokim.vn", "hiennt19@baokim.vn")
        private val DEV_EMAILS = setOf("haumv@baokim.vn")

        /** Default when the email is unknown or absent: least-surprise QA member (never ADMIN). */
        val DEFAULT: Role = QA

        /** Maps a login email to its role. Case/space-insensitive; unknown → [DEFAULT]. */
        fun fromEmail(email: String?): Role {
            val e = email?.trim()?.lowercase() ?: return DEFAULT
            return when (e) {
                in ADMIN_EMAILS -> ADMIN
                in DEV_EMAILS -> DEV
                else -> DEFAULT
            }
        }
    }
}
