# Trang chủ theo ảnh tham khảo 13/11/2026

Nguồn: bốn ảnh Airtable người dùng gửi, gồm Gần đây, Đã đánh dấu, Không gian và menu của một cơ sở dữ liệu. Người dùng chọn sửa trang chủ trước, tạm hoãn kiểm thử bộ chọn bảng/view vừa cài. Giữ thương hiệu 3DDK, tiếng Việt và bỏ Ask Omni.

## Phần đã sửa trong mã

Tab có độ rộng theo nhãn, chữ đậm và gạch chân ở mục chọn. Hàng tab cuộn ngang khi cỡ chữ lớn. Hàng cơ sở dữ liệu có biểu tượng màu, tên, thời điểm mở và sao vàng nếu đã đánh dấu. Tab Đã đánh dấu dùng dòng phụ “Cơ sở dữ liệu”. Avatar Google và các thao tác tài khoản giữ nguyên.

Nhấn giữ một cơ sở dữ liệu ở Gần đây hoặc Đã đánh dấu mở bảng menu từ dưới lên: Mở dữ liệu, Đánh dấu/Bỏ đánh dấu, Đi tới không gian làm việc. Mục đi tới không gian chỉ có khi metadata đã lưu có workspaceId. Đánh dấu dùng cơ chế cục bộ theo tài khoản đã có; không thêm API hoặc migration. Chọn mở dữ liệu đi đến base, backend tiếp tục kiểm tra quyền khi tải.

Tab Không gian hiển thị mục Được chia sẻ và các không gian người dùng sở hữu. Được chia sẻ liệt kê các không gian có vai trò EDITOR hoặc VIEWER. Tìm kiếm theo tên trả về cả không gian sở hữu và tham gia. Biểu tượng nhóm dùng nền xám; không hiển thị gói Free vì 3DDK chưa có thông tin gói dịch vụ.

## Giới hạn có chủ đích

Backend chưa có API di chuyển, nhân bản, xóa hoặc chia sẻ riêng base. Không thêm các nút đó vào menu. Mời người khác vẫn qua quản lý thành viên của workspace, giữ đúng phạm vi quyền. Không giả thông báo chưa đọc bằng chấm xanh. Sao vẫn là tùy chọn trên thiết bị, chưa đồng bộ giữa các máy.

## Kiểm tra

Thêm unit test kiểm tra workspaceId lấy từ cache đúng tài khoản, dữ liệu cũ không có workspaceId và không lẫn hai chủ cache cùng ID. Thêm kiểm thử Android cho sao và callback nhấn giữ khi RecyclerView tái sử dụng hàng. Kiểm thử Android phải được ghi riêng với unit test; biên dịch không đồng nghĩa đã chạy.

Trên Xiaomi cần xác nhận: mở menu bằng nhấn giữ, đánh dấu và bỏ đánh dấu, mục xuất hiện đúng một lần ở Đã đánh dấu, mở dữ liệu, đi tới workspace và tìm kiếm. Máy chặn INJECT_EVENTS nên người dùng phải chạm, trợ lý đọc lại màn hình. Chưa coi các bước này đạt nếu chưa có bằng chứng.

## Kết quả build

33 unit test đạt, 0 failure/error. Mã kiểm thử Android biên dịch qua; chưa thực thi bộ kiểm thử Android. Build release và lint vital release qua. Lần build trung gian lỗi `Unresolved reference homeTabScroll` do binding chưa cập nhật theo layout đang sửa; chạy lại trên nguồn đã chốt thành công.

Đã cài đè bản cuối lên Xiaomi, ADB báo Success, không gỡ app. Ảnh chụp điện thoại xác nhận Gần đây hiển thị avatar và các cơ sở dữ liệu thật, không có Ask Omni. Sau kiểm tra ảnh đã chỉnh chuông/tìm kiếm sang nét viền, build và cài lại thành công. Đang chờ người dùng mở menu để kiểm tra thao tác.

APK: `artifacts/recent-bases/3ddk-home-reference-release.apk`. SHA-256: `b137912244e4db0bb76f5525aee542690037fb2dd318f23e6560de5de306a472`.

## Xác nhận trên Xiaomi

Sau thao tác của người dùng, UI dump `artifacts/recent-bases/home-star-verify2.xml` xác nhận `starred_tab` checked=true, có đúng một hàng tên “Dự án kiểm thử”, dòng phụ “Cơ sở dữ liệu” và ImageView `star` xuất hiện. Đạt bước hiển thị mục đã đánh dấu, không trùng trong danh sách hiện tại. Chưa kiểm thử bỏ đánh dấu, giữ trạng thái sau khởi động lại hoặc đồng bộ sang thiết bị khác. Menu mở được là xác nhận từ người dùng, chưa có ảnh/dump menu để đối chiếu bố cục.
