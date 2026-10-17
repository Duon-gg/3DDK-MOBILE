# Thiết kế dữ liệu đề xuất

Ngày: 17/10/2026. **Chỉ thiết kế, không phải migration đã chạy.** Mục tiêu mới: công ty khoảng 100 người, tải đỉnh được hiểu khoảng 20 người cùng thao tác theo câu trả lời tiếp theo; số dòng và tỷ lệ đọc/ghi chưa được chốt. Xem [hiện trạng](PROJECT_CURRENT_STATE.md) để phân biệt schema thật và bảng đề xuất dưới đây.

## 1. Lựa chọn lưu trữ

Giữ PostgreSQL trên Supabase, `ws_records.cells JSONB`, catalog field UUID và quan hệ workspace → base → table. Không dùng một cột PostgreSQL mới cho mỗi custom field ở giai đoạn đầu; không dùng EAV cho mọi ô. JSONB giảm việc di trú dữ liệu hiện có nhưng validation và query theo kiểu phải do server kiểm soát. GIN và expression indexes có khả năng khác nhau; benchmark trước khi thêm index vào mọi field. [PostgreSQL JSONB](https://www.postgresql.org/docs/current/datatype-json.html).

Mọi tên bảng mới dưới đây là thiết kế dự kiến; không thể gọi từ APK hiện tại. Không xóa schema v1. UUID tài nguyên, Firebase UID text, timestamp sự kiện UTC, DATE thuần `YYYY-MM-DD`. Database đang chạy cloud chưa được introspect trong đợt này; phải kiểm tra version/extensions trước khi viết migration thật.

## 2. Bảng hiện hữu cần mở rộng

| Bảng | Dữ liệu hiện tại | Bổ sung đề xuất và ràng buộc |
|---|---|---|
| `ws_profiles` | uid, email, display_name, avatar_url | version, updated_at; không gán quyền công ty tại profile toàn cục |
| `ws_workspaces` | id, name, created_at | version, archived_at, created_by; workspace archived chặn mutation và invite accept |
| `ws_members` | workspace_id, uid, role | version, joined_at; P0 giữ đúng OWNER/EDITOR/VIEWER và một OWNER duy nhất |
| `ws_invites` | email, role, hạn/accepted/revoked | created_by, created_at; một lời mời đang chờ cho cùng workspace/email theo transaction, không unique theo `expires_at > now()` trong partial index |
| `ws_bases` | id, workspace_id, name | version, created/updated timestamps, archived_at |
| `ws_tables` | id, base_id, name | version, schema_version, primary_field_id, template_key, archived_at; primary field phải thuộc chính table |
| `ws_fields` | id, table_id, name, type, options, position | version, config JSONB, system_key nullable, archived_at; name unique trong table theo chính sách đã chọn, không dùng tên làm ID |
| `ws_records` | id, table_id, cells, version, timestamps, archived_at | created_by, updated_by; giữ cells và row version, không reset version khi restore |
| `ws_events` | workspace, actor, path, method, time | entity_type/entity_id, action, request_id, before_version/after_version, changes JSONB, schema_version; append-only qua RPC |
| `ws_requests` | uid/request_id, method/path/body/response | body_hash thay lưu body khi phù hợp, created_at/expires_at, status; retention phải dài hơn cửa sổ retry; không prune request đang dùng |
| `ws_limits` | window/count theo uid | giữ P0; bổ sung quota workspace/job nếu thêm import, export, webhooks |

Đổi membership bằng DELETE cho thành viên không phải owner (có audit) trong phương án P0; không thêm cờ active rồi quên cập nhật route GET cũ. Transfer owner kiểm tra actor là owner, target đang là member, đổi hai role trong cùng giao dịch với unique constraint phù hợp. Không dùng DELETE profile để thu hồi workspace; lịch sử vẫn cần actor UID.

`primary_field_id` và `system_key` phải được kiểm tra cùng table bởi constraint/trigger hoặc RPC có khóa, không chỉ Android. Chỉ archive cột sau khi kiểm tra phụ thuộc view/linked/formula; không xóa cells âm thầm. Đổi kiểu cột có dữ liệu cần preview và migration riêng; chưa thuộc P0.

## 3. Kiểu field và giá trị ô

| Type | JSON canonical | Config/validation dự kiến | Giai đoạn |
|---|---|---|---|
| TEXT | string hoặc null | max length; multiline chỉ là hiển thị; primary field có quy tắc bắt buộc riêng | Có, củng cố P0 |
| NUMBER | number hoặc null | precision/scale/bounds; từ chối NaN/Infinity và parse mơ hồ theo locale | Có, củng cố P0 |
| DATE | string ISO date hoặc null | ngày tồn tại, không timezone; sort theo ngày | Có |
| CHECKBOX | boolean hoặc null | không nhận chuỗi `"true"` từ API mới; phân biệt null và false | Có |
| SELECT | string label hoặc null ở schema cũ | options array labels hiện tại; so sánh đúng lựa chọn, không silently create | Có |
| USER | Firebase UID string hoặc null | chỉ thành viên workspace tại lúc gán; dùng tên để hiển thị, không lưu tên làm liên kết | P0 đề xuất |
| DATETIME | ISO instant UTC hoặc null | timezone dùng cho UI/view; không đổi nghĩa DATE cũ | Ứng viên sau P1; chưa lên lịch, cần duyệt task riêng |
| MULTI_SELECT | array stable option IDs | no duplicates, option thuộc field; cần migration và client support | Ứng viên sau P1; chưa lên lịch, cần duyệt task riêng |
| ATTACHMENT | array attachment IDs | đối chiếu metadata/record/workspace, không tin URL do client gửi | P1 |
| LINK | qua `ws_record_links`, API trả list IDs | target cùng base ở phạm vi ban đầu, cardinality config | P2 |
| FORMULA/LOOKUP/ROLLUP | giá trị tính toán chỉ đọc | config expression/dependencies, typed result/error | P2 |

**Tương thích SELECT:** không đổi labels hiện hữu thành option IDs ngay trong P0. Khi bổ sung sửa cấu hình SELECT, bắt đầu bằng thêm lựa chọn; chặn rename/remove lựa chọn đang dùng cho đến khi có quy trình migration. P1 nếu duyệt sẽ thêm `ws_field_options(id,field_id,label,color,position,archived_at)` và chuyển đổi schema version rõ ràng, giữ mapping labels cho nháp cũ trong giai đoạn tương thích. Không trộn label và ID mà không có discriminant/version; không trả representation mới qua legacy snapshot.

USER chỉ cho một người ở P0; nhiều người phụ trách là quyết định sản phẩm riêng. Thành viên đã bị gỡ vẫn có tên lịch sử trong ô cũ nhưng không thể được gán mới. Server cho giữ giá trị UID cũ không thay đổi khi sửa cột khác, đồng thời kiểm tra actor vẫn có quyền. Profile lookup bị giới hạn thành viên/UID được tham chiếu trong workspace, không cho quét toàn bộ tài khoản.

Mỗi mutation dùng catalog schema hiện tại. Field lạ, sai table, computed field writable, giá trị JSON sai kiểu, thiếu primary field theo contract mới đều bị từ chối. Kiểm tra kích thước cả request và từng cells trước transaction đắt tiền. Đối chiếu validator Kotlin/SQL bằng các fixture hợp đồng chung trong lần triển khai.

## 4. View và query

`ws_views` đề xuất:

| Thuộc tính | Ý nghĩa |
|---|---|
| id, table_id, name, type | UUID, FK table, tên; enum GRID/KANBAN/CALENDAR/GALLERY/FORM mở theo capability |
| visibility, owner_uid | SHARED hoặc PERSONAL; PERSONAL bắt buộc owner; shared owner_uid có thể ghi người tạo nhưng không quyết định quyền |
| version, created/updated timestamps | khóa lạc quan khi nhiều người sửa cấu hình |
| filter_spec, sort_spec, group_spec | JSON AST đã kiểm tra field IDs/type/operators; không chứa SQL |
| visible_field_ids, field_widths, field_order | projection hiển thị; ID duy nhất, thuộc table, width bị giới hạn |
| layout_config | groupFieldId, dateFieldId, coverFieldId tương ứng view type; không config tùy ý |

Mỗi table có default shared Grid được tạo cùng table; không tạo unique dựa tên hiển thị. Shared view chỉ Owner sửa config trong P0; Editor/Viewer có thể tạo personal view nếu quyết định được duyệt. Đọc shared view cần quyền table; view cá nhân còn cần owner_uid. Filter không biến record không hiển thị thành record không được quyền đọc.

`ws_fields.position` giữ thứ tự mặc định toàn table; thay đổi nó là Owner/schema mutation. `ws_views.field_order` là thứ tự hiển thị riêng của view và chỉ tăng view version. Nếu một view không có override thì dùng default. Field mới được thêm cuối view khi chưa có trong override; field archived bị loại khỏi rendering nhưng vẫn giữ cấu hình/draft để xử lý phụ thuộc rõ ràng.

AST hỗ trợ group `and/or` và predicate `{fieldId,operator,value}`; P0 đề xuất tối đa sâu 4, 20 predicate, 3 sort và 1 group. Các số là **giới hạn kỹ thuật cần duyệt/đo**, không phải giới hạn Teable/Airtable. Empty không đồng nghĩa chuỗi rỗng nếu field có type khác; trường thiếu và JSON null thống nhất empty cho query mới, false/0 vẫn là giá trị.

## 5. Bảng P1/P2, chưa tạo

| Bảng dự kiến | Khóa và invariant |
|---|---|
| `ws_comments` | id, workspace/table/record IDs, author_uid, body, version, timestamps, deleted_at; validate record cùng workspace |
| `ws_comment_mentions` | comment_id + recipient_uid unique, recipient là thành viên có quyền; không coi `@text` là UID |
| `ws_notifications` | id, recipient_uid, event_id, entity IDs, read_at; unique recipient/event/kind |
| outbox v2 | event_id + recipient + channel unique, state/attempt/available_at; cùng transaction với sự kiện |
| `ws_attachments` | id, workspace/record/field IDs, storage_key, MIME/size/hash/status, created_by; private bucket |
| `ws_record_links` | source_record_id, field_id, target_record_id unique; target table lấy từ config; cấm liên kết vượt tenant |
| `ws_field_dependencies` | dependent_field_id + source_field_id; graph không chu trình, computed error không làm mất input |
| `ws_derived_values` | record_id + field_id, result/type/error, source versions; có thể bắt đầu tính on-read ở giới hạn nhỏ rồi benchmark |
| `ws_jobs` | workspace, kind IMPORT/EXPORT/AUTOMATION, actor, state/progress, input/output private reference; idempotency/expiry |
| `ws_webhook_deliveries` | event+subscription unique, attempt/status/next_retry; secret không nằm trong payload/log |
| `ws_snapshots` | metadata bản sao, schema version, storage reference/created_at; dữ liệu snapshot không phải audit delta |

P2 chưa chọn formula grammar/engine, trigger scheduler hay sandbox. Không dùng SQL/eval/JavaScript tùy ý từ công thức người dùng. Audit, lịch sử record và backup snapshot là ba chức năng riêng, cần retention riêng.

## 6. Index, pagination và giới hạn

- Giữ index membership theo UID, resource parent FK và records live theo table; query mặc định dùng thứ tự ổn định `created_at,id`.
- Bổ sung views theo `table_id,visibility,owner_uid`, audit theo `workspace_id,entity_type,entity_id,created_at,id`, notifications theo `recipient_uid,created_at,id` khi đến P1.
- Expression index cho field động cần cast an toàn và kiểm tra schema; giới hạn số index nóng/table và build có kiểm soát. Không tạo index theo request client.
- Giới hạn record count hiện tại 500 vẫn là server guard cho đến khi test và phê duyệt nâng cap. Phân trang mục tiêu 50 rows, max 100/page; field projection giảm payload. Đây là cấu hình đề xuất.
- Không dùng LIMIT/OFFSET không có ORDER BY; tài liệu PostgreSQL cảnh báo kết quả không xác định khi thiếu thứ tự ổn định. [PostgreSQL LIMIT/OFFSET](https://www.postgresql.org/docs/current/queries-limit.html).

## 7. Room và nháp

Giữ `Draft` của Room v1 trong migration. Thêm metadata và record cache có khóa UID/resource; query pages là quan hệ record IDs theo UID/table/view/queryHash/cursor, kèm `fetched_at`, `complete`, schema/view version. Cập nhật page và record cache trong cùng transaction. Xóa một page khỏi cache không xóa draft; thay bộ lọc không được xóa toàn bộ bảng đã tải.

Nháp mới bổ sung table/record/localDraftId, changed field IDs, base cells cho so sánh xung đột, expected record/schema version, requestId. Mỗi dòng mới có localDraftId riêng; không dùng mã `new` chung cho mọi bản nháp mới. Từ chối ghi vào nháp tài khoản khác khi logout xảy ra giữa coroutine. Nếu server đã nhận create nhưng client timeout, replay đúng request trước khi tạo ID mới.

Schema local tăng version với migration xác định và instrumentation test 1→mới, không `fallbackToDestructiveMigration`. Kiểm thử cả nháp v1 và v2. [Android Room migration](https://developer.android.com/training/data-storage/room/migrating-db-versions).

## 8. Lộ trình chuyển đổi và rollback

1. Chụp backup và kiểm thử restore; đối chiếu các migration source với schema thực tế ở staging trước production.
2. Thêm version/schema/audit columns, default/fill nhỏ có kiểm tra; giữ routes/columns cũ. Mỗi bước transaction độc lập và có kiểm tra FK/count.
3. Bổ sung template atomic + capability endpoint; APK mới không gọi endpoint chưa có.
4. Thêm views/query + Room migration, sau đó mới thay màn hình list; legacy snapshot còn ở cap cũ.
5. Thêm USER, cập nhật schema validator và UI theo cùng capability. Không suy ra UID từ chuỗi tên đang có; chuyển cột cũ cần người dùng chọn mapping.
6. P1/P2 mỗi bước migration riêng sau duyệt; không đưa tất cả bảng tương lai vào một migration lớn.

Bật USER/array/computed type yêu cầu contract metadata/query mới. Table dùng type ngoài năm loại legacy phải chặn legacy snapshot/full-replacement mutation bằng `CLIENT_UPGRADE_REQUIRED`, không trả array cho editor APK cũ đang gọi `asString`, không giấu giá trị chưa biết rồi cho ghi nguyên JSON. Cần cập nhật APK trước rollout và giữ renderer read-only phù hợp khi tắt write capability. Thay đổi type/SELECT representation cần schema migration và test old draft riêng.

Rollback ưu tiên tắt capability và chạy app/API tương thích. Không DROP cột/bảng chứa dữ liệu vừa tạo. Restore backup chỉ khi có phê duyệt và kế hoạch xử lý các mutation phát sinh sau backup. Giữ audit/idempotency khi rollback UI; chúng không được coi là cache bỏ được.

## 9. Cần duyệt trước triển khai

Quy mô record/editor đỉnh; shared/personal views; một hay nhiều người phụ trách; lifecycle archive/restore; thời gian giữ audit/idempotency; file quota; quyền theo workspace hay nhỏ hơn. Thiết kế này chưa quyết định thay người dùng các chính sách trên.
