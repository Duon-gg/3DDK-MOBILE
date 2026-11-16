# Bộ chọn bảng và view theo video ngày 13/11/2026

Nguồn đối chiếu: video người dùng cung cấp `1794570983418_2067757490806799291_7518849171369305425.mp4`, dài khoảng 63 giây. Khung hình giây 58 cho thấy tab bảng nằm ngang, ô tìm view và danh sách view bên dưới. Các đoạn giây 8–24 cho thấy tiêu đề dòng ở trên các ô và ba biểu tượng ở thanh dưới. Không dùng tài nguyên thương hiệu Airtable.

## Thay đổi

Hai nút mở bảng/view của 3DDK dùng chung bộ chọn. Tab bảng cuộn ngang; chọn tab tải danh sách view của bảng đó, nhưng chưa chuyển màn lưới cho đến khi chọn một view. Ô tìm chỉ lọc tên view của bảng đang chọn. Hàng view có biểu tượng lưới và nền đánh dấu mục hiện tại.

Chọn view thuộc bảng khác truyền cả đường dẫn bảng và ID view vào ViewModel. ViewModel tải metadata để áp dụng filter, sort và thứ tự cột đã lưu. View đã bị xóa được xử lý bằng cơ chế tải lại hiện có. Đóng bộ chọn hủy yêu cầu metadata đang chạy; phản hồi của tab cũ không được thay nội dung tab mới. Lỗi tải có thông báo trong bộ chọn. Khi lỗi kết nối, dùng metadata cache nếu có; không dùng cache để vượt lỗi quyền truy cập.

Nút lưu view mới chỉ xuất hiện cho bảng hiện tại khi có quyền chỉnh sửa. Không tạo nút cộng tác, đính kèm hoặc Ask Omni trong lượt này.

Lưới giữ tiêu đề dòng phía trên ô; khoảng cách giữa dòng giảm từ 10 xuống 6 dp, góc nền từ 10 xuống 3 dp, header dùng chữ 12sp và ô tối thiểu 40dp. Thanh dưới có ba biểu tượng bảng, thêm dòng, tìm kiếm, mỗi vùng chạm cao 48dp và có mô tả trợ năng. Cơ chế nháp/lưu vẫn giữ nguyên.

## Kiểm thử cần phân biệt

Unit test kiểm tra tìm tên có khoảng trắng, không phân biệt hoa thường, hai view trùng tên vẫn giữ đúng chỉ số gốc; kiểm tra query khi mở view ở bảng khác nhận schemaVersion và thứ tự cột đúng. Kiểm thử Android cũ được đổi từ nhãn chữ tìm kiếm sang trạng thái selected của nút biểu tượng.

Kiểm tra trên Xiaomi cần thực hiện: mở bộ chọn, chuyển qua lại hai tab bảng, tìm và chọn view, đóng/mở lại, vào dòng rồi quay về đúng bảng/view. Chỉ sau khi chạy thực tế mới ghi đạt. Bản build không chứng minh giao diện hoặc luồng lưu hoạt động trên điện thoại.

Các khác biệt còn lại với video: chưa bổ sung thumbnail đính kèm, avatar trong ô thành viên hoặc nhãn lựa chọn có màu; chưa xác nhận bố cục trùng từng điểm ảnh. Tên view mặc định là “Tất cả dòng”, tương ứng truy vấn chưa có view đã lưu của 3DDK.

## Bản bàn giao kiểm thử

Build cuối `testDebugUnitTest assembleRelease compileDebugAndroidTestKotlin` thành công, 31 unit test, 0 failure/error; lint vital release qua. Kiểm thử Android mới chỉ biên dịch. Đã cài đè lên Xiaomi bằng ADB, kết quả Success; không gỡ dữ liệu. APK `artifacts/recent-bases/3ddk-table-picker-release.apk`, SHA-256 `a0774207e3c82a6b2b13e76bfa1f97912f03d99d7a5f004ae5e731cf051c14c4`. Đang chờ người dùng chạm mở bộ chọn trên Xiaomi vì máy chặn INJECT_EVENTS.
