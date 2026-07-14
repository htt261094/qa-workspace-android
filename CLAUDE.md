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
- **D6 (contract `/api/my-work`, E4/#7)**: Backend chưa xong (E0.2 `[BE]`, blocking #6). App CHỐT shape client-side: `{ok, buckets:[{key,label,tasks:[{key,summary,status,statusCategory,dueDate,assignee,project,url,overdue}]}]}`. Nguyên tắc theo D3 — **app không tự chia bucket / sort / tính overdue**; backend quyết bucket nào, thứ tự, sort due date, và cờ `overdue`; app render đúng thứ tự trả về (Room lưu `bucketOrder`/`taskOrder` để giữ nguyên order khi regroup). `ignoreUnknownKeys` → backend thêm field không vỡ build cũ. Nếu BE trả shape khác thì sửa DTO trong `data/mywork/MyWorkApi.kt` + mapper `MyWorkRepository`, domain/UI giữ nguyên. Cache offline (E4.4) = 1 bảng Room flat, replace-all mỗi refresh, KHÔNG chứa secret (OPSEC §7).

## OPSEC (NON-NEGOTIABLE — spec §7)
- KHÔNG hardcode PAT/secret vào code/resource. PAT → EncryptedSharedPreferences.
- Secret hạ tầng (CF token, service-account, PAT chung) KHÔNG BAO GIỜ vào APK — ở host.
- KHÔNG log PAT (kể cả một phần); redact trong mọi error/crash.
- `local.properties`, `*.keystore/*.jks`, `google-services.json` đã gitignore.
- KHÔNG tracking/analytics bên thứ ba.

## Phong cách
Peer tone, tiếng Việt/English, direct. Không sugar-coat, không follow-up thừa. Mỗi thay đổi kiến trúc → ghi Decision vào đây. "Làm issue #N" = checkout branch hậu tố `-<N>` rồi code.
