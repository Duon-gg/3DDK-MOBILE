# 3DDK: bố cục dùng trên điện thoại

Ngày đối chiếu: 01/11/2026. Giữ Kotlin/XML, MVVM, Room, Retrofit, Firebase và Supabase; tiếp tục phạm vi P0 đã duyệt.

## Căn cứ

- [Airtable trên Google Play](https://play.google.com/store/apps/details?id=com.formagrid.airtable) mô tả bản ghi dưới dạng thẻ dễ chạm và nhiều cách xem cùng bảng. Listing hiển thị ngày cập nhật 22/10/2026.
- [Mobile interfaces](https://support.airtable.com/articles/2483653332-mobile-interfaces-in-airtable) mô tả mở từ Home vào trang toàn màn hình, tìm/lọc và cập nhật bản ghi.
- [Khác biệt desktop/mobile](https://support.airtable.com/articles/9609457174-airtable-desktop-and-mobile-feature-differences) cho thấy bộ tính năng di động khác bản web. Không dùng toàn bộ tính năng web làm cam kết cho lần chỉnh giao diện này.

Đã đọc tài liệu và listing; chưa đăng nhập, cài, dịch ngược hoặc quan sát trực tiếp runtime Airtable. Các quyết định bố cục bên dưới là thiết kế của 3DDK dựa trên mô tả chính thức, không phải bản sao pixel của Airtable. Giữ tên, màu xanh và tài nguyên riêng của 3DDK.

## Phần triển khai

1. Không gian → cơ sở dữ liệu → bảng: danh sách gọn, tìm kiếm rõ, tên và quyền dễ đọc; tạo xong tiếp tục tự mở đối tượng mới. Dùng layout riêng cho WorkspaceFragment để không đổi màn hình công việc cũ.
2. Bảng trên điện thoại mặc định là thẻ: tiêu đề và tối đa ba trường xem trước. Nút trên thanh tiêu đề chuyển giữa thẻ và bảng ngang hiện có; giữ lựa chọn khi tạo lại màn hình. Cùng dữ liệu, bộ lọc, sắp xếp, cột và phân trang; đây là cách trình bày cục bộ, không phải thêm loại Gallery/saved view ở server.
3. Tìm/lọc/sắp xếp/chọn cột vẫn tới được từ màn hình bảng. Giảm khoảng trống, giữ vùng chạm ít nhất 48dp và nội dung co giãn khi chữ lớn.
4. Chạm thẻ/hàng mở RecordEditor hiện có toàn màn hình; trường xếp dọc, nhãn rõ, nút lưu cố định bên dưới. Giữ nháp, trạng thái chưa gửi, gửi lại, xử lý xung đột và lịch sử. Chỉ xóa nháp sau xác nhận server như trước.
5. Bottom Navigation xuất hiện ở Không gian, Lời mời và Cá nhân. Màn hình con dùng Back/Up để có thêm diện tích cho dữ liệu và bàn phím. Luồng hết phiên vẫn xóa ngăn xếp riêng tư rồi mở Auth.

## Phạm vi dữ liệu và kiểm tra

Không đổi schema, migration, API, xác thực hoặc quyền. OWNER/EDITOR/VIEWER giữ nguyên; đổi cách hiển thị không cấp thêm quyền ghi. Null, checkbox false, ngày lỗi, thành viên bị gỡ và trường tùy chỉnh tiếp tục dùng cellLabel hiện có.

Kiểm tra cần chạy: build/unit/lint; thẻ hiển thị đúng kiểu ô và callback đúng bản ghi khi tái sử dụng; chuyển thẻ/bảng không đổi query hoặc tạo dữ liệu; tạo lại màn hình giữ lựa chọn; logout không quay lại Profile; luồng tạo → nhập → xoay → lưu trên workspace QA riêng nếu phiên thiết bị cho phép. Đánh giá hình thực tế, màn hình nhỏ/chữ lớn và APK release được ghi riêng theo bằng chứng chạy, không suy từ build.

Lần này chưa bổ sung Kanban, Calendar, attachments, comments, automation hoặc Interface Builder. Đây là các phần còn thiếu so với sản phẩm tham khảo, không phải chức năng đã có sau đổi giao diện.
