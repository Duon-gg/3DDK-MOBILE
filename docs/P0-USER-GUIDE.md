# Hướng dẫn dùng thử 3DDK Tasks P0

Dành cho bản Android và backend P0 tương ứng. Tài liệu mô tả thao tác đã có trong mã P0; không phải biên bản nghiệm thu trên điện thoại. Nếu ứng dụng báo cần cập nhật backend hoặc APK, báo người quản trị trước khi thử tiếp. Tiến độ kiểm tra và điều kiện triển khai nằm trong [P0-VERIFICATION.md](P0-VERIFICATION.md).

## 1. Bắt đầu và tạo bảng công việc

1. Đăng ký tài khoản bằng email thật, xác minh email rồi đăng nhập.
2. Trong **Không gian**, bấm **Tạo không gian** và nhập tên nhóm hoặc công ty.
3. Mở không gian vừa tạo → **Tạo cơ sở dữ liệu**. Đây là nơi chứa các bảng của một dự án.
4. Mở cơ sở dữ liệu → **Tạo bảng**. Chủ không gian chọn **Bảng công việc (có sẵn cột)**, nhập tên và bấm **Tạo**.
5. Mở bảng. Mẫu có các cột **Tên, Mô tả, Trạng thái, Ưu tiên, Hạn, Người phụ trách**. Toàn bộ mẫu được tạo bằng một yêu cầu; nếu lỗi, kiểm tra thông báo rồi thử lại.

Người tự tạo không gian là chủ của không gian đó. Người tham gia không nhìn thấy không gian của người khác nếu chưa được mời.

## 2. Mời người cùng làm và quản lý quyền

Chủ không gian mở màn hình không gian → menu **⋮** → **Mời thành viên** → nhập email → chọn quyền → **Mời**. Đây là lời mời trong ứng dụng; ứng dụng chưa gửi thư mời qua email. Nhờ người nhận đăng nhập bằng đúng email và mở tab **Lời mời** → chọn lời mời → **Tham gia**. Lời mời có hiệu lực 7 ngày.

| Quyền | Cách dùng chính |
|---|---|
| Chủ không gian | Quản lý thành viên, cột và chế độ xem chung; tạo bảng công việc |
| Có thể chỉnh sửa | Thêm, sửa, lưu trữ dòng trong không gian |
| Chỉ xem | Đọc dữ liệu; tạo chế độ xem cá nhân, không sửa dữ liệu chung |

Để đổi quyền hoặc gỡ người, chủ mở **⋮ → Thành viên** → chọn người → chọn thao tác → đọc cảnh báo và xác nhận. **Chuyển quyền chủ không gian** sẽ chuyển người thao tác hiện tại thành thành viên có thể chỉnh sửa. Chỉ thực hiện khi đã chọn đúng người nhận.

Mở **⋮ → Lời mời đã gửi** để thu hồi lời mời đang chờ. Việc thu hồi lời mời khác với gỡ thành viên đã tham gia. Người đang ngoại tuyến nhận biết việc mất quyền khi kết nối lại; dữ liệu đã tải trên thiết bị không thể bị thu hồi tức thời qua mạng bị ngắt.

## 3. Thêm và cập nhật công việc

1. Mở bảng → **Thêm dòng**.
2. Điền **Tên** công việc. Tên bắt buộc đối với mẫu bảng công việc.
3. Chọn trạng thái và ưu tiên. Chạm **Hạn** để mở lịch; ứng dụng hiển thị ngày/tháng/năm.
4. Chạm **Người phụ trách**, tìm theo tên và chọn một thành viên. Có thể **Bỏ phân công** hoặc dùng biểu tượng xóa của ô.
5. Bấm **Lưu lên bảng**. Nháp chỉ được xóa sau khi máy chủ xác nhận lưu thành công.

Chạm một dòng trong bảng để sửa. Các thay đổi khi nhập được lưu thành **nháp trên máy**, chưa tự gửi cho mọi người. Người có quyền chỉ xem không có nút thêm hoặc lưu dữ liệu.

Nếu mất kết nối khi tạo dòng và chưa biết máy chủ đã nhận chưa, ứng dụng khóa ô nhập và hiện **Thử gửi lại**. Bấm nút này để xác nhận lại đúng lần tạo trước, tránh tạo trùng. Có thể **⋮ → Sao chép bản nháp**. Không có thao tác xóa nháp đang chờ xác nhận; mở nháp để kiểm tra lần gửi trước.

Các cột **Người phụ trách** kiểu cũ nhập bằng chữ vẫn giữ dữ liệu cũ. Cột thành viên mới lưu theo tài khoản; ứng dụng không tự đoán và chuyển tên chữ thành tài khoản.

## 4. Tìm, lọc, sắp xếp và chọn cột

- **Tìm trong bảng:** nhập từ khóa; ứng dụng đợi một khoảng ngắn rồi tìm trên máy chủ. Khi có nút **Tải thêm dòng**, bấm để xem trang tiếp theo.
- **Lọc:** bấm **Lọc → Thêm điều kiện** → chọn cột → chọn phép so sánh → nhập/chọn giá trị. Với cột thành viên, **là tôi** tìm các việc gán cho tài khoản hiện tại. Chạm một điều kiện để xóa; **Xóa tất cả** bỏ bộ lọc.
- **Sắp xếp:** chọn cột và tăng/giảm dần. Có thể sắp theo tối đa 3 cột trong giao diện này; ô trống ở cuối. Chạm một mục sắp xếp để bỏ mục đó.
- **Cột:** chọn các cột muốn hiển thị → **Áp dụng**. Cần giữ ít nhất một cột. Ẩn cột chỉ thay đổi cách xem, không phải giới hạn quyền đọc cột.

Các điều chỉnh này chưa tự thay đổi chế độ xem chung của mọi người.

## 5. Lưu và mở lại chế độ xem

1. Chọn bộ lọc, cách sắp xếp và cột cần hiển thị.
2. Mở **⋮ → Lưu thành chế độ xem mới** → đặt tên.
3. Chọn **Cá nhân**; chủ không gian còn có thể chọn **Chung cho không gian**.
4. Bấm **Lưu**. Sau này mở danh sách chế độ xem phía trên ô tìm kiếm để chọn lại.

Muốn cập nhật một chế độ xem đã chọn, dùng **⋮ → Lưu thay đổi chế độ xem** rồi xác nhận. Chỉ chủ không gian sửa chế độ xem chung; mỗi người sửa chế độ xem cá nhân của mình. Từ khóa tìm kiếm không được lưu trong chế độ xem này.

## 6. Nháp và mất mạng

- Chỉ những trang và kết quả đã tải có sẵn trên thiết bị. Kết quả chưa tải không chứng minh rằng bảng không có dữ liệu; kết nối lại rồi bấm **Làm mới**.
- Mở **⋮ → Bản nháp trên máy** ở màn hình bảng để tiếp tục một dòng mới chưa gửi. Mỗi dòng mới có nháp riêng.
- Chỉ xóa nháp thông thường sau khi đọc xác nhận: nháp chưa gửi sẽ không thể khôi phục. Với lần tạo chưa được xác nhận, ứng dụng giữ nháp và hướng dẫn thử gửi lại.
- Có mạng trở lại không tự gửi nháp. Kiểm tra nội dung rồi bấm **Lưu lên bảng** hoặc **Thử gửi lại**.
- Đăng xuất có cảnh báo về nháp; xác nhận đăng xuất xóa dữ liệu cục bộ của tài khoản. Sao chép nội dung cần giữ trước khi xác nhận.

## 7. Khi hai người cùng sửa

Nếu ứng dụng báo dòng hoặc cấu trúc bảng đã thay đổi, nháp vẫn được giữ. Bấm **Xem phiên bản mới**, đọc giá trị lúc bắt đầu, giá trị trên máy chủ và nháp của mình. Với mỗi ô bị xung đột, chọn **Giữ nháp ô này** hoặc **Dùng máy chủ**. Sau đó kiểm tra toàn bộ nội dung rồi bấm **Lưu lên bảng**.

Cột không còn khả dụng được giữ dưới dạng nháp chỉ đọc để sao chép. Không dùng tên cột giống nhau làm căn cứ cho rằng đó là cùng một cột.

## 8. Lịch sử và lưu trữ

Mở một dòng → **⋮ → Lịch sử thay đổi** để xem tối đa **50 thay đổi gần nhất** đã tải, gồm người thực hiện, thời điểm và các giá trị thay đổi.

**⋮ → Lưu trữ dòng** có xác nhận. Dòng biến mất khỏi bảng; dữ liệu không bị xóa vĩnh viễn. Bản P0 chưa có thao tác khôi phục dòng trên giao diện.

## 9. Bài thử cho nhóm

Dùng không gian có tên rõ là dữ liệu thử, hai thiết bị và tài khoản khác nhau:

1. Chủ tạo bảng công việc, mời một người chỉnh sửa và một người chỉ xem.
2. Người chỉnh sửa thêm việc, chọn người phụ trách và hạn; người chỉ xem xác nhận đọc được và không sửa được.
3. Tạo chế độ xem cá nhân **Của tôi** bằng điều kiện người phụ trách **là tôi**. Chủ tạo thêm chế độ xem chung theo trạng thái.
4. Hai thiết bị mở cùng một dòng; lưu lần lượt để kiểm tra thông báo xung đột và cách chọn từng ô.
5. Tắt mạng, tạo hai nháp, đóng/mở lại ứng dụng và kiểm tra cả hai vẫn tồn tại. Kết nối rồi gửi thủ công.
6. Kiểm tra lịch sử sau khi sửa; thử gỡ quyền người đang mở bảng và xác nhận lần kết nối tiếp theo bị chặn.
7. Thử chữ lớn, xoay màn hình và bàn phím. Ghi màn hình, thao tác, kết quả mong đợi/thực tế và cách lặp lại lỗi; không ghi mật khẩu hay token.

Giới hạn tạo dữ liệu hiện vẫn là **500 dòng mỗi bảng**. Bài thí nghiệm 5.000 dòng chỉ nằm trong dữ liệu kiểm thử cục bộ. Cần hoàn tất các bước trong [P0-VERIFICATION.md](P0-VERIFICATION.md) trước khi kết luận phù hợp cho công ty 100 người.
