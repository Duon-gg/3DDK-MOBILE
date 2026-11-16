# Đối chiếu Airtable Android với 3DDK, 13/11/2026

Phạm vi đã chốt với người dùng: hoàn thiện luồng bảng trước, sau đó mới thêm từng phần. Giữ Kotlin/XML, Firebase, Supabase và thương hiệu 3DDK; không thêm Ask Omni.

Đây là kiểm thử hộp đen qua giao diện app đã đăng nhập, không phải chứng nhận toàn bộ Airtable. Không đọc mã độc quyền hoặc lấy thông tin xác thực. Các tệp bằng chứng nằm trong `artifacts/recent-bases/` ở máy thực hiện, không tự đưa chúng lên repo công khai vì có thể chứa tên workspace của người dùng.

## Môi trường và dữ liệu

AVD `airtable-api35`, thiết bị `emulator-5554`, package `com.formagrid.airtable`. Sau khi gặp ANR System UI ở cấu hình cũ, khởi động nguội với 4 CPU, RAM 3072 MB, màn hình 720 × 1600 / 280 dpi, không tải snapshot. Thao tác ADB trong máy ảo, không chiếm chuột hoặc focus Windows. Không dùng kết quả khi build chạy cùng lúc để kết luận hiệu năng.

Tạo riêng base **3DDK-QA-20261113** trong My First Workspace. Hai dòng thử là `QA-Record-A` và bản nhân đôi `QA-Record-A copy`, Notes `QA-note-20261113`, Status `In progress`. Giữ lại dữ liệu này để đối chiếu. Không gửi lời mời, bình luận hoặc xóa dữ liệu có sẵn.

## Kết quả quan sát

| Luồng | Đã làm và thấy | Bằng chứng XML/PNG |
|---|---|---|
| Trang chủ | Đọc Recents, Starred, Workspaces; danh sách gần đây chứa base | airtable-cold-state.xml, airtable-starred.xml, airtable-workspaces.xml |
| Workspace | Mở workspace, xem menu Share / Add to starred / Edit; chưa gửi lời mời | airtable-workspace-detail.xml, airtable-workspace-menu.xml |
| Tạo base | Chọn blank base, chọn nơi lưu, đổi tên thành tên QA | airtable-create2.xml, airtable-create-form.xml, airtable-renamed.xml |
| Đánh dấu base | Chọn Add to starred, về Starred thấy đúng base QA; mở lại được | airtable-config.xml, airtable-starred-verified.xml, airtable-base-reopened.xml |
| Tạo và sửa dòng | Thêm dòng; nhập tiêu đề; Notes mở màn riêng, quay về thấy nội dung | airtable-record-new.xml, airtable-notes-edit.xml, airtable-note-saved.xml |
| Single select | Mở bộ chọn Status và chọn In progress | airtable-status-picker.xml, airtable-status-applied.xml |
| Nhân bản dòng | Menu Duplicate record tạo dòng có hậu tố copy, giữ Notes và Status | airtable-record-menu.xml, airtable-record-duplicated.xml, airtable-grid-two.xml |
| Bộ chọn view | Mở danh sách view, thấy Grid và Kanban; quay về Grid thấy hai dòng còn nội dung | airtable-views-new.xml, airtable-grid-again.xml |
| Cấu hình trường | Mở Customize fields và bộ chọn loại; chưa kiểm thử lưu/chuyển kiểu hay dữ liệu không hợp lệ | airtable-fields.xml, airtable-add-field.xml, airtable-field-types.xml |
| Kanban | Tạo được view, mở chọn nhóm theo Assignee/Status. Có màn Kanban nhưng chưa xác minh thẻ nằm đúng nhóm hoặc kéo thả | airtable-kanban.xml, airtable-group.xml, airtable-kanban-final.xml |

Không thấy nút Lưu chung trong màn chi tiết Airtable đã quan sát. Dữ liệu còn sau khi về Grid là bằng chứng ở UI, chưa phải xác minh đồng bộ bằng thiết bị thứ hai. 3DDK hiện giữ nút Lưu để bảo toàn cơ chế nháp và kiểm tra version; bỏ nút này cần kiểm thử timeout, đổi dòng và xung đột trước.

## Thay đổi 3DDK trong lượt này

Đã đánh dấu hiển thị cơ sở dữ liệu thay cho từng bảng. Mở mục đánh dấu đi đến danh sách bảng của base. Menu đánh dấu có ở màn base và màn bảng, cùng trỏ đến base. Hai bảng cùng base chỉ tạo một mục.

Dấu bảng cũ được đọc thành dấu của base; thao tác bỏ đánh dấu mới ưu tiên hơn các dấu cũ. Không xóa dấu cũ, không chạy migration production. Trạng thái này vẫn lưu cục bộ theo tài khoản; chưa đồng bộ dấu sang thiết bị khác. Bộ kiểm thử mới kiểm tra gộp hai bảng, thời điểm gần nhất, bỏ dấu cũ, tách tài khoản và loại base không còn trong cache.

## Các phần còn phải chạy

Luồng lõi còn cần chứng minh trên bản APK mới: đổi hai bảng, chọn view, sửa ô, đổi dòng khi có nháp, lưu rồi mở lại và đọc máy chủ; mất mạng giữa lúc lưu, tài khoản chỉ xem và xung đột hai người sửa. Chưa đánh dấu các mục này là đạt chỉ từ code hoặc kết quả cũ.

Chưa thử đầy đủ filter/sort/group/search, bulk edit, attachments, comments/mentions, calendar/gallery, linked records, formula/lookup/rollup, phân quyền chia sẻ, offline Airtable và đồng bộ nhiều thiết bị. Chưa xác minh việc mở rồi hủy màn Field7 có lưu trường nháp ở Airtable hay không.

Tài liệu chính thức phân biệt rõ tính năng web và Android: [Airtable desktop and mobile feature differences](https://support.airtable.com/articles/9609457174-Airtable-desktop-and-mobile-feature-differences). Không mặc định mọi tính năng trên web đều có trong app Android. Đây là nguồn tài liệu hỗ trợ xác định phạm vi, không thay thế kiểm thử trực tiếp.

## Thứ tự tiếp tục

Chốt kiểm thử luồng bảng và bản nháp trước khi đổi sang tự lưu. Sau đó lần lượt bổ sung các phần đã được người dùng chọn; mỗi phần cần dữ liệu thật, kiểm tra quyền và lỗi mạng. Không triển khai các nút chỉ để giống ảnh nhưng không có hành vi.

## Kết quả build và chặn cài đặt

Chạy `:app:testDebugUnitTest :app:assembleRelease :app:compileDebugAndroidTestKotlin --offline`: BUILD SUCCESSFUL. Có 29 unit test, 0 lỗi, trong đó RecentBasesTest có 3 test. Kiểm thử Android mới chỉ biên dịch, chưa chạy. Lint vital release qua; chưa chạy lại toàn bộ lint.

APK: `artifacts/recent-bases/3ddk-base-star-release.apk`, checksum cạnh file. Khi thử cài đè lên emulator-5554, ADB trả:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.threeddk.tasks signatures do not match newer version; ignoring!
```

Giữ nguyên app trên máy ảo, không gỡ và không xóa dữ liệu. Chưa xác minh giao diện hoặc thao tác đánh dấu trên APK mới; cần chọn build tương thích chữ ký hoặc người dùng cho phép gỡ app máy ảo và đăng nhập lại. Chưa cài bản này lên Xiaomi.
