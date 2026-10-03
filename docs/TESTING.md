# Kiểm thử và nghiệm thu
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

## Nghiệm thu cloud và hai thiết bị
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
Firebase/Supabase chưa được cấp: nghiệm thu cloud, FCM thật, hai tài khoản thật và thử sinh viên chưa được thực hiện.
