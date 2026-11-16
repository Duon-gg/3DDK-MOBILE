# Màn Không gian trên Android

Phạm vi: tham khảo bốn ảnh người dùng gửi, giữ nhận diện 3DDK và tiếng Việt; không có Omni.

- Tab Không gian liệt kê không gian người dùng có quyền, có mục Được chia sẻ và icon nền xám. Dòng phụ là quyền thực tế, không giả lập gói Free.
- Mở không gian: tên nằm trên toolbar, danh sách cơ sở dữ liệu dùng icon màu và thời điểm mở trên thiết bị; không lặp tiêu đề và hàng nút giữa danh sách.
- Menu toolbar: chia sẻ, đánh dấu/bỏ đánh dấu, sửa tên (chủ sở hữu). Tạo cơ sở dữ liệu và Làm mới vẫn được giữ trong menu.
- Chia sẻ dùng lại lời mời, thành viên, đổi quyền và thu hồi ở cấp không gian.
- Đánh dấu lưu theo tài khoản trên thiết bị trong Room; xuất hiện cùng cơ sở dữ liệu ở tab Đã đánh dấu. Chưa đồng bộ đánh dấu giữa các thiết bị.
- Sửa tên gọi PATCH /workspaces/{id}; chỉ chủ sở hữu, tên 1–100 ký tự, expectedVersion và requestId bắt buộc; có audit và chống ghi đè.
- Nhấn giữ cơ sở dữ liệu ngay trong không gian dùng chung menu với Gần đây, kể cả chưa từng mở cơ sở dữ liệu đó.

Migration: 202611130002_workspace_actions.sql. Đã áp dụng trên cloud, SQL Editor báo Success. Kiểm thử tự động gồm quyền EDITOR bị từ chối đổi tên, tên rỗng, version cũ, retry không tăng version lần hai. Kiểm thử giao diện Xiaomi phải được ghi nhận riêng, không suy từ build thành công.

Kết quả đóng gói: 35 ca backend và 35 ca Android qua; assembleRelease và lintVitalRelease thành công. APK `artifacts/recent-bases/3ddk-workspace-actions-release.apk`, SHA-256 `c8dbe9895a0211663dedc75c6c502337f6aedf93030691f29e05bc0c9ab81351`. Đã kiểm chữ ký, cài đè Xiaomi thành công và đọc UI xác nhận Gần đây mở được, các dự án còn nguyên.

Chưa xác nhận truy vấn smoke-test đổi tên trên cloud vì công cụ trình duyệt timeout sau khi gửi Run query. Dữ liệu thử được bọc transaction/rollback. Chưa nghiệm thu menu Không gian trên Xiaomi; đang chờ thao tác chạm của người dùng. Không tuyên bố giống từng pixel hoặc đã kiểm thử toàn bộ luồng.

## Gom không gian kiểm thử — 14/11/2026

Tab Không gian gom tên theo mẫu chính xác `3DDK • Kiểm thử P0 <8 ký tự hex>` vào một mục Không gian kiểm thử có số lượng. Mở mục này mới thấy các không gian; mở từng không gian mới thấy cơ sở dữ liệu. Không đổi workspace_id, quyền, tên hoặc dữ liệu trên Supabase. Các tên thật như Team mobile và 3DDK • Bảng dùng thử vẫn hiện ngoài. Không gian kiểm thử đã đánh dấu vẫn truy cập được ở Đã đánh dấu.

Đây là phân nhóm theo quy ước tên hiện có, không phải thuộc tính kiểm thử trên server: nếu đổi tên ra ngoài mẫu, không gian sẽ xuất hiện ở danh sách chính. Không tự phân loại theo từ “test” hoặc “kiểm thử” chung chung để tránh gom nhầm dữ liệu thật.

## Mở dữ liệu trực tiếp — 14/11/2026

Mọi đường vào cơ sở dữ liệu qua workspaceBrowser tự chọn bảng đã mở gần nhất trên tài khoản/thiết bị hiện tại. Đối chiếu với danh sách bảng vừa tải; bảng không còn trong danh sách không được chọn. Lần đầu hoặc bảng cũ không còn thì chọn bảng đầu tiên server trả về. Nếu không có bảng, giữ giao diện trống để tạo bảng. Khi mất mạng chỉ dùng snapshot đã tải; lỗi quyền không được coi là lỗi mạng để fallback.

Màn danh sách bảng trung gian được loại khỏi back stack khi chuyển thẳng vào bảng. Bộ chọn bảng/view hiện có trong màn dữ liệu tiếp tục dùng để chuyển bảng. Không thay đổi schema hoặc dữ liệu Supabase.

Kiểm chứng bản mở bảng trực tiếp: 38 unit tests đạt; assembleRelease và lintVitalRelease đạt. Chữ ký release giữ nguyên. APK artifacts/recent-bases/3ddk-direct-table-release.apk đã cài đè Xiaomi thành công, đọc lại thấy trang Gần đây. Chưa xác nhận thao tác mở/chuyển bảng trên Xiaomi; đang chờ người dùng chạm vì thiết bị chặn INJECT_EVENTS.

## Menu và thùng rác không gian — 14/11/2026
- Nhấn giữ không gian tại Không gian, Đã đánh dấu hoặc nhóm kiểm thử mở menu Chia sẻ / Đánh dấu / Chỉnh sửa.
- Màn chia sẻ hiển thị tên, email, ảnh, quyền thành viên; chỉ chủ không gian được mời, thu hồi hoặc sửa quyền.
- Màn chỉnh sửa có tên, thành viên và xóa vào thùng rác. Xác nhận nêu rõ toàn bộ dữ liệu bên trong sẽ ngừng truy cập.
- API workspace-trash chỉ trả mục do người gọi sở hữu. DELETE workspaces/id và POST workspaces/id/restore dùng expectedVersion và requestId.
- Xóa không gian chỉ đặt deleted_at; không sửa/xóa bảng, dòng, quyền, lời mời. Gate chung chặn truy cập và mutation dưới không gian đã xóa, kể cả chia sẻ riêng; chặn chuyển base vào không gian đã xóa. Các danh sách loại bỏ mục đã xóa.
- Khôi phục giữ nguyên quyền, dữ liệu; base đã xóa riêng không tự được khôi phục. Nháp cục bộ không bị xóa khi dọn cache.
- Chưa có xóa vĩnh viễn hay tự dọn theo thời hạn. Không rollback API về phiên bản cũ khi còn không gian đã xóa, vì API cũ không có gate deleted_at. Muốn rollback phải khôi phục có kiểm soát bằng API mới trước, hoặc giữ nguyên gate trong bản hotfix.

### Kết quả xác minh
- Android: 39 unit tests đạt; assembleRelease và lintVitalRelease đạt.
- Backend: toàn bộ 36 tests đạt, sau đó bổ sung tình huống chia sẻ riêng/lời mời/move-in và chạy lại 9 tests base-actions đều đạt.
- Supabase: migration 202611140001 đã chạy thành công; tests/workspace-trash-cloud.sql trả PASS quyền owner, retry, chặn dữ liệu con, trash và restore. Dữ liệu thử đã rollback. Chụp bằng chứng trình duyệt báo Unable to capture screenshot; kết quả được đọc từ bảng Results.
- APK artifacts/recent-bases/3ddk-workspace-settings-release.apk đã xác minh chữ ký và adb install -r thành công trên Xiaomi XKLZBILFBUQG8HMZ. SHA256 2004d88fa80efd7b4d64ed3f051781b7fbe194a2b9150fadb0e6a707ed213c3b.
- Chưa xác minh bố cục/chạm trên Xiaomi: khi đọc UI chưa thấy 3DDK; đã yêu cầu người dùng mở app và nhấn giữ không gian. Không đánh dấu UI test là đạt.

## Tách lối truy cập dữ liệu kiểm thử
Tab Không gian không còn dòng nhóm kiểm thử. Nút ⋮ riêng ở đầu trang mở Dữ liệu kiểm thử; dùng lại bộ lọc và màn test-workspaces. Không thay đổi dữ liệu, quyền, backend hoặc cách mở từng không gian.

### Sửa nút Xóa không gian không xuất hiện
WorkspaceFragment chỉ quan sát data; cache có thể phát trước capabilities. Khi snapshot không thay đổi, StateFlow không phát lại nên UI giữ danh sách thiếu nút xóa. Thêm observer capabilities để render lại và invalidate menu; không bỏ điều kiện OWNER/workspaceTrash. Cần xác nhận trên Xiaomi sau cài bản sửa.

## QA Xiaomi thực tế — 14/11/2026
- Sửa picker_view_background: bỏ tham chiếu theme trong selector drawable gây InflateException trên Xiaomi, dùng màu checked/pressed và nền transparent.
- Build assembleRelease/lintVitalRelease đạt; cài đè 3ddk-picker-fix-release.apk thành công.
- ADB touch hoạt động. Tự thực hiện toàn bộ luồng trên không gian 3DDK • Kiểm thử P0 98a8994e.
- Trước: Dự án kiểm thử / Công việc của nhóm; dòng Nhập trực tiếp trên điện thoại và qa; thành viên Dương (nvtd2gg@gmail.com), OWNER. Bộ chọn mở được với Tất cả dòng và Tất cả, không văng.
- DELETE qua Chỉnh sửa không gian, xác nhận đúng tên; base biến mất khỏi Gần đây. Thùng rác không gian có đúng 98a8994e.
- Restore qua Thùng rác; không gian trở lại danh sách kiểm thử; mở lại dữ liệu báo Đã tải kết quả. Bảng, hai dòng, thành viên/email/quyền trùng trước; bộ chọn vẫn mở được. Không đụng các mục khác trong thùng rác.
- Không gian hiện đã khôi phục. Chưa kiểm thử đồng thời bằng tài khoản thứ hai trên điện thoại. Sau xóa/khôi phục, lịch sử mở gần đây cục bộ được làm mới do dọn cache; không phải mất bảng.
