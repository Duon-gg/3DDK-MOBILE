# Đặc tả API đề xuất

Ngày: 17/10/2026. **Hợp đồng thiết kế để duyệt; không phải endpoint đã deploy.** Prefix giữ `/functions/v1/api/v2`. [Hiện trạng](PROJECT_CURRENT_STATE.md) liệt kê route đã có. Không coi các route đề xuất dưới đây là gọi được ngay.

## 1. Hợp đồng chung

- HTTPS, `Authorization: Bearer <Firebase ID token>`; Edge xác minh project/issuer/audience/expiry/email_verified trước nghiệp vụ. Không nhận UID/role tin cậy từ body.
- UUID cho resource, Firebase UID cho account. Timestamp UTC ISO 8601; DATE là ISO date. `schemaVersion`, record `version`, view `version` là các bộ đếm khác nhau.
- Mutation có UUID `requestId`; update/archive/restore có `expectedVersion`. Route cells mới thêm `expectedSchemaVersion`.
- Cùng UID/requestId + cùng method/path/body đã chuẩn hóa trả lại response ban đầu; cùng ID khác nội dung trả 409 `REQUEST_REUSED`. Không tạo audit/notification thêm lần hai.
- Giữ giới hạn body 32KiB ở Edge như hiện tại trong P0; lỗi 413 trước parse/transaction. Batch vẫn chịu cùng trần body, không hứa 50 dòng lớn luôn vừa. Import file dùng job/upload riêng nếu được duyệt.
- Giới hạn hiện tại 60 mutation/phút/UID được giữ cho đến khi đo lại; query có ngân sách đọc riêng, không giả làm mutation chỉ vì dùng POST.
- Lỗi có `{code,message,requestId,details?}`; message tiếng Việt thân thiện do client ánh xạ. Log chỉ requestId/action/status/latency, không bearer/password/body nhạy cảm.
- Server đọc capability/minClientVersion để client mới không gọi feature chưa deploy. Trong đợt này chưa bật capability mới.

| HTTP | Code tiêu biểu | Client xử lý |
|---|---|---|
| 401 | UNAUTHORIZED | Firebase refresh một lần, replay cùng requestId; tiếp tục lỗi thì đăng nhập |
| 403 | UNVERIFIED, FORBIDDEN | không retry vô hạn; khóa thao tác và xử lý cache đúng scope |
| 404 | NOT_FOUND | resource không tồn tại/đã lưu trữ theo contract; giữ nháp để sao chép |
| 409 | CONFLICT, SCHEMA_CHANGED, REQUEST_REUSED, CURSOR_STALE | giữ input; tải bản mới/cấu hình rồi người dùng quyết định |
| 409 | CLIENT_UPGRADE_REQUIRED | table dùng schema/type không an toàn cho legacy; cập nhật APK, không retry full replacement |
| 413 | PAYLOAD_TOO_LARGE | không retry nguyên request; hướng dẫn giảm hoặc dùng job được hỗ trợ |
| 422 | VALIDATION, UNSUPPORTED_FIELD, INVALID_VIEW | hiển thị lỗi theo field; không tự bỏ giá trị sai |
| 429 | RATE_LIMIT | Retry-After/backoff; không đổi requestId để lách quota |
| 5xx/timeout | SERVER_ERROR | kết quả mutation có thể đã commit; retry đúng ID, giữ nháp |

403 không trả nội dung record của workspace khác. Resource ID phải được resolve tới workspace ở server cho từng route; không dùng viewId/fieldId của workspace khác chỉ vì tableId hợp lệ.

## 2. Tài khoản và quyền

| Endpoint | Trạng thái | Quyền/validation và hành vi |
|---|---|---|
| GET/PATCH `/me` | Có; PATCH version là bổ sung | profile độc lập workspace, name/avatar theo policy; UID từ token |
| GET `/capabilities` | P0 mới | authenticated; versions, template/view/field support và giới hạn hiệu lực; không secret |
| GET/POST `/workspaces` | Có | list memberships; create + OWNER trong transaction |
| GET `/workspaces/{id}` | Có | member; legacy bases/members/invites vẫn tương thích |
| PATCH `/workspaces/{id}` | P1 mới | OWNER, expectedVersion, rename/archive policy; không thuộc CRUD hiện có |
| GET/POST `/workspaces/{id}/invites` | GET mới; POST có | OWNER, email normalized, role EDITOR/VIEWER; trả lời mời đang chờ |
| DELETE `/workspaces/{id}/invites/{inviteId}` | Có | OWNER, kiểm tra đúng workspace; retry không lỗi lặp |
| GET `/invitations` | Có | email claim đã xác minh; lời mời chưa dùng/chưa thu hồi/chưa hết hạn |
| POST `/invitations/{id}/accept` | Có, củng cố P0 | email đúng, workspace active, khóa invite; replay hợp lệ không đổi role mới của member |
| PATCH `/workspaces/{id}/members/{uid}` | P0 mới | OWNER sửa EDITOR/VIEWER với expectedVersion; không sửa OWNER qua đường này |
| DELETE `/workspaces/{id}/members/{uid}` | P0 mới | OWNER, không xóa OWNER; target đúng workspace; audit và chặn request sau thu hồi |
| POST `/workspaces/{id}/transfer-ownership` | P0 mới, chờ duyệt | OWNER + target là member; đúng một owner sau commit; requestId + version membership |

JWT không tự thể hiện membership hiện tại: mỗi mutation/query đều kiểm tra server. Firebase token revocation/disabled-user policy cần chọn riêng; không hứa JWT ký hợp lệ đồng nghĩa account chưa bị disable. Không cấp ADMIN hoặc quyền table/field riêng trong P0 khi chưa duyệt.

## 3. Base, table, field và template

| Endpoint | Hành vi đề xuất |
|---|---|
| GET `/bases/{id}`; POST `/workspaces/{id}/bases` | giữ hiện có; quyền member đọc, OWNER/EDITOR tạo như hiện tại |
| POST `/bases/{id}/tables` | giữ `{name}` tạo blank; bổ sung `{name,template:"TASKS",requestId}` tạo table+fields+Grid cùng transaction; template schema chỉ OWNER |
| GET `/tables/{id}` | legacy snapshot giữ response/cap cho table chỉ năm type cũ; type mới trả CLIENT_UPGRADE_REQUIRED, không đưa JSON array vào APK cũ |
| GET `/tables/{id}/metadata` | mới: table/version/schemaVersion, role/capabilities, fields, views; không nhúng records |
| POST `/tables/{id}/fields` | có, bổ sung schema version; OWNER; type/config allowlist và giới hạn cột |
| PATCH `/tables/{id}/fields/{fieldId}` | P1 mới: OWNER; rename/add select option, expected field/schemaVersion; không chuyển kiểu mất dữ liệu |
| PATCH `/tables/{id}/field-order` | P1 mới: OWNER; đổi default order toàn table, IDs duy nhất và đủ tập active fields; expectedSchemaVersion. Đổi order trong view dùng PATCH view và viewVersion |
| POST `/tables/{id}/fields/{fieldId}/archive` | P1 ứng viên; kiểm tra view/dependency/primary field; không xóa cells |

Response template có `id,defaultViewId,schemaVersion,version`; retry sau timeout trả chính các ID đó. Template chỉ tạo một table với field keys cố định nội bộ, tên hiển thị tiếng Việt; `system_key` làm dấu cho status/due/assignee. Đổi tên không làm mất filter hoặc editor.

EDITOR hiện được tạo blank table nhưng không được tạo schema; không cho chọn template qua UI rồi lách quyền bằng cùng endpoint. Nếu sau này muốn editor tạo template, phải thay ma trận quyền có duyệt và test.

## 4. Saved views và query

| Endpoint | Quyền và hành vi |
|---|---|
| GET `/tables/{id}/views` | member; shared + personal của chính UID |
| POST `/tables/{id}/views` | OWNER cho SHARED; PERSONAL theo chính sách chờ duyệt; type capability đã bật |
| PATCH `/tables/{id}/views/{viewId}` | owner shared hoặc chủ personal; expectedVersion; validate toàn bộ config trước commit |
| POST `/tables/{id}/views/{viewId}/archive` | như quyền sửa; không archive default view cuối cùng |
| POST `/tables/{id}/records/query` | **read-only POST**, không yêu cầu requestId; auth/quota đọc; body có viewId/overrides/search/cursor/limit |
| GET `/tables/{id}/records/{recordId}` | member; đọc một dòng và version, không phụ thuộc filter đang chọn |

Current Edge chỉ chuyển pathname, không chuyển query string vào RPC. Đề xuất POST query tránh giả định GET `?filter=` đã hoạt động. Dispatcher cần phân loại thao tác đọc theo route+method để query không vào idempotency/mutation audit; đây là thay đổi phải kiểm thử, không chỉ thêm UI.

Viewer hiện bị chặn mọi mutation ở dispatcher SQL. Nếu duyệt personal view cho Viewer, phải kiểm quyền theo action/resource cụ thể và owner_uid của view; ngoại lệ ghi personal config không cấp quyền ghi record, field hay shared view. Test bắt buộc cả ba đường bị cấm, không sửa guard thành “Viewer được POST”.

Ví dụ query **đề xuất** (các ID là placeholder, không phải ID thật):

```json
{
  "viewId": "<uuid-view>",
  "viewVersion": 3,
  "expectedSchemaVersion": 8,
  "filter": {"op":"and","conditions":[{"fieldId":"<uuid-status>","operator":"eq","value":"Đang làm"}]},
  "sort": [{"fieldId":"<uuid-due>","direction":"asc","nulls":"last"}],
  "search": "nghiệm thu",
  "fieldIds": ["<uuid-title>","<uuid-status>","<uuid-due>"],
  "limit": 50,
  "cursor": null
}
```

Response: `{items:[{id,cells,version,createdAt,updatedAt}],nextCursor,hasMore,schemaVersion,viewVersion,serverTime}`. Không luôn trả exact total để tránh COUNT đắt; group count là endpoint/option riêng tính trên **toàn bộ tập lọc**, không trên một trang. Limit đề xuất 50, max100; nếu có byte budget response thì có thể trả ít hơn limit nhưng nextCursor phải đúng.

Quy tắc query: depth≤4, predicates≤20, sorts≤3 trong P0 đề xuất; field IDs thuộc table và active; allowlist operator theo type (`eq/ne/isEmpty`, text `contains`, number/date `lt/lte/gt/gte`, USER `isMe` resolve UID từ token). `false`, `0` không empty; CASE/regex/SQL tự do bị từ chối. Null-last thống nhất server/client. `id` là tie-breaker cuối.

Cursor opaque có chữ ký server, TTL và query/table/UID/schema/view binding; client không tạo hoặc sửa giá trị cursor. Đổi query/cấu hình bắt đầu lại. P0 không bảo đảm snapshot qua nhiều request đang có mutation; UI khử trùng ID và cho refresh. Không gọi saved filter là cơ chế bảo mật.

## 5. Sửa dữ liệu, bulk và lịch sử

| Endpoint | Giai đoạn/hợp đồng |
|---|---|
| POST `/tables/{id}/records` | có; thêm expectedSchemaVersion cho contract mới và validate primary/USER |
| PATCH `/tables/{id}/records/{id}` | legacy full cells replacement chỉ cho table/schema legacy-safe; type mới bị chặn CLIENT_UPGRADE_REQUIRED |
| PATCH `/tables/{id}/records/{id}/cells` | P0 mới: `{changes,expectedVersion,expectedSchemaVersion,requestId}`; chỉ sửa keys gửi, null xóa giá trị |
| DELETE `/tables/{id}/records/{id}` | có: soft archive, expectedVersion, idempotency |
| POST `/tables/{id}/records/{id}/restore` | P1: version check, validate schema hiện tại và quota trước restore |
| POST `/tables/{id}/records/batch` | P1: tối đa50 ops và32KiB body ban đầu, all-or-nothing; version mỗi record, audit từng entity, requestId cả batch |
| GET `/tables/{id}/records/{id}/history` | P0 mới: cursor, quyền đọc hiện tại; event delta/version, không trả secret |
| POST `/tables/{id}/records/{id}/restore-version` | P1 ứng viên: tạo version mới, không giảm counter; validate quyền/schema/expectedVersion; không viết lại lịch sử |
| GET `/workspaces/{id}/activity` | P0 mới: OWNER, filter resource/action và cursor; không là toàn bộ server log |

Body cells mới:

```json
{
  "requestId": "<uuid-request>",
  "expectedVersion": 12,
  "expectedSchemaVersion": 8,
  "changes": {"<uuid-status>": "Hoàn thành"}
}
```

Validation/type/permission/version và audit trong cùng transaction. 409 có serverVersion và changedFieldIds nếu actor vẫn có quyền; nội dung so sánh lấy qua GET được kiểm tra quyền. Khi thiếu quyền không lộ before/after trong lỗi. Nháp giữ nguyên sau mọi thất bại chưa xác nhận; retry timeout giữ ID và payload cũ.

Backcompat: APK cũ không gửi schema/capability header, nên server không thể giả định nó hiểu USER hay array types. `GET /tables/{id}` luôn là legacy route; table không legacy-safe trả CLIENT_UPGRADE_REQUIRED. New metadata/query trả typed cells. Create/archive trên table dùng type mới phải có expectedSchemaVersion; legacy PATCH full replacement không cho ghi table đó. Chỉ bật type mới sau thông báo/cập nhật APK và smoke test. Không cắt bỏ unknown fields hoặc stringify array để giả tương thích.

## 6. API P1/P2 chỉ là ứng viên

| Nhóm | API dự kiến | Kiểm soát bắt buộc |
|---|---|---|
| Comments/mentions | GET/POST record `/comments`; PATCH/DELETE comment | member đọc, author sửa/xóa theo version; mention UID có quyền; plain text/sanitize |
| Notifications | GET `/notifications`; PATCH `/{id}` read | chỉ recipient, deep link kiểm tra lại quyền; outbox dedupe |
| Attachments | POST upload-intent/complete; GET download-url | private storage, quota/MIME/size/hash, quyền record, URL hết hạn; không nhận storage path tùy ý |
| CSV | POST `/imports/preview`, `/imports/commit`; GET job | mapping/validation dry-run, encoding và CSV injection khi xuất; không import vào bảng khác scope |
| Export | POST `/exports`; GET job/download | quyền/query cố định, snapshot consistency, quota/expiry |
| Linked/derived | link cells + field config routes | target same base, cycle/dependency validation; computed read-only |
| Webhooks | subscriptions/deliveries | OWNER, secret encrypted, SSRF/egress policy, signature/retry/dedup |
| Automation | rules/runs | allowed actions, quotas, loop guard, idempotency; chưa chọn engine |
| Public Form | share/submit routes riêng | **không bật mặc định**; token scope, spam/rate/file safeguards, không liệt kê records |
| Dashboard | GET aggregate query | cùng ACL, group theo field IDs, không tải toàn bảng về máy để đếm |

Không tạo API key tích hợp cho bên thứ ba bằng Firebase token người dùng dài hạn. API hiện có phục vụ app; API cho bên thứ ba cần scoped/revocable credentials được duyệt ở P2.

## 7. Test hợp đồng và rollout

Fixtures bắt buộc: JSON sai kiểu/null/empty; field của table khác; cursor UID khác; viewer mutation trực tiếp; template replay; invalid field thứ N rollback cả mẫu; two-device stale version; schema đổi lúc có nháp; accepted/revoked invite; membership thu hồi giữa query và save; audit/outbox không lặp sau timeout.

Giữ bộ test DB v1/v2, thêm test Edge method/path/query classification và Kotlin MockWebServer cho contract mới. Deploy database→Edge capability false→APK tương thích→bật capability sau smoke test. Không chạy production mutation trong phiên nghiên cứu này.
