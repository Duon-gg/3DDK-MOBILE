# Khoảng cách tính năng và ưu tiên cho 3DDK

Ngày: 17/10/2026. Đối chiếu mã hiện tại với [inventory F01–F37](FEATURE_INVENTORY.md) và [hiện trạng có bằng chứng](PROJECT_CURRENT_STATE.md). Đây là đánh giá source, không phải nghiệm thu cloud hay benchmark mới. **Mục tiêu là công ty khoảng 100 người; câu trả lời về tải đỉnh được hiểu là khoảng 20 người cùng thao tác, số dòng chưa biết.**

“Có một phần” nghĩa đã có mã/luồng cụ thể, không có nghĩa tương đương Teable/Airtable. Khác biệt giữa cloud, community và gói phí nằm trong [báo cáo nguồn](REVERSE_ENGINEERING_REPORT.md). P0/P1/P2 dưới đây là **đề xuất chờ duyệt**, không tự mở rộng phạm vi đã được phép triển khai.

## 1. Ma trận đầy đủ

| ID | Khả năng | Trong v2 hiện nay | Khoảng thiếu chính | Đợt/task đề xuất |
|---|---|---|---|---|
| F01 | Workspace | tạo/list, membership, xem nội dung | quản lý role/gỡ/chuyển chủ, lifecycle/version | P0-04/05; lifecycle P1-10 |
| F02 | Base | tạo/list/đi vào tables | rename/archive/version, quản lý nhiều dự án dễ hơn | P1-10; chưa cần đổi stack |
| F03 | Table | tạo/list, blank/task template | template nhiều request gây partial, metadata/query chưa tách | P0-02/07/08; lifecycle P1-10 |
| F04 | Record | create/edit/archive, row version/requestId | primary/schema contract, cell patch, history/restore, paging | P0-03/08/12; P1-09 |
| F05 | Field types | TEXT/NUMBER/DATE/CHECKBOX/SELECT | USER thật; DATETIME/multi-select/attachments/derived chưa có | P0-11; P1/P2 theo type. DATETIME/multi-select chưa lên lịch, cần duyệt riêng |
| F06 | Field config | add field/options cơ bản | rename/order/archive/conversion; stable option IDs | P0-03; P1-03; conversion phải duyệt riêng |
| F07 | Grid | RecyclerView hàng, cells ngang, mở editor | saved config, load pages, toolbar rõ, render/virtualization đo thực | P0-07..10/13/15 |
| F08 | Kanban | chưa có | group SELECT, shared IDs, move với version, accessibility | P1-01 |
| F09 | Calendar | chưa có | date range, timezone semantics, ngày trống, move conflict | P1-02 |
| F10 | Gallery | chưa có; CardView danh sách không phải Gallery view | cover/layout config, query, private image handling | P1-08 |
| F11 | Form | có editor một dòng; chưa có configurable Form view | required/config/submit, quyền và public access policy | P1-08 nội bộ; public P2-06 cần duyệt |
| F12 | Filter | local SELECT tên “Trạng thái” | field-ID AST, nhiều điều kiện, lưu view, server query | P0-07/08/10 |
| F13 | Sort | local DATE tên chứa “hạn/deadline” | sort nhiều field có kiểu, ổn định cursor, lưu view | P0-07/08/10 |
| F14 | Group | chưa có | config/aggregate toàn query, collapse, paging semantics | schema/query P0; UI P1-01/03 |
| F15 | Search | local trên snapshot đã tải | server search, debounce/cancel, cache partial labels | P0-08/10 |
| F16 | Inline edit | chưa có; mở form để sửa | typed cell pending/error, diff patch, keyboard/TalkBack | P1-03 sau P0-12 |
| F17 | Bulk edit | chưa có | selection semantics, size/atomicity/version/preview | P1-03 |
| F18 | Drag & drop | chưa có | order keys/version, card/field move, menu thay kéo | P1-01/03; không cần cho P0 |
| F19 | Linked records | chưa có; text assignee không là relation | link config/edges/scope/cardinality/deletion | P2-01 |
| F20 | Lookup | chưa có | link/dependency/read ACL, typed values/invalidation | P2-02 sau P2-01 |
| F21 | Rollup | chưa có | aggregate linked values, empty/error/fanout | P2-02 |
| F22 | Formula | chưa có | grammar/allowlist/types/cycle/time budget/cache | P2-02; không eval tùy ý |
| F23 | Authentication | Firebase Email/Password, verify/reset/public signup | chính sách công ty, disable/revocation, SSO/MFA nếu cần | P0-01/14; discovery P2-06 |
| F24 | RBAC | backend workspace OWNER/EDITOR/VIEWER, invite accept/revoke | quản trị member, owner transfer, ACL toàn route mới | P0-04/05/14; granular ACL cần duyệt |
| F25 | Comments | chưa có v2 | author/version/body permissions | P1-04 |
| F26 | Mentions | chưa có | UID picker, recipient ACL, dedup notification | P1-04/05 |
| F27 | Notifications | v1 có worker/inbox; v2 chưa kích dispatch | inbox/outbox/events/devices v2, push quyền hiện tại | P1-05; không nhận phần v1 là đã xong v2 |
| F28 | Attachments | chỉ avatar URL; link kết quả v1 không là upload | private bucket/metadata/quotas/upload/download | P1-06 |
| F29 | Import | chưa có cho người dùng | CSV mapping/preview/job/errors/schema limits | P1-07 |
| F30 | Export | chưa có user CSV; hướng dẫn backup không là export UI | ACL/query snapshot/job/download/CSV safety | P1-07 |
| F31 | Automation | chưa có v2; nhắc hạn v1 không là rule engine | triggers/actions/quotas/run log/retry/loop guard | P2-03 |
| F32 | Webhook | chưa có | chiều vào/ra, signature, retries, SSRF, credentials | P2-03 |
| F33 | REST API | Edge/RPC phục vụ app, JWT/validation/errors | docs/schema/capability/query; scoped API cho bên thứ ba chưa có | P0-01/08; integrations P2-06 |
| F34 | Dashboard | chưa có aggregate dashboard v2 | metrics/query/ACL/chart/drilldown/cache freshness | P2-04 |
| F35 | Audit/activity | ws_events ghi actor/path/method/time | entity/delta/version/read API/retention | P0-06 |
| F36 | Record history | v1 có submission/events; v2 không có before/after history | typed deltas/read ACL/version restore | P0-06; restore P1-09 |
| F37 | Base snapshot/version | chưa có tính năng app | backup snapshot/job/schema/files/restore policy | P2-05; vận hành backup P0-16 |

## 2. Các khoảng thiếu ảnh hưởng trực tiếp tới độ tin cậy

1. **Tạo bảng partial đã xác định qua source.** Template tạo table rồi từng field riêng. Cần một transaction + replay đúng requestId, không chỉ retry thêm ở UI.
2. **Query hiện là snapshot toàn bảng, chưa có API phân trang.** Trần 500 record là giới hạn tạo live records, không phải page size. Không thể gọi filter hiện tại là query server cho bảng lớn.
3. **Nháp/xung đột có nền nhưng UX chưa đủ.** Giữ nháp không đồng nghĩa merge an toàn; reconcile có thể đưa version mới vào nháp cũ, cần so sánh/kiểm tra trước save. Key `new` chưa phục vụ nhiều nháp tạo mới độc lập.
4. **Quyền backend có, quyền quản trị chưa đủ.** Không được mô tả chỉ là “ẩn nút”; hiện DB có checks thật. Tuy nhiên chưa có route đổi/gỡ member hoặc chuyển chủ và chưa chứng minh năng lực 100 tài khoản/khoảng 20 người thao tác đồng thời.
5. **Audit v2 chưa là lịch sử nội dung.** Không có delta thì không thể hiển thị trước/sau hoặc restore từ log path/method.
6. **Một số chức năng v1 không tự chạy trên v2.** Phân công/duyệt bắt buộc/push/nhắc hạn phải được nối đúng mô hình mới, không nhận gộp bằng chứng cũ.

Các khả năng race cache/logout, throughput hoặc JWT revocation là **rủi ro cần test**, không kết luận có sự cố lộ/mất dữ liệu khi chưa tái hiện. Repo hiện có test chống quyền và một số test live, nhưng không phải bằng chứng toàn bộ ma trận đã đạt.

## 3. Tái sử dụng và phần phải xây

| Tái sử dụng | Phần cần mở rộng |
|---|---|
| Firebase auth + ApiTransport refresh401 | capability/contract và chính sách disabled/revoked account |
| RPC workspace scope, row version, ws_requests | schema/view versions, query AST, atomic template, member admin |
| Room singleton/cache/draft | page membership, schema migration, độc lập draft mới, scope invalidation |
| Grid RecyclerView + typed RecordEditor | saved view toolbar, page rendering, USER/date editor, conflict comparison |
| v1 transactional outbox/worker concepts | recipient/routing/token permissions/inbox v2; không chỉ đổi URL |
| DB/Edge/Android tests + CI + keystore ngoài repo | fixtures theo tính năng được duyệt trong 37 mục, load harness, two-device tests, upgrade evidence |

## 4. Ưu tiên cho công ty 100 người

P0 chọn độ đúng dữ liệu, cộng tác có quyền và Grid thật sự dùng được trước. P1 tăng giá trị hằng ngày bằng Kanban/Calendar/comments/notifications và trao đổi tệp/dữ liệu khi được duyệt. P2 có chi phí/rủi ro cao (relation compute, automation, webhook, snapshots) và chưa được coi là bắt buộc cho bản đầu.

100 tài khoản không phải bằng chứng cần dùng microservices, không tự chứng minh gói miễn phí đủ, và không cho phép bỏ kiểm thử ghi đồng thời. Workload khoảng 20 người cùng thao tác cần mô tả tỷ lệ đọc/ghi, các dòng hay bị sửa cùng lúc, số cột, kích thước text và số dòng. Ngày bàn giao chưa có nên roadmap dùng thứ tự phụ thuộc và các cổng kiểm tra, không hứa ngày.

## 5. Quyết định chưa được chốt

- Duyệt P0 và loại view cần giao đầu tiên; không dùng “giống hệt” làm tiêu chí nghiệm thu toàn bộ 37 tính năng.
- Số dòng/table, 20 người là cùng đọc hay cùng sửa, deadline và hosting budget.
- Workspace-only RBAC hay từng phòng ban/table/field; USER một hay nhiều; trạng thái tự do hay workflow nộp/duyệt.
- Retention/backup, files, public sharing, SSO và integration API.
- Có cài REA cho vòng quan sát thực nghiệm không. Hiện bằng chứng REA runtime là **0**; 37 mục không phải do REA phát hiện.

Chi tiết kiến trúc/API/schema/UI và từng task có nghiệm thu/test/rollback nằm trong bốn đặc tả và [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md).
