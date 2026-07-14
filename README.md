# QA Workspace (Android)

Native Android app cho QA Dashboard của team QA Bảo Kim. Front-end mới cho backend Python hiện tại (`qa_dashboard.py`) qua Cloudflare Tunnel — pull data live từ Jira **mà không cần VPN trên điện thoại** (host lo phần VPN/Jira).

## Stack
Kotlin · Jetpack Compose · Material 3 · Retrofit/OkHttp · Hilt · Room · DataStore · WorkManager.

## Yêu cầu
- Android Studio (mới nhất) — tự cài SDK khi mở lần đầu.
- JDK 17 (JBR đi kèm Android Studio là đủ).

## Chạy
```
./gradlew :app:assembleDebug
```
Hoặc mở project trong Android Studio và Run cấu hình `app`.

## Tài liệu
- `ANDROID_PORT_SPEC.md` — spec đầy đủ (domain Jira, feature, kiến trúc Hướng 2, endpoint).
- `BACKLOG.md` — backlog chia epic/task.
- `CLAUDE.md` — decisions + OPSEC + build notes.

> ⚠ Không commit secret. Xem OPSEC trong `CLAUDE.md`.
