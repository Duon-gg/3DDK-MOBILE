# Kiểm tra P0 trước khi dùng thử

Ngày kiểm tra: 17/10/2026. Công nghệ giữ nguyên: Kotlin/XML, MVVM, Room, Retrofit, Firebase Auth, Supabase/PostgreSQL/Edge Functions. REA chưa được cài hoặc chạy.

## Phạm vi và bằng chứng

| Phần | Kiểm tra có thể chạy lại | Giới hạn |
|---|---|---|
| API Edge | `npm run test:edge` — 11/11 đạt | Deno, JWT ký bằng khóa kiểm thử và RPC giả; chưa dùng Firebase token thật để nghiệm thu P0 |
| Kiểu TypeScript/deploy entry | `npm run check:edge` — đạt; source đóng gói để Dashboard cũng qua Deno check | Dashboard đã cập nhật API; vẫn cần smoke test Auth/API thật |
| Database và phân quyền | `npm test` — 28/28 test đạt trong lượt tích hợp P0 | PostgreSQL chạy qua PGlite trong bộ nhớ, không sửa cloud |
| Tải thử cục bộ | `npm run test:load:local` | 100 UID tổng hợp, 1/5/20 worker, một kết nối PGlite xếp hàng |
| Android | Build cuối sau thay đổi giao diện: debug, unit, lint, test APK, release APK và AAB thành công | 22 JVM test đạt, gồm 11 WorkspaceData; build cuối mất 3 phút 14 giây |
| Room và luồng thiết bị | Direct ADB instrumentation trên API 24 — 8/8 đạt | 5 Room/offline và 3 CellDisplay; không thay luồng nhiều tài khoản thật |
| Bản ký và nâng cấp | APK release/AAB đã xác minh chữ ký; APK release cài `-r` thành công trên API 35 | Dùng cùng khóa, không gỡ app/xóa dữ liệu; chưa nghiệm thu toàn bộ dữ liệu qua luồng thật |

Trạng thái ở lượt cập nhật cuối: 28 Node test, 11 Deno test, TypeScript check, 22 JVM test và 8 instrumentation test API 24 đã đạt; build debug/release/AAB cuối thành công. APK release đã cài nâng cấp trên API 35. Migration P0 đã áp dụng trên cloud; kiểm tra chỉ đọc xác nhận giữ hai bảng dữ liệu/hai record hiện có, backfill hai saved view, tạo ba bảng P0, chặn client truy cập trực tiếp bảng/helper. Dashboard đã cập nhật API từ source gồm bốn module hiện tại được ghép để deploy và đã qua Deno check; source API cũ được sao lưu riêng. Chưa có smoke test endpoint P0 bằng Firebase ID token thật, luồng cộng tác hai tài khoản hoặc ảnh xác nhận giao diện P0 cuối, nên chưa coi toàn bộ cổng nghiệm thu P0 là hoàn thành.

API Edge có 11 test đã chạy đạt: từ chối token thiếu/sai/hết hạn/sai Firebase project; email chưa xác minh; không nhận UID/role của client làm danh tính; JSON sai kiểu; giới hạn 32.768 byte theo header và luồng thực; hủy luồng quá lớn; UTF-8 lỗi; route/method sai; POST query được chuyển nguyên đến v2; v1 vẫn gọi worker sau mutation thành công; mã lỗi và request ID không lộ lỗi nội bộ.

`tests/p0-security.test.mjs` có thêm 5 test độc lập đã chạy đạt: tổng tối đa 20 predicate qua các nhóm lồng nhau; toán tử/kiểu filter không hợp lệ; projection khác workspace, view cá nhân và cursor khác UID; VIEWER query/history không tạo audit/replay mutation; chuỗi có hình thức SQL vẫn được coi là giá trị; client không được đọc/ghi trực tiếp các bảng P0 hoặc gọi helper RPC. Chỉ dispatcher `workspace_api` được cấp EXECUTE cho `service_role`.

Mỗi response Edge có `X-Request-Id` và `Cache-Control: no-store`. Lỗi JSON có `code`, `message`, `requestId`. Request ID của HTTP do server tạo để tra log, khác với `requestId` client gửi trong mutation để chống gửi trùng. Lỗi `RATE_LIMIT` có `Retry-After: 60` giây, là khoảng chờ bảo thủ cho cửa sổ một phút.

## Thí nghiệm tải trong repo

`tests/p0-load.mjs` luôn tạo database PGlite riêng trong bộ nhớ. Script không nhận URL cloud, không đọc secrets và đóng database sau mỗi trường hợp. Hai kích thước cố định là 500 và 5.000 dòng; 5.000 chỉ là dữ liệu tổng hợp chèn trực tiếp trong fixture. Giới hạn tạo dòng qua API vẫn là 500 và script có assertion chứng minh không thể tạo vượt giới hạn đó.

Mỗi kích thước chạy lần lượt 1, 5 và 20 worker, mỗi trường hợp tạo mới database và 100 thành viên tổng hợp: 1 OWNER, 79 EDITOR, 20 VIEWER. Workload chính có 200 thao tác: 160 query trang 50 dòng và 40 cell patch lên các dòng khác nhau. Trong 40 lần ghi có 8 lần cố ý dùng VIEWER để kiểm tra server từ chối; 32 lần ghi hợp lệ phải tăng version. Query không được trả quá 50 dòng. Kết quả JSON tách p50/p95/p99/max, kích thước response, lỗi ngoài dự kiến và từ chối quyền đúng dự kiến. CI lưu stdout thành artifact `local-p0-load-experiment`; không đặt ngưỡng tốc độ theo máy CI.

Sau workload chính, mỗi trường hợp có kiểm tra riêng: hai yêu cầu ghi cùng dòng/cùng version phải có đúng một commit và một `CONFLICT`; năm chu kỳ thu hồi lời mời phải chặn nhận lời mời cũ, gỡ thành viên phải chặn query ngay, rồi mời và nhận lời mời mới phải khôi phục quyền đọc. Các kiểm tra này chạy trên hàng đợi PGlite và không chứng minh tranh chấp transaction PostgreSQL song song. Tổng thời gian workload dưới đây không tính setup hoặc các kiểm tra riêng.

Đây là phép đo hồi quy logic/query trong môi trường cục bộ. PGlite dùng một kết nối, nên 20 worker bị xếp hàng, không mô phỏng 20 transaction PostgreSQL song song. Kết quả không bao gồm mạng, Edge cold start, Firebase/JWKS, PostgREST, điện thoại, đồng bộ Room hay hạn mức Supabase. Không dùng kết quả này để tuyên bố hệ thống đã chịu được công ty 100 người. Tình huống hai người cùng ghi một version được kiểm tra bằng assertion xung đột trong bộ test database; vẫn cần kiểm tra tranh chấp khóa trên staging PostgreSQL.

Một lần chạy trên máy phát triển Windows/Node 24 ngày 17/10/2026 cho kết quả dưới đây. Các thời gian bao gồm hàng đợi PGlite và thay đổi theo máy/tải máy; chỉ dùng làm mốc hồi quy. P99 dùng nearest rank; chỉ 40 mẫu ghi nên P99 ghi trùng max và chưa phải ước lượng ổn định.

| Dòng tổng hợp | Worker xếp hàng | Query p95 / p99 (ms) | Patch p95 / p99 (ms) | Tổng 200 thao tác (ms) | Lỗi ngoài dự kiến |
|---|---|---|---|---|---|
| 500 | 1 | 69,42 / 163,82 | 85,37 / 121,03 | 5.113,73 | 0/200 (0%) |
| 500 | 5 | 278,25 / 333,46 | 259,09 / 322,61 | 6.687,82 | 0/200 (0%) |
| 500 | 20 | 192,50 / 196,61 | 191,33 / 193,05 | 1.764,58 | 0/200 (0%) |
| 5.000 (chỉ fixture) | 1 | 121,83 / 173,08 | 15,01 / 24,98 | 8.362,26 | 0/200 (0%) |
| 5.000 (chỉ fixture) | 5 | 262,17 / 305,12 | 253,37 / 320,28 | 6.747,96 | 0/200 (0%) |
| 5.000 (chỉ fixture) | 20 | 729,47 / 914,88 | 640,38 / 943,96 | 4.706,81 | 0/200 (0%) |

Cả sáu trường hợp đều đạt 160 query, 32 patch thành công, 8 lần `FORBIDDEN` đúng dự kiến (8/200 = 4% request), kiểm tra xung đột cùng dòng và năm chu kỳ lời mời/quyền. Không lỗi assertion; tỷ lệ lỗi ngoài dự kiến 0%; giới hạn tạo dòng API 500 vẫn được giữ. Response query lớn nhất trong toàn bộ matrix là 10.557 byte. Việc thời gian không tăng đều theo số worker phản ánh hàng đợi, tải máy và làm nóng runtime; không suy ra khả năng mở rộng cloud từ bảng này. Đây chưa phải chỉ tiêu SLA.

## Các bước nghiệm thu cần thiết bị và cloud

1. Sao lưu database trước migration; deploy migration bổ sung và Edge tương ứng trên môi trường thử. Kiểm tra capability bằng token hợp lệ, sau đó cài APK hiểu contract P0.
2. Dùng hai tài khoản Firebase đã xác minh trên hai thiết bị: OWNER và EDITOR/VIEWER. Tạo bảng mẫu, lời mời, đổi quyền, gỡ quyền; xác nhận request kế tiếp bị chặn sau thu hồi.
3. Mở cùng một dòng trên cả hai thiết bị, lưu ở thiết bị A rồi lưu ở B. B phải báo xung đột và giữ nháp; không được ghi đè hoặc xóa nháp. Sửa schema trong khi B đang soạn cũng phải giữ input.
4. Tắt mạng sau khi tải một trang; mở lại app, xem cache và nháp; phân biệt dữ liệu tải một phần. Đăng xuất/đổi tài khoản khi request đang chạy, xác nhận không xuất hiện dữ liệu tài khoản cũ.
5. Mở saved view, thay filter/sort/search rồi tải thêm trang; quay lại, xoay màn hình và khởi động lại app. Không để kết quả query cũ chồng lên query mới.
6. Kiểm tra API 24 và 34/35, chữ 200%, TalkBack; APK ký/R8 và nâng cấp từ APK trước phải giữ Room/draft.
7. Khi biết số dòng thực tế mỗi bảng và ngân sách/hạn mức cloud, thống nhất workload staging 100 tài khoản thật và khoảng 20 người thao tác đồng thời. Đo lỗi, p95, lock wait, response bytes, Room/render và giới hạn dịch vụ trước khi cho công ty dùng thử.

## Triển khai và khôi phục

Đã diễn tập khôi phục cục bộ ngày 17/10/2026 từ bản sao riêng trước P0, lấy từ PostgreSQL 17.11. Script chỉ đọc CSV riêng ngoài repo, tạo PGlite mới trong bộ nhớ, dựng schema bằng hai migration gốc v1/v2, khôi phục 11 bảng `ws_*` theo thứ tự khóa ngoại, rồi áp dụng migration P0. Kết quả đạt: 38 dòng được khôi phục; mọi ID, thời gian và giá trị cột gốc được đối chiếu đầy đủ trước/sau P0; hai bảng dữ liệu có đúng hai default view được backfill với OWNER hợp lệ. Không in dữ liệu dòng, token hoặc nội dung riêng; không gọi cloud.

| Bảng trong bản sao trước P0 | Số dòng khôi phục |
|---|---|
| ws_profiles / ws_workspaces / ws_members | 1 / 2 / 2 |
| ws_bases / ws_tables / ws_fields / ws_records | 2 / 2 / 4 / 2 |
| ws_invites / ws_events / ws_requests / ws_limits | 0 / 11 / 11 / 1 |

Diễn tập này xác nhận bản sao các bảng workspace có thể nạp lại và đi qua migration P0 cục bộ. Phạm vi không bao gồm dữ liệu v1, Firebase Authentication, secrets hoặc cấu hình cloud; chưa phải diễn tập khôi phục toàn bộ dịch vụ trên PostgreSQL/Supabase thật. Bản sao và script chạy với đường dẫn riêng không được đưa vào Git.

Repo chưa có script phát hành Edge riêng; `check:edge` chỉ kiểm tra kiểu, `.tools/export-v2.cjs` chỉ xuất APK/AAB cũ. Không coi hai lệnh này là deploy server. Máy hiện chưa tìm thấy lệnh Supabase CLI trong PATH.

Phạm vi deploy nhỏ nhất là migration `supabase/migrations/202610170001_p0_workspaces.sql` đúng một lần trên database đã có v2, rồi toàn bộ bundle function `api` hiện tại. Không chạy lại migration v1/v2 bằng SQL Editor và không chạy `db push` nếu chưa đối chiếu lịch sử migration đã áp dụng thủ công. Entry `supabase/functions/api/index.ts` giờ nhập thêm `_shared/api_handler.ts`; bundle phải gồm `auth.ts`, `backend.ts`, `api_handler.ts` cùng cấu hình imports `supabase/functions/deno.json`. Chỉ dán entry index cũ vào trình sửa cloud sẽ thiếu module mới.

Nếu người vận hành đã có CLI được cấu hình, lệnh phát hành tương ứng với quy trình hiện có là `supabase functions deploy api --project-ref ohkohjuwgdzhbetaqegx`. Giữ `verify_jwt = false` trong `supabase/config.toml` vì handler xác minh Firebase JWT trước RPC. Đợt P0 không cần phát hành lại `dispatch`, thay service-account hoặc thay scheduler. Sau deploy, dùng token Firebase đã xác minh để kiểm tra `/api/v2/capabilities`, metadata, query và một mutation dữ liệu thử; không lưu token vào repo/log.

Migration P0 là bổ sung; không xóa migration/dữ liệu cũ. Không dùng down migration phá dữ liệu để khôi phục. Giữ bản backup, APK và Edge trước phát hành; khi lỗi, dừng phát hành APK và vô hiệu hóa capability/route mới phù hợp, giữ các dữ liệu đã ghi. Không đưa app cũ về legacy full replacement trên bảng có USER hoặc kiểu dữ liệu mới; app cũ phải nhận `CLIENT_UPGRADE_REQUIRED`. Nếu lỗi migration Room, sửa bằng migration tiến tiếp và kiểm thử với bản sao database, không bật destructive migration.

## Giới hạn còn phải xử lý hoặc nghiệm thu

- Các thao tác tạo cấu trúc như workspace/base/table, field/view và lời mời giữ request ID chờ trong ViewModel, chưa có nhật ký yêu cầu lưu bền trong Room. Nếu process bị đóng sau server commit nhưng trước khi nhận response, mở lại rồi bấm tạo có thể sinh đối tượng thứ hai. Tạo record đã có payload chờ lưu trong draft; giới hạn này liên quan các thao tác cấu trúc còn lại.
- Cap tạo dòng qua API vẫn là 500 mỗi bảng. Fixture 5.000 dòng chỉ dùng trong PGlite, không mở cap production hoặc chứng minh năng lực công ty 100 người.
- Còn cần đăng nhập Firebase thật để kiểm tra cloud API với APK mới, cộng tác hai tài khoản, thao tác đồng thời trên PostgreSQL thật, tải theo dữ liệu công ty, kiểm tra giao diện cuối và diễn tập khôi phục toàn bộ dịch vụ. Việc cài nâng cấp bản ký đã đạt ở mức cài đặt; luồng dữ liệu thật sau nâng cấp còn cần nghiệm thu.
