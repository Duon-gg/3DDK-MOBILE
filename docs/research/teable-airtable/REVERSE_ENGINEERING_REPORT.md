# Báo cáo nghiên cứu hành vi và cấu trúc Teable/Airtable

Ngày: **17/10/2026**. Phục vụ thiết kế 3DDK bằng Kotlin/XML, Firebase và Supabase; chỉ phân tích, không triển khai sản phẩm.

## 1. Kết quả thực tế và mức độ bằng chứng

Đã lập [37 phiếu tính năng F01–F37](FEATURE_INVENTORY.md), mỗi phiếu có giá trị, UX dự kiến, dữ liệu, backend/API, ngoại lệ, bằng chứng, mức chắc chắn, ưu tiên/rủi ro và cách áp dụng. Nghiên cứu dựa trên tài liệu công khai của hai hãng và đọc tĩnh một số file Teable tại commit cố định. **Chưa reverse-engineer toàn bộ sản phẩm; chưa chứng minh khả năng tương đương hay độ ổn định khi chạy thực tế.**

| Loại bằng chứng | Đã làm | Có thể kết luận | Không thể kết luận |
|---|---|---|---|
| Tài liệu trợ giúp chính thức | Đọc các trang tính năng, quyền và API, link ngay ở inventory | Hành vi hãng công bố, giới hạn được tài liệu ghi | Hành vi thực tế trên mọi edition, độ trễ và bug runtime |
| Mã nguồn công khai Teable | Xem metadata commit, LICENSE và các file kiến trúc bên dưới | Những thành phần và schema có trong đúng revision | Toàn bộ cloud dùng cùng revision/config hay mọi code path an toàn |
| Marketing/README | Đọc trang chính và README để tìm hướng sản phẩm | Định vị sản phẩm; cần kiểm lại bằng tài liệu/source | Benchmark, SLA hay “triệu dòng” đã được kiểm nghiệm |
| Quan sát UI trực tiếp | **Chưa thực hiện** phiên đăng nhập/ứng dụng hai hãng | Không có kết luận tương tác trực tiếp | Không gắn nhãn “đã thao tác”, không tạo ảnh giả làm bằng chứng |
| API/network runtime | **Chưa gọi authenticated API**, chưa thu network trace | Public contract từ tài liệu | Private endpoint, token/session hay cơ chế concurrency nội bộ Airtable |
| REA | **Chưa cài, chưa khởi chạy, chưa phân tích binary/site bằng REA** | Chỉ có kế hoạch trong [REA_SETUP_PLAN.md](REA_SETUP_PLAN.md) | Không có output REA để tuyên bố công cụ phát hiện thành công |

Không đọc cookie, credential, database production, source proprietary hoặc nội dung tài khoản để làm nghiên cứu. HTTP đọc public GitHub API/raw file và trang tài liệu không phải sử dụng REA.

### Độ phủ nguồn đã kiểm tra

Thống kê theo URL duy nhất được viện dẫn trong **inventory và báo cáo này**, không tính tài liệu của các phần nghiên cứu khác. “Mở trực tiếp” nghĩa công cụ nhận được nội dung trang nguồn và đã đối chiếu phần liên quan đến claim; không đồng nghĩa đã kiểm thử hành vi trên tài khoản hay rà mọi câu trong tài liệu.

| Tập nguồn | Số nguồn | Đã mở trực tiếp | Chỉ search excerpt / chưa lấy được trang |
|---|---:|---:|---:|
| Trang tính năng/API/vận hành `help.teable.ai` | 30 | 30 | 0 |
| Index `help.teable.ai/llms.txt` | 1 | 1 | 0 |
| Bài hỗ trợ `support.airtable.com/articles/…` | 40 | 40 | 0 |
| File kiến trúc Teable cố định SHA trong mục 3 | 6 | 6 qua raw file cùng SHA | 0 |
| LICENSE gốc và `packages/core/LICENSE` | 2 | 2 qua raw file cùng SHA | 0 |

Ngoài các tập trên, đã đọc trang sản phẩm hai hãng, README/repository Teable và metadata commit qua public GitHub API để định vị. Trang chủ support chỉ là lối vào, không được tính thành bài bằng chứng. Các permalink GitHub `blob/...#L...` trỏ cùng nội dung raw đã đọc. `AGPL_LICENSE` đầy đủ được dẫn như tài liệu tham chiếu, **chưa rà toàn văn và không đưa ra tư vấn pháp lý**; nhận định giấy phép ở mục 2 dựa trên LICENSE gốc và LICENSE package đã đọc. GitHub commit HTML không được dùng làm nguồn nội dung tính năng.

Các nhóm trước đây chỉ có search excerpt (sort/filter/group/lookup/rollup/formula/quyền/bình luận/thông báo/import/API/audit/mobile/bulk) đã được mở trang chính thức và đối chiếu thêm. Không có nhóm tính năng nào được gắn nhãn “đã xác minh” chỉ nhờ một URL chưa tải được. Có trường hợp mở URL Formula Teable ban đầu lỗi; mở lại qua link nội bộ chính thức đã trả nội dung cùng URL.

## 2. Phiên bản và quyền sử dụng mã tham khảo

Teable được tham chiếu tại nhánh `develop`, commit **`b91d88eeb0e923263db24b7f8d29ddb4f50a544e`**, thời điểm commit do GitHub API trả **2026-10-16T08:44:10Z**. Đây là revision nghiên cứu, không phải khẳng định cloud đang deploy revision này. Có thể kiểm tra [commit cố định](https://github.com/teableio/teable/commit/b91d88eeb0e923263db24b7f8d29ddb4f50a544e) và [metadata API public](https://api.github.com/repos/teableio/teable/commits/b91d88eeb0e923263db24b7f8d29ddb4f50a544e).

`LICENSE` ở revision này phân biệt core applications với `packages/`: core `apps/nestjs-backend`/`apps/nextjs-app` ghi AGPL-3.0; các package ghi MIT. File còn có điều khoản riêng về thương hiệu. Đã đọc LICENSE gốc và LICENSE package; chưa rà toàn bộ AGPL text hay đánh giá nghĩa vụ pháp lý khi phân phối một bản phái sinh. [LICENSE gốc](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/LICENSE), [AGPL text tham chiếu](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/AGPL_LICENSE), [MIT của core package](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/packages/core/LICENSE).

Đợt này chỉ mô tả hành vi và thiết kế độc lập, không đưa source/tài nguyên thương hiệu tham khảo vào 3DDK. Nếu sau này chọn tái sử dụng một package phải kiểm giấy phép chính file/package, dependencies, notices và cách phân phối trước khi duyệt; chữ “open source” không có nghĩa mọi thành phần cloud/enterprise đều được cấp cùng quyền.

Airtable là hệ thống được nghiên cứu qua tài liệu/public UI description. Không có source server Airtable trong nghiên cứu này; không suy luận database, event bus, framework hay giao thức đồng bộ của họ từ bề ngoài sản phẩm.

## 3. Những gì xác minh được trong mã Teable

Các permalink dưới đây giữ revision cố định; số dòng là vị trí để kiểm lại, không phải mã được đề xuất sao chép.

| File và vị trí | Sự thật đọc được | Kết luận giới hạn và bài học cho 3DDK |
|---|---|---|
| [Prisma metadata schema, L17–35, L204–251, L309–344, L570–621](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/packages/db-main-prisma/prisma/postgres/schema.prisma#L204) | Có Space/Base/TableMeta/Field/View; TableMeta giữ `dbTableName`, Field giữ `dbFieldName`, type/options/version; View gắn table, giữ sort/filter/group/options/columnMeta/version; có Reference giữa field IDs | Metadata và cấu hình view được tách khỏi record; chưa đủ để suy ra toàn bộ mô hình lưu record hay thiết kế cloud |
| [View schema, L29–105](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/packages/core/src/models/view/view.schema.ts#L29) | Schema có ID/type/config truy vấn/column metadata; `superRefine` chọn schema options theo loại view | View cần validation đúng loại, không chỉ blob JSON bất kỳ. Không cần chép enum/naming/schema TypeScript sang Android |
| [Field schema, L44–160, L209–253, L418–428](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/packages/core/src/models/field/field.schema.ts#L44) | Field chứa typed options, computed flags và value metadata; các kiểu chọn options schema riêng; patch schema strict | Áp dụng nguyên tắc validation theo kiểu. Không kết luận strict schema một file đã bảo vệ mọi route |
| [RecordQueryService, L25–26, L43–75](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/apps/nestjs-backend/src/features/record/record-query.service.ts#L43) | Query sử dụng table database name, truy cập cột của field, rồi chuyển DB values thành cells theo field ID | Đây là bằng chứng Teable có đường query ánh xạ cột thực; **không phải bằng chứng cells JSONB của 3DDK nên được thay bằng dynamic physical tables** |
| [PermissionGuard, L59–88, L696–719](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/apps/nestjs-backend/src/features/auth/guard/permission.guard.ts#L696) | Backend guard xét resource/decorator và gọi permission check; có token/user/public cases | Quyền có lớp backend. Chưa audit toàn service/route nên không tuyên bố đã chứng minh mọi đường truy cập an toàn |
| [ShareDbService, L5–19, L41–104](https://github.com/teableio/teable/blob/b91d88eeb0e923263db24b7f8d29ddb4f50a544e/apps/nestjs-backend/src/share-db/share-db.service.ts#L41) | Dùng ShareDB, presence, adapter, Redis pubsub tùy config, auth middleware và hook phát operations sau transaction | Có hạ tầng realtime trong CE revision này. Không đủ chứng minh offline merge/CRDT/không mất update trong mọi case; không cần bê ShareDB/Redis vào Supabase hiện tại |

Không build hoặc chạy test suite Teable. Việc đọc enum/schema/service chỉ chứng minh cấu trúc tĩnh và nhánh được xem. Hiện có cả thư mục `packages/v2` lẫn service cũ; một vài file không đủ để kết luận request thực tế luôn đi qua đường nào.

## 4. Các khác biệt có ảnh hưởng đến thiết kế

### 4.1. Workspace / Base / Project

Teable hiện dùng nhãn **Project** ở sản phẩm nhưng giữ `base` trong API. 3DDK không cần đổi ID/API theo thay đổi nhãn tham khảo; có thể dùng “Dự án” trong UI nhất quán. [Teable Project](https://help.teable.ai/en/basic/base).

Điều cần lấy là quan hệ sở hữu và membership. Dữ liệu chỉ được chia sẻ theo quyền không gian/dự án đã duyệt; việc ai cũng tự đăng ký không đồng nghĩa người đăng ký đọc được dữ liệu công ty khác. [Teable permissions](https://help.teable.ai/en/basic/space/space-permission), [Airtable workspace/base permissions](https://support.airtable.com/articles/3994336578-airtable-permissions-overview).

### 4.2. View là cấu hình hiển thị/truy vấn của cùng record

Grid, Kanban, Calendar, Gallery và Form không cần tạo bản sao bảng dữ liệu. Bằng chứng tài liệu hai hãng mô tả view trên cùng table; source Teable có View gắn TableMeta. [Teable toolbar](https://help.teable.ai/en/basic/view/toolbar), [Airtable use views](https://support.airtable.com/articles/2972965253-using-views-to-section-out-related-data-in-airtable).

Thiết kế tương đương cho 3DDK: record UUID duy nhất, view config tham chiếu field UUID; query service áp quyền trước filter/sort/page. Đó là đề xuất của báo cáo, không phải khẳng định Airtable sử dụng compiler hay JSONB giống vậy.

### 4.3. Web UX không được mặc định là Android UX

Tài liệu Airtable phân biệt rõ web/iOS/Android và nhiều chức năng cấu hình không có cùng mức hỗ trợ; import dữ liệu là ví dụ chưa có ở native mobile. Vì thế “giống Teable/Airtable” ở 3DDK nên được duyệt theo hành vi cốt lõi và khả năng thao tác trên điện thoại, không lấy screenshot web làm tiêu chí duy nhất. [Mobile differences](https://support.airtable.com/articles/9609457174-airtable-desktop-and-mobile-feature-differences), [Import](https://support.airtable.com/articles/9412806945-importing-third-party-data-into-airtable).

Đề xuất UI 3DDK vẫn là XML/Fragments, RecyclerView, Material cards và dialog typed editor. Kéo thả luôn có menu thay thế; font lớn, TalkBack, loading/error/empty, cache chưa đủ đều phải có luồng rõ.

### 4.4. Khả năng field và quyền khác nhau giữa sản phẩm/gói

Danh mục field không hoàn toàn một-một; Airtable liệt kê một số kiểu chuyên biệt như barcode/duration, còn Teable có conditional lookup/rollup và button tích hợp workflow. Inventory ưu tiên TEXT/NUMBER/DATE/CHECKBOX/SELECT hiện có và USER cho quản lý việc trước. [Teable fields](https://help.teable.ai/en/basic/field), [Airtable types](https://support.airtable.com/articles/4808831313-supported-field-types-in-airtable-overview).

Teable Authority Matrix và SSO ghi Business+; audit admin panel ghi self-host Business+. Airtable Enterprise audit ghi Enterprise Scale. Đây là bằng chứng tài liệu/gói, không chứng minh source community có toàn bộ tính năng tương ứng. Các role OWNER/EDITOR/VIEWER ở 3DDK là mô hình riêng hiện tại; không tự thêm billing/SSO/đại lý phân quyền doanh nghiệp vì mục tiêu 100 tài khoản. [Authority Matrix](https://help.teable.ai/en/basic/authority-matrix), [SSO](https://help.teable.ai/en/basic/sso/overview), [Teable audit](https://help.teable.ai/en/basic/admin-panel/audit-log), [Airtable audit](https://support.airtable.com/articles/1665450173-accessing-enterprise-audit-logs-in-airtable).

### 4.5. Bộ lọc không phải backend ACL

Lọc, ẩn cột và giới hạn lựa chọn linked record là các chức năng UX khác nhau. Đặc biệt, tài liệu Airtable nói giới hạn linked picker không bảo đảm các đường automation/API cùng áp điều kiện chọn. Bản thiết kế 3DDK phải tự xác định ràng buộc nào là business invariant và kiểm ở server, thay vì dựa vào view. [Airtable picker/view limit](https://support.airtable.com/articles/8324991167-limiting-linked-record-selection-to-a-view-in-airtable), [Dynamic linked filters](https://support.airtable.com/articles/4666474548-dynamic-filtering-in-linked-record-fields).

### 4.6. Quan hệ, lookup, rollup và formula tạo đồ thị phụ thuộc

Tài liệu thể hiện Lookup/Rollup dựa vào Link; formula theo record khác công thức tùy từng cell Excel. 3DDK cần dependency graph, type result, xử lý deleted source/cycle, giới hạn fanout/computation trước khi cam kết những tính năng này. [Teable Link](https://help.teable.ai/en/basic/field/link), [Lookup](https://help.teable.ai/en/basic/field/lookup), [Rollup](https://help.teable.ai/en/basic/field/rollup), [Formula](https://help.teable.ai/en/basic/field/formula), [Airtable formulas](https://support.airtable.com/articles/8772429326-getting-started-with-the-airtable-formula-field).

Đây là lý do nhóm tính năng này đặt P2, không phải thêm vài nhãn field và chấp nhận JSON tùy ý.

### 4.7. Webhook hai chiều không phải cùng một cơ chế

Teable có webhook **nhận vào** để kích hoạt automation; Airtable Webhooks API mô tả thông báo **thay đổi base ra tích hợp**. Một dấu “có webhook” trong bảng so sánh không đủ để cho rằng contract/delivery/retry giống nhau. [Teable webhook trigger](https://help.teable.ai/en/basic/automation/trigger/external/webhook-received), [Airtable Webhooks API](https://support.airtable.com/articles/7385030819-airtable-webhooks-api-overview).

3DDK cần quyết định chiều, danh sách event, authentication/signature, retry, idempotency và SSRF policy trước khi triển khai; chưa có yêu cầu tích hợp cụ thể thì không cần đưa general workflow engine vào lõi.

### 4.8. Dashboard, history, backup là những khả năng khác nhau

Teable nói standalone Dashboard cũ đã được thay bằng App Builder và không tiếp tục phát triển; điều đó không buộc 3DDK xây AI App Builder để có KPI. Airtable Dashboard là một interface layout trên dữ liệu nguồn. [Teable legacy dashboard](https://help.teable.ai/en/deploy/dashboard), [Airtable Dashboard](https://support.airtable.com/articles/5415824889-airtable-interface-layout-dashboard).

Record history phục vụ nhìn thay đổi, không thay thế backup. Airtable nói history không có export endpoint tương ứng và snapshot restore không giữ revision history. Bài snapshot có một câu “replaces” không thống nhất với FAQ nói tạo base mới; báo cáo giữ đây là điểm cần xác minh bằng thử nghiệm trong tài khoản được ủy quyền, không lấy câu mâu thuẫn làm cam kết phục hồi. [Airtable record history](https://support.airtable.com/articles/3516802427-record-level-revision-history-in-airtable), [Snapshots](https://support.airtable.com/articles/2163132802-taking-and-restoring-snapshots-in-airtable).

## 5. Quyết định kiến trúc rút ra cho 3DDK

Các quyết định sau là **đề xuất độc lập**, chưa viết code/migration:

1. Giữ stack và mô hình `ws_*` JSONB hiện có; không fork Teable hoặc thay hệ thống bằng Next.js/NestJS/Prisma/Redis vì source tham khảo sử dụng chúng.
2. Hoàn thiện transaction/idempotency/quyền/cache trước view nâng cao. Tạo mẫu bảng phải atomic; mời/chia quyền và query cần test xuyên không gian.
3. Thêm saved views trên cùng nguồn record; query server có AST validation, sort ổn định, pagination/projection. Room cache phải ghi nhận độ đầy đủ; không báo search/filter toàn bảng khi mới có một trang.
4. USER field lưu UID, không lưu tên làm người phụ trách; dùng lại typed editor và validator.
5. Record mutation tiếp tục optimistic concurrency và requestId. UI conflict giữ nháp, cho tải lại và quyết định ghi, không hứa tự merge mọi thay đổi offline.
6. Audit theo transaction và history có quyền/retention rõ. Realtime có thể là invalidation/refresh trước khi có live cell editing; không hứa mức đồng bộ giống ShareDB.
7. Với mục tiêu khoảng 100 tài khoản và khoảng 20 người sửa đồng thời theo cách diễn giải câu trả lời mới của người dùng, nghiệm thu vẫn cần workload và dữ liệu đo được. Số field/row, attachment storage, latency target và quyền phòng ban chưa được xác định. Đây là mục tiêu thử nghiệm, chưa có benchmark 3DDK/Teable/Airtable trong đợt này.

Chi tiết thống nhất trong [TARGET_ARCHITECTURE.md](TARGET_ARCHITECTURE.md), [DATABASE_DESIGN.md](DATABASE_DESIGN.md), [API_SPECIFICATION.md](API_SPECIFICATION.md), [UI_UX_SPECIFICATION.md](UI_UX_SPECIFICATION.md).

## 6. Kế hoạch kiểm chứng thực nghiệm sau phê duyệt

Chỉ dùng tài khoản và dữ liệu thử do chủ dự án cho phép, không chứa dữ liệu công ty thật. Từng ca lưu sản phẩm/edition/version, ngày, steps, expected/actual, screenshot hoặc trace đã loại credential, cùng kết luận đạt/chưa đạt. Bảng này là **ca thử dự kiến, chưa chạy**.

| Ca | Thao tác cần thử | Điều cần chứng minh và bằng chứng cần lưu |
|---|---|---|
| V01 Quan hệ hierarchy | Tạo workspace/base/table/record; mời người thứ hai | Hai người thấy đúng scope, ID nhất quán; ảnh UI và response đã redact |
| V02 Nhiều view | Tạo Grid + Kanban trên cùng bảng; đổi trạng thái ở một view | Cùng record phản ánh ở view còn lại; cấu hình view không nhân bản dữ liệu |
| V03 Field rename/config | Đổi tên cột/options có filter và record cũ | Giá trị và tham chiếu không mất; ghi rõ semantics conversion/options |
| V04 Filter/sort/group | Null, tiếng Việt, ngày, nhiều điều kiện, trang thứ hai | Sort/filter trên toàn query, group counts không chỉ tính trang đã tải |
| V05 Ghi đồng thời | Hai phiên cùng mở một record, sửa cùng và khác field | Response conflict/merge thực tế và nội dung nào còn; không suy từ source enum |
| V06 Thu hồi quyền | Editor đang mở bảng bị hạ viewer/remove | Read/mutation/export/link/file/history đều tôn trọng quyền mới; cache behavior ghi rõ |
| V07 Link/compute | Link một/nhiều; đổi/xóa nguồn; cycle và phép chia 0 | UI lỗi/type propagation, update timing, quyền nguồn; không copy dữ liệu thật |
| V08 Comments/push | Mention, reply, logout thiết bị, revoke người nhận | Đúng recipient, deep link kiểm quyền, không lộ body qua push |
| V09 Automation/webhook | Test event trùng, lỗi action, tắt rule, rate limit | Delivery/retry thực tế, run log và side effects có bị lặp; không gọi dịch vụ trả phí |
| V10 Backup/restore | Export/snapshot dữ liệu giả và restore bản sao | Record/config/file/history được gì, thiếu gì; tuyệt đối không restore đè production |

REA nếu được duyệt chỉ dùng khi loại artifact/capability của công cụ phù hợp và nguồn truy cập hợp lệ. Public docs/source hiện đã đủ cho phân tích khái niệm; công cụ không tạo bằng chứng runtime nếu chưa chạy. Xem [REA_SETUP_PLAN.md](REA_SETUP_PLAN.md) để phân biệt công cụ sẵn có, bước cần duyệt và giới hạn.

## 7. Các câu hỏi còn mở

- Số record/cột lớn nhất dự kiến, tỷ lệ đọc/ghi và độ trễ chấp nhận để chọn benchmark cho công ty 100 người, mục tiêu tạm thời khoảng 20 editor đồng thời.
- Một công ty dùng một không gian hay nhiều phòng ban có dữ liệu kín? Có cần giới hạn theo base/row/field ngay P0 hay ba role workspace đủ cho pilot?
- Viewer có được export? Cần lưu audit/record history bao lâu và ai đọc được giá trị cũ?
- Hạn mức file, tổng storage và ngân sách vận hành cho 100 người; gói miễn phí hiện tại có còn là điều kiện cứng?
- Public forms, webhook, SSO, formulas/relations và snapshot UI có là phạm vi phê duyệt hay chỉ nhóm nghiên cứu P2?

Thiếu các câu trả lời này không cản việc lập kiến trúc có giới hạn, nhưng chặn cam kết chịu tải, quyền phòng ban và ngân sách. Không tự coi đề xuất là quyết định đã duyệt.

## 8. Danh mục nguồn kiểm tra lại

Các link cụ thể ở mỗi mục inventory là bằng chứng chính. Danh mục này giúp mở lại theo nhóm, không thay thế câu dẫn gần claim.

| Nhóm | Nguồn Teable | Nguồn Airtable |
|---|---|---|
| Điểm xuất phát/định vị | [Trang sản phẩm](https://teable.ai/), [repository](https://github.com/teableio/teable), [docs index](https://help.teable.ai/llms.txt) | [Trang sản phẩm](https://airtable.com/), [support](https://support.airtable.com/) |
| Hierarchy | [Space](https://help.teable.ai/en/basic/space), [Base/Project](https://help.teable.ai/en/basic/base), [Table](https://help.teable.ai/en/basic/table), [Record](https://help.teable.ai/en/basic/record) | [Bases](https://support.airtable.com/articles/7403739720-creating-and-managing-airtable-bases), [workspace roles](https://support.airtable.com/articles/3990016353-airtable-workspace-permissions) |
| Types/config | [Fields](https://help.teable.ai/en/basic/field), [User](https://help.teable.ai/en/basic/field/user) | [Catalog](https://support.airtable.com/articles/4808831313-supported-field-types-in-airtable-overview), [configuration](https://support.airtable.com/articles/2361876459-field-type-overview) |
| Views | [Grid](https://help.teable.ai/en/basic/view/grid), [Kanban](https://help.teable.ai/en/basic/view/kanban), [Calendar](https://help.teable.ai/en/basic/view/calendar), [Gallery](https://help.teable.ai/en/basic/view/gallery), [Form](https://help.teable.ai/en/basic/view/form) | [Views](https://support.airtable.com/articles/5189551686-getting-started-with-airtable-views), [Kanban](https://support.airtable.com/articles/9686375912-getting-started-with-airtable-kanban-views), [Calendar](https://support.airtable.com/articles/1834189958-getting-started-with-airtable-calendar-views), [Gallery](https://support.airtable.com/articles/6624642620-getting-started-with-airtable-gallery-views), [Form](https://support.airtable.com/articles/8588280258-getting-started-with-airtable-form-views) |
| Query/edit | [Toolbar](https://help.teable.ai/en/basic/view/toolbar), [Record API](https://help.teable.ai/en/api-doc/record/get) | [Filter](https://support.airtable.com/articles/9290731839-filtering-records-using-conditions), [sort](https://support.airtable.com/articles/8716174546-sorting-records-in-airtable-views), [group](https://support.airtable.com/articles/7361540623-Grouping-records-in-Airtable), [batch](https://support.airtable.com/articles/6559274760-batch-update-extension) |
| Relations/compute | [Link](https://help.teable.ai/en/basic/field/link), [Lookup](https://help.teable.ai/en/basic/field/lookup), [Rollup](https://help.teable.ai/en/basic/field/rollup), [Formula](https://help.teable.ai/en/basic/field/formula) | [Relationships](https://support.airtable.com/articles/1484376934-understanding-linked-record-relationships-in-airtable), [Lookup](https://support.airtable.com/articles/8927322518-lookup-field-overview), [Rollup](https://support.airtable.com/articles/7497685062-rollup-field-overview), [Formula](https://support.airtable.com/articles/8772429326-getting-started-with-the-airtable-formula-field) |
| Permissions | [Roles](https://help.teable.ai/en/basic/space/space-permission), [Authority Matrix](https://help.teable.ai/en/basic/authority-matrix), [SSO](https://help.teable.ai/en/basic/sso/overview) | [Overview](https://support.airtable.com/articles/3994336578-airtable-permissions-overview), [base](https://support.airtable.com/articles/5450095623-airtable-base-permissions), [field/table](https://support.airtable.com/articles/2296468758-airtable-field-and-table-editing-permissions), [interface](https://support.airtable.com/articles/2193541120-interface-designer-permissions) |
| Collaboration | [Comments](https://help.teable.ai/en/basic/record/comment), [history](https://help.teable.ai/en/basic/record/record-history) | [Comments](https://support.airtable.com/articles/1999477400-commenting-in-airtable), [notifications](https://support.airtable.com/articles/8089803354-managing-airtable-notifications), [history](https://support.airtable.com/articles/3516802427-record-level-revision-history-in-airtable) |
| Files/data transfer | [Attachment](https://help.teable.ai/en/basic/field/attachment), [import](https://help.teable.ai/en/basic/table/import), [export](https://help.teable.ai/en/basic/table/export) | [Attachment](https://support.airtable.com/articles/9139007724-attachment-fields-in-airtable), [import/export](https://support.airtable.com/articles/9412806945-importing-third-party-data-into-airtable) |
| Integrations | [Automation](https://help.teable.ai/en/basic/automation), [webhook](https://help.teable.ai/en/basic/automation/trigger/external/webhook-received), [PAT](https://help.teable.ai/en/api-doc/token) | [Automation](https://support.airtable.com/articles/3669392397-getting-started-with-airtable-automations), [webhooks](https://support.airtable.com/articles/7385030819-airtable-webhooks-api-overview), [API](https://support.airtable.com/articles/6292134965-getting-started-with-airtable-s-web-api), [limits](https://support.airtable.com/articles/7735693959-managing-api-call-limits-in-airtable) |
| Dashboard/ops | [Legacy dashboard status](https://help.teable.ai/en/deploy/dashboard), [audit](https://help.teable.ai/en/basic/admin-panel/audit-log) | [Dashboard](https://support.airtable.com/articles/5415824889-airtable-interface-layout-dashboard), [audit](https://support.airtable.com/articles/1665450173-accessing-enterprise-audit-logs-in-airtable), [snapshots](https://support.airtable.com/articles/2163132802-taking-and-restoring-snapshots-in-airtable) |

Tài liệu online có thể đổi. Link source cố định giữ được revision nghiên cứu; docs không được coi là versioned source code. Các URL `help.teable.io` cũ được kiểm tra có chuyển sang `help.teable.ai`; báo cáo dùng đích chính thức hiện tại.

## 9. Bộ tài liệu và kiểm tra đầu ra

| File | Nội dung để người dùng duyệt |
|---|---|
| [PROJECT_CURRENT_STATE.md](PROJECT_CURRENT_STATE.md) | Cấu trúc repo, công nghệ, schema/API/quyền thật, tái sử dụng và bằng chứng kiểm thử hiện có |
| [REA_SETUP_PLAN.md](REA_SETUP_PLAN.md) | Runtime, công cụ thực có, cách nối Codex, các bước cài/config chưa được thực hiện |
| [FEATURE_INVENTORY.md](FEATURE_INVENTORY.md) | 37 mục tính năng, mỗi mục đủ chín nội dung phân tích |
| [REVERSE_ENGINEERING_REPORT.md](REVERSE_ENGINEERING_REPORT.md) | Phương pháp, nguồn, giới hạn và kết quả đối chiếu |
| [FEATURE_GAP_ANALYSIS.md](FEATURE_GAP_ANALYSIS.md) | Đã có/chưa có và ánh xạ vào các task |
| [TARGET_ARCHITECTURE.md](TARGET_ARCHITECTURE.md) | Kiến trúc giữ stack hiện có, query/view/offline/quyền/version |
| [DATABASE_DESIGN.md](DATABASE_DESIGN.md) | Thiết kế dữ liệu và lộ trình tương thích, không phải migration đã chạy |
| [API_SPECIFICATION.md](API_SPECIFICATION.md) | Hợp đồng đề xuất, validation, quyền, lỗi và tương thích APK |
| [UI_UX_SPECIFICATION.md](UI_UX_SPECIFICATION.md) | Màn hình và tương tác Android dự kiến, trạng thái UI và QA |
| [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | 32 hạng mục P0/P1/P2 có mục đích, phụ thuộc, nghiệm thu, test, rủi ro và rollback |

Đã kiểm tra cấu trúc: đủ 10 file, 37 mục × 9 nội dung, 32 task, đường dẫn nội bộ tồn tại và code fences cân bằng. Đã rà chéo ưu tiên, phụ thuộc, thứ tự cột mặc định/view và giới hạn tương thích APK cũ với type mới. Đây là kiểm tra tài liệu; không phải chạy test tính năng hoặc benchmark.

Đợt nghiên cứu không sửa các file sản phẩm đã tracked, không chạy migration/ghi dữ liệu cloud, không cài REA hay sửa cấu hình toàn cục. Các file đề xuất DOCX và hướng dẫn test có sẵn vẫn được giữ. Chưa commit/push bộ nghiên cứu này; tài liệu đang ở checkout cục bộ để duyệt.

**Dừng chờ người dùng duyệt trước khi triển khai.** Những câu hỏi về số dòng, tỷ lệ đọc/ghi ở mốc khoảng 20 người, deadline, quyền phòng ban, workflow duyệt và ngân sách còn mở; không lấy các con số thử nghiệm trong thiết kế thay cho câu trả lời đó.
