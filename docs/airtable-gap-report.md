# Airtable mobile vs 3DDK Tasks: so sánh tính năng trong bảng

Nguồn: thao tác app Airtable Android qua adb, đối chiếu code app và backend (`supabase/migrations`). Chỉ ghi hành vi nhìn thấy được, không decompile. Cập nhật 2026-11-25 (tối).

✅ có và đã chạy trên điện thoại thật · ❌ chưa có

## Điều hướng và chế độ xem

| Tính năng Airtable | App | Ghi chú |
|---|---|---|
| Danh sách bảng, chuyển bảng | ✅ | |
| Tìm trong bảng | ✅ | |
| Lọc, sắp xếp, ẩn cột | ✅ | |
| Lưu chế độ xem có tên, tìm chế độ xem | ✅ | |
| Dạng lưới / dạng thẻ | ✅ | Kiểu hiển thị lưu theo chế độ xem; "Tất cả dòng" (chưa chọn chế độ xem) chỉ nhớ trên máy. |
| Nhóm theo một cột | ✅ | Lưu vào chế độ xem cùng kiểu hiển thị. |
| Kanban theo cột lựa chọn, kéo thẻ sang cột khác | ✅ | Giữ thẻ rồi kéo; TalkBack có thao tác "Chuyển sang cột". Lưu vào chế độ xem. |
| Calendar | ❌ | Để sau. |
| Gallery, Form, List, chỉnh chiều cao dòng | ❌ | Không làm. |

## Cột (field)

| Tính năng Airtable | App | Ghi chú |
|---|---|---|
| Khôi phục cột đã xoá | ❌ | Xoá cột là xoá hẳn dữ liệu (có cảnh báo trước). |
| Thêm cột, chèn bên trái / bên phải | ✅ | Menu cột giống Airtable: Sửa, Nhân bản, Chèn trái, Chèn phải, Ẩn, Xoá. |
| Sửa tên, đổi kiểu, sửa lựa chọn | ✅ | Lựa chọn sai với dữ liệu đang có thì cả thao tác bị huỷ. |
| Nhân bản cột, xoá cột | ✅ | Nhân bản chỉ chép cấu hình, không chép dữ liệu. |
| Kéo đổi thứ tự cột | ✅ | Lưu theo chế độ xem (`fieldOrder`). |
| Long text, URL, Multiple select, Email, Phone | ✅ | URL/Email/Phone có nút mở trình duyệt, mail, trình gọi. |
| Attachment | ❌ | Để sau, cần Supabase Storage. |
| Link bảng, Lookup, Rollup, Formula, Rating, Autonumber | ❌ | Không làm lúc này. |

## Dòng (record)

| Tính năng Airtable | App | Ghi chú |
|---|---|---|
| Thêm, sửa, xoá, nhân bản dòng | ✅ | Xoá dòng vào thùng rác. |
| Chọn nhiều dòng rồi xoá | ✅ | Giữ một dòng ở dạng lưới hoặc dạng thẻ để chọn; Kanban dùng giữ để kéo thẻ. |
| Thùng rác dòng, khôi phục | ✅ | Menu ⋮ của bảng → "Thùng rác dòng", 100 dòng xoá gần nhất. |
| Trước/Sau "1 / N", lịch sử thay đổi | ✅ | Chuyển dòng tự lưu lên server. |
| Bình luận, @nhắc tên | ✅ | Người được nhắc nhận thông báo đẩy và có mục trong hộp Thông báo. |
| Hộp thông báo (chuông), chấm đỏ chưa đọc | ✅ | Gồm lời mời đang chờ và các lần được nhắc tên; chạm để mở đúng dòng. |
| Sửa, xoá bình luận | ✅ | Chỉ người viết sửa; người viết hoặc chủ base xoá. Bình luận đã sửa hiện "(đã sửa)". |
| Chia sẻ link dòng, in dòng | ❌ | Không làm. |

## Khác

| Tính năng Airtable | App | Ghi chú |
|---|---|---|
| Dòng tổng cuối bảng | ✅ | Hiện "50 / 300 dòng"; tổng, % và số đã điền chỉ tính trên các dòng đã tải. |
| Tự tải thêm dòng khi cuộn | ✅ | Không cần bấm "Tải thêm". |
| Số trong nhóm tính trên toàn bảng | ✅ | Kanban tự tải hết dòng nên số trong cột cũng đúng. |
| Màn "Cộng tác viên": mời qua email, đổi quyền, gỡ người, huỷ lời mời | ✅ | Có gửi email mời thật qua Resend (chưa xác minh tên miền thì chỉ gửi tới email chủ tài khoản Resend). Lời mời đang chờ hiện trên màn chính và có số trên chuông. |
| Quyền: Chủ, Quản trị, Chỉnh sửa, Chỉ xem | ✅ | Quản trị ngang chủ, ở cả cấp base và cấp không gian; không đổi được quyền chủ thật. Chưa có "Chỉ bình luận". |
| Link mời vào base: tạo, chia sẻ, tạo lại, tắt, tham gia bằng link | ✅ | Link https (qua Supabase), bấm trong Zalo/trình duyệt là mở app. Chưa có trang hướng dẫn khi máy chưa cài app (cần tên miền riêng). Tham gia không làm giảm quyền đang có. |
| Mời vào không gian làm việc (workspace) | ✅ | Có "Đặt làm Quản trị". Vẫn dùng giao diện cũ, chưa giống màn "Cộng tác viên". |
| Nhân bản base | ✅ | |
| Nhập CSV | ✅ | |
| Configure base, Add to homescreen, Omni/AI | ❌ | Không làm. |

## Bảng MY TASK

Base **CÔNG VIỆC HẰNG NGÀY** → bảng **MY TASK**, 9 dòng nhập qua CSV; dòng trống tạo lúc 02:30 ngày 25/11 đã được người dùng điền nội dung. Cấu trúc giống Airtable: CHI TIẾT và DEADLINE là Long text, TRẠNG THÁI có 6 lựa chọn (⚪ Chưa làm, 🔵 Đang làm, 🟡 Chờ phản hồi, 🟠 Tạm dừng, 🟢 Hoàn thành, 🔴 Đã hủy), HOÀN THÀNH là checkbox.
