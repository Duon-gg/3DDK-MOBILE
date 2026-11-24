# Airtable mobile vs 3DDK Tasks: so sánh tính năng trong bảng

Nguồn: thao tác app Airtable Android qua adb, đối chiếu code app và backend (`supabase/migrations`). Chỉ ghi hành vi nhìn thấy được, không decompile. Cập nhật 2026-11-25.

✅ có và đã chạy trên điện thoại thật · ❌ chưa có

## Điều hướng và chế độ xem

| Tính năng Airtable | App | Ghi chú |
|---|---|---|
| Danh sách bảng, chuyển bảng | ✅ | |
| Tìm trong bảng | ✅ | |
| Lọc, sắp xếp, ẩn cột | ✅ | |
| Lưu chế độ xem có tên, tìm chế độ xem | ✅ | |
| Dạng lưới / dạng thẻ | ✅ | |
| Nhóm theo một cột | ✅ | Chỉ nhớ trên máy, chưa lưu vào chế độ xem. |
| Kanban theo cột lựa chọn | ✅ | Chưa kéo thẻ sang cột khác. Chỉ nhớ trên máy. |
| Calendar | ❌ | Để sau. |
| Gallery, Form, List, chỉnh chiều cao dòng | ❌ | Không làm. |

## Cột (field)

| Tính năng Airtable | App | Ghi chú |
|---|---|---|
| Thêm cột | ✅ | |
| Sửa tên, đổi kiểu, sửa lựa chọn | ✅ | Lựa chọn sai với dữ liệu đang có thì cả thao tác bị huỷ. |
| Nhân bản cột, xoá cột | ✅ | Nhân bản chỉ chép cấu hình, không chép dữ liệu. |
| Kéo đổi thứ tự cột | ✅ | Lưu theo chế độ xem (`fieldOrder`). |
| Long text, URL, Multiple select, Email, Phone | ✅ | URL/Email/Phone có nút mở trình duyệt, mail, trình gọi. |
| Attachment | ❌ | Để sau, cần Supabase Storage. |
| Link bảng, Lookup, Rollup, Formula, Rating, Autonumber | ❌ | Không làm lúc này. |

## Dòng (record)

| Tính năng Airtable | App | Ghi chú |
|---|---|---|
| Thêm, sửa, xoá, nhân bản dòng | ✅ | |
| Trước/Sau "1 / N", lịch sử thay đổi | ✅ | |
| Bình luận, @nhắc tên | ✅ | Người được nhắc chưa nhận thông báo; chưa sửa/xoá bình luận. |
| Chia sẻ link dòng, in dòng | ❌ | Không làm. |

## Khác

| Tính năng Airtable | App | Ghi chú |
|---|---|---|
| Dòng tổng cuối bảng | ✅ | Chỉ tính trên các dòng đã tải. |
| Mời cộng tác viên, nhân bản base | ✅ | |
| Nhập CSV | ✅ | |
| Configure base, Add to homescreen, Omni/AI | ❌ | Không làm. |

## Bảng MY TASK

Base **CÔNG VIỆC HẰNG NGÀY** → bảng **MY TASK**, 9 dòng nhập qua CSV. Cấu trúc giống Airtable: CHI TIẾT và DEADLINE là Long text, TRẠNG THÁI có 6 lựa chọn (⚪ Chưa làm, 🔵 Đang làm, 🟡 Chờ phản hồi, 🟠 Tạm dừng, 🟢 Hoàn thành, 🔴 Đã hủy), HOÀN THÀNH là checkbox.
