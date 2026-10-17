# Danh mục tính năng Teable/Airtable và phương án cho 3DDK

Ngày nghiên cứu: 17/10/2026. Đây là phân tích tài liệu công khai và một số phần mã nguồn Teable, không phải chứng nhận đã kiểm thử hai sản phẩm. Không đăng nhập, không gọi API tài khoản của người khác, không chạy REA. Phạm vi và hạn chế nằm trong [REVERSE_ENGINEERING_REPORT.md](REVERSE_ENGINEERING_REPORT.md).

## Cách đọc

- **Xác minh**: hành vi hoặc cấu trúc được nguồn dẫn trực tiếp mô tả. Không có nghĩa đã thao tác trực tiếp hay có trong mọi gói dịch vụ.
- **Đề xuất**: thiết kế tương đương cho 3DDK, không khẳng định đây là nội bộ Airtable/Teable. Các mục 2–5 và 9 dưới đây là thiết kế dự kiến cho 3DDK; chỉ mục 6 mô tả sản phẩm tham khảo.
- **Độ khó**: Thấp / Vừa / Cao / Rất cao, xét cả Android, API, database, kiểm thử, vận hành. **P0** là lõi tin cậy; **P1** là cộng tác và thao tác thường ngày; **P2** cần phê duyệt sau lõi. Ưu tiên không tự động mở rộng phạm vi.
- Giữ Kotlin + XML/Fragments + MVVM/StateFlow + Room + Retrofit/Gson/Coroutines; Firebase Auth; Supabase Edge API + PostgreSQL. Dùng lại `ws_*`, `ws_records.cells` JSONB có khóa là field UUID, `version`, `requestId`. Các tên bảng mới dưới đây là khái niệm dự kiến; [DATABASE_DESIGN.md](DATABASE_DESIGN.md) và [API_SPECIFICATION.md](API_SPECIFICATION.md) là bản thiết kế cụ thể.
- Mục tiêu công ty khoảng 100 tài khoản, với câu trả lời hiện tại được diễn giải là khoảng 20 người sửa đồng thời; đây là mục tiêu cần đo, không phải năng lực đã đạt. Số record/cột còn chưa rõ. Lọc view không thay thế quyền đọc dữ liệu tại backend.
- Độ phủ nguồn cho hai tài liệu inventory/report: đã mở trực tiếp **30 trang tài liệu Teable, index tài liệu, 40 bài hỗ trợ Airtable, 6 file kiến trúc Teable và 2 file LICENSE**. Các nguồn tính năng được dùng không còn chỉ dựa trên search snippet. Đã đối chiếu đoạn liên quan đến claim, không tuyên bố đọc toàn bộ repository hay kiểm thử runtime; AGPL text đầy đủ chưa được rà soát. Chi tiết ở mục 1 của báo cáo.

## Không gian và dữ liệu

### F01 — Workspace

1. **Giá trị:** tách dữ liệu, thành viên và trách nhiệm quản lý theo công ty/đội.
2. **UI:** chọn không gian → tạo tên → vào danh sách dự án; quản lý thành viên ở trang riêng.
3. **Dữ liệu:** tái sử dụng `ws_workspaces`, `ws_members`, UID và role; thêm trạng thái membership nếu cần thu hồi mềm.
4. **API/backend:** list chỉ những không gian được phép; tạo không gian và owner cùng giao dịch; kiểm tra membership trên mọi đường truy cập con.
5. **Ngoại lệ:** trùng tên khác không gian được phép; không bỏ owner cuối cùng; chuyển tài khoản phải xóa cache cũ; không public dữ liệu khi đăng ký.
6. **Bằng chứng:** [Teable Space](https://help.teable.ai/en/basic/space) mô tả tạo/quản lý không gian; [Airtable workspace permissions](https://support.airtable.com/articles/3990016353-airtable-workspace-permissions) mô tả quyền trên các base trong workspace.
7. **Mức chắc chắn:** mô hình người dùng được tài liệu xác nhận; cơ chế lưu và cách ly 3DDK là đề xuất.
8. **Độ khó/rủi ro/ưu tiên:** Vừa / rò dữ liệu giữa công ty / **P0**.
9. **Áp dụng:** giữ màn hình không gian và API hiện có; bổ sung ca thử xuyên không gian, khôi phục phiên và thu hồi quyền.

### F02 — Base / Dự án

1. **Giá trị:** tập hợp bảng phục vụ cùng một dự án.
2. **UI:** trong không gian → tạo dự án → chọn dự án → xem bảng; hiển thị đường dẫn quay lại.
3. **Dữ liệu:** `ws_bases.workspace_id`; tên/metadata và version nếu cho sửa đồng thời.
4. **API/backend:** tạo/list theo không gian; đổi tên có quyền owner; archive chỉ khi đã xác định cách xử lý bảng và liên kết.
5. **Ngoại lệ:** không di chuyển xuyên công ty tự động; thao tác copy phải kiểm tra người nhận và không copy lời mời.
6. **Bằng chứng:** [Teable Project](https://help.teable.ai/en/basic/base) nói tên UI hiện là Project, API vẫn `baseId`; [Airtable bases](https://support.airtable.com/articles/7403739720-creating-and-managing-airtable-bases) mô tả base chứa nhiều bảng.
7. **Mức chắc chắn:** tầng khái niệm đã xác minh; không suy ra hai hãng dùng cùng schema.
8. **Độ khó/rủi ro/ưu tiên:** Vừa / xóa hoặc di chuyển làm hỏng tham chiếu / **P0** giữ create/list hiện có; rename/archive **P1-10**; copy/move **P2**.
9. **Áp dụng:** giữ `ws_bases` và navigation hiện tại; tên tiếng Việt cần nhất quán, không đổi API chỉ để giống nhãn Teable mới.

### F03 — Table

1. **Giá trị:** định nghĩa loại dữ liệu như công việc, dự án, nhân sự.
2. **UI:** tạo bảng trống hoặc mẫu công việc → mở Grid mặc định → thêm cột.
3. **Dữ liệu:** `ws_tables`, `ws_fields`, view mặc định; mẫu dùng field ID thực, không dựa vào tên cột.
4. **API/backend:** tạo bảng + cột mẫu + view + audit trong một giao dịch, cùng requestId.
5. **Ngoại lệ:** timeout rồi gửi lại không tạo hai bảng; không để bảng mẫu thiếu cột; không xóa cột dữ liệu khi tạo view.
6. **Bằng chứng:** [Teable Table](https://help.teable.ai/en/basic/table) mô tả tạo/import/quản lý bảng; [Airtable Grid](https://support.airtable.com/articles/7905594155-airtable-grid-view) xác nhận mỗi bảng có Grid mặc định.
7. **Mức chắc chắn:** hành vi tạo bảng có nguồn; transaction mẫu là thiết kế 3DDK.
8. **Độ khó/rủi ro/ưu tiên:** Vừa / tạo dang dở, mất metadata / **P0**.
9. **Áp dụng:** tái sử dụng màn tạo bảng, chuyển chuỗi tạo cột ở Android thành một mutation server sau khi được duyệt.

### F04 — Record

1. **Giá trị:** một công việc hoặc đối tượng hoàn chỉnh, cùng ID ở mọi view.
2. **UI:** thêm việc → form đúng kiểu cột → lưu; chạm dòng mở chi tiết và giữ nháp khi lỗi.
3. **Dữ liệu:** `ws_records.cells`, `version`, timestamp, `archived_at`; metadata người tạo/sửa và lịch sử riêng.
4. **API/backend:** create/patch/archive; mutation nhận expectedVersion/requestId; trả bản đã xác nhận, không coi cache là đã lưu server.
5. **Ngoại lệ:** unknown field ID, null/empty, cột bị sửa giữa lúc soạn, archived record, xung đột, timeout sau commit.
6. **Bằng chứng:** [Teable Record](https://help.teable.ai/en/basic/record) mô tả dòng/cell/chi tiết; [Airtable revision history](https://support.airtable.com/articles/3516802427-record-level-revision-history-in-airtable) mô tả activity khi mở rộng record.
7. **Mức chắc chắn:** UX record được xác minh; thuật toán chống ghi đè của hai hãng chưa được chứng minh ở đây.
8. **Độ khó/rủi ro/ưu tiên:** Cao / ghi đè dữ liệu và nháp / **P0**.
9. **Áp dụng:** giữ RecordEditor, Room draft và optimistic concurrency; bổ sung refresh an toàn và tình huống quyền bị thu hồi.

## Field types và cấu hình

### F05 — Field types

1. **Giá trị:** nhập đúng dữ liệu, chọn người phụ trách thay vì gõ tên tự do.
2. **UI:** thêm cột → chọn kiểu → cấu hình → xem trước editor; hiện chỉ những kiểu đã hỗ trợ thực sự.
3. **Dữ liệu:** mở rộng `ws_fields.type/options`; giá trị JSON phải có quy ước riêng cho text, number, date, datetime, checkbox, select, multiselect, user, attachment và computed.
4. **API/backend:** validation theo type tại server; USER chứa UID được cấp quyền trong không gian; client gửi type lạ bị từ chối.
5. **Ngoại lệ:** số không hữu hạn, dấu phẩy thập phân, date-only khác timestamp, user đã rời nhóm, unknown type từ phiên bản app mới.
6. **Bằng chứng:** [Teable field catalog](https://help.teable.ai/en/basic/field) có nhóm cơ bản/nâng cao/system; [Airtable supported types](https://support.airtable.com/articles/4808831313-supported-field-types-in-airtable-overview) có thêm currency, duration, barcode, URL/email/phone, count; [Teable User](https://help.teable.ai/en/basic/field/user) chọn thành viên.
7. **Mức chắc chắn:** danh mục theo tài liệu, không cam kết đủ mọi kiểu của mọi edition. Thiết kế JSONB là của 3DDK.
8. **Độ khó/rủi ro/ưu tiên:** Cao / schema và editor không thống nhất / **P0** kiểu hiện có + USER; kiểu phụ **P1/P2**.
9. **Áp dụng:** mở rộng validator và editor hiện có, không tạo framework plugin; computed read-only và file upload được thực hiện ở giai đoạn riêng.

### F06 — Field configuration

1. **Giá trị:** đổi tên, thứ tự, lựa chọn, cách trình bày mà không mất giá trị đã nhập.
2. **UI:** menu cột → sửa tên/options → xem ảnh hưởng → lưu; chuyển kiểu nguy hiểm cần preview và xác nhận.
3. **Dữ liệu:** field UUID ổn định; P0 giữ SELECT labels để tương thích, option ID/label/color/order là nâng cấp P1 sau duyệt; field version; thứ tự/ẩn cột theo view, tách khỏi thứ tự mặc định catalog.
4. **API/backend:** patch metadata với expectedVersion; xác thực quyền schema; type conversion là operation riêng có kiểm tra phụ thuộc.
5. **Ngoại lệ:** trùng tên sau trim, xóa option đang dùng, filter tham chiếu option cũ, đổi kiểu mất dữ liệu, primary field bị xóa.
6. **Bằng chứng:** [Teable sửa/chuyển kiểu](https://help.teable.ai/en/basic/field) cảnh báo mất dữ liệu; [Airtable field overview](https://support.airtable.com/articles/2361876459-field-type-overview) mô tả cấu hình theo kiểu và phụ thuộc.
7. **Mức chắc chắn:** nhu cầu và UI có nguồn; option ID/version là đề xuất, không khẳng định Airtable lưu như vậy.
8. **Độ khó/rủi ro/ưu tiên:** Cao / mất dữ liệu khi conversion / **P0** củng cố validation/type/schema version; **P1-03** sửa metadata/đổi tên/options; conversion **P2**.
9. **Áp dụng:** giữ cột UUID trong cells; nâng cấp select từ tên chuỗi phải có migration không phá bảng cũ.

## Views dùng chung dữ liệu

### F07 — Grid

1. **Giá trị:** đọc/chỉnh dữ liệu dày, so sánh nhiều công việc.
2. **UI:** P0 chọn view → Grid cuộn → chạm mở chi tiết record; toolbar tìm/lọc/sort dễ thấy. Xem dạng thẻ theo Gallery **P1-08** nếu được duyệt, không phải chế độ P0 đã có.
3. **Dữ liệu:** `ws_views(type=GRID, table_id, config, version)`; config chứa field IDs, độ rộng, thứ tự, hidden fields.
4. **API/backend:** list/get/update view; query record có pagination và projection; chỉ trả trường người dùng được đọc.
5. **Ngoại lệ:** cột nhiều/chữ lớn, viewport nhỏ, xung đột config, field đã bị xóa, cache mới có một phần bảng.
6. **Bằng chứng:** [Teable Grid](https://help.teable.ai/en/basic/view/grid) có reorder/freeze/độ cao dòng; [Airtable Grid](https://support.airtable.com/articles/7905594155-airtable-grid-view) là view mặc định.
7. **Mức chắc chắn:** hành vi có tài liệu; không đo FPS hay chứng minh khả năng triệu dòng bằng benchmark.
8. **Độ khó/rủi ro/ưu tiên:** Cao / hiệu năng và cache thiếu dòng / **P0** saved Grid + query; polish **P1**.
9. **Áp dụng:** giữ RecyclerView/View Binding; không nhúng React grid hoặc thay Android bằng web app.

### F08 — Kanban

1. **Giá trị:** theo dõi việc theo Chưa làm/Đang làm/Xong.
2. **UI:** chọn view Kanban → chọn cột trạng thái → xem stack → mở thẻ hoặc chuyển trạng thái bằng menu/kéo.
3. **Dữ liệu:** view config `stackFieldId`, `cardFieldIds`; cùng record IDs với Grid.
4. **API/backend:** query theo group có count và trang riêng; chuyển stack dùng record patch expectedVersion.
5. **Ngoại lệ:** trạng thái trống, option đã xóa, field read-only, viewer kéo thẻ, sort đang bật, group nhiều dòng chưa tải.
6. **Bằng chứng:** [Teable Kanban](https://help.teable.ai/en/basic/view/kanban) kéo sang stack thay giá trị; [Airtable Kanban](https://support.airtable.com/articles/9686375912-getting-started-with-airtable-kanban-views) giới hạn nhóm bởi single select/user/single linked record, không phải mọi kiểu.
7. **Mức chắc chắn:** khác biệt này có nguồn; không giả định Kanban Teable và Airtable hoàn toàn đồng nhất.
8. **Độ khó/rủi ro/ưu tiên:** Cao / đổi trạng thái nhầm và tải hết bảng / **P1**.
9. **Áp dụng:** RecyclerView thẻ Material, một field SELECT để khởi đầu; luôn có nút chuyển trạng thái thay thế thao tác kéo.

### F09 — Calendar

1. **Giá trị:** nhận biết hạn, lịch làm và việc chưa có ngày.
2. **UI:** chọn cột ngày → tháng/ngày → chạm việc mở editor → đổi hạn bằng date picker.
3. **Dữ liệu:** view config start/end/title field IDs, timezone; record date không copy sang bảng khác.
4. **API/backend:** query khoảng ngày giao với thời gian hiển thị; sửa hạn dùng cùng record endpoint.
5. **Ngoại lệ:** việc nhiều ngày, end trước start, ngày trống, DST/timezone, datetime và date-only, cột ngày bị đổi kiểu.
6. **Bằng chứng:** [Teable Calendar](https://help.teable.ai/en/basic/view/calendar) cấu hình date fields; [Airtable Calendar](https://support.airtable.com/articles/1834189958-getting-started-with-airtable-calendar-views) thay ngày phản ánh lại dữ liệu các view.
7. **Mức chắc chắn:** hành vi ngày và UI được mô tả; engine calendar của hãng chưa được khảo sát.
8. **Độ khó/rủi ro/ưu tiên:** Cao / lệch ngày/hạn / **P1** lịch tháng và danh sách ngày; resize nâng cao **P2**.
9. **Áp dụng:** ưu tiên widget/platform đang có và XML; giữ date-only `YYYY-MM-DD`, timestamp UTC riêng.

### F10 — Gallery

1. **Giá trị:** duyệt hồ sơ/tài sản/công việc có ảnh bằng thẻ.
2. **UI:** chọn cover field → lưới thẻ → xem ảnh/chi tiết; placeholder khi file thiếu.
3. **Dữ liệu:** view config coverFieldId/cardFieldIds; attachment metadata tham chiếu file riêng.
4. **API/backend:** tái dùng query/projection; tạo URL tải có thời hạn sau kiểm tra quyền.
5. **Ngoại lệ:** ảnh không hợp lệ, file không phải ảnh, URL hết hạn, record không có cover, cache ảnh sau logout.
6. **Bằng chứng:** [Teable Gallery](https://help.teable.ai/en/basic/view/gallery) cover bằng attachment; [Airtable Gallery](https://support.airtable.com/articles/6624642620-getting-started-with-airtable-gallery-views) thẻ và cover tùy chỉnh.
7. **Mức chắc chắn:** UX tài liệu; chưa kiểm chứng bố cục native mobile của hai bên.
8. **Độ khó/rủi ro/ưu tiên:** Vừa sau attachments / bộ nhớ và file riêng tư / **P1-08**, cover cần P1-06; vẫn là đề xuất chờ duyệt.
9. **Áp dụng:** RecyclerView GridLayoutManager + MaterialCardView + Glide, không cần thư viện giao diện khác.

### F11 — Form

1. **Giá trị:** thu yêu cầu công việc có cấu trúc mà không lộ bảng nguồn.
2. **UI:** chủ bảng chọn trường/required → preview → chia sẻ; người điền gửi và thấy xác nhận, không xem record khác.
3. **Dữ liệu:** view/form config, field order, required, submit policy; share token hash/expiry/revoked nếu mở công khai.
4. **API/backend:** metadata form được phép công khai và submit route riêng; server kiểm required/type/size/rate/idempotency.
5. **Ngoại lệ:** form bị thu hồi, cột đổi, upload dang dở, spam, duplicate submit, link field vô tình tiết lộ bảng khác.
6. **Bằng chứng:** [Teable Form](https://help.teable.ai/en/basic/view/form) submit tạo record, có yêu cầu login; [Airtable Form views](https://support.airtable.com/articles/8588280258-getting-started-with-airtable-form-views) cấu hình trường đầu vào.
7. **Mức chắc chắn:** chức năng có nguồn; form nội bộ trong app khác public web form, cả hai vẫn là đề xuất chờ duyệt.
8. **Độ khó/rủi ro/ưu tiên:** Cao / spam và rò dữ liệu / **P1-08** form nội bộ cho member; public form **P2-06** cần quyết định bề mặt truy cập.
9. **Áp dụng:** tái dùng typed editor cho form nội bộ; public form cần quyết định có thêm bề mặt web, không tự ý đổi nền tảng.

## Truy vấn và chỉnh sửa

### F12 — Filter

1. **Giá trị:** xem việc của tôi/chưa xong/quá hạn mà không nhân bản dữ liệu.
2. **UI:** Lọc → field/operator/value → AND/OR → xem số kết quả → lưu vào view.
3. **Dữ liệu:** AST JSON gồm field ID, operator allowlist, typed value và nhóm điều kiện; timezone cho ngày tương đối.
4. **API/backend:** validate AST theo type và quyền; parameterized query; giới hạn depth/conditions; kết hợp điều kiện quyền trước phân trang.
5. **Ngoại lệ:** null khác chuỗi rỗng, field bị xóa, current-user filter, cache chưa đủ, không dùng filter UI để bảo vệ record.
6. **Bằng chứng:** [Teable toolbar](https://help.teable.ai/en/basic/view/toolbar) filter theo view; [Airtable filtering](https://support.airtable.com/articles/9290731839-filtering-records-using-conditions) điều kiện theo kiểu và AND/OR.
7. **Mức chắc chắn:** cú pháp UX đã xác minh; AST/compiler 3DDK là đề xuất.
8. **Độ khó/rủi ro/ưu tiên:** Cao / SQL injection hoặc lọc sai / **P0** điều kiện lõi; nâng cao sau.
9. **Áp dụng:** nâng lọc local hiện có thành query server, offline chỉ lọc tập đã tải và ghi rõ phạm vi.

### F13 — Sort

1. **Giá trị:** xếp hạn gần nhất, mức ưu tiên hoặc người phụ trách.
2. **UI:** Sắp xếp → field + chiều → thêm quy tắc → lưu; hiển thị sort đang áp dụng.
3. **Dữ liệu:** view sort array field ID/direction/null placement; tie-break bằng record ID.
4. **API/backend:** sort trên tập server trước LIMIT; cursor chứa khóa sort hợp lệ; không nhận SQL biểu thức từ client.
5. **Ngoại lệ:** null, dấu tiếng Việt, chuỗi số, option order, chỉnh sort giữa các trang, manual reorder trong auto-sort.
6. **Bằng chứng:** [Teable toolbar](https://help.teable.ai/en/basic/view/toolbar) sort độc lập view; [Airtable sorting](https://support.airtable.com/articles/8716174546-sorting-records-in-airtable-views) nhiều khóa và auto/manual sort.
7. **Mức chắc chắn:** hành vi được mô tả; collation và cursor 3DDK cần quy định/kiểm thử riêng.
8. **Độ khó/rủi ro/ưu tiên:** Vừa / bỏ/lặp dòng khi paging / **P0**.
9. **Áp dụng:** tái dùng lựa chọn hạn hiện tại, thay giả định tên cột bằng UUID trong view config.

### F14 — Group

1. **Giá trị:** thấy phân bố công việc theo trạng thái hoặc thành viên.
2. **UI:** Nhóm theo → field → nhóm có count → mở/thu gọn → vào danh sách.
3. **Dữ liệu:** view group field IDs/direction; collapsed state cá nhân; server group counts.
4. **API/backend:** aggregate sau quyền/filter; cursor cho record trong group, không tự đếm chỉ các dòng đã tải.
5. **Ngoại lệ:** giá trị trống/multiple values, nhóm rỗng, field computed, hàng bị chuyển group bởi người khác.
6. **Bằng chứng:** [Teable toolbar](https://help.teable.ai/en/basic/view/toolbar) nhiều cấp; [Airtable grouping](https://support.airtable.com/articles/7361540623-Grouping-records-in-Airtable) tối đa ba cấp và kéo giữa nhóm đổi giá trị.
7. **Mức chắc chắn:** hành vi tài liệu; P1 3DDK chỉ một cấp trước, không hứa tương đương ba cấp.
8. **Độ khó/rủi ro/ưu tiên:** Cao / count sai hoặc aggregate lộ dữ liệu / **P1**.
9. **Áp dụng:** RecyclerView section header, reuse cùng query engine của Grid/Kanban.

### F15 — Search

1. **Giá trị:** tìm nhanh việc trong bảng lớn.
2. **UI:** nhập từ khóa → debounce → kết quả và nút xóa; offline báo tìm trong dữ liệu đã tải.
3. **Dữ liệu:** không sửa record; query text/field scope; chỉ mục theo nhu cầu đo được.
4. **API/backend:** search thuộc query endpoint, quyền/filter/sort nhất quán; cap độ dài và timeout.
5. **Ngoại lệ:** Unicode, có/không dấu, ký tự wildcard, record archive, token cũ và response về sai thứ tự.
6. **Bằng chứng:** [Teable query docs](https://help.teable.ai/en/api-doc/record/get) có search cùng truy vấn; [Airtable shortcuts](https://support.airtable.com/articles/7980233311-airtable-keyboard-shortcuts) mô tả find-in-view, không chứng minh tìm toàn doanh nghiệp.
7. **Mức chắc chắn:** phân biệt find-in-view của hãng với full search 3DDK dự kiến; không tuyên bố search engine nội bộ.
8. **Độ khó/rủi ro/ưu tiên:** Vừa / trả kết quả thiếu mà không báo / **P0** trong bảng; toàn công ty **P2**.
9. **Áp dụng:** tái dùng search StateFlow, hủy request cũ và query server có phân trang.

### F16 — Inline editing

1. **Giá trị:** đổi một cell nhanh, ít mở màn hình.
2. **UI:** chạm cell → editor theo type → xác nhận; có dấu đang lưu/lỗi; phím back giữ nháp nếu cần.
3. **Dữ liệu:** patch chứa field IDs đã sửa và expectedVersion của record.
4. **API/backend:** reuse record patch, validate và kiểm quyền; trả version mới; lỗi 409 giữ nội dung nhập.
5. **Ngoại lệ:** đổi schema lúc nhập, bàn phím che cell, xoay máy, nhiều request cùng record, computed read-only.
6. **Bằng chứng:** [Teable Grid](https://help.teable.ai/en/basic/view/grid) edit cell; [Airtable Grid](https://support.airtable.com/articles/7905594155-airtable-grid-view) giao diện bảng, [Airtable mobile differences](https://support.airtable.com/articles/9609457174-airtable-desktop-and-mobile-feature-differences) cảnh báo parity theo bề mặt khác nhau.
7. **Mức chắc chắn:** chứng minh khả năng trên web, chưa chứng minh cùng UX Android.
8. **Độ khó/rủi ro/ưu tiên:** Cao / ghi nhiều lần và mất nháp / **P1**, sau record editor tin cậy.
9. **Áp dụng:** dùng lại bộ editor hiện có trong dialog/bottom sheet, giữ mở chi tiết cho nội dung dài.

### F17 — Bulk editing

1. **Giá trị:** đổi trạng thái/người phụ trách cho nhiều việc.
2. **UI:** chọn dòng → hành động → xem số dòng/giá trị → xác nhận → báo kết quả.
3. **Dữ liệu:** record IDs + expectedVersion từng dòng; requestId chung; giới hạn số lượng.
4. **API/backend:** mutation batch có validation toàn bộ; bản đầu atomic batch nhỏ, một xung đột hủy batch và trả IDs cần tải lại.
5. **Ngoại lệ:** chọn tất cả kết quả khác chọn trang đang xem, mất quyền giữa chừng, retry, field read-only, không coi vòng lặp request là atomic.
6. **Bằng chứng:** [Teable Record](https://help.teable.ai/en/basic/record) chọn/copy/paste nhiều cell; [Airtable batch extension](https://support.airtable.com/articles/6559274760-batch-update-extension) là extension trả phí, khác lựa chọn nhiều cell của grid.
7. **Mức chắc chắn:** bulk capability có tài liệu; atomic semantics của hãng chưa xác minh.
8. **Độ khó/rủi ro/ưu tiên:** Cao / sửa nhầm diện rộng / **P1** trạng thái/assignee giới hạn; workflow batch **P2**.
9. **Áp dụng:** RecyclerView selection mode, dùng lại validation và audit server; luôn hiển thị số việc chịu tác động.

### F18 — Drag & Drop

1. **Giá trị:** sắp cột/thẻ và đổi trạng thái bằng thao tác trực tiếp.
2. **UI:** giữ/kéo có drag handle, vùng đích rõ; menu “Chuyển sang…” tương đương cho trợ năng.
3. **Dữ liệu:** tách view order của cột/record khỏi giá trị business; Kanban move thay stack field.
4. **API/backend:** patch record nếu đổi trạng thái; patch view nếu reorder presentation; concurrency check từng loại.
5. **Ngoại lệ:** auto-sort, computed field, canceled drag, nhiều người reorder, app offline, ngón tay cuộn nhầm.
6. **Bằng chứng:** [Teable Kanban](https://help.teable.ai/en/basic/view/kanban) drag thẻ; [Airtable grouping](https://support.airtable.com/articles/7361540623-Grouping-records-in-Airtable) kéo giữa group thay dữ liệu.
7. **Mức chắc chắn:** semantics thao tác có nguồn; thuật toán rank nội bộ không được khẳng định.
8. **Độ khó/rủi ro/ưu tiên:** Cao / thay dữ liệu ngoài ý muốn / **P1** Kanban move; manual order **P2**.
9. **Áp dụng:** Android ItemTouchHelper khi đủ; không phát mutation ở mọi pixel kéo.

## Liên kết và tính toán

### F19 — Linked records

1. **Giá trị:** nối việc với dự án/khách hàng, tránh nhập lặp.
2. **UI:** thêm kiểu Link → chọn bảng đích → chọn một/nhiều record được phép → mở liên kết.
3. **Dữ liệu:** relation field metadata + bảng cạnh record links có FK, uniqueness; không lưu tên làm khóa.
4. **API/backend:** link picker có pagination và quyền bảng đích; ghi cạnh và audit trong transaction; không cho link xuyên workspace.
5. **Ngoại lệ:** self-link, một/nhiều, target archive/xóa, quyền target thay đổi, cycle trong dependency khác cycle dữ liệu.
6. **Bằng chứng:** [Teable Link](https://help.teable.ai/en/basic/field/link) bốn cardinality và same-Space; [Airtable relationships](https://support.airtable.com/articles/1484376934-understanding-linked-record-relationships-in-airtable) quan hệ và junction table.
7. **Mức chắc chắn:** hành vi đã xác minh; schema cạnh/FK là thiết kế 3DDK.
8. **Độ khó/rủi ro/ưu tiên:** Rất cao / toàn vẹn tham chiếu và leak / **P2**.
9. **Áp dụng:** giữ JSONB cho field thường; quan hệ có bảng riêng, tránh dò toàn cells để join.

### F20 — Lookup

1. **Giá trị:** hiện thông tin từ record được liên kết, tránh copy tay.
2. **UI:** chọn Link + source field → preview → read-only trên record.
3. **Dữ liệu:** field dependency IDs, result type/cardinality; tùy chọn cache tính toán nếu đo hiệu năng yêu cầu.
4. **API/backend:** resolve có quyền ở nguồn; không nhận client ghi computed; refresh khi link hoặc source đổi.
5. **Ngoại lệ:** nhiều link ra array, null, target thiếu quyền, field nguồn xóa/đổi kiểu, fanout lớn.
6. **Bằng chứng:** [Teable Lookup](https://help.teable.ai/en/basic/field/lookup) read-only và dependency; [Airtable Lookup](https://support.airtable.com/articles/8927322518-lookup-field-overview) kết quả array.
7. **Mức chắc chắn:** mô hình hành vi có nguồn; caching/invalidation cần tự thiết kế.
8. **Độ khó/rủi ro/ưu tiên:** Rất cao / trả dữ liệu nguồn vượt quyền / **P2**, sau F19.
9. **Áp dụng:** computed field server, Android chỉ render typed value và cảnh báo khi không tính được.

### F21 — Rollup

1. **Giá trị:** tổng giờ, số việc xong, tổng chi phí theo dự án.
2. **UI:** chọn Link + field + phép tổng hợp → xem trước số → lưu.
3. **Dữ liệu:** dependency metadata, aggregate whitelist, result type và quy tắc null.
4. **API/backend:** aggregate quan hệ trong database sau kiểm quyền; invalidate khi source/link đổi.
5. **Ngoại lệ:** tập rỗng, numeric/text sai kiểu, duplicate links, precision, aggregate tiết lộ nguồn không được đọc.
6. **Bằng chứng:** [Teable Rollup](https://help.teable.ai/en/basic/field/rollup) dựa Link; [Airtable Rollup](https://support.airtable.com/articles/7497685062-rollup-field-overview) view filter nguồn không mặc nhiên giới hạn rollup.
7. **Mức chắc chắn:** hành vi tổng hợp có nguồn; không sao chép giả định filter = security.
8. **Độ khó/rủi ro/ưu tiên:** Rất cao / số sai và fanout / **P2**, sau Link/Lookup/quyền.
9. **Áp dụng:** ban đầu SUM/COUNT/MIN/MAX có giới hạn, không nhận SQL/formula tùy ý.

### F22 — Formula

1. **Giá trị:** tự tính chậm hạn, effort và giá trị dẫn xuất.
2. **UI:** tạo Formula → nhập biểu thức → preview/test → lỗi chỉ đúng vị trí → read-only cells.
3. **Dữ liệu:** expression + AST đã resolve field UUID + dependency edges + result type/version.
4. **API/backend:** parser allowlist, không eval JavaScript/SQL; giới hạn complexity; phát hiện cycle; tính server.
5. **Ngoại lệ:** chia 0, null, timezone, rename field, deleted dependency, chain sâu và tính quá lâu.
6. **Bằng chứng:** [Teable Formula](https://help.teable.ai/en/basic/field/formula) tính theo record và complexity limits; [Airtable Formula](https://support.airtable.com/articles/8772429326-getting-started-with-the-airtable-formula-field) không trực tiếp tham chiếu ô record khác.
7. **Mức chắc chắn:** hành vi có nguồn; AST/evaluator 3DDK chưa tồn tại và cần spike riêng.
8. **Độ khó/rủi ro/ưu tiên:** Rất cao / injection, sai kết quả, chiếm tài nguyên / **P2**.
9. **Áp dụng:** giữ cột hạn/quá hạn tính rõ bằng logic ứng dụng trước; chỉ thêm subset formula đã duyệt, không hứa tương thích toàn bộ cú pháp hãng.

## Tài khoản, quyền và cộng tác

### F23 — Authentication

1. **Giá trị:** người dùng tự đăng ký và có dữ liệu đúng tài khoản.
2. **UI:** đăng ký → xác minh email → tạo/chọn không gian hoặc nhận lời mời; không yêu cầu một nhóm trưởng toàn hệ thống cấp quyền đăng nhập.
3. **Dữ liệu:** Firebase UID là identity; `ws_profiles`; session do SDK quản lý.
4. **API/backend:** xác minh Firebase ID token, issuer/audience/expiry và email-verified; membership là bước riêng sau identity.
5. **Ngoại lệ:** token hết hạn/sai project, email chưa xác minh, đổi tài khoản, email lời mời không khớp token; SSO doanh nghiệp chưa được yêu cầu tự động.
6. **Bằng chứng:** [Teable SSO](https://help.teable.ai/en/basic/sso/overview) có domain verification/OIDC và Business+; [Airtable 2FA](https://support.airtable.com/articles/7887361168-enabling-two-factor-authentication-in-airtable) là lớp xác thực bổ sung.
7. **Mức chắc chắn:** khả năng bảo mật hãng được tài liệu mô tả; không tái dùng token/protocol nội bộ của hãng.
8. **Độ khó/rủi ro/ưu tiên:** Cao / xác thực sai người / **P0** Firebase hiện có; SSO/2FA riêng cần duyệt.
9. **Áp dụng:** giữ Firebase, không thay auth stack để bắt chước Teable; làm onboarding rõ ràng.

### F24 — RBAC và permissions

1. **Giá trị:** công ty cộng tác mà người xem không sửa, người ngoài không đọc.
2. **UI:** thành viên → role → giải thích quyền → xác nhận; UI phản ánh quyền server và lý do bị chặn.
3. **Dữ liệu:** OWNER/EDITOR/VIEWER hiện có; permission scope workspace; base/table/field ACL chỉ thêm sau quyết định nhu cầu phòng ban.
4. **API/backend:** kiểm trên mọi route, export/query/audit/link/attachment; bảo vệ raw tables/RPC; không lấy UID/role từ body.
5. **Ngoại lệ:** last owner, revoke khi editor đang mở, stale cache, quyền qua nhiều đường, aggregate/search bypass, token đặc quyền không vào APK.
6. **Bằng chứng:** [Teable collaboration roles](https://help.teable.ai/en/basic/space/space-permission) và [Authority Matrix](https://help.teable.ai/en/basic/authority-matrix) khác tầng; [Airtable permissions](https://support.airtable.com/articles/3994336578-airtable-permissions-overview) phân workspace/base/interface, [field/table edit](https://support.airtable.com/articles/2296468758-airtable-field-and-table-editing-permissions) áp dụng cả API.
7. **Mức chắc chắn:** role/actions tài liệu; không gán sẵn semantics cộng quyền hai hãng cho 3DDK.
8. **Độ khó/rủi ro/ưu tiên:** Rất cao / lộ hoặc sửa dữ liệu công ty / **P0** cách ly và role hiện có; quyền chi tiết cần duyệt.
9. **Áp dụng:** ma trận backend và negative tests bắt buộc; ẩn cột trong view không được coi là field ACL.

### F25 — Comments

1. **Giá trị:** trao đổi gắn với việc, tìm lại lý do quyết định.
2. **UI:** chi tiết việc → bình luận → gửi → sửa/xóa bình luận của mình có dấu đã sửa.
3. **Dữ liệu:** comments(record_id, author_uid, body, created/edited/deleted_at); reply_parent nếu được duyệt.
4. **API/backend:** paged list/create/edit/delete; kiểm record access; sanitize display; idempotent create.
5. **Ngoại lệ:** xóa record, tác giả rời công ty, nội dung dài, offline draft, notification không làm lộ comment.
6. **Bằng chứng:** [Teable Comments](https://help.teable.ai/en/basic/record/comment) panel theo record; [Airtable Commenting](https://support.airtable.com/articles/1999477400-commenting-in-airtable) yêu cầu commenter+.
7. **Mức chắc chắn:** chức năng có nguồn; 3DDK có thể giữ ba role, không bắt buộc thêm Commenter ngay.
8. **Độ khó/rủi ro/ưu tiên:** Vừa / nội dung riêng tư và spam / **P1** text trước.
9. **Áp dụng:** Fragment/RecyclerView và Room cache; không biến thành chat toàn hệ thống.

### F26 — Mentions

1. **Giá trị:** gọi đúng người vào một công việc.
2. **UI:** gõ @ → chọn thành viên đang có quyền → gửi → chạm tên mở hồ sơ.
3. **Dữ liệu:** structured mention UID + vị trí/token, không parse tên hiển thị làm identity.
4. **API/backend:** validate recipient quyền đọc record; create mention event cùng comment trong transaction.
5. **Ngoại lệ:** tên trùng, người được tag rời nhóm, sửa comment để tag thêm, tag tất cả gây spam.
6. **Bằng chứng:** [Teable Comments](https://help.teable.ai/en/basic/record/comment) mention gửi notification; [Airtable Commenting](https://support.airtable.com/articles/1999477400-commenting-in-airtable) chọn collaborator từ @.
7. **Mức chắc chắn:** flow có nguồn; data structure UID là đề xuất.
8. **Độ khó/rủi ro/ưu tiên:** Vừa / tag ngoài quyền và notification trùng / **P1**, sau comments và inbox.
9. **Áp dụng:** tái dùng API thành viên và typed text editor; không thêm group mention trước nhu cầu cụ thể.

### F27 — Notifications

1. **Giá trị:** biết được giao việc, được tag, việc sắp hạn.
2. **UI:** inbox unread → mở đúng việc sau kiểm quyền; profile chỉnh kênh; app vẫn dùng khi tắt push.
3. **Dữ liệu:** v2 notification recipient/event key/read_at; device installation token; preferences; outbox retry.
4. **API/backend:** enqueue cùng mutation, worker FCM; kiểm quyền và trạng thái lại trước gửi; không chứa nội dung nhạy cảm trong push.
5. **Ngoại lệ:** token đổi, logout, quyền thu hồi, đa thiết bị, retry trùng, deadline đổi/hoàn thành trước lịch.
6. **Bằng chứng:** [Teable Comments](https://help.teable.ai/en/basic/record/comment) notification center và subscribe; [Airtable notifications](https://support.airtable.com/articles/8089803354-managing-airtable-notifications) assignment/mention/invite qua inbox/email/push.
7. **Mức chắc chắn:** nhóm hành vi đã xác minh; không có bằng chứng đảm bảo push tới đúng giây.
8. **Độ khó/rủi ro/ưu tiên:** Cao / gửi sai người hoặc trùng / **P1** cho v2.
9. **Áp dụng:** tái dùng FCM/outbox pattern v1 sau khi tách scope v2; không mặc nhiên coi push v1 hoạt động cho ws records.

## Tệp, dữ liệu vào/ra và tích hợp

### F28 — Attachments

1. **Giá trị:** lưu bằng chứng/kết quả ngay trong việc.
2. **UI:** chọn tệp bằng Android picker → progress → xem/xóa sau xác nhận quyền; ảnh dùng Glide.
3. **Dữ liệu:** attachment ID, workspace/record/field, object key, name, MIME, size, uploader, trạng thái; private bucket.
4. **API/backend:** authorize upload → upload private object → finalize có validation → record patch; download signed URL qua quyền.
5. **Ngoại lệ:** MIME giả, file quá lớn, mất mạng upload, orphan object, URL hết hạn, file nguy hiểm, quota công ty.
6. **Bằng chứng:** [Teable Attachment](https://help.teable.ai/en/basic/field/attachment) nhiều tệp/preview; [Airtable Attachment](https://support.airtable.com/articles/9139007724-attachment-fields-in-airtable) có format Files/Versions, không phải mọi gói giống nhau.
7. **Mức chắc chắn:** hành vi có nguồn; object-store implementation 3DDK là đề xuất.
8. **Độ khó/rủi ro/ưu tiên:** Cao / file riêng tư và chi phí lưu trữ / **P1** hạn mức nhỏ sau phê duyệt.
9. **Áp dụng:** Supabase Storage private + Edge API, không để public URL vĩnh viễn thay thế phân quyền.

### F29 — Import

1. **Giá trị:** đưa danh sách công việc cũ vào app.
2. **UI:** chọn CSV → preview/mapping → kiểm lỗi → xác nhận → báo hàng hợp lệ/lỗi.
3. **Dữ liệu:** import job/requestId, schema version, mapping field IDs, row errors và batch checkpoints.
4. **API/backend:** server parse có giới hạn bytes/rows, validate types, ghi theo batch và báo trạng thái rõ; không partial âm thầm.
5. **Ngoại lệ:** BOM/UTF-8, quote/newline, duplicate headers, date mơ hồ, loại file giả, retry, schema đổi khi import.
6. **Bằng chứng:** [Teable Import](https://help.teable.ai/en/basic/table/import) preview và mapping; [Airtable Import](https://support.airtable.com/articles/9412806945-importing-third-party-data-into-airtable) import không mặc nhiên là live sync và mobile native còn hạn chế.
7. **Mức chắc chắn:** chức năng nhập có nguồn; không đề xuất lấy dữ liệu của hãng khi chưa có ủy quyền.
8. **Độ khó/rủi ro/ưu tiên:** Cao / dữ liệu bẩn, duplicate và partial / **P1** CSV nhỏ; XLSX/connectors **P2**.
9. **Áp dụng:** Android Storage Access Framework, reuse cell validator; chỉ thêm parser dependency nếu stdlib/installed chưa đủ xử lý CSV đúng.

### F30 — Export

1. **Giá trị:** lấy dữ liệu được phép ra để báo cáo và chuyển công cụ.
2. **UI:** xuất view/toàn bảng → mô tả phạm vi → lưu file; không ghi là backup toàn hệ thống.
3. **Dữ liệu:** snapshot query/config cho job, actor/scope, audit event; file có expiry nếu server tạo.
4. **API/backend:** export sau quyền/filter/projection; không chỉ xuất trang cache hiện tại; chống spreadsheet formula injection.
5. **Ngoại lệ:** hidden fields, quyền cột, CSV escaping, dữ liệu đổi giữa trang, file lớn, export là nguồn rò dữ liệu.
6. **Bằng chứng:** [Teable Export](https://help.teable.ai/en/basic/table/export) phân table và view; [Airtable Views](https://support.airtable.com/articles/5189551686-getting-started-with-airtable-views) CSV theo view và chính sách chia sẻ.
7. **Mức chắc chắn:** chức năng có nguồn; quyền export của 3DDK cần chọn rõ, không bê mặc định hãng.
8. **Độ khó/rủi ro/ưu tiên:** Vừa / xuất quá phạm vi / **P1** CSV; backup đầy đủ riêng.
9. **Áp dụng:** REST server export + Android file picker; viewer có được export là quyết định phê duyệt.

### F31 — Automation

1. **Giá trị:** giảm thao tác lặp như nhắc hạn và đổi người theo điều kiện.
2. **UI:** cấu hình trigger/condition/action → test với dữ liệu mẫu → enable → xem run log.
3. **Dữ liệu:** rule version, enabled, trigger/action allowlist, run/event IDs, state, attempt, next_retry; secret reference.
4. **API/backend:** event/outbox worker + scheduler, idempotent side effects, chạy dưới quyền actor/service đã định rõ.
5. **Ngoại lệ:** loop nhiều rule, quyền chủ rule mất, deadline đổi, quota, retry email trùng, live rule khác draft.
6. **Bằng chứng:** [Teable Automation](https://help.teable.ai/en/basic/automation) có draft/live và run history; [Airtable Automation](https://support.airtable.com/articles/3669392397-getting-started-with-airtable-automations) trigger/actions, quota và khác biệt theo gói.
7. **Mức chắc chắn:** hành vi workflow xác minh; không thử run và không hứa tương thích engine.
8. **Độ khó/rủi ro/ưu tiên:** Rất cao / vòng lặp, gửi ngoài ý muốn, chi phí / **P2**; nhắc hạn cố định **P1**.
9. **Áp dụng:** dùng worker Supabase hiện có cho rule cố định trước; không thêm trình chạy script người dùng vào P0.

### F32 — Webhook

1. **Giá trị:** nhận sự kiện hệ ngoài hoặc đẩy thay đổi ra tích hợp.
2. **UI:** owner tạo integration → chọn events/scope → lấy secret một lần → xem delivery, revoke.
3. **Dữ liệu:** webhook subscription, signing secret reference/hash, delivery/event IDs, status/retry và expiry.
4. **API/backend:** inbound validation/auth/replay protection; outbound sign, retry/backoff và giới hạn URL để chặn SSRF.
5. **Ngoại lệ:** endpoint timeout, secret rotate, events trùng/khác thứ tự, private IP, domain đổi DNS, recipient không còn quyền.
6. **Bằng chứng:** [Teable incoming webhook](https://help.teable.ai/en/basic/automation/trigger/external/webhook-received) là trigger POST; [Airtable Webhooks API](https://support.airtable.com/articles/7385030819-airtable-webhooks-api-overview) báo thay đổi base. Hai chiều này không đồng nhất.
7. **Mức chắc chắn:** có nguồn về hai cơ chế khác nhau; chưa kiểm tra delivery guarantee hoặc retry nội bộ hãng.
8. **Độ khó/rủi ro/ưu tiên:** Rất cao / SSRF, giả sự kiện và dữ liệu ra ngoài / **P2** cần duyệt.
9. **Áp dụng:** tái dùng outbox cho outbound; không cung cấp URL công khai vô danh mặc định.

### F33 — REST API

1. **Giá trị:** Android dùng dữ liệu thống nhất và có khả năng tích hợp có kiểm soát.
2. **UI:** app dùng Retrofit; public API nếu duyệt có trang cấp/revoke token riêng, không để service key trong app.
3. **Dữ liệu:** DTO rõ version, error code, requestId/cursor; external token scope/resource/expiry/hash riêng sau này.
4. **API/backend:** giữ Edge v2 và SQL transaction; validation/quyền/rate/body limits; query paginated; schema endpoint không lộ database internals.
5. **Ngoại lệ:** token hết hạn, 409/422/429, pagination thay schema, backward compatibility, retry mutation cùng ID.
6. **Bằng chứng:** [Teable token](https://help.teable.ai/en/api-doc/token) scope và resource; [Teable get records](https://help.teable.ai/en/api-doc/record/get); [Airtable API](https://support.airtable.com/articles/6292134965-getting-started-with-airtable-s-web-api) pagination và [limits](https://support.airtable.com/articles/7735693959-managing-api-call-limits-in-airtable).
7. **Mức chắc chắn:** public contract được tài liệu xác minh; không gọi authenticated endpoint của hãng và không đo quota thực tế.
8. **Độ khó/rủi ro/ưu tiên:** Cao / bỏ qua quyền qua integration / **P0** internal API; external PAT/OAuth **P2**.
9. **Áp dụng:** giữ Firebase bearer cho app; không dùng Teable/Airtable làm backend thay thế.

## Quan sát, báo cáo và khôi phục

### F34 — Dashboard

1. **Giá trị:** xem số việc xong/quá hạn và khối lượng theo người.
2. **UI:** tổng quan dự án → KPI/biểu đồ → chạm chỉ số mở view tương ứng.
3. **Dữ liệu:** dashboard widgets query/table/field IDs, filter scope; version config và timestamp số liệu.
4. **API/backend:** aggregate có cùng quyền/filter với list, cap độ phức tạp; không tính tổng từ một trang cache.
5. **Ngoại lệ:** timezone của quá hạn, mẫu có custom trạng thái khác, số 0 khác chưa tải, aggregate lộ nhóm nhỏ bị ẩn.
6. **Bằng chứng:** [Teable legacy dashboard](https://help.teable.ai/en/deploy/dashboard) đã được thay bằng App Builder; [Airtable Dashboard](https://support.airtable.com/articles/5415824889-airtable-interface-layout-dashboard) numbers/charts/drill-down.
7. **Mức chắc chắn:** trạng thái tài liệu được xác minh; không kết luận dashboard legacy là hướng Teable khuyên dùng hiện tại.
8. **Độ khó/rủi ro/ưu tiên:** Vừa cho KPI / thống kê sai do cache thiếu / **P2-04** tổng quan công việc, sau query/quyền/metrics đã được duyệt; không đưa App Builder vào bản lõi.
9. **Áp dụng:** XML MaterialCardView + aggregate API; không xây App Builder/AI để có vài chỉ số.

### F35 — Audit / Activity log

1. **Giá trị:** biết ai thay quyền/cấu trúc/dữ liệu quan trọng khi nhiều người dùng.
2. **UI:** chủ không gian mở nhật ký → lọc người/thời gian/đối tượng → xem chi tiết phù hợp quyền.
3. **Dữ liệu:** mở rộng `ws_events` với action/resource IDs/requestId/outcome và metadata tối thiểu; before/after cần redaction/retention.
4. **API/backend:** ghi cùng transaction mutation; list có cursor/quyền; không cho client tự ghi actor hoặc sửa/xóa log.
5. **Ngoại lệ:** event nhạy cảm, deleted resource, failed request không thành business event, retry không nhân log, retention ảnh hưởng lưu trữ.
6. **Bằng chứng:** [Teable Audit](https://help.teable.ai/en/basic/admin-panel/audit-log) self-host Business+; [Airtable audit](https://support.airtable.com/articles/1665450173-accessing-enterprise-audit-logs-in-airtable) Enterprise Scale, khác record history.
7. **Mức chắc chắn:** điều kiện gói từ docs; không suy ra mọi Teable CE đều có audit UI.
8. **Độ khó/rủi ro/ưu tiên:** Cao / nhật ký giả hoặc lộ giá trị cũ / **P0** schema/membership/mutation events; UI nâng cao **P1**.
9. **Áp dụng:** tái dùng `ws_events`, bổ sung thông tin truy vết cần thiết và test transactional, không dùng log console thay audit.

### F36 — Record version history

1. **Giá trị:** xem giá trị cũ/mới và người sửa; hỗ trợ xử lý thay đổi nhầm.
2. **UI:** chi tiết việc → Lịch sử → chọn thay đổi → xem field diff; restore riêng sau xác nhận.
3. **Dữ liệu:** revisions(record_id, version, actor, timestamp, changed field IDs, old/new values); schema labels lịch sử có snapshot tối thiểu.
4. **API/backend:** history pagination theo quyền hiện tại; phục hồi là mutation mới expectedVersion, không hạ version ngược.
5. **Ngoại lệ:** cột đã xóa/giảm quyền, dữ liệu nhạy cảm cũ, retention, imported record, khôi phục kiểu field không còn tương thích.
6. **Bằng chứng:** [Teable Record history](https://help.teable.ai/en/basic/record/record-history) cell changes và filters; [Airtable revision history](https://support.airtable.com/articles/3516802427-record-level-revision-history-in-airtable) view-only, retention theo gói, không có endpoint public trả toàn lịch sử.
7. **Mức chắc chắn:** lịch sử và giới hạn tài liệu xác minh; chức năng restore từng field của 3DDK là đề xuất riêng.
8. **Độ khó/rủi ro/ưu tiên:** Cao / lộ dữ liệu đã mất quyền và phục hồi sai / **P0-06** lịch sử đọc cơ bản; restore **P1-09** sau kiểm tra schema/quyền/version.
9. **Áp dụng:** ghi diff server cùng record patch; giữ `version` concurrency vì history không tự chống ghi đè.

### F37 — Base snapshots / Khôi phục phiên bản

1. **Giá trị:** có điểm phục hồi trước thay cấu trúc hoặc import lớn.
2. **UI:** trước mắt hướng dẫn backup/restore vận hành; user-facing snapshot list/restore chỉ khi phạm vi được duyệt.
3. **Dữ liệu:** snapshot manifest schema version, consistent data point, private object references, checksum, retention, restore job.
4. **API/backend:** export nhất quán và restore vào bản sao để kiểm tra; kiểm quyền chặt, không overwrite production tự động.
5. **Ngoại lệ:** file hết retention, thiếu attachments, ID collision, secrets trong export, backup đọc không khôi phục được.
6. **Bằng chứng:** [Teable project export](https://help.teable.ai/en/basic/base) `.tea` chứa cấu trúc/dữ liệu/config, không đủ để kết luận có snapshot manager tương đương; [Airtable snapshots](https://support.airtable.com/articles/2163132802-taking-and-restoring-snapshots-in-airtable) mô tả restore thành base mới.
7. **Mức chắc chắn:** Airtable article còn một câu mô tả “replaces” mâu thuẫn với FAQ không overwrite; lấy FAQ làm hướng tham khảo và đánh dấu cần thử, không khẳng định đã chạy. Chưa xác minh snapshot UI Teable CE.
8. **Độ khó/rủi ro/ưu tiên:** Rất cao / mất dữ liệu khi restore / **P0** quy trình backup có diễn tập; snapshot trong app **P2**.
9. **Áp dụng:** backup database + files ngoài repo, kiểm restore môi trường riêng; không viết migration phục hồi production trong đợt phân tích này.

## Kết luận phạm vi của inventory

37 mục là danh mục nghiên cứu; không phải cam kết triển khai đầy đủ trong một đợt. [FEATURE_GAP_ANALYSIS.md](FEATURE_GAP_ANALYSIS.md) đối chiếu từng mục với mã hiện tại; [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) xác định việc thực hiện và gate nghiệm thu. Những khả năng như SSO, public forms, quyền chi tiết, công thức và tích hợp ngoài cần quyết định trước khi trở thành scope sản phẩm.
