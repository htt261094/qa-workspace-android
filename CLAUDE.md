# CLAUDE.md — QA Workspace Android

Native Android front-end cho QA Dashboard team Bảo Kim. Client của backend Python hiện tại (`qa_dashboard.py`) qua Cloudflare Tunnel. **App KHÔNG gọi Jira trực tiếp, KHÔNG cần VPN** (Hướng 2 — xem `ANDROID_PORT_SPEC.md`).

## Build / run
- Mở bằng Android Studio (JBR bundled). Lần đầu Studio sẽ tự cài Android SDK (compileSdk 35).
- CLI: `./gradlew :app:assembleDebug` (cần `local.properties` trỏ `sdk.dir`, hoặc `ANDROID_HOME`).
- minSdk 26 · targetSdk/compileSdk 35 · JVM 17 · Kotlin 2.0.21 · AGP 8.7.2 · Gradle 8.10.2.

## Kiến trúc
- **UI**: Jetpack Compose + Material 3. Bottom nav 3 tab (Dashboard / Việc của tôi / Bugs), start = "Việc của tôi".
- **Networking**: Retrofit + OkHttp + kotlinx.serialization. Base URL `https://baokim-qa.com/` (BuildConfig.BASE_URL).
- **DI**: Hilt.
- **Storage**: DataStore (prefs/session) · EncryptedSharedPreferences (PAT/token) · Room (cache offline).
- **Background**: WorkManager (poll `/activity-feed` → notification).
- Package: `vn.baokim.qa`. Module: `:app` (sẽ tách data/domain/ui khi lớn — E1.2).

## Backlog
Xem `BACKLOG.md` — 11 epic, thứ tự thực thi ở cuối file.

## Decisions
- **D1 (kiến trúc)**: Hướng 2 — app là client backend, không gọi Jira/KV/Drive trực tiếp. Lý do: Jira sau VPN nội bộ; nhét VPN vào điện thoại UX tệ + phụ thuộc IT. Secret hạ tầng không rời host.
- **D2 (auth) — CHỐT: Hướng C, server-brokered token handoff.** App KHÔNG tự chạy OAuth (không cầm client_secret). Server làm full OAuth (như web) rồi giao chính token HMAC self-contained hiện có (`make_session_token` = `b64(json{email,iat,exp}).sig`) cho app qua redirect. App gửi `Authorization: Bearer <token>`; backend `_user_email()` đọc Bearer → `email_from_session()`. Tái dùng nguyên crypto, không hệ token mới. Google console không đổi.
  - **D2a — transport = App Links (https verified), KHÔNG raw custom scheme.** Raw scheme `vn.baokim.qa://` bị app khác cướp được; App Links verify qua `/.well-known/assetlinks.json` trên `baokim-qa.com`. Token đi trong URL **fragment** `#token=` (không vào server access log phía app).
  - Backend delta (4 chỗ): `config.py` (+APP_REDIRECT/APP_LINK_HOST), `auth.py` (state mang cờ `app` + `state_data()`), `routes/oauth.py` (`_do_login` nhận `?app=1`, `_do_callback` rẽ nhánh app→redirect token), `qa_dashboard.py::_user_email` (đọc Bearer trước cookie).
  - App: Custom Tabs + intent-filter App Links (KHÔNG cần AppAuth-Android vì server broker). NetworkModule swap `cookieJar(...)` → AuthInterceptor gắn Bearer.
  - Sliding refresh app (optional): response header `X-Session-Token` khi token quá nửa đời; else 401 → re-login.
- **D3 (nguồn chân lý)**: Logic đang ở JS client (`computeBacklog`, dedup fingerprint, analytics) đẩy về backend trả JSON — tránh parity Python↔Kotlin. App chỉ hiển thị.
- **D4 (build)**: Version catalog (`gradle/libs.versions.toml`) làm nguồn version duy nhất. Wrapper jar commit vào repo (bản chính thức v8.10.2).
- **D5 (role, E2.4)**: Session token chỉ mang `{email,iat,exp}`, KHÔNG có role claim; backend chưa có role endpoint. Nên app đọc `email` từ payload token (không verify chữ ký — backend verify) rồi map email→`Role` (ADMIN/QA/DEV) tại **một chỗ duy nhất** `Role.fromEmail` theo team table (spec §2). Gate UI: Dashboard = ADMIN; MyWork+Bugs = mọi role; DEV = Bug Log read-only + export. **KHÔNG phải security boundary** — mọi write vẫn do backend enforce (401/403); gate chỉ để UX. Nếu sau này backend thêm `role` claim / `/api/me` thì chỉ đổi `Role.fromEmail`, UI giữ nguyên.
- **D7 (contract `/api/dashboard`, E6/#8 — backend đã làm)**: Endpoint JSON dashboard team admin đã ship ở backend (`build_dashboard_payload` + `_get_api_dashboard`, PR feat/api-dashboard-8). Shape (verify server live, 535 task/5 member/11 activity từ snapshot): `{ok, stale, tasks:[…], meta, members:[name…], workload:[{name,init,cls,count,level}], activities:[…]}`. **ADMIN only** (D5) → 403 nếu không admin, 503 khi Jira down. `tasks` = flat list team-wide, mỗi task có `active`(bool), `jira`(raw status), `overdue`, `stuck`, `isNew`, `assignee:{name,init,cls}`, `hasTc`, `customs`, `due/dueDisp/dueCls`, `created/updated`, `jiraUrl` (cùng bộ field admin web dùng). `meta` = KPI đếm sẵn: `{active, todo, progress, new, stuck, overdue, done, resolvedWeek, createdWeek}` — map spec §3.4: Active=`active`, Overdue=`overdue`, Kẹt=`stuck`, Vào tuần=`createdWeek`, Ra tuần=`resolvedWeek`. `workload` = số task ACTIVE/người + `level` (`over`≥15 / `ok` 5–14 / `light`≤4, ngưỡng spec §2 backend tính sẵn — app KHÔNG tự tính lại). KHÁC D6: dashboard **counts/buckets do backend đếm sẵn trong `meta`** (đúng D3), app chỉ filter tasks theo pill client-side (todo/progress/new/stuck/overdue/done) nếu cần bảng — không phải re-derive KPI. `ignoreUnknownKeys` drop field render-only (dueDisp/updatedDisp/canCustom…). Cache offline (nếu làm) tương tự E4.4: Room flat, replace-all, KHÔNG secret.
- **D6 (contract `/api/my-work`, E4/#7)**: Shape THẬT (verify với server live): **flat list**, KHÔNG có buckets — `{ok, stale, tasks:[{key, summary, jira, due, overdue, stuck, dueWeek, assignee:{name,init,cls}, jiraUrl, hasTc, customs, nComments, …}]}`. Status ở field `jira` (raw Jira status), due ở `due`. Backend KHÔNG chia bucket / KHÔNG gửi `statusCategory`. → Trái với kỳ vọng D3 (backend chưa làm phần này), nên app **buộc phải group client-side**: `MyWorkBuckets.group()` map status→bucket (Active/Đang làm/TO DO/Done, spec §3.4) + sort due tăng dần, và `StatusCategory.fromStatus()` derive category từ status cho màu pill. Cả 2 để **một chỗ** trong `domain/mywork/MyWork.kt` — logic thin, cố ý gom lại. `ignoreUnknownKeys` drop field render-only. **TODO backend (E0.2)**: lý tưởng `/api/my-work` nên trả sẵn buckets + statusCategory + sort để đúng D3; khi đó chỉ đổi `MyWorkApi` DTO + bỏ `MyWorkBuckets`, domain/UI giữ nguyên. Cache offline (E4.4) = 1 bảng Room flat lưu kết quả đã group (`bucketOrder`/`taskOrder`), replace-all mỗi refresh, KHÔNG chứa secret (OPSEC §7). Đổi schema entity phải bump `AppDatabase.version` (fallbackToDestructiveMigration chỉ chạy khi version tăng).

## OPSEC (NON-NEGOTIABLE — spec §7)
- KHÔNG hardcode PAT/secret vào code/resource. PAT → EncryptedSharedPreferences.
- Secret hạ tầng (CF token, service-account, PAT chung) KHÔNG BAO GIỜ vào APK — ở host.
- KHÔNG log PAT (kể cả một phần); redact trong mọi error/crash.
- `local.properties`, `*.keystore/*.jks`, `google-services.json` đã gitignore.
- KHÔNG tracking/analytics bên thứ ba.

## Phong cách
Peer tone, tiếng Việt/English, direct. Không sugar-coat, không follow-up thừa. Mỗi thay đổi kiến trúc → ghi Decision vào đây. "Làm issue #N" = checkout branch hậu tố `-<N>` rồi code.
