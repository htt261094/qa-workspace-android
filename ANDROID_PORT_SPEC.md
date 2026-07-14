# QA Dashboard → Android — Bản mô tả & tóm tắt để dựng lại project

> Tài liệu handoff: mô tả toàn bộ project QA Dashboard hiện tại (Python + web) và cách chuyển sang **app Android native**. Mang file này sang project mới làm điểm khởi đầu.
>
> **Kiến trúc chốt: Hướng 2 — App Android là native front-end MỚI, dùng chung backend Python hiện tại (qua Cloudflare Tunnel). App KHÔNG gọi Jira trực tiếp, KHÔNG cần VPN trên điện thoại.** Xem mục 4.

---

## 1. Project này là gì

Dashboard quản lý cho **team QA Bảo Kim** (5 QA + 1 manager), pull data **live từ Jira** (Data Center 10.7.3, `jira.baokim.vn:8443`) qua REST API. Thay thế Jira native dashboard. User là **Acting QA Manager** (Thành / `thanhht1`), quản lý team trong lúc QA Manager (Hiền) nghỉ thai sản.

Mục tiêu: briefing hàng ngày + quản lý acting + theo dõi bug log + roadmap + tài liệu, tất cả 1 chỗ, không phải mở Jira.

### Hiện tại (web) chạy thế nào
```
[Browser bất kỳ] ──internet──→ [Cloudflare Tunnel baokim-qa.com] + Google OAuth
                                        │
                          [Máy Windows host — ĐANG Ở TRONG VPN công ty]
                            (chạy qa_dashboard.py, localhost:8080)
                                        │  ←── chỉ CHỖ NÀY cần VPN
                                  [Jira jira.baokim.vn:8443]
```
- Python `http.server` stdlib (KHÔNG Flask), server-side render HTML bằng string template, vanilla JS (KHÔNG framework).
- **Browser KHÔNG gọi Jira** — máy host (trong VPN) gọi hộ. Browser chỉ nói chuyện với host qua Cloudflare Tunnel.
- F5 = pull data tươi từ Jira (host làm), có cache SWR.

---

## 2. Domain / dữ liệu Jira (GIỮ NGUYÊN)

### Instance
- URL: `https://jira.baokim.vn:8443`, Jira **Data Center 10.7.3**
- Auth: **PAT** qua header `Authorization: Bearer <token>` (KHÔNG phải Basic/Cookie)
- REST: `/rest/api/2/search`, `/rest/api/2/issue/{key}`, `/rest/api/2/user/properties/{key}`

### Workflow statuses (chính xác case + spacing)
`TO DO` · `In Progress` · `PENDING` · `DONE` · `CANCELLED`
- Filter theo **statusCategory**: `new` (TO DO) · `indeterminate` (In Progress, PENDING) · `done` (DONE, CANCELLED).

### Team (username → tên → role)
| Username | Tên | Role |
|---|---|---|
| `quangbm` | Quang | QA |
| `nhungnh` | Nhung | QA |
| `phuongct` | Phương | QA |
| `tholt` | Thơ | QA |
| `thanhht1` | Thành | QA (acting manager / admin) |
| `hiennt19` | Hiền | QA Manager (maternity leave) |

- Track workload theo **assignee**.
- Project keys hay gặp: `PSIT1H26`, `DA51H26`, `DA61H26`, `DA2B` — KHÔNG hardcode, filter theo assignee.
- Task summary convention: `[QA] <mô tả>`.
- Role thứ 3 **dev** (`haumv@baokim.vn`): chỉ xem "Việc của tôi" + Bug Log read-only + export.

### Ngưỡng & metric nghiệp vụ (KHÔNG tự đổi)
- Workload: **≥15 QUÁ TẢI / 5–14 OK / ≤4 NHẸ**.
- **Kẹt**: task in-flight (không phải TO DO) mà `updated` ≥ **5 ngày**.
- **Vào/Ra tuần**: created tuần này vs status CHANGED TO DONE tuần này. Vào > Ra → backlog phình.
- Bucket done: `status = "DONE" ORDER BY updated DESC`. Bucket active: `statusCategory != Done`.

---

## 3. Tính năng cần port (checklist)

### MVP (must-have)
1. **Login Google OAuth** (gate `@baokim.vn`) + phân quyền 3 role: **admin** / **QA member** / **dev**.
2. **Nhập & lưu PAT cá nhân** — dùng để ghi Jira đúng tên người.
3. **Dashboard team (admin)**: bảng task active/new/done + KPI (Active, Overdue, Kẹt, Vào/Ra tuần) + pill status + workload per người.
4. **Lens cá nhân "Việc của tôi"**: task của chính mình (Active / Đang làm / TO DO / Done), sort theo due date.
5. **Chi tiết task**: mô tả + comment history + gửi comment + đổi status + đổi due date + nhãn custom — ghi bằng PAT cá nhân.
6. **Notification**: poll định kỳ → push notification khi có activity mới. Ẩn noti do chính mình gây ra.

### Nên có
7. **Custom status overlay** (8 nhãn tình trạng: "Dev fix bug", "Chờ BA confirm"…, nhiều nhãn/task).
8. **Tạo QA sub-task** dưới Task-PTSP (1 hoặc nhiều cùng lúc, auto-fill `[QA] ` + Leader Hiền).
9. **Bug Log**: bảng bug theo tháng, filter tester/dev, export Excel, link bug↔task, chart analytics (số bug, Valid Bug Rate, Reopen, Tồn đọng T-1).
10. **Command palette / tìm kiếm nhanh** (task + bug).

### Sau cùng
11. **Roadmap** (giai đoạn › mục › sub-task, % tự tính, cảnh báo hạn ≤14 ngày).
12. **Tài liệu** (cây folder + link Google Drive + upload file).
13. **Report tháng** gửi CTO qua Google Chat webhook — **giữ nguyên ở server/cron**, không cần trong app.

---

## 4. Kiến trúc Android — HƯỚNG 2 (đã chốt)

### Vì sao KHÔNG gọi Jira trực tiếp
Jira `jira.baokim.vn:8443` là **internal, gate sau VPN công ty**. Web né được vì **máy host chạy trong VPN gọi hộ**, browser chỉ chạm host qua Cloudflare Tunnel. Nếu app Android gọi thẳng Jira thì **điện thoại phải cài + bật VPN công ty mỗi lần dùng** → phụ thuộc IT, UX tệ, rớt VPN là chết. → Loại.

### App = client của backend hiện tại
```
[Android app] ──HTTPS + Google OAuth──→ [baokim-qa.com / Cloudflare Tunnel]
                                                │
                                    [Máy Windows host trong VPN]
                                    (qa_dashboard.py — GIỮ NGUYÊN)
                                       ├─→ Jira REST (PAT chung + PAT cá nhân)
                                       ├─→ Cloudflare KV (roadmap/docs/custom-status/PAT)
                                       └─→ Google Drive (bug log) + Google Chat (report)

App local: DataStore (prefs/session) + Room (cache offline) + WorkManager (poll → notification)
```

- **Điện thoại KHÔNG cần VPN.** Host lo hết phần VPN/Jira/KV/Drive — y hệt cách browser đang hoạt động.
- Backend **đã internet-reachable** qua tunnel + Google OAuth. App chỉ cần: login OAuth → gọi endpoint JSON.
- **Không nhúng secret vào APK**: CF_API_TOKEN, service-account key, PAT chung… đều ở host, không rời server. (Đây là lý do hướng 2 an toàn hơn hẳn việc app tự cầm token.)
- Phụ thuộc **host luôn chạy + luôn trong VPN** — giống hiện tại (host tắt thì web cũng chết), không tệ hơn.

### Việc backend cần bổ sung (nhỏ)
Server hiện render **HTML** cho các trang chính. Cần thêm biến thể trả **JSON thuần** để app tiêu thụ (tách data khỏi render):
- `/api/dashboard` (thay trang `/` — trả buckets + KPI + workload data)
- `/api/my-work` (thay `/my-work`)
- `/api/bug-log` + `/api/analytics` (thay `/bug-log`)
- `/api/roadmap`, `/api/docs`

Logic nghiệp vụ (`fetch_all`, fingerprint, backlog, analytics…) **giữ nguyên** — chỉ đổi lớp output từ `render_*()` sang `json.dumps()`. Cân nhắc thêm `?format=json` hoặc header `Accept: application/json` để tái dùng handler.

### Endpoint JSON ĐÃ CÓ (app dùng lại gần như nguyên)
| Endpoint | Method | Việc |
|---|---|---|
| `/login`, `/oauth/callback`, `/logout` | GET | Google OAuth flow |
| `/activity-feed` | GET | Poll notification (60s) → `{activities, tasks}` |
| `/issue-comments` | GET | Comment history + detail 1 issue |
| `/global-search`, `/search-bugs`, `/search-tasks`, `/search-parents`, `/search-people` | GET | Tìm kiếm nhanh |
| `/has-pat`, `/save-pat`, `/delete-pat` | GET/POST | Quản lý PAT cá nhân |
| `/jira-transitions`, `/do-transition` | POST | Lấy + thực hiện đổi status |
| `/add-comment` | POST | Gửi comment |
| `/duedate-perm`, `/set-duedate` | POST | Gate quyền + đổi due date |
| `/set-custom-status` | POST | Nhãn tình trạng overlay |
| `/create-subtask`, `/create-subtasks` | POST | Tạo 1 / nhiều sub-task |
| `/dismiss` | POST | Đánh dấu đã đọc notification |
| `/export-bug-log` | POST | Xuất Excel bug log |
| `/link-task`, `/tc-link-task` | POST | Link bug↔task |
| `/save-roadmap`, `/save-docs`, `/upload-file` | POST | Lưu roadmap/docs/file |
| `/sync-bug-log`, `/save-bug-log-sources` | POST | Đồng bộ bug log từ Drive |

> Nguồn chuẩn: xem `do_GET`/`do_POST` trong `qa_dashboard.py` (dispatch quanh dòng 383–947).

### Stack đề xuất (app)
| Layer | Android |
|---|---|
| UI | **Kotlin + Jetpack Compose** (Material 3 — hợp UI "Stitch" hiện tại) |
| Networking | **Retrofit + OkHttp** + kotlinx.serialization |
| Auth | **AppAuth-Android** / Credential Manager (Google Sign-In), giữ session cookie/token của server |
| Lưu session/prefs | **DataStore**; token/secret nhạy cảm → **EncryptedSharedPreferences** |
| Cache offline | **Room** (bug log, task list gần nhất) |
| Poll notification | **WorkManager** periodic → gọi `/activity-feed` → `NotificationManager` |
| Async | **Coroutines** (`coroutineScope { async {} }` thay `ThreadPoolExecutor`) |

### Auth trên mobile — lưu ý
Server dùng **session cookie HMAC** (Decision #15). App không có cookie jar tự nhiên như browser → dùng OkHttp `CookieJar` để giữ `qa_session`, hoặc bàn với backend đổi sang **Bearer token** cho client mobile (thêm header `Authorization`). OAuth redirect URI cần thêm scheme cho app (custom scheme / App Links) bên cạnh `https://baokim-qa.com/oauth/callback`.

---

## 5. Logic nghiệp vụ nằm ở BACKEND (app không cần tự tính)

Các thuật toán dưới đây **ở server**, app chỉ hiển thị. Liệt kê để hiểu data, KHÔNG port lại:
- **Fingerprint bug** = `project|service|feature|summary` (lower + gộp khoảng trắng + trim, **KHÔNG bỏ dấu**). Định danh bug bền qua copy sang sheet tháng mới.
- **Tồn đọng T-1** tính live theo fingerprint + tín hiệu "carried" (bug có bản ở sheet `T<tháng>`).
- **Số lần fix reopen** = `count + (1 nếu status ∈ {Fixed, Closed})`.
- **Analytics bucket theo SHEET tháng (Tn)**, KHÔNG theo created date.
- **is_open bug** = status ∉ {Closed, Reject}. `Fixed` vẫn "còn mở".
- **Valid Bug Rate** dedup theo fingerprint; **Reopen** giữ RAW.
- **Freeze metric tháng đã đóng**: past = snapshot, current = live.
- **Ghi Jira** bằng **PAT cá nhân** người login (attribution đúng tên); gate due date bằng `editmeta`.

> ⚠ Một số logic HIỆN đang ở JS client (`app_v2.js`): `computeBacklog`, dedup fingerprint, render analytics chart. Khi bỏ HTML/JS, các phần này **phải chuyển về backend** (thành JSON) hoặc port sang Kotlin. Ưu tiên đẩy về backend để giữ một nguồn chân lý (parity Python↔JS vốn đã là điểm dễ vỡ — xem Decision #36/#46/#49).

---

## 6. Cloudflare KV & Google Drive — vẫn ở host

- **Cloudflare Workers KV** (`core/remote_store.py`) là kho sync chéo máy cho roadmap/docs/custom-status/PAT — gọi qua REST công cộng (KHÔNG cần VPN). Jira property chỉ là fallback khi không set CF creds. **App KHÔNG chạm KV trực tiếp** (tránh nhúng CF token vào APK) → đi qua endpoint server.
  - Nếu sau này muốn app đọc KV trực tiếp (vd để đọc roadmap khi host tắt): dựng **Cloudflare Worker proxy** gate bằng OAuth JWT, KHÔNG bỏ CF token thô vào app.
  - ⚠ KV thiết kế cho **1 writer tại 1 thời điểm** (host-migration, last-write-wins, không timestamp). Thêm app làm writer thứ 2 song song có thể mất edit — cứ để **ghi qua host** để giữ 1 writer.
- **Bug Log đọc Google Drive/Sheet** cần service account → **giữ ở host** (`bug_log*.py`), app lấy JSON đã scan.
- **Report tháng Google Chat webhook**: Windows Scheduled Task + `.ps1` — giữ nguyên.

---

## 7. OPSEC (NON-NEGOTIABLE)
- KHÔNG hardcode PAT/secret vào code hay resource — dùng EncryptedSharedPreferences/Keystore; secret hạ tầng (CF token, service-account) **không bao giờ vào APK** (ở host).
- KHÔNG log PAT (kể cả một phần) — redact trong mọi error/crash report.
- KHÔNG commit secret/config thật lên git.
- Redact PAT trước khi ném exception / gửi crash analytics.
- KHÔNG thêm tracking/analytics bên thứ ba.

---

## 8. Phong cách làm việc với user
- Peer tone, tiếng Việt hoặc English, direct. KHÔNG "Here is…"/"I'll help you…".
- KHÔNG sugar-coat, KHÔNG follow-up thừa. Options nhiều → A/B/C rõ ràng.
- User hiểu sâu kỹ thuật (LLM internals, mobile) — không simplify.
- Trước khi recommend: (1) reverse thinking "approach này fail thế nào?", (2) critical thinking "có cách hiểu khác không?".
- Mỗi thay đổi cấu trúc/kiến trúc → tự ghi "Decision" vào CLAUDE.md của project mới.
- Issue có branch chuẩn bị sẵn, hậu tố `-<số issue>`; "làm issue #N" = checkout branch đó code luôn.

---

## 9. Lộ trình khởi động project Android
1. **Backend trước**: thêm endpoint JSON (`/api/my-work` đầu tiên) — tái dùng handler, đổi output HTML→JSON. Test bằng curl qua `baokim-qa.com`.
2. Skeleton Compose + Material 3, bottom nav 3 tab (Dashboard / Việc của tôi / Bugs) + màn chi tiết task.
3. Auth: màn login Google OAuth → giữ session (CookieJar hoặc Bearer). Nhập PAT cá nhân (`/save-pat`).
4. Màn "Việc của tôi" (đơn giản nhất, giá trị ngay): gọi `/api/my-work`, render list, sort theo due.
5. Chi tiết task: `/issue-comments` → hiển thị + gửi comment (`/add-comment`) + đổi status (`/jira-transitions` → `/do-transition`) + due date (`/duedate-perm` → `/set-duedate`).
6. WorkManager poll `/activity-feed` → notification (ẩn noti của chính mình — backend đã lo).
7. Bug Log + analytics: `/api/bug-log` + `/api/analytics` → chart Compose (hoặc Vico/MPAndroidChart).
8. Roadmap + docs sau cùng.

> Toàn bộ 50+ Decision chi tiết + logic chính xác nằm trong `CLAUDE.md` của project web hiện tại — tham chiếu khi cần.
