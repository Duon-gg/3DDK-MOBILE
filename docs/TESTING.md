# Kiểm thử và nghiệm thu

## Luồng bảng P0 hiện tại

Luồng chính là Không gian → Cơ sở dữ liệu → Bảng → Dòng công việc. Luồng nhóm trưởng giao, đại diện nộp và duyệt ở phần v1 bên dưới không phải tiêu chí nghiệm thu giao diện P0.

Hai test `WorkspaceLiveTest` và `WorkspaceEditLiveTest` tự bỏ qua nếu không có đối số `liveWorkspace=true`. Muốn chạy thật, cài APK debug và test APK cùng bản build trên máy ảo dành cho kiểm thử, đăng nhập Firebase đã xác minh trong ứng dụng và giữ kết nối mạng. Không truyền mật khẩu hay token qua đối số test. Nếu thiết bị đã cài release bằng khóa khác, dùng máy ảo khác; không gỡ app hoặc xóa dữ liệu để ép cài debug.

```powershell
adb -s emulator-5556 shell am instrument -w -r -e liveWorkspace true -e class com.threeddk.tasks.WorkspaceLiveTest,com.threeddk.tasks.WorkspaceEditLiveTest com.threeddk.tasks.test/androidx.test.runner.AndroidJUnitRunner
```

Thay mã thiết bị theo `adb devices`. Mỗi test tạo không gian riêng tên `3DDK • Kiểm thử P0 <mã>` và giữ lại để xem bằng chứng; không tái sử dụng hay sửa không gian có sẵn.

| Test | Nội dung kiểm tra |
|---|---|
| `WorkspaceLiveTest` | Cloud hỗ trợ P0; tạo bảng công việc; gửi lại không tạo trùng; PATCH từng ô; từ chối version cũ; replay không sinh thêm lịch sử; query lưu vào Room; Grid hiển thị và xoay màn hình. |
| `WorkspaceEditLiveTest` | Tạo không gian, cơ sở dữ liệu và bảng qua giao diện, mỗi lần thành công tự mở mục vừa tạo; nháp có khóa riêng; xoay màn hình giữ nội dung; lưu thành công rồi xóa đúng nháp đã gửi. |

Các test này chỉ dùng một tài khoản. Đọc cache Room không thay cho bài thử tắt mạng thực tế; xung đột hai request cùng tài khoản không thay cho cộng tác hai người.

### Nghiệm thu P0 với hai tài khoản

1. Chủ không gian tạo bảng công việc, mời tài khoản thứ hai chỉnh sửa. Người nhận mở tab Lời mời, tham gia và mở được đúng bảng.
2. Đổi người thứ hai sang Chỉ xem; không thể thêm, sửa hay lưu trữ dòng qua giao diện hoặc API. Chuyển lại quyền chỉnh sửa để thử tiếp.
3. Hai máy mở cùng dòng. A lưu trước, B lưu version cũ phải báo xung đột, giữ nháp và cho so sánh thay đổi.
4. Tải dữ liệu rồi tắt mạng, mở lại app và soạn nháp. Nháp phải còn sau khi khởi động lại; chưa có cache phải báo cần kết nối.
5. Có mạng, kiểm tra rồi bấm lưu. Lỗi giữa lúc gửi không làm mất nháp hoặc tạo dòng thứ hai khi thử lại.
6. Thu hồi quyền; request tiếp theo bị chặn. Khi kết nối lại, app bỏ cache bị thu hồi, giữ nháp và dữ liệu của không gian khác.
7. Đăng xuất theo hộp xác nhận rồi đổi tài khoản, không thấy cache hoặc nháp tài khoản trước.
8. Kiểm tra tìm, lọc, sắp xếp, chế độ xem cá nhân/chung, tải trang tiếp và xoay màn hình, không ghép nhầm kết quả giữa hai bộ lọc.

Kết quả đã chạy xem [P0-VERIFICATION.md](P0-VERIFICATION.md). Danh sách trên là cách nghiệm thu, không xác nhận mọi bài thử cloud đã đạt. Thông báo v2 và tải 100 người/20 người cùng sửa cần kiểm tra riêng.

## Kiểm tra tự động
```sh
npm ci
npm test
npm run test:edge
npm run check:edge
```
```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```
Lệnh connected cần emulator hoặc thiết bị đã cấp quyền ADB. Test Room dùng database riêng và xóa đúng database do test tạo.

Database test chạy PostgreSQL trong PGlite, không thay database cloud. Kiểm tra quyền, trạng thái, idempotency, xung đột, outbox và nhắc hạn.
Edge test ký JWT RSA tại chỗ và kiểm tra project/email/auth_time, không gọi Firebase thật.
Android unit test dùng MockWebServer kiểm tra Authorization và replay khi 401; không giả lập rằng cloud đã hoạt động.

## Luồng v1 giữ để kiểm tra tương thích
1. Dùng tài khoản nhóm trưởng và hai thành viên email đã xác minh. Tạo thêm tài khoản không được mời.
2. Nhóm trưởng giao một việc cho hai người, chỉ định đại diện. Máy thành viên tải được đúng dữ liệu.
3. Người không phải đại diện không nộp được; thành viên không tạo/giao/duyệt bằng UI hoặc gọi API trực tiếp.
4. Đại diện bắt đầu, bật chế độ máy bay, soạn nháp, đóng rồi mở app. Nháp phải còn.
5. Có mạng, nộp một lần; mô phỏng timeout rồi thử lại. Chỉ có một submission.
6. Nhóm trưởng trả lại kèm lý do; đại diện nộp lại; duyệt đúng submission hiện tại.
7. Hai thiết bị cùng sửa version cũ: một request bị 409 và nội dung soạn được giữ.
8. Kiểm tra push khi foreground/background, nhấn để mở đúng task, từ chối quyền, đổi deadline và hoàn thành trước nhắc hạn.
9. Tắt quyền thành viên sau khi đã phân công lại; lần refresh tiếp theo phải chặn quyền và xóa cache.
10. Cài release rồi nâng cấp bằng APK cùng khóa ký và versionCode cao hơn; database/nháp phải còn.
11. Mở màn hình sửa trên A; B sửa cùng việc; A tải cache mới. A phải thấy lựa chọn xử lý xung đột, không tự ghi đè.
12. Mô phỏng tạo việc thành công nhưng mất phản hồi, xoay màn hình rồi bấm Lưu lại với cùng nội dung: chỉ một task. Đổi mức ưu tiên phải tạo requestId mới.
13. Lưu nháp, làm phiên đăng nhập hết hiệu lực rồi đăng nhập lại cùng UID: nháp còn. Đổi sang UID khác không đọc được nháp cũ.

Nhắc hạn kiểm tra bằng deadline ngắn trên project thử nghiệm; không cần chờ 24 giờ.
Đo bằng timestamp server, ghi giờ gửi/nhận và quyền thông báo. Không coi push không đến vì force-stop là lỗi lịch cron.

## Giao diện và bộ nhớ
Chạy API 24 và API 35, màn hình nhỏ, font 1.3x/1.5x, xoay màn hình khi soạn.
Kiểm tra loading, lỗi, empty, ảnh tải lỗi và trạng thái cache.
LeakCanary chỉ có ở debug; lặp mở/đóng từng Fragment và xoay màn hình. Dùng Profiler xác nhận release nếu có điều kiện.

## Trạng thái bằng chứng
Xem docs/IMPLEMENTATION.md để biết những kiểm tra đã chạy thực tế.
Firebase/Supabase đã được cấu hình và P0 đã được triển khai theo [P0-VERIFICATION.md](P0-VERIFICATION.md); những bài thử thật còn thiếu phải được ghi nhận riêng, không suy ra từ build hoặc test cục bộ.
