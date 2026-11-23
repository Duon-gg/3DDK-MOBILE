# Airtable mobile vs 3DDK Tasks: so sánh tính năng

Nguồn: thao tác app Airtable Android qua adb (2026-11-24), đối chiếu code app và route backend (`supabase/migrations`). Chỉ ghi hành vi nhìn thấy được, không decompile.

## Đã có trong 3DDK

| Tính năng | Ở đâu |
|---|---|
| Recents / Starred / Workspaces | `data/RecentBases.kt`, `RowAdapter.kt` |
| Tạo workspace, base, bảng (trống hoặc mẫu công việc) | `WorkspaceFragment`, `WorkspaceViewModel` |
| Kiểu field: Text, Number, Date, Checkbox, Single select, User | `WorkspaceViewModel.fieldTypes()` |
| Thêm cột | `DataTableFragment.addField()` |
| Tìm, lọc, sắp xếp; lưu chế độ xem có tên | `TableQueryRules.kt`, route `views` |
| Xem dạng lưới và dạng thẻ | `DataTableFragment` |
| Tạo, sửa, xoá bản ghi; lịch sử bản ghi | `RecordViewModel`, route `records/.../history` |
| Mời thành viên, link mời base, chuyển quyền sở hữu | route `invites`, `base-invitations`, `transfer-ownership` |
| Duplicate, di chuyển, thùng rác base/workspace | route `duplicate`, `move`, `restore` |
| Offline (Room), thông báo đẩy FCM | `TaskDatabase.kt`, `TaskMessagingService.kt` |
| **Nhập CSV** (mới) | `data/CsvImport.kt` |

## Airtable có, 3DDK chưa có

| Ưu tiên | Tính năng Airtable | Cần backend mới? |
|---|---|---|
| P0 | Sửa field: đổi tên, đổi kiểu, sửa/thêm lựa chọn (header → Customize field) | Có: `PATCH fields` |
| P0 | Ẩn field, xoá field, chèn trái/phải, duplicate field | Có: `PATCH/DELETE fields` |
| P0 | Duplicate bản ghi (menu bản ghi) | Không (tạo bản ghi mới từ cells) |
| P0 | Previous / Next "1 / 18" trong màn bản ghi | Không |
| P1 | Kiểu field Long text, rich text | Có: thêm kiểu `LONG_TEXT` |
| P1 | View Kanban theo field Single select | Không (chỉ giao diện) |
| P1 | Comment trên bản ghi, @mention, đính kèm file trong comment | Có |
| P2 | View Calendar (theo Date), Gallery | Không |
| P2 | Field Attachment (chụp ảnh / chọn file) | Có + lưu trữ file |
| P2 | Inbox thông báo: All / Unread, tìm kiếm, Mark all as read | Một phần (`notifications` PATCH đã có) |
| P2 | Summary dòng cuối cột (đếm, tổng) | Không |
| Bỏ | Omni / Run agent (AI), Add to homescreen, Configure base | |

## Bảng MY TASK đã chép sang 3DDK

Base **CÔNG VIỆC HẰNG NGÀY** → bảng **MY TASK** (workspace Team mobile), nhập 9 dòng qua CSV. 9 dòng trống của Airtable bị bỏ.

Khác biệt còn lại, đều do thiếu "Sửa field" (P0):
- CHI TIẾT là Text thay vì Long text.
- DEADLINE thành Single select (Chưa được giao, Ngày mai) thay vì Long text.
- TRẠNG THÁI chỉ có 3 lựa chọn đang dùng; thiếu 🟡 Chờ phản hồi, 🟠 Tạm dừng, 🔴 Đã hủy.
