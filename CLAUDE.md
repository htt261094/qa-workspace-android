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
- **D2 (auth)**: CHƯA CHỐT. A = giữ session cookie HMAC + OkHttp CookieJar; B = thêm Bearer token cho mobile (recommend B: sạch, dễ debug, backend vốn đã phải thêm JSON endpoints). Chặn E2+. → cập nhật khi chốt.
- **D3 (nguồn chân lý)**: Logic đang ở JS client (`computeBacklog`, dedup fingerprint, analytics) đẩy về backend trả JSON — tránh parity Python↔Kotlin. App chỉ hiển thị.
- **D4 (build)**: Version catalog (`gradle/libs.versions.toml`) làm nguồn version duy nhất. Wrapper jar commit vào repo (bản chính thức v8.10.2).

## OPSEC (NON-NEGOTIABLE — spec §7)
- KHÔNG hardcode PAT/secret vào code/resource. PAT → EncryptedSharedPreferences.
- Secret hạ tầng (CF token, service-account, PAT chung) KHÔNG BAO GIỜ vào APK — ở host.
- KHÔNG log PAT (kể cả một phần); redact trong mọi error/crash.
- `local.properties`, `*.keystore/*.jks`, `google-services.json` đã gitignore.
- KHÔNG tracking/analytics bên thứ ba.

## Phong cách
Peer tone, tiếng Việt/English, direct. Không sugar-coat, không follow-up thừa. Mỗi thay đổi kiến trúc → ghi Decision vào đây. "Làm issue #N" = checkout branch hậu tố `-<N>` rồi code.
