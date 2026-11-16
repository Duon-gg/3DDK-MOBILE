# Bổ sung thao tác cơ sở dữ liệu

Phạm vi người dùng duyệt: chia sẻ riêng base, mở dữ liệu, đánh dấu, tới workspace, di chuyển, nhân bản, xóa. Giữ Kotlin/XML, Room, Firebase và Supabase. Không có Omni.

## Hiện trạng đã kiểm tra

Migration `202611130001_base_actions.sql` bổ sung quyền riêng cấp cơ sở dữ liệu. Metadata, query, lịch sử, view và ghi ô đều đi qua cùng kiểm tra quyền trong workspace_api. API do Edge gọi bằng service role; các bảng không được mở truy cập trực tiếp cho client.

## Hành vi triển khai

- Chia sẻ riêng qua email đã xác minh, vai trò EDITOR hoặc VIEWER; không tự thêm người nhận vào workspace. Chủ workspace quản lý chia sẻ. Quyền hiệu lực là quyền cao hơn giữa tư cách workspace và chia sẻ trực tiếp; không cho quyền chia sẻ trực tiếp hạ quyền chủ workspace.
- Di chuyển chỉ khi người thao tác là chủ cả workspace nguồn và đích. Cảnh báo thay đổi quyền kế thừa trước khi xác nhận. Người dùng đã chốt giữ những người được chia sẻ riêng khi di chuyển. Giữ ID bảng/dòng; xử lý cột USER và view cá nhân có chủ không còn quyền trước khi hoàn tất.
- Nhân bản tạo ID mới cho base, bảng, trường, dòng và view; ánh xạ lại khóa trường trong cells/filter/sort/projection/order. Không sao chép lời mời, quyền chia sẻ riêng hoặc lịch sử thao tác thành lịch sử của bản mới. Không sao chép view cá nhân của người khác.
- Xóa là xóa mềm; chủ workspace có thể khôi phục. Base đã xóa bị chặn ở mọi route bảng/dòng, không chỉ biến mất khỏi danh sách.
- Mutation dùng expectedVersion và requestId, giao dịch nguyên tử và audit. Retry không tạo bản sao thứ hai; khóa workspace theo thứ tự cố định khi chuyển giữa hai workspace.

## Phần cần sửa

Migration bổ sung ws_bases.version/deleted_at, thành viên/lời mời riêng base; cập nhật workspace_api và mọi helper kiểm tra quyền. Giữ migrations cũ. Thêm capability để APK không đưa ra thao tác chưa có trên cloud.

Android thêm màn quản lý chia sẻ, chọn workspace đích, đặt tên bản sao, xác nhận xóa và khôi phục. Menu hiện có tiếp tục tái sử dụng. Room cần dọn cache theo base khi mất quyền/xóa và cập nhật đường dẫn workspace sau di chuyển; giữ nháp để người dùng sao chép, không gửi khi đã mất quyền.

## Các kiểm thử bắt buộc trước deploy

Người nhận base không đọc được base khác hoặc danh sách thành viên workspace; VIEWER không ghi; thu hồi áp dụng cả metadata/query/history/view; xóa chặn mọi route và restore phục hồi dữ liệu; move chặn người không sở hữu đích; duplicate giữ ánh xạ trường và không lộ private view; requestId retry chỉ tạo một bản sao; xung đột version không ghi đè; cache/nháp không vượt quyền sau thay đổi.

## Kết quả ngày 13/11/2026

- Đã triển khai SQL và giao diện Android; capability `baseActions` chỉ bật các thao tác khi server hỗ trợ.
- Đã áp dụng migration trên Supabase project `ohkohjuwgdzhbetaqegx`. Định nghĩa API trước cập nhật được lưu riêng tại `C:/Users/nvtd2/3ddk-private/backups/2026-11-13-base-actions/api-before.sql`. Đây là bản sao hàm, không phải bản backup đầy đủ database.
- `npm test`: 34 ca qua, gồm kiểm thử mới và hồi quy các API cũ trên migration mới.
- `tests/base-actions-cloud.sql`: chạy thật trên database cloud và rollback dữ liệu thử; xác nhận chia sẻ, quyền chỉ xem, không lộ workspace, di chuyển giữ chia sẻ, nhân bản/retry, xóa/retry, khôi phục và thu hồi. Trước/sau vẫn 17 base, 27 record. Đây là kiểm thử SQL RPC, không thay thế kiểm thử xác thực HTTP hoặc thao tác Android.
- Build release, unit test Android (33 ca tại thời điểm build), lintVitalRelease thành công. Sau khi bổ sung kiểm thử cache: 35 ca Android đều qua.
- APK `artifacts/recent-bases/3ddk-base-actions-release.apk` đã cài đè Xiaomi thành công; đọc UI xác nhận app mở Gần đây và menu có đủ Chia sẻ/Di chuyển/Nhân bản/Xóa vào thùng rác. SHA-256: `b487e918e92d4bb40911fdab685d7dc29f1e23b345636ed5a27ce2ff696f1d46`.
- Chưa nghiệm thu end-to-end các thao tác mới trên Xiaomi; cần người dùng chạm vì ADB bị chặn INJECT_EVENTS. Chưa commit/push thay đổi.

## Cách dùng

1. Gần đây → nhấn giữ một cơ sở dữ liệu do mình sở hữu: Chia sẻ, Di chuyển, Nhân bản, Xóa vào thùng rác.
2. Chia sẻ → Mời qua email, chọn chỉnh sửa/chỉ xem. Người nhận mở biểu tượng chuông → Lời mời cơ sở dữ liệu để chấp nhận. Không tự gửi email.
3. Không gian → Được chia sẻ: bao gồm không gian được mời và cơ sở dữ liệu được chia sẻ riêng.
4. Dấu + → Thùng rác → chọn cơ sở dữ liệu → xác nhận khôi phục. Không có xóa vĩnh viễn.

## Giới hạn và khôi phục vận hành

Thao tác quản trị cần mạng. Thu hồi quyền không thể xóa dữ liệu từ thiết bị đang hoàn toàn offline; khi server trả lỗi quyền, cache được dọn nhưng bản nháp giữ riêng. Di chuyển/xóa dọn cache base trên thiết bị thao tác; mở lại từ không gian đích sau khi di chuyển. Chưa đo tải đồng thời 20 người cho các thao tác này.

Không tự chạy lại API cũ sau khi đã có cơ sở dữ liệu xóa mềm: API cũ không hiểu deleted_at và có thể làm lộ lại dữ liệu đã xóa. Nếu có lỗi triển khai, tạm dừng tính năng và sửa tiến về trước; chỉ phục hồi định nghĩa cũ khi xác nhận chưa có dữ liệu chia sẻ/xóa/di chuyển cần bảo toàn. Không xóa các bảng mới để rollback.
