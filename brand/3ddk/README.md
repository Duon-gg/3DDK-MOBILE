# Bộ nhận diện 3DDK

Phiên bản 1.0 · 12/11/2026

3DDK là ứng dụng bảng dữ liệu và cộng tác nhóm. Logo ghép ba ô dữ liệu với một cạnh cong thành số 3. Giữ tên viết hoa **3DDK** trong giao diện và tài liệu.

## Tài sản

| Thư mục / tệp | Nội dung |
|---|---|
| `logo/` | Biểu tượng, chữ, logo ngang và logo dọc. Mỗi loại có bản xanh, trắng, đen; SVG với chữ chuyển thành đường nét. |
| `png/` | Logo PNG nền trong, app icon 1024 px và store icon 512 px. |
| `icons/` | App icon, store icon nền vuông và favicon SVG. |
| `icons/ui/` | 10 icon SVG xuất từ vector đang dùng trong app. |
| `android/` | Vector drawable, adaptive foreground, monochrome và biểu tượng thông báo. |
| `tokens.json` | Màu, font và kích thước vùng chạm. |
| `preview.html` | Trang xem tổng thể, mở trực tiếp trong trình duyệt. |
| `brand-board.png` | Bảng nhận diện để chia sẻ. |

## Màu và chữ

- Xanh chính `#2563EB`: icon app, nút chính, trạng thái được chọn.
- Navy `#173B88`: chữ logo và điểm nhấn trên nền sáng.
- Xanh nhạt `#EAF1FF`: nền vùng nhấn; dùng chữ navy.
- Chữ `#202124`, chữ phụ `#555960`, viền `#E4E7EC`, nền trắng `#FFFFFF`.
- Dùng font hệ thống Android (`sans-serif`, thường là Roboto). Web dùng `Roboto, Arial, sans-serif`. Không đóng gói font bên ngoài.
- Chữ 3DDK trong logo là đường vector riêng. Không gõ lại logo bằng font giao diện.

## Sử dụng

Chừa khoảng trống tối thiểu bằng ¼ chiều cao phần biểu tượng nhìn thấy. Logo ngang nên rộng từ 120 px. Dùng biểu tượng độc lập cho avatar và các vùng nhỏ; favicon có bố cục riêng để giữ dấu hiệu ở 16 px.

Dùng logo xanh/navy trên trắng hoặc xanh nhạt. Dùng logo trắng trên navy. Bản đen dành cho in đơn sắc. Không kéo giãn, thêm bóng, đổi màu từng ô hoặc xoay biểu tượng. Đặt biểu tượng trên nền yên, tránh ảnh nhiều chi tiết.

Icon chức năng giữ phong cách tô kín của app, trên lưới 24 px. Vùng chạm tối thiểu 48 dp; nút chỉ có icon cần nhãn truy cập tiếng Việt trong ứng dụng. Dùng chữ hoặc hình dạng để bổ sung ý nghĩa trạng thái, không chỉ dựa vào màu.

## Android

Các bản trong `android/` đã được áp dụng vào `app/src/main/res/`:

- `ic_app`: icon cũ và ảnh đại diện dự phòng.
- `ic_brand_foreground`: hình trắng nằm trong vùng an toàn adaptive icon.
- `ic_brand_monochrome`: cùng hình cho launcher hỗ trợ themed icon.
- `ic_notification`: biểu tượng trắng, nền trong cho thông báo.
- `drawable-v26/ic_launcher`: nền xanh và các lớp adaptive.

Giữ nguyên manifest và alias launcher hiện có. Store icon là PNG vuông, không bo góc sẵn. Hệ thống cửa hàng/launcher áp dụng mặt nạ khi hiển thị.

## Tái tạo và nguồn

Chạy `python brand/3ddk/build.py` từ thư mục dự án để dựng lại SVG, xuất 10 icon UI và cập nhật các vector Android. Script có kiểm tra XML và vùng an toàn của adaptive icon. Script không xuất PNG; PNG là bản render từ SVG bằng trình duyệt.

Phác thảo dùng công cụ Imagegen tích hợp; prompt và ảnh được lưu trong `.skill-archive/logo-creator/2026-11-12-3ddk/`. Bộ vector cuối dựng riêng từ hướng giữa của phác thảo, chuẩn hóa màu và đường nét. Quy trình tham khảo: https://mcpmarket.com/tools/skills/logo-creator-1 .

Chưa thực hiện tra cứu đăng ký nhãn hiệu. Trước khi phát hành thương mại, nhóm cần kiểm tra tên và biểu tượng theo thị trường sử dụng.
