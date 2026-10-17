# Bản dùng thử bảng tùy biến

Người dùng yêu cầu tiếp tục triển khai trực tiếp, không dừng ở phân công hoặc vòng duyệt tài liệu.

Thứ tự thực hiện: kiểm thử SQL thất bại trước → schema/API v2 → Room/repository → màn hình không gian, cơ sở dữ liệu, bảng và trình sửa bản ghi → kiểm tra build/API/quyền → deploy bổ sung → xuất APK.

Phạm vi bản dùng thử: tự tạo không gian; mời qua email; tạo cơ sở dữ liệu và bảng; cột văn bản, số, ngày, checkbox, lựa chọn; thêm/sửa bản ghi; tìm kiếm; cache và nháp local. Bản này chưa tương đương toàn bộ Teable. Công thức, liên kết bảng, các view nâng cao, AI và tự động hóa còn ở kế hoạch thiết kế.

API v1 và dữ liệu cũ được giữ để APK cũ tiếp tục hoạt động. Không chuyển quyền toàn cục sang owner tự động. Quyền mới luôn theo không gian. Triển khai cloud chỉ sau kiểm thử database và Edge đạt.
