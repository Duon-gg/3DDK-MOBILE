# 3DDK 0.2.0-preview — bảng tùy biến

## Dùng thử

1. Cài APK có cấu hình cloud. Bản debug cập nhật được APK debug trước đó; bản release có chữ ký khác và không được cài đè lên debug.
2. Đăng nhập hoặc tạo tài khoản rồi xác minh email. Không cần được nhóm trưởng mời để vào app.
3. Không gian → Tạo không gian → Tạo cơ sở dữ liệu → Tạo bảng.
4. Bảng có sẵn cột Tên. Menu trên cùng → Thêm cột: văn bản, số, ngày, đánh dấu, lựa chọn.
5. Thêm dòng, nhập nội dung rồi Lưu lên bảng. Chạm một dòng để sửa. Vuốt ngang để xem các cột.
6. Tại không gian, menu → Mời thành viên. Người nhận dùng đúng email, mở mục Lời mời và chấp nhận. Thông báo lời mời hiện nằm trong app; chưa gửi email tự động.

## Quyền và giới hạn của bản dùng thử

- Chủ không gian quản lý cột và mời người. Người chỉnh sửa tạo cơ sở dữ liệu/bảng và sửa dòng. Người chỉ xem không ghi dữ liệu.
- Tối đa 20 không gian do một tài khoản sở hữu, 50 cơ sở dữ liệu/không gian, 50 bảng/cơ sở dữ liệu, 30 cột và 500 dòng chưa lưu trữ/bảng. Giới hạn được kiểm tra trên server.
- Đọc cache khi offline. Nhập bản nháp trên máy, gửi thủ công khi có mạng. Nháp gắn tài khoản/bảng/dòng.
- Khi xung đột, không tự ghi đè. Người dùng chủ động nhận phiên bản mới và kiểm tra lại trước khi gửi.
- Không tự chuyển công việc v1 thành bảng. API v1 và dữ liệu cũ được giữ.
- Chưa có công thức, quan hệ giữa bảng, Kanban/Calendar, tệp đính kèm, nhập/xuất, realtime hoặc tự động hóa/AI của Teable.

## Backend

API mới: `/functions/v1/api/v2`. Firebase JWT đã xác minh email là điều kiện chung; quyền không gian được kiểm tra trong từng route.

Migration bổ sung: `supabase/migrations/202610040001_workspaces.sql`. Chạy một lần trong giao dịch; không chạy lại sau khi đã thành công. Edge `api/index.ts` hỗ trợ cả v1/v2. V2 chưa phát sự kiện qua worker thông báo cũ.

Không cấp quyền gọi trực tiếp `workspace_api` cho anon/authenticated; chỉ service_role phía Edge. Không đưa service account, khóa đặc quyền, cấu hình ký vào Git.

## Kiểm tra

- `npm test`: cả database v1 và workspace v2.
- `npm run test:edge`, `npm run check:edge`: xác thực và TypeScript.
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Kiểm thử thật opt-in `WorkspaceLiveTest` với argument `liveWorkspace=true`: cần tài khoản đã xác minh đăng nhập trên thiết bị. Tạo không gian mẫu có tên rõ ràng, kiểm tra API và grid; không xóa dữ liệu người dùng. Chạy lại tạo một không gian mẫu khác.

Các cảnh báo deprecated của EncryptedSharedPreferences được giữ vì yêu cầu môn học. Trạng thái preview không có nghĩa đã tương đương toàn bộ Teable.

## Kết quả ngày 2026-10-04

- 14 kiểm thử PostgreSQL đạt (9 v1, 5 v2); 2 kiểm thử Firebase JWT đạt; Deno check đạt.
- 9 unit test Android đạt; lint không còn error (vẫn có warnings).
- Build debug, APK release và AAB có chữ ký thành công; apksigner xác nhận APK release.
- Migration v2 và Edge API đã triển khai trên môi trường thử nghiệm hiện có. GET v2/me thiếu token trả 401.
- Điện thoại thật: WorkspaceLiveTest tạo không gian/bảng/cột, thêm và sửa dòng qua Firebase token thật, render grid và recreate đạt.
- Điện thoại thật: WorkspaceEditLiveTest nhập bằng giao diện, xác nhận nháp trong Room, recreate giữ nội dung, gửi thành công và xóa nháp sau xác nhận server đạt.
- Có không gian mẫu “3DDK • Bảng dùng thử” trong tài khoản trên thiết bị. Không phải dữ liệu giả chỉ hiển thị trên giao diện.
- Chưa nghiệm thu thông báo v2, nhiều thiết bị đồng thời, thu hồi thành viên hoặc quy trình nâng cấp release; không công bố bản này là v1.0 hoàn chỉnh.
