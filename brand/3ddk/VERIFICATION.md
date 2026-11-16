# Kiểm tra bộ nhận diện · 12/11/2026

- `python brand/3ddk/build.py`: đạt. SVG và XML parse được; 10 icon UI đã xuất; hình adaptive nằm trong vòng tròn an toàn bán kính 33/108.
- `:app:assembleDebug :app:lintDebug`: BUILD SUCCESSFUL. Lint báo 0 lỗi và 113 cảnh báo trong dự án; không có cảnh báo tại các file icon vừa cập nhật. Bản dựng đã xử lý các tài nguyên mới.
- Trang preview: 19 ảnh tải được; không tràn ngang ở viewport 1300 px và 390 px. Đã xem bảng nhận diện sau khi xuất.
- 20 PNG đã kiểm tra header và kích thước. Logo PNG dùng định dạng RGBA; favicon ICO chứa PNG 32 × 32.
- Giữ nguyên các màu giao diện đang có. Các thay đổi Android nằm ở icon app, adaptive foreground/monochrome, nền adaptive và biểu tượng thông báo.

Chưa cài bản mới lên thiết bị thật, chưa kiểm tra mặt nạ của từng launcher hoặc thông báo thực tế. Chưa tra cứu nhãn hiệu. Không chạy lại kiểm thử backend vì không thay đổi logic hay dữ liệu.
