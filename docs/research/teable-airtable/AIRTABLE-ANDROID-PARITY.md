# Đối chiếu Airtable Android và 3DDK

Nguồn quan sát trực tiếp là Airtable Android trên điện thoại kết nối qua USB. Màn hình chính có Gần đây, Đã đánh dấu và Không gian làm việc. Màn hình bảng có tên bảng và chế độ xem ở trên, các cột dữ liệu ở giữa, nút chọn bảng, thêm dòng và tìm kiếm ở dưới. Chi tiết dòng mở toàn màn hình, có tên dòng, các trường theo chiều dọc và điều hướng dòng ở chân trang. Dữ liệu hiển thị trên điện thoại không dùng làm dữ liệu mẫu của 3DDK.

Kiểm tra tĩnh bằng `adb shell pm path com.formagrid.airtable` và `aapt2 dump badging` trên bản APK lấy từ thiết bị xác định package `com.formagrid.airtable`, phiên bản 7.11.0 (386), target SDK 36 và màn hình khởi chạy `SessionLoaderActivity`. Các kết quả này chỉ mô tả APK Android. Chúng không xác minh cách Airtable triển khai máy chủ.

Danh sách tính năng công bố gồm nhiều chế độ xem, biểu mẫu, đính kèm, liên kết bản ghi, bình luận cộng tác và Omni. Một số tính năng mô tả giao diện được tạo từ Airtable Interfaces. [Airtable trên Google Play](https://play.google.com/store/apps/details?id=com.formagrid.airtable)

Phạm vi Android cần được đối chiếu riêng với web. Airtable hỗ trợ Kanban, lịch và gallery trong base trên Android, còn quản lý automation và nhập/xuất CSV thuộc bản web. Form trong Interfaces là một luồng khác với form của base. [So sánh Android và desktop](https://support.airtable.com/articles/9609457174-airtable-desktop-and-mobile-feature-differences) · [Interfaces trên thiết bị di động](https://support.airtable.com/articles/2483653332-mobile-interfaces-in-airtable)

| Phần | 3DDK hiện có | Điều kiện để đạt tương đương |
|---|---|---|
| Trang chủ | Gần đây và Đã đánh dấu mở lại bảng, lưu trên thiết bị theo tài khoản. Không gian, lời mời và cá nhân vẫn truy cập được | Tìm bảng trong toàn bộ không gian có quyền, đồng bộ dấu sao giữa thiết bị |
| Bảng | Thẻ, lưới, chế độ xem đã lưu, lọc, sắp xếp, tìm và phân trang. Thanh dưới có bộ chọn bảng trong cùng cơ sở dữ liệu, thêm dòng và tìm | Bố cục lưới như bản tham chiếu, giữ vị trí khi quay lại |
| Chi tiết dòng | Tên dòng, biểu mẫu dọc, lịch sử, bản nháp và lưu có xác nhận. Nút trước/sau theo thứ tự các dòng đã tải, ghi bản nháp trước khi chuyển | Sửa ô trực tiếp, cập nhật nền và chuyển tiếp qua các trang chưa tải |
| Dữ liệu và chế độ xem | Kiểu trường cơ bản, quyền theo không gian, kiểm tra phiên bản | Kanban, lịch, thư viện ảnh và form dùng chung dữ liệu. Thêm tệp, liên kết, lookup, rollup và công thức với kiểm tra quyền |
| Cộng tác | Lời mời, vai trò và lịch sử chỉnh sửa | Hai thiết bị nhận cập nhật, bình luận, nhắc tên và thông báo đúng người. Quyền bị thu hồi có hiệu lực với dữ liệu đã lưu và phiên đang mở |
| Mở rộng | API 3DDK, cấu hình bảng và mẫu công việc | Nhập/xuất, webhook, tự động hóa, dashboard và trình dựng giao diện. Omni cần đặc tả dữ liệu, quyền truy cập và cách đánh giá kết quả riêng |

Thứ tự thực hiện là trang chủ và các thao tác chính của bảng, sau đó các chế độ xem và loại trường, rồi cộng tác trực tiếp và tích hợp. Mỗi phần cần chạy trên dữ liệu thử với ít nhất hai tài khoản trước khi xác nhận tương đương. Mục tiêu 100 người và khoảng 20 người cùng thao tác cần đo bằng kiểm thử tải riêng.
