# Roadmap triển khai để phê duyệt

Ngày kế hoạch: 17/10/2026. **P0 đã được người dùng phê duyệt và đang triển khai.** Các mục dưới đây là tiêu chí của kế hoạch gốc; trạng thái và bằng chứng hiện tại xem [P0-VERIFICATION](../../P0-VERIFICATION.md). P1/P2 chưa được duyệt.

Mục tiêu người dùng xác nhận: công ty khoảng 100 người. Câu trả lời tiếp theo “khoảng 20 người” được hiểu là tải đỉnh khoảng 20 người cùng thao tác; cần xác nhận lại khi chốt bài kiểm thử tải. Số dòng mỗi bảng, số workspace/base và ngày bàn giao chưa được cung cấp. Không áp dụng mốc 28 ngày của phạm vi một nhóm cũ cho toàn bộ roadmap mới.

## 1. Các cổng kiểm tra

- **G0 — Duyệt:** kiến trúc hiện tại, phạm vi P0, workload/chi phí, chính sách quyền/view/offline và có dùng REA hay không. Không cần REA để bắt đầu đọc nguồn công khai; cần phê duyệt trước cài/chạy thiết lập.
- **G1 — Lõi server:** mẫu bảng tạo nguyên tử, RBAC, version, audit và hợp đồng query đạt trên local/staging; chỉ công bố bản mới khi APK đã tương thích.
- **G2 — Luồng Android:** Room migration giữ nháp; saved view, query và chỉnh sửa hoạt động trên hai thiết bị với hai tài khoản thật có quyền khác nhau. Việc tạo 100 UID giả trong unit test không thay thế nghiệm thu này.
- **G3 — Công ty dùng thử:** kiểm thử tải với 100 tài khoản và khoảng 20 người thao tác đồng thời theo workload đã xác nhận; thử nội bộ, kiểm tra hạn mức/chi phí, diễn tập khôi phục, APK release và nâng cấp. Chỉ nói hỗ trợ 100 người sau khi cổng kiểm tra này có bằng chứng.
- **G4 — Mở rộng:** duyệt từng P1/P2, không tự thực hiện cả inventory.

Test commands tái sử dụng: `npm test` (DB local), `npm run test:edge`, `npm run check:edge`, `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`; instrumentation `:app:connectedDebugAndroidTest` cần thiết bị. Tài liệu hiện trạng có điều kiện môi trường cụ thể. Load test là fixture/harness cần bổ sung sau duyệt, không giả định repo đã có.

Mỗi task ưu tiên một phần hoàn chỉnh dùng được, phần lớn có kích thước S/M (khoảng 2–5 file chính); nếu khối lượng thực tế lớn hơn thì tách tiếp trước khi viết. Các file mới là vị trí dự kiến, chưa được tạo.

## 2. P0 — Lõi đáng tin và bảng công việc dùng được

### P0-01 — Chốt contract và capability (S)

- **Mô tả/mục đích:** phân biệt API cũ/mới, báo feature/limits/schema support để APK không gọi phần chưa deploy.
- **Thành phần:** Edge `api/index.ts`, `_shared/backend.ts`, contract test, tài liệu API.
- **Phụ thuộc:** G0.
- **Nghiệm thu:** capability không chứa secret; APK cũ giữ response cũ; route mới có error codes thống nhất.
- **Test case:** thiếu token hoặc sai Firebase project; tính năng chưa bật; route không tồn tại; query qua GET/POST được phân loại đúng là thao tác chỉ đọc; APK cũ gặp table có type mới nhận CLIENT_UPGRADE_REQUIRED thay vì payload gây lỗi.
- **Rủi ro:** vô tình thay path/v1 hoặc đọc POST bị tính mutation.
- **Rollback:** tắt capability và revert Edge contract mới; giữ routes cũ.

### P0-02 — Tạo template trong một transaction (M)

- **Mô tả/mục đích:** thay chuỗi tạo bảng và 5 cột riêng ở client bằng một mutation server; tránh bảng thiếu cột.
- **Thành phần:** migration mới, `tests/workspaces.test.mjs`, `WorkspaceViewModel.kt`, `WorkspaceFragment.kt`.
- **Phụ thuộc:** P0-01.
- **Nghiệm thu:** tạo đủ bảng và các cột của mẫu trong một transaction, hoặc không tạo gì; retry cùng ID chỉ tạo một bảng; EDITOR không vượt quyền tạo cấu trúc. Default view được bổ sung tại P0-07, khi schema view đã có.
- **Test case:** một cột trong mẫu không hợp lệ khiến toàn bộ transaction rollback; timeout sau commit rồi retry; bấm tạo hai lần; cùng requestId nhưng khác payload; quyền bị thu hồi.
- **Rủi ro:** app cũ còn dùng luồng nhiều request; chưa có schema default view tại thời điểm task này.
- **Rollback:** tắt capability tạo mẫu; giữ cách tạo bảng trống cũ. P0-07 sẽ bổ sung default view cho bảng đã có và cập nhật transaction tạo mẫu, không dựng view giả trong P0-02.

### P0-03 — Củng cố validator và schema version (M)

- **Mô tả/mục đích:** thống nhất null/type/option/primary field và nhận biết metadata đổi khi đang soạn.
- **Thành phần:** migration mới, DB fixtures/tests, `RecordViewModel.kt`, `TypedCellsTest.kt`.
- **Phụ thuộc:** P0-01.
- **Nghiệm thu:** route cells mới yêu cầu schemaVersion; unknown field/cross-table/type sai bị chặn; chính sách primary field tương thích dữ liệu cũ được duyệt.
- **Test case:** gửi chuỗi vào cột số hoặc boolean; ngày không tồn tại; lựa chọn không có trong cấu hình; null, 0 và false; metadata thay đổi trước khi gửi.
- **Rủi ro:** siết validator làm APK cũ không sửa được dữ liệu hợp lệ trước đây.
- **Rollback:** chỉ bật strict contract mới theo capability; giữ legacy validator/route tương thích, không sửa hoặc xóa dữ liệu cũ để test pass.

### P0-04 — Quản lý quyền thành viên ở server (M)

- **Mô tả/mục đích:** OWNER đổi EDITOR/VIEWER, gỡ thành viên và xử lý lời mời đã dùng/thu hồi an toàn. Nếu được duyệt, bổ sung chuyển quyền sở hữu trong một transaction.
- **Thành phần:** migration mới, DB permission tests, Edge contract tests.
- **Phụ thuộc:** P0-01.
- **Nghiệm thu:** request sau thu hồi bị chặn; không xóa OWNER qua member route; không đọc/ghi workspace khác bằng ID đoán được. Chuyển quyền sở hữu, nếu được duyệt, kiểm tra version và luôn kết thúc với đúng một OWNER trong transaction.
- **Test case:** VIEWER gọi API ghi trực tiếp; xử lý OWNER cuối cùng; gửi lại yêu cầu chấp nhận lời mời đã dùng; phiên bản quyền cũ; gỡ thành viên đồng thời với lưu dữ liệu; lời mời đang có hiệu lực bị tạo trùng; chuyển owner sang người ngoài hoặc chuyển đồng thời bị chặn; lỗi giữa chừng không làm mất owner.
- **Rủi ro:** dữ liệu offline chưa thu hồi ngay; unique owner/lock order.
- **Rollback:** đóng routes quản trị mới, giữ membership hiện tại và audit; không tự phục hồi role đã đổi khi rollback code.

### P0-05 — Giao diện quản lý thành viên (S/M)

- **Mô tả/mục đích:** nối quản trị server vào màn hình dễ hiểu, xác nhận trước thay đổi quyền.
- **Thành phần:** `WorkspaceFragment.kt`, `WorkspaceViewModel.kt`, dialog/layout resources, UI test.
- **Phụ thuộc:** P0-04.
- **Nghiệm thu:** chỉ Owner thấy thao tác quản trị; lỗi quyền/version hiển thị rõ; không báo đã gửi email khi chỉ tạo lời mời in-app.
- **Test case:** lần lượt dùng OWNER, EDITOR và VIEWER; gỡ người đang online; xoay màn hình khi mở dialog; API trả 409, 403 hoặc mất mạng.
- **Rủi ro:** thao tác nhầm user hoặc dữ liệu danh sách cũ.
- **Rollback:** ẩn tính năng theo capability; server checks vẫn giữ.

### P0-06 — Audit và lịch sử record cơ bản (M)

- **Mô tả/mục đích:** ghi entity/version/field delta cùng mutation, cung cấp history được kiểm tra quyền.
- **Thành phần:** migration mới, DB tests, `RecordViewModel.kt`, history layout/Fragment tái dùng pattern hiện có.
- **Phụ thuộc:** P0-03/P0-04.
- **Nghiệm thu:** mỗi mutation thành công đúng một history event; replay không lặp; người ngoài không đọc history.
- **Test case:** tạo, sửa và lưu trữ dòng; mutation không hợp lệ không sinh event; version cũ; thành viên bị gỡ; field đổi tên vẫn truy lại được bằng ID.
- **Rủi ro:** log chứa nội dung nhạy cảm; audit lớn dần.
- **Rollback:** tắt UI/read route mới; giữ audit append-only, retention theo quyết định được duyệt.

### P0-07 — Saved Grid views và cấu hình theo ID (M)

- **Mô tả/mục đích:** lưu filter/sort/field visibility độc lập dữ liệu, không còn dựa tên cột cứng.
- **Thành phần:** migration `ws_views`, DB view tests, metadata/capability response.
- **Phụ thuộc:** P0-02/P0-03/P0-04.
- **Nghiệm thu:** mỗi bảng có default Grid, gồm cả bảng đã tạo trước task này; personal/shared view áp dụng đúng ACL; sửa cấu hình kiểm tra version; transaction tạo mẫu từ P0-02 tạo luôn default view.
- **Test case:** viewId thuộc bảng hoặc workspace khác; field bị lưu trữ; cấu hình shared view cũ; VIEWER bị chặn khi sửa shared view; truy cập personal view của UID khác.
- **Rủi ro:** người dùng hiểu nhầm hidden field/filter là bảo mật.
- **Rollback:** tắt chọn view mới, giữ default Grid + cấu hình đã lưu; không DROP views.

### P0-08 — Query có phân trang và validation (M, tách nếu trên 5 file)

- **Mô tả/mục đích:** search/filter/sort trên server và trả page, giảm tải snapshot toàn bảng.
- **Thành phần:** SQL query function/migration mới, query tests, Edge route classification, API docs.
- **Phụ thuộc:** P0-03/P0-07.
- **Nghiệm thu:** stable order/cursor binding, null/typed sort đúng; filter chỉ field thuộc table; legacy snapshot không đổi response/cap.
- **Test case:** đầu vào có hình thức chèn lệnh; cursor của UID, query hoặc schema khác; các dòng trùng khóa sắp xếp; trang trống; thêm/sửa đồng thời; bộ dữ liệu giả lập 500 và 5.000 dòng.
- **Rủi ro:** dynamic SQL, full scan, pagination live khác snapshot.
- **Rollback:** capability query off; fallback snapshot ở cap hiện tại, không tăng cap trước benchmark.

### P0-09 — Room cache theo page và giữ nháp khi nâng cấp (M)

- **Mô tả/mục đích:** lưu metadata/record/query membership có scope; nhiều draft mới không đè cùng key `new`.
- **Thành phần:** `TaskDatabase.kt`, schema export mới, `WorkspaceRepository.kt`, instrumentation migration tests.
- **Phụ thuộc:** P0-08 (contract chốt trước).
- **Nghiệm thu:** migration từ Room version 1 giữ nháp v1/v2; các trang không trộn tài khoản hoặc query; chế độ offline phân biệt cache đầy đủ và cache mới tải một phần.
- **Test case:** tắt app hoặc xoay màn hình; đổi tài khoản khi request/lưu nháp đang chạy; hai bản nháp dòng mới; trang 1 của query A và trang 2 của query B; workspace bị từ chối chỉ xóa cache đúng phạm vi.
- **Rủi ro:** mất draft, race logout, cache phình.
- **Rollback:** server vẫn hỗ trợ APK cũ; schema local không downgrade phá dữ liệu. Giữ backup test và phát hành hotfix forward nếu migration lỗi.

### P0-10 — Grid chọn view và tải trang (M)

- **Mô tả/mục đích:** người dùng chọn view, lọc/sort/search dễ thấy, tải thêm mà không trống màn hình.
- **Thành phần:** `DataTableFragment.kt`, `WorkspaceViewModel.kt`, table layout/resources, UI tests.
- **Phụ thuộc:** P0-07/P0-08/P0-09.
- **Nghiệm thu:** mở lại view giữ config; query cũ không ghi đè kết quả mới; empty/no-cache/error khác nhau; filter offline có nhãn dữ liệu chưa đủ.
- **Test case:** gõ tìm kiếm liên tục và debounce; cuộn tải trang; thay query; lỗi mạng; VIEWER; cỡ chữ 200%, TalkBack và Android API 24.
- **Rủi ro:** adapter giữ view/binding, rendering quá nhiều cột hoặc data stale.
- **Rollback:** capability UI mới off, dùng legacy Grid dưới cap; giữ draft/cache mới tương thích.

### P0-11 — Field người phụ trách thật (M)

- **Mô tả/mục đích:** gán UID thành viên thay nhập text; hỗ trợ filter “Là tôi”.
- **Thành phần:** migration validator, DB tests, `RecordViewModel.kt`, `RecordEditorFragment.kt`, template definition.
- **Phụ thuộc:** P0-03/P0-04/P0-08.
- **Nghiệm thu:** USER lưu UID đúng workspace; người bị gỡ không được gán mới; tên hiển thị không thay identity; chưa tự chuyển cột text cũ; chỉ bật sau khi contract mới và chính sách cập nhật APK sẵn sàng.
- **Test case:** UID không tồn tại hoặc thuộc workspace khác; đổi tên hồ sơ; thành viên bị gỡ sau khi tải form; sửa field khác vẫn giữ UID lịch sử; quyền tạo mẫu có field USER; legacy snapshot/full replacement trên bảng dùng kiểu mới bị chặn an toàn.
- **Rủi ro:** membership và historical values; nhu cầu nhiều người phụ trách chưa chốt.
- **Rollback:** ngừng tạo USER mới theo capability; giữ render read-only và dữ liệu cũ, không đổi UID về tên mất liên kết.

### P0-12 — Nháp, cell patch và màn hình xung đột (M)

- **Mô tả/mục đích:** giữ input và cho biết khác biệt trước khi gửi lại; không tự thay toàn dòng từ cache cũ.
- **Thành phần:** migration mutation mới, DB concurrency tests, `RecordViewModel.kt`, `RecordEditorFragment.kt`, test/fixture chung.
- **Phụ thuộc:** P0-03/P0-06/P0-09.
- **Nghiệm thu:** cell patch vẫn yêu cầu expectedVersion; lỗi 409 giữ nháp; schema thay đổi có hướng dẫn xử lý; retry sau timeout dùng đúng requestId và payload.
- **Test case:** hai người sửa cùng version; sửa hai field khác nhau vẫn trả 409 ở P0 nếu version đã đổi; server commit nhưng client timeout; schema cũ; lưu trữ đồng thời với sửa; logout khi đang ghi nháp.
- **Rủi ro:** người dùng xác nhận overwrite sai, nháp base snapshot cũ.
- **Rollback:** tắt patch mới và giữ legacy route cho app cũ; không clear drafts sau lỗi.

### P0-13 — Hoàn thiện editor và trạng thái UI (S/M)

- **Mô tả/mục đích:** date picker, nhập số/type rõ ràng, trạng thái cache/nháp/server, resource strings/theme/accessibility.
- **Thành phần:** `RecordEditorFragment.kt`, layout field editor, strings/themes resources, UI tests.
- **Phụ thuộc:** P0-10/P0-11/P0-12.
- **Nghiệm thu:** DATE hiển thị theo định dạng địa phương và gửi ISO; chỉ báo đã đồng bộ sau xác nhận server; thao tác chính không bị cỡ chữ hoặc bàn phím che.
- **Test case:** ngày không hợp lệ; 0, false và null; URL/avatar lỗi; bàn phím; xoay màn hình trên API 24 và 35; TalkBack và vùng chạm tối thiểu 48dp.
- **Rủi ro:** formatter làm đổi dữ liệu hoặc text hardcoded không khớp semantics.
- **Rollback:** revert editor hiển thị mới, giữ contract/backend validation và drafts.

### P0-14 — Test nhiều account, quyền và offline end-to-end (M)

- **Mô tả/mục đích:** chứng minh luồng thật trước bàn giao, không chỉ unit tests.
- **Thành phần:** `tests/workspaces.test.mjs`, Edge auth tests, Android instrumentation tests, test evidence docs.
- **Phụ thuộc:** G1 và P0-05..P0-13.
- **Nghiệm thu:** hai tài khoản trên hai thiết bị với vai trò OWNER/EDITOR, sau đó thêm VIEWER; chạy tạo mẫu → giao người phụ trách → sửa → xem lịch sử; offline, nháp và từ chối quyền đều đúng.
- **Test case:** token sai project hoặc email chưa xác minh; lời mời và thu hồi quyền; retry trùng; version cũ; tải lần đầu khi offline và đọc cache; đổi tài khoản; JSON hoạt động trên release có R8.
- **Rủi ro:** synthetic token không phản ánh Firebase thực, fixture làm bẩn dữ liệu công ty.
- **Rollback:** chỉ dùng local/staging và test workspace có tên rõ; xóa fixture bằng quy trình được duyệt, không xóa dữ liệu người dùng.

### P0-15 — Benchmark công ty 100 người và kế hoạch vận hành (M)

- **Mô tả/mục đích:** xác lập năng lực và chi phí với khoảng 20 người thao tác đồng thời sau khi xác nhận workload; tìm điểm nghẽn trước khi tăng giới hạn.
- **Thành phần:** harness/fixtures local-staging mới, query indexes có số đo, operational docs, kết quả benchmark.
- **Phụ thuộc:** P0-08/P0-14; workload và môi trường được duyệt.
- **Nghiệm thu:** báo p50/p95/p99, tỷ lệ lỗi, CPU, thời gian chờ khóa database, lưu lượng mạng và RAM khi dùng Room; đo các luồng đọc/sửa/tìm kiếm/retry với nhiều vai trò; đối chiếu số đo với hạn mức gói miễn phí.
- **Test case:** bộ dữ liệu thí nghiệm 500 và 5.000 dòng; lần lượt 1, 5 và 20 người thao tác đồng thời; cùng sửa một dòng và sửa các dòng khác nhau; nhiều thao tác thu hồi/mời liên tiếp; khởi động lạnh và retry. Bộ 5.000 dòng chỉ dành cho môi trường thí nghiệm, không tự tăng cap sản phẩm.
- **Rủi ro:** benchmark làm quá quota, con số sai nếu môi trường không giống thực tế.
- **Rollback:** ngừng harness, trả limits an toàn; chỉ thêm index sau EXPLAIN/measurement, revert index nếu write cost tăng. Không đổi gói phí/gắn thẻ tự động.

### P0-16 — Phát hành thử có thể dựng lại (M)

- **Mô tả/mục đích:** từ source→backend→APK có hướng dẫn và kế hoạch khôi phục.
- **Thành phần:** CI, cấu hình version, SETUP/OPERATIONS/TESTING/DELIVERY, bản build có chữ ký lưu ngoài Git.
- **Phụ thuộc:** G2, P0-15 và kết quả pilot/chi phí đã duyệt; quyết định release và môi trường. G3 chỉ hoàn tất sau task này, không là điều kiện đầu vào của chính nó.
- **Nghiệm thu:** clone sạch build được; APK/AAB có chữ ký và checksum; nâng cấp giữ nháp; diễn tập backup/restore; thành viên khác dựng lại được từ hướng dẫn.
- **Test case:** Auth/API trên bản ký có R8; nâng cấp từ preview; backend chưa hỗ trợ capability; refresh token; diễn tập khôi phục; rà secrets.
- **Rủi ro:** keystore/secrets, migration cloud chưa khớp staging, giới hạn hosting.
- **Rollback:** giữ APK/backend trước đó; tắt capability trước khi rollback UI; migration bổ sung giữ dữ liệu. Không đưa keystore/secret lên GitHub Release.

**Checkpoint P0:** các task có test thật tương ứng, không suy từ build đạt thành enterprise-ready. P0-15 có quyền đề nghị giữ cap thấp hơn nếu benchmark không đạt. Ngày hoàn thành chỉ ước lượng sau G0 và kiểm tra khối lượng thực.

## 3. P1 — Cách xem và cộng tác, duyệt sau lõi

### P1-01 — Kanban dùng shared records (M)

- **Mô tả/mục đích:** nhóm việc theo SELECT, đổi trạng thái bằng menu/kéo.
- **Thành phần:** KanbanFragment/layout mới, query view support, UI/DB tests.
- **Phụ thuộc:** P0 views/query/version.
- **Nghiệm thu:** chuyển thẻ cập nhật cùng bản ghi đang hiển thị ở Grid; nhóm chưa có trạng thái hoặc trống được thể hiện rõ; có thao tác thay thế kéo thả để hỗ trợ tiếp cận.
- **Test case:** kéo thả gặp lỗi 409; chế độ chỉ xem; chính sách đổi tên lựa chọn; bảng mới tải một phần số trang; chặn gửi khi offline.
- **Rủi ro:** đếm sai số thẻ trong nhóm vì chỉ đếm trang đã tải; thao tác kéo ra ngoài màn hình.
- **Rollback:** tắt capability KANBAN, mở dữ liệu bằng Grid.

### P1-02 — Calendar theo DATE (M)

- **Mô tả/mục đích:** xem lịch theo field ngày, không nhân bản record.
- **Thành phần:** CalendarFragment/layout, query theo khoảng ngày, tests.
- **Phụ thuộc:** P0 views/query; chỉ dùng DATETIME nếu đã được duyệt.
- **Nghiệm thu:** tải đủ dữ liệu trong khoảng ngày; vẫn truy cập được việc chưa có ngày; thay đổi ngày kiểm tra version.
- **Test case:** cuối tháng/năm; ngày nhuận; múi giờ; DST nếu có DATETIME; hai người chuyển ngày đồng thời.
- **Rủi ro:** nhầm ngày lịch và instant; bỏ việc không có ngày.
- **Rollback:** tắt CALENDAR, giữ field DATE và Grid.

### P1-03 — Cấu hình cột và bulk editing (M, hai lát cắt nếu lớn)

- **Mô tả/mục đích:** sửa trực tiếp ô đơn giản; đổi thứ tự cột bằng menu trước khi hỗ trợ kéo; xem trước thay đổi hàng loạt.
- **Thành phần:** RPC cấu hình field và sửa record theo batch; DB tests; Grid/editor UI; fixtures.
- **Phụ thuộc:** P0 schema/version/query; migration sang SELECT option ID phải được duyệt riêng.
- **Nghiệm thu:** batch thành công toàn bộ hoặc không ghi gì; kiểm tra đủ version; không tự chọn toàn bộ kết quả query khi chỉ mới tải một trang.
- **Test case:** batch 1 và 50 thao tác; lỗi 413; field chỉ đọc; một dòng xung đột; thao tác trùng; thứ tự dòng đã cũ.
- **Rủi ro:** ghi hàng loạt sai dữ liệu; nội dung dán chứa script.
- **Rollback:** tắt sửa hàng loạt và sửa inline; giữ thao tác lưu từng bản ghi và audit.

### P1-04 — Bình luận và mention (M)

- **Mô tả/mục đích:** trao đổi ngay trên việc, chọn thành viên được nhắc đến theo UID.
- **Thành phần:** migration comment/mention; API tests; giao diện bình luận trong bản ghi.
- **Phụ thuộc:** P0 RBAC/audit/USER.
- **Nghiệm thu:** tác giả và version đúng; người nhận có quyền truy cập; không đọc hoặc ghi sang workspace khác.
- **Test case:** nội dung dạng XSS được hiển thị như văn bản; UID thuộc workspace khác; tác giả bị gỡ; xung đột khi sửa/xóa bình luận.
- **Rủi ro:** nội dung nhạy cảm và gửi thông báo quá nhiều.
- **Rollback:** bình luận chuyển sang chỉ đọc hoặc tắt capability; giữ lịch sử theo chính sách retention.

### P1-05 — Thông báo v2 và retry outbox (M)

- **Mô tả/mục đích:** giao việc hoặc mention tạo inbox và push có chống trùng; mở rộng worker hiện có để phục vụ v2.
- **Thành phần:** migration notifications/outbox; dispatch worker; Android messaging/inbox; tests.
- **Phụ thuộc:** P0 audit/USER; P1-04 cho mention; nhắc deadline cần chính sách được chốt.
- **Nghiệm thu:** chỉ phát thông báo sau commit; xử lý đúng logout, đổi token và từ chối quyền thông báo; deep link kiểm tra lại ACL.
- **Test case:** timeout rồi retry; FCM token không còn hợp lệ; người nhận bị gỡ; deadline thay đổi; thử đọc thông báo của người khác.
- **Rủi ro:** hạn mức FCM/Edge; payload cũ hoặc thông báo trùng.
- **Rollback:** dừng job gửi push, inbox vẫn đọc được; không dùng membership v1 để đăng ký thiết bị v2.

### P1-06 — Attachments riêng tư (M)

- **Mô tả/mục đích:** gắn tệp vào bản ghi qua private storage và URL có chữ ký.
- **Thành phần:** attachment metadata/Storage policy; Edge upload intents; Android picker/uploader; tests.
- **Phụ thuộc:** P0 ACL/query; giới hạn kích thước, MIME, hạn mức và ngân sách được duyệt.
- **Nghiệm thu:** người ngoài không tải được tệp; bước hoàn tất xác minh object và kích thước; dọn tệp mồ côi đúng phạm vi.
- **Test case:** MIME giả; vượt kích thước; URL hết hạn; thành viên bị thu hồi quyền; hủy upload; dọn tệp mồ côi không ảnh hưởng object của yêu cầu khác.
- **Rủi ro:** phí lưu trữ/tải xuống; tệp nguy hiểm; URL đã ký vẫn có hiệu lực một thời gian sau khi thu hồi quyền.
- **Rollback:** tắt capability upload; giữ quyền tải các tệp đã có; không xóa hàng loạt object.

### P1-07 — CSV preview/import/export có job (M, tách import và export)

- **Mô tả/mục đích:** nhập/xuất dữ liệu với ánh xạ cột, báo lỗi kiểu dữ liệu và snapshot nhất quán.
- **Thành phần:** job metadata/worker/API; giao diện ánh xạ cột; parser fixtures/tests.
- **Phụ thuộc:** P0 query/schema/audit; quy trình xử lý tệp riêng tư ở P1.
- **Nghiệm thu:** preview không ghi dữ liệu; commit có idempotency; export áp dụng đúng ACL, query và snapshot; tiến độ/retry rõ ràng.
- **Test case:** UTF-8 có BOM; ô có xuống dòng nằm trong dấu nháy; tiền tố công thức CSV; cột trùng; dữ liệu sai kiểu; tệp lớn; thu hồi quyền khi job đang chạy.
- **Rủi ro:** tài nguyên chạy job; CSV injection; nhập dữ liệu mới được một phần.
- **Rollback:** dừng nhận job mới, hoàn tất hoặc hủy job theo chính sách; mỗi import batch có manifest để hoàn tác kèm kiểm tra version, không DELETE vô điều kiện.

### P1-08 — Gallery và Form nội bộ (M, một view mỗi lát cắt)

- **Mô tả/mục đích:** xem dạng thẻ và nộp form dành cho thành viên có quyền.
- **Thành phần:** view configs/renderers; validation khi gửi form; UI/contract tests.
- **Phụ thuộc:** P0 views/query; attachments nếu dùng ảnh bìa.
- **Nghiệm thu:** các view dùng chung record ID; quyền nộp form không tự cấp quyền đọc danh sách bản ghi; field bắt buộc tuân theo schema chung.
- **Test case:** field bị lưu trữ; bỏ trống field bắt buộc; gửi form hai lần; chính sách VIEWER; ảnh bìa không được phép tải.
- **Rủi ro:** nhầm form công khai với form nội bộ.
- **Rollback:** tắt loại view mới, giữ dữ liệu trong Grid; chưa bật chia sẻ công khai.

### P1-09 — Restore record và lịch sử có kiểm soát (M)

- **Mô tả/mục đích:** phục hồi dòng hoặc phiên bản cũ thành version mới, có audit.
- **Thành phần:** restore RPC; giao diện lịch sử; DB/UI tests.
- **Phụ thuộc:** P0 history/version/schema.
- **Nghiệm thu:** không ghi lại audit cũ; validate theo schema hiện tại; kiểm tra quyền hiện tại của người khôi phục.
- **Test case:** expectedVersion cũ; field đã xóa; liên kết không hợp lệ; bảng đạt giới hạn; khôi phục sau khi quyền bị thu hồi.
- **Rủi ro:** payload cũ không phù hợp schema hiện tại.
- **Rollback:** tắt thao tác khôi phục và giữ màn hình chỉ đọc; giữ lịch sử và bản ghi hiện tại.

### P1-10 — Vòng đời không gian/cơ sở dữ liệu/bảng (M, tách theo loại tài nguyên nếu lớn)

- **Mô tả/mục đích:** cho OWNER đổi tên và lưu trữ mềm workspace, base hoặc table; thể hiện rõ dữ liệu con và thao tác đang phụ thuộc trước khi xác nhận.
- **Thành phần:** migration bổ sung version/archived_at; lifecycle RPC và kiểm tra quyền; `WorkspaceFragment.kt`, `WorkspaceViewModel.kt`; DB/UI tests và API docs.
- **Phụ thuộc:** P0-03/P0-04/P0-06/P0-12; chính sách phạm vi lưu trữ dữ liệu con và quyền truy cập được duyệt.
- **Nghiệm thu:** chỉ OWNER được thay đổi vòng đời, có expectedVersion và audit; đổi tên giữ ID, membership và liên kết; archive không xóa dữ liệu. Đọc/ghi tài nguyên con và lời mời tuân theo trạng thái archive của workspace/base/table. APK cũ vẫn nhận phản hồi tương thích, không được tiếp tục ghi vào tài nguyên đã lưu trữ.
- **Test case:** ID thuộc workspace khác; expectedVersion cũ; archive khi thiết bị khác còn bản nháp; ghi/lời mời đồng thời với archive; đổi tên giữ ID và membership; resource có dữ liệu con hoặc liên kết phụ thuộc; APK cũ truy cập tài nguyên đã archive.
- **Rủi ro:** vô tình chặn cả nhóm hoặc làm người dùng hiểu nhầm dữ liệu bị xóa; nháp offline chỉ biết trạng thái archive khi kết nối lại; phụ thuộc dữ liệu cần chính sách rõ.
- **Rollback:** tắt capability và routes lifecycle mới, giữ dữ liệu cùng audit; không DELETE dữ liệu hoặc tự phục hồi quyền/member. Việc khôi phục trạng thái archive cần thao tác riêng được duyệt, không tự bỏ archive hàng loạt khi rollback code.

## 4. P2 — Nâng cao; cần duyệt từng nhóm, không cam kết full parity

### P2-01 — Linked records (M)

- **Mô tả/mục đích:** liên kết các bảng công việc, dự án hoặc khách hàng thay vì sao chép nội dung chữ.
- **Thành phần:** link metadata/edges/validator; picker/query; tests.
- **Phụ thuộc:** field ID ổn định và query của P0; chính sách lưu trữ schema của P1.
- **Nghiệm thu:** giới hạn cùng base/workspace theo phạm vi đã duyệt; quy định rõ quan hệ một/nhiều và liên kết ngược.
- **Test case:** liên kết sang workspace khác; bản ghi đích đã xóa; trùng liên kết nhiều-nhiều; sửa đồng thời.
- **Rủi ro:** query N+1 và kiểm tra ACL xuyên quan hệ.
- **Rollback:** tắt ghi liên kết; giữ edges ở chế độ chỉ đọc; không chuyển thành chữ rồi xóa dữ liệu nguồn.

### P2-02 — Formula, lookup, rollup (M mỗi kiểu; thiết kế engine trước)

- **Mô tả/mục đích:** tính giá trị dẫn xuất có kiểu dữ liệu và đồ thị phụ thuộc.
- **Thành phần:** grammar/engine được duyệt; dependency graph/cache; validators và lỗi trên UI; tests.
- **Phụ thuộc:** P2-01 cho lookup/rollup; danh sách hàm hữu hạn được duyệt.
- **Nghiệm thu:** field tính toán chỉ đọc; từ chối vòng phụ thuộc; lỗi không làm mất đầu vào; kết quả ghi nhận version nguồn.
- **Test case:** vòng phụ thuộc; chia cho 0; null; sai kiểu; dữ liệu phụ thuộc thay đổi; quyền trên liên kết thay đổi; vượt ngân sách thời gian tính toán.
- **Rủi ro:** sandbox/bảo mật; chi phí tính lại; ngữ nghĩa khác Teable/Airtable.
- **Rollback:** dừng tính lại và tạo field mới; hiển thị kết quả tính gần nhất kèm trạng thái đã cũ; giữ biểu thức và đầu vào.

### P2-03 — Automation và webhooks (M mỗi phân hệ)

- **Mô tả/mục đích:** hỗ trợ tập quy tắc hữu hạn và danh sách hành động cho phép; webhook có chữ ký và retry.
- **Thành phần:** rule/job/outbox; worker và kiểm soát kết nối ra ngoài; giao diện cấu hình; tests.
- **Phụ thuộc:** audit/outbox của P1; ngân sách, secrets và retention được duyệt.
- **Nghiệm thu:** không thực thi mã tùy ý; có chống trùng, chặn vòng lặp và hạn mức; mọi thao tác giới hạn theo workspace.
- **Test case:** SSRF qua webhook hoặc chuyển hướng đến IP nội bộ; timeout/replay; hành động lỗi; vòng lặp sự kiện; chủ sở hữu bị thu hồi quyền.
- **Rủi ro:** tác động ra hệ thống ngoài; secrets; phí dịch vụ ngoài.
- **Rollback:** tạm dừng job/subscription; giữ lịch sử gửi; không tự gửi bù toàn bộ hàng đợi.

### P2-04 — Dashboard tổng hợp (M)

- **Mô tả/mục đích:** thống kê việc theo trạng thái, người phụ trách và deadline từ query server.
- **Thành phần:** aggregate API/config; biểu đồ native dùng dependency được duyệt; tests.
- **Phụ thuộc:** query ACL, hiệu năng và định nghĩa chỉ số đã chốt.
- **Nghiệm thu:** đếm trên toàn bộ kết quả query; mở chi tiết sang Grid giữ filter; không lộ dữ liệu workspace khác.
- **Test case:** nhóm null; query lớn; thay đổi quyền; cập nhật đồng thời; cache offline chưa đầy đủ.
- **Rủi ro:** số liệu sai hoặc tải database lớn.
- **Rollback:** tắt dashboard, giữ Grid nguồn.

### P2-05 — Base snapshot và restore staging (M)

- **Mô tả/mục đích:** sao lưu theo phiên bản toàn bộ schema và bản ghi của base; đây là mức sao lưu khác với lịch sử từng bản ghi.
- **Thành phần:** snapshot job/metadata/private artifact; validation khôi phục; tests/docs.
- **Phụ thuộc:** chính sách backup/retention, RPO/RTO và ngân sách được duyệt.
- **Nghiệm thu:** khôi phục thành bản mới trên staging, đối chiếu số lượng, liên kết và ACL; mặc định không ghi đè base đang dùng.
- **Test case:** schema thay đổi; thiếu attachment; snapshot hỏng; ghi đồng thời; khôi phục nhầm workspace.
- **Rủi ro:** mất các thay đổi phát sinh sau snapshot; dữ liệu nhạy cảm.
- **Rollback:** hủy job snapshot/restore, giữ base hiện có; thao tác phá hủy dữ liệu cần duyệt riêng.

### P2-06 — Chính sách doanh nghiệp nếu cần (discovery trước)

- **Mô tả/mục đích:** xác định nhu cầu SSO, MFA, ACL chi tiết, form công khai và API tích hợp có phạm vi; quy mô 100 người chưa đủ để mặc định cần tất cả chức năng này.
- **Thành phần:** decision record/threat model/contract proposal; chưa chọn code phải sửa.
- **Phụ thuộc:** người dùng trả lời nhu cầu và ngân sách; G4.
- **Nghiệm thu:** mỗi phân hệ có phạm vi, quyền, test và chi phí rõ ràng, được duyệt trước khi viết code.
- **Test case:** diễn tập tình huống mất quyền truy cập, link công khai và tích hợp không tin cậy; prototype trong môi trường riêng nếu được duyệt.
- **Rủi ro:** thay auth/hosting vượt phạm vi hoặc vi phạm giấy phép.
- **Rollback:** dừng đề xuất, giữ Firebase và RBAC theo workspace hiện có; không tự bật SSO hay link công khai.

## 5. Phê duyệt cần nhận trước khi viết code

1. Có chọn toàn bộ P0 ở trên làm đợt kế tiếp không? Có thể duyệt P0 theo các cổng kiểm tra; quyết định này chưa bao gồm P1/P2.
2. Xác nhận workload 100 tài khoản và khoảng 20 người thao tác đồng thời; cung cấp số dòng mỗi bảng và ngày bàn giao. Nếu chưa biết, duyệt đợt thử nội bộ để đo workload trước khi tăng giới hạn.
3. Chọn quyền theo workspace hiện có hay cần quyền chi tiết hơn; có personal view hay không; field USER chọn một hay nhiều người; trạng thái tự do hay quy trình duyệt.
4. Chọn có cài/thử REA cục bộ theo [kế hoạch thiết lập](REA_SETUP_PLAN.md) không. Việc duyệt kiến trúc không phụ thuộc vào cài REA.
5. Duyệt môi trường staging, backup/retention, hạn mức và chi phí trước khi kiểm thử tải hoặc chạy migration cloud. Chưa tự đổi gói miễn phí.

**Dừng tại đây để duyệt.** Không task nào của roadmap được tự động kích hoạt bởi việc ghi tài liệu này.
