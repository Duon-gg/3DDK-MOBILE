# Milestone 2 — MVVM và View Binding

## Cấu trúc mã Android

Trong `app/src/main/java/com/threeddk/tasks/`:

| Package | Trách nhiệm |
|---|---|
| `ui` | Fragment, hiển thị trạng thái, hộp thoại, bàn phím và điều hướng |
| `viewmodel` | Trạng thái màn hình, lọc dữ liệu, kiểm tra nội dung, xử lý thao tác và gọi repository |
| `model` | Task, Member, Invite, History, Row và hàm đọc giá trị JSON |
| `repository` | TaskRepository, WorkspaceRepository: truy cập API, phối hợp cache và đồng bộ |
| `adapter` | RowAdapter, GridAdapter, RecordCardAdapter, WelcomeAdapter; bind hàng bằng View Binding |
| `data` | Room, vận chuyển API, tích hợp Firebase và các hàm xử lý dữ liệu dùng chung |

`MainActivity` vẫn ở package gốc để giữ nguyên tên component Android. Không đổi applicationId, schema Room, hợp đồng API hoặc database trên Supabase.

## Phần đã tái cấu trúc

- Di chuyển bốn ViewModel, hai repository và các model vào package tương ứng; cập nhật nơi sử dụng và kiểm thử.
- Tách adapter khỏi Fragment; hàng lưới dùng `ItemGridRecordBinding`. `viewBinding = true` tiếp tục được bật, mã ứng dụng không dùng `findViewById`.
- ViewModel nhận thao tác hồ sơ, đăng xuất, nộp kết quả, lời mời, tạo/sửa dữ liệu và quản lý thành viên. Fragment không truy cập repository hay DAO trực tiếp.
- Bộ lọc, sắp xếp, chọn cột, tạo cột, lọc không gian kiểm thử và chọn thành viên được xử lý trong ViewModel. View giữ phần định dạng, hiển thị và điều hướng.
- `TaskEditorViewModel` giữ biểu mẫu bằng StateFlow và SavedStateHandle. Nhận lại dữ liệu ban đầu khi tạo lại View không ghi đè bản nháp; mã yêu cầu và phiên bản được giữ để tránh gửi lặp hoặc ghi đè ngoài ý muốn.
- Nội dung ba trang giới thiệu được cung cấp từ AuthEntryViewModel.
- Bổ sung quy tắc giữ tên model khi đóng gói release, tránh lỗi Gson sau khi chuyển package.

## Kiểm tra

Lệnh xác minh trên JDK 21:

```text
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --max-workers=2
```

Các kiểm thử bổ sung:

- `MvvmBoundaryTest`: bảo vệ ranh giới UI/repository và yêu cầu View Binding.
- `TaskEditorViewModelTest`: giữ nháp, người đại diện, phiên bản và mã gửi lại; phục hồi trạng thái từ SavedStateHandle.
- `TableQueryRulesTest`: toán tử lọc phù hợp kiểu cột.
- `Milestone2UiTest`: tạo lại Activity và kiểm tra hàng lưới dùng binding mở đúng dòng. Bài này dùng dữ liệu cục bộ trong kiểm thử, không xác nhận việc lưu lên máy chủ.

Kết quả ngày 16/11/2026:

- 46/46 kiểm thử đơn vị đạt, không bỏ qua bài nào.
- 17/17 kiểm thử Android đạt trên máy ảo `3ddk-api35` chạy ngầm: Room, hiển thị ô, adapter danh sách/thẻ/lưới, bố cục chữ lớn và tạo lại Activity.
- Build debug, APK kiểm thử và release thành công. Lint debug có 0 lỗi và 137 cảnh báo; lint vital release đạt.
- Sửa cách dựng dữ liệu/màn hình trong kiểm thử cũ: RecyclerView cần LayoutManager; khung cuộn tab phải được bật trước khi đo; tab ngoài màn hình được cuộn tới khi kiểm tra; nhấn giữ không gian là chức năng hợp lệ; mục không có menu được kiểm tra bằng trạng thái `isLongClickable`.
- Chưa kiểm thử lại đăng nhập, ghi dữ liệu cloud hay thao tác trên Xiaomi trong đợt này. ADB không thấy Xiaomi kết nối khi kiểm tra. Không thay đổi database production.
- Log cục bộ: `artifacts/m2-final-build.log`, `artifacts/milestone-2/android-tests.log`, `artifacts/milestone-2/android-tests.json` (không đưa vào Git).

## Hồ sơ nộp còn cần nhóm thực hiện

- Mỗi thành viên phải thực hiện, hiểu và commit/push phần phụ trách. Tên tác giả Git không thay thế bằng chứng đóng góp thực tế.
- Phụ lục 2 chờ mẫu và khai báo thực tế của nhóm; chưa xác nhận tỷ lệ sử dụng AI.
- Khi chuẩn bị ZIP cuối cùng: chạy `gradlew.bat clean`, chỉ đóng gói mã nguồn và file build cần thiết. Không đưa `.git`, `.gradle`, build output, SDK, keystore, `keystore.properties`, `local.properties`, khóa dịch vụ, log hoặc dữ liệu cá nhân vào bài nộp. Dùng `local.properties.example` để hướng dẫn cấu hình.
- Link nộp: https://github.com/Duon-gg/3DDK-MOBILE . Thay đổi local cần được push trước khi giảng viên có thể xem trên GitHub.
