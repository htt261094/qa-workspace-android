# QA Dashboard Android — Backlog

> Nguồn: `ANDROID_PORT_SPEC.md`. Kiến trúc Hướng 2: app native = client của backend Python hiện tại qua Cloudflare Tunnel. App KHÔNG gọi Jira trực tiếp, KHÔNG cần VPN.
> Granularity: mỗi task ~0.5–2 ngày. Dependency ghi `⇦ #id`. Task cross-repo (backend web) đánh dấu `[BE]`.

## Legend
- `[BE]` = làm ở repo web (`qa_dashboard.py`), không phải repo Android này
- `[DEC]` = task quyết định, cần chốt trước khi code phần phụ thuộc
- Priority: **P0** MVP-blocking · **P1** nên có · **P2** sau cùng

---

## E0 — Backend: tách JSON khỏi HTML render `[BE]` (P0, blocking)
Logic nghiệp vụ giữ nguyên, chỉ đổi output layer `render_*()` → `json.dumps()`.

- [ ] **E0.1** `[DEC]` Chốt cơ chế reuse handler: `?format=json` query vs header `Accept: application/json`. → quyết định shape mọi endpoint sau.
- [ ] **E0.2** `[BE]` `/api/my-work` — buckets (Active/Đang làm/TO DO/Done) + sort due. (endpoint đầu tiên, test bằng curl)
- [ ] **E0.3** `[BE]` `/api/dashboard` — buckets task + KPI (Active/Overdue/Kẹt/Vào-Ra tuần) + workload/người.
- [ ] **E0.4** `[BE]` `/api/bug-log` + `/api/analytics` — bug theo tháng + metric (Valid Bug Rate, Reopen, Tồn đọng T-1).
- [ ] **E0.5** `[BE]` `/api/roadmap`, `/api/docs`.
- [ ] **E0.6** `[BE]` Đẩy logic đang ở JS client (`computeBacklog`, dedup fingerprint, render analytics) về backend → trả JSON (một nguồn chân lý).
- [ ] **E0.7** `[BE]` `[DEC]` Auth cho mobile: giữ session cookie HMAC hay thêm Bearer token? (xem E2.1) — sửa backend nếu chọn Bearer + thêm redirect URI cho app.

## E1 — Skeleton project & build (P0)
- [ ] **E1.1** Init Gradle project: Kotlin + Compose + Material 3, minSdk/targetSdk, version catalog.
- [ ] **E1.2** Setup module structure (data / domain / ui) + DI (Hilt hoặc manual).
- [ ] **E1.3** Networking layer: Retrofit + OkHttp + kotlinx.serialization, base URL `baokim-qa.com`, interceptor logging (redact PAT).
- [ ] **E1.4** Bottom nav 3 tab (Dashboard / Việc của tôi / Bugs) + host cho màn chi tiết task. Placeholder screens.
- [ ] **E1.5** Theme Material 3 (light/dark) khớp UI "Stitch" hiện tại.
- [ ] **E1.6** DataStore (prefs/session) + EncryptedSharedPreferences (token nhạy cảm) wiring.

## E2 — Auth (P0) ⇦ E0.7, E1.3
- [ ] **E2.1** `[DEC]` Chốt luồng OAuth mobile (AppAuth vs Credential Manager) + cơ chế giữ session (CookieJar vs Bearer).
- [ ] **E2.2** Màn Login Google OAuth (gate `@baokim.vn`).
- [ ] **E2.3** Giữ session qua request (OkHttp CookieJar giữ `qa_session`, hoặc Authorization header).
- [ ] **E2.4** Phân quyền 3 role (admin / QA member / dev) — ẩn/hiện UI theo role.
- [ ] **E2.5** Logout + xử lý session hết hạn (401 → về login).

## E3 — PAT cá nhân (P0) ⇦ E2
- [ ] **E3.1** Màn nhập PAT + lưu qua `/save-pat`, đọc trạng thái `/has-pat`, xoá `/delete-pat`.
- [ ] **E3.2** Lưu PAT vào EncryptedSharedPreferences; redact trong log/crash (OPSEC mục 7).

## E4 — "Việc của tôi" (P0) ⇦ E0.2, E2, E3
- [x] **E4.1** Repository + model cho `/api/my-work`. (#7)
- [x] **E4.2** UI list task (Active/Đang làm/TO DO/Done), pill status, sort theo due. (#7 — buckets + sort do backend quyết, app render theo thứ tự trả về, D3)
- [x] **E4.3** Pull-to-refresh (F5 = pull tươi) + loading/error state. (#7)
- [x] **E4.4** Room cache offline cho list gần nhất. (#7)

## E5 — Chi tiết task (P0) ⇦ E4
- [ ] **E5.1** Màn detail: `/issue-comments` → mô tả + comment history.
- [ ] **E5.2** Gửi comment `/add-comment` (ghi bằng PAT cá nhân).
- [ ] **E5.3** Đổi status: `/jira-transitions` → `/do-transition`.
- [ ] **E5.4** Đổi due date: gate `/duedate-perm` → `/set-duedate`.
- [ ] **E5.5** Custom status overlay (8 nhãn, nhiều nhãn/task) `/set-custom-status`.

## E6 — Dashboard team admin (P0) ⇦ E0.3, E2.4
- [ ] **E6.1** Repository + model `/api/dashboard`.
- [ ] **E6.2** KPI cards (Active/Overdue/Kẹt/Vào-Ra tuần).
- [ ] **E6.3** Bảng task active/new/done + pill status.
- [ ] **E6.4** Workload/người + ngưỡng màu (≥15 quá tải / 5–14 OK / ≤4 nhẹ).

## E7 — Notification (P0) ⇦ E2
- [ ] **E7.1** WorkManager periodic poll `/activity-feed` (60s / interval hợp lý theo battery).
- [ ] **E7.2** Diff activity → NotificationManager push (ẩn noti do chính mình — backend đã lo).
- [ ] **E7.3** `/dismiss` khi user đọc noti. Xin quyền POST_NOTIFICATIONS (Android 13+).

## E8 — Bug Log + analytics (P1) ⇦ E0.4
- [ ] **E8.1** Repository + model `/api/bug-log` + `/api/analytics`.
- [ ] **E8.2** Bảng bug theo tháng + filter tester/dev.
- [ ] **E8.3** Chart analytics (số bug / Valid Bug Rate / Reopen / Tồn đọng T-1) — Vico hoặc Compose canvas.
- [ ] **E8.4** Link bug↔task `/link-task`, `/tc-link-task`.
- [ ] **E8.5** Export Excel `/export-bug-log` (download → xin quyền, xem OPSEC).
- [ ] **E8.6** Room cache bug log offline.

## E9 — Tạo QA sub-task (P1) ⇦ E5
- [ ] **E9.1** UI chọn Task-PTSP cha (`/search-parents`) + auto-fill `[QA] ` + Leader Hiền.
- [ ] **E9.2** Tạo 1 `/create-subtask` / nhiều `/create-subtasks` cùng lúc.

## E10 — Search / command palette (P1) ⇦ E2
- [ ] **E10.1** Global search `/global-search` (+ `/search-bugs`, `/search-tasks`, `/search-people`).
- [ ] **E10.2** UI command palette / quick search overlay.

## E11 — Roadmap + Docs (P2) ⇦ E0.5
- [ ] **E11.1** Roadmap: giai đoạn › mục › sub-task, % tự tính, cảnh báo hạn ≤14 ngày. Đọc `/api/roadmap`, ghi `/save-roadmap`.
- [ ] **E11.2** Docs: cây folder + link Google Drive `/api/docs`, `/save-docs`, upload `/upload-file`.
- [ ] **E11.3** `[DEC]` KV chỉ 1 writer — mọi ghi roadmap/docs đi qua host (không cho app làm writer thứ 2).

## X — Cross-cutting (xuyên suốt)
- [ ] **X.1** OPSEC checklist: không hardcode secret, không log PAT, redact crash, không tracking bên thứ 3.
- [ ] **X.2** CLAUDE.md project: ghi mỗi Decision kiến trúc.
- [ ] **X.3** Error/empty/offline state chuẩn cho mọi màn.
- [ ] **X.4** CI build + lint (tuỳ chọn).

---

## Thứ tự thực thi đề xuất (theo spec mục 9)
1. E0.1 → E0.2 (backend `/api/my-work`) + E0.7/E2.1 (chốt auth) — **làm trước tiên**
2. E1 skeleton
3. E2 auth → E3 PAT
4. E4 Việc của tôi (giá trị ngay, đơn giản nhất)
5. E5 chi tiết task
6. E7 notification
7. E0.3 → E6 dashboard admin
8. E0.4 → E8 bug log + analytics
9. E9 sub-task, E10 search
10. E11 roadmap + docs
