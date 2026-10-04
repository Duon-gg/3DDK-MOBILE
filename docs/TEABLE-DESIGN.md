# 3DDK: bảng dữ liệu và cộng tác trên Android

Ngày: 2026-10-04. Trạng thái: thiết kế chuyển đổi; chưa triển khai.

## Mục tiêu đã được người dùng chọn

Lấy Teable (https://github.com/teableio/teable) làm sản phẩm tham chiếu về chức năng và cách tổ chức dữ liệu. Ai cũng có thể đăng ký và tạo không gian riêng. Giữ ứng dụng Android Kotlin, XML, MVVM, View Binding, StateFlow, Room, Retrofit/Gson/Coroutines, Firebase Auth, Glide, EncryptedSharedPreferences và bản phát hành có chữ ký.

Không dùng WebView thay cho giao diện Android. Tên sản phẩm vẫn là 3DDK. Không khẳng định tương đương Teable khi chưa có bằng chứng kiểm thử từng chức năng. Mốc 28 ngày của sản phẩm giao việc cũ không còn là ước lượng hợp lệ cho toàn bộ phạm vi mới.

## Phương án

1. Dùng nguyên Teable web: tận dụng nhiều tính năng nhưng không đáp ứng giao diện Kotlin/XML.
2. Android gọi backend Teable: giảm phần xử lý bảng phải tự xây, nhưng cần giải quyết đăng nhập Firebase, khả năng API, vận hành máy chủ và giấy phép trước khi chọn.
3. Android và backend hiện tại được mở rộng: giữ Firebase/Supabase và khả năng kiểm soát phân quyền, nhưng công thức, cộng tác và bảng lớn cần xây mới.

Thiết kế cho phân hệ đầu chọn phương án 3. Trước phân hệ công thức/cộng tác nâng cao, đánh giá lại phương án 2 bằng thử nghiệm API có kiểm chứng. Không chuyển cloud hoặc nhập mã Teable vào repo trước khi có đánh giá tương thích và giấy phép của phần mã sử dụng.

## Các phân hệ và thứ tự

| Thứ tự | Phân hệ | Điều kiện nghiệm thu |
|---|---|---|
| 1 | Tài khoản và không gian làm việc | Tài khoản mới vào được app, tạo không gian, mời người, chuyển không gian; hai không gian không đọc/ghi dữ liệu của nhau |
| 2 | Cơ sở dữ liệu, bảng, cột, bản ghi | Tạo base/table; thêm, sửa, lưu trữ cột và bản ghi; kiểm tra kiểu dữ liệu; tìm kiếm/phân trang |
| 3 | Kiểu hiển thị | Grid, Form, Kanban, Gallery, Calendar; lưu bộ lọc, nhóm, sắp xếp và cột hiển thị theo view |
| 4 | Quan hệ và tính toán | Liên kết bản ghi, lookup, rollup, công thức; kiểm tra vòng lặp, lỗi kiểu và cập nhật phụ thuộc |
| 5 | Cộng tác | Lịch sử, bình luận, cập nhật trực tiếp, xung đột, hoàn tác có kiểm tra phiên bản |
| 6 | Trao đổi dữ liệu | Tệp đính kèm, nhập/xuất, chia sẻ có giới hạn quyền, API |
| 7 | Nâng cao | Tự động hóa, biểu đồ, plugin, truy vấn; đánh giá riêng AI/App Builder, chi phí và môi trường chạy mã |

Mỗi phân hệ có thiết kế và kiểm thử riêng. Danh sách này là phạm vi mục tiêu, không phải danh sách tính năng đã có. Phải đối chiếu phiên bản Teable tham chiếu khi lập tiêu chí chi tiết, không lấy README làm bằng chứng mọi tính năng có sẵn trong bản mã nguồn miễn phí.

## Thiết kế chi tiết phân hệ 1

### Người dùng và màn hình

- Đăng ký, xác minh email và đăng nhập giữ Firebase. GET hồ sơ không yêu cầu lời mời của nhóm cũ.
- Người chưa có không gian thấy màn hình chào mừng với Tạo không gian và Lời mời, không thấy thông báo bị chặn.
- Tạo không gian cần tên 1–100 ký tự. Server tạo không gian và gán người tạo làm owner trong một giao dịch.
- Trang Không gian hiển thị các không gian đang tham gia. Chọn không gian mới tải dữ liệu thuộc không gian đó.
- Trang Thành viên cho owner/admin mời email, xem lời mời đang chờ và thu hồi lời mời.
- Người nhận đăng nhập bằng đúng email đã xác minh và chủ động chấp nhận lời mời. Lời mời hết hạn sau 7 ngày; mời lại cấp lần mời mới.
- Chủ sở hữu chuyển quyền trước khi rời không gian. Không cho xóa hoặc vô hiệu hóa owner cuối cùng.
- Bottom Navigation gồm Không gian, Thông báo, Cá nhân. Các màn hình base/bảng xuất hiện ở phân hệ 2.

### Quyền trong không gian

Owner quản lý sở hữu và lưu trữ không gian. Admin quản lý thành viên trừ owner. Editor sửa nội dung. Viewer chỉ đọc. Không vai trò nào tự động có quyền ở không gian khác. Người mới tạo không gian khác trở thành owner của không gian đó, không thành quản trị viên hệ thống.

### Dữ liệu server

- profiles: uid, display_name, avatar_url; UID lấy từ token Firebase đã xác minh.
- workspaces: uuid, name, created_by, version, archived_at.
- workspace_members: workspace_id + uid duy nhất, role, active, version.
- workspace_invites: uuid, workspace_id, email chuẩn hóa, role, expires_at, accepted_at, revoked_at.
- workspace_events: người thực hiện, loại thao tác và thời gian; không lưu token hoặc nội dung nhạy cảm vào log.
- workspace_requests: chống lặp theo uid/requestId; cùng khóa khác payload trả xung đột.

Giữ bảng và endpoint v1 cũ. Endpoint v2 dùng namespace riêng và chỉ kiểm tra membership của workspace đích. Không gọi middleware thành viên toàn cục của v1 trước route v2.

Mọi truy vấn workspace phải kiểm tra quyền trên server; không tin workspaceId hoặc role do client gửi. Bật RLS và thu hồi truy cập trực tiếp của anon/authenticated; Edge xác minh token trước khi gọi RPC đặc quyền. Khóa giao dịch theo workspace; không dùng một khóa toàn hệ thống cho tất cả nhóm.

### API v2

- GET/PATCH /me: hồ sơ tài khoản đã xác minh, độc lập membership.
- GET/POST /workspaces: danh sách của UID và tạo không gian.
- GET/PATCH /workspaces/{id}: xem/sửa tên theo quyền, expectedVersion.
- GET /workspaces/{id}/members.
- POST /workspaces/{id}/invites; DELETE /workspaces/{id}/invites/{inviteId}.
- GET /invitations: chỉ lời mời khớp email đã xác minh trong token.
- POST /invitations/{id}/accept: kiểm tra hạn, thu hồi và email trong giao dịch.
- PATCH /workspaces/{id}/members/{uid}: thay đổi vai trò/trạng thái theo ma trận quyền.
- POST /workspaces/{id}/transfer-ownership và /leave.

Mutation có requestId; cập nhật đối tượng có expectedVersion. Trả 401, 403, 404, 409, 422, 429 rõ ràng. Không trả email thành viên ngoài phạm vi được phép.

### Android và offline

Tách WorkspaceRepository/ViewModel khỏi TaskRepository hiện tại. Room là nguồn UI quan sát; cache dùng khóa UID + workspaceId + loại dữ liệu + ID. Lựa chọn workspace gắn UID. Bản nháp cũ không bị xóa khi nâng cấp.

Offline được xem dữ liệu đã tải và lưu nháp; tạo không gian, chấp nhận lời mời, đổi quyền cần mạng. Không tự gửi bản nháp khi có mạng lại. Khi server xác nhận bị thu hồi quyền, xóa cache chia sẻ của workspace đó và giữ nháp riêng để sao chép. Không thể bảo đảm thu hồi ngay dữ liệu đã cache trên thiết bị đang offline.

Phân biệt hết phiên, thiếu quyền workspace, chưa xác minh và lỗi mạng. Lỗi mạng không được biến thành màn hình chưa được mời.

### Chuyển đổi dữ liệu và phát hành

Migration chỉ bổ sung cấu trúc mới. Backup trước khi chạy cloud. Nhóm cũ chỉ được chuyển vào một workspace kế thừa sau khi kiểm tra owner thực tế; không tự trao owner cho tài khoản đầu tiên. Đối chiếu số lượng thành viên/công việc/lịch sử sau chuyển đổi.

App mới tăng versionCode và dùng keystore hiện có. API v1 tiếp tục phục vụ APK cũ trong thời gian chuyển đổi. Các secret, google-services và keystore không commit. Không triển khai migration cloud chưa kiểm thử.

### Kiểm thử bắt buộc trước phát hành phân hệ 1

1. Tài khoản mới chưa được mời vẫn gọi /me, xem danh sách rỗng và tạo workspace được.
2. Request tạo gửi lại chỉ tạo một workspace và một owner.
3. Tài khoản thuộc A không liệt kê, đọc, sửa hoặc mời người vào B qua ID đoán được.
4. Viewer không sửa; admin không tự chiếm owner; owner cuối không bị xóa.
5. Chấp nhận lời mời sai email, hết hạn hoặc đã thu hồi bị chặn; nhận hai lần không tạo membership trùng.
6. Hai thiết bị cập nhật cùng version có một xung đột, không ghi đè âm thầm.
7. Đổi tài khoản/không gian không hiển thị cache cũ; mạng lỗi giữ dữ liệu tốt.
8. Nâng cấp Room giữ draft v1; logout cảnh báo draft trước khi xóa dữ liệu local.
9. API 24 và 35, màn hình nhỏ, chữ lớn, xoay màn hình; build debug/lint/unit/backend và kiểm thử thiết bị đạt.

## Trạng thái triển khai

Chỉ có thiết kế này được bổ sung. Mã sản phẩm và cloud vẫn là phiên bản quản lý công việc một nhóm. Chưa có chức năng bảng tùy biến hoặc tương đương Teable.
