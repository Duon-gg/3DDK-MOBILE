# Đối chiếu Teable với 3DDK

Ngày ghi nhận: **31/10/2026**. Mục tiêu: đưa cách dùng của **Ứng dụng quản lí công việc 3DDK** gần Teable, giữ Kotlin/XML, MVVM, Room, Retrofit/Gson, Firebase Auth và Supabase. Đây là phần bổ sung cho [danh mục 37 tính năng](FEATURE_INVENTORY.md) và [roadmap](IMPLEMENTATION_ROADMAP.md), không phải báo cáo đã làm xong mọi tính năng Teable.

## 1. Phạm vi và bằng chứng

| Nguồn | Đã kiểm tra | Giới hạn kết luận |
|---|---|---|
| `https://pm.uptech.vn/` | Ban đầu thấy trang đăng ký email/mật khẩu và Google. Sau đó tìm được tab Edge đã có phiên đăng nhập: mở không gian, dự án, bảng Grid và các menu lọc/sắp xếp/nhóm/ẩn cột. Chi tiết ở mục 1.1. | Quan sát giao diện và mở menu chỉ đọc; không sửa bản ghi, không thử lưu cấu hình, không kiểm thử phân quyền server. Chưa xác định phiên bản, tùy chỉnh hoặc edition của tên miền này. |
| Mã nguồn Teable công khai | GitHub API trả SHA **`b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9`**, thời điểm commit **28/10/2026 06:22:38 UTC**; đọc các phần liên quan dưới đây tại đúng SHA. | Không suy ra `pm.uptech.vn` chạy cùng SHA. Không build hoặc chạy Teable, không kiểm thử các đường mã chưa đọc. |
| 3DDK | Đối chiếu mã P0 tại `d044254` cùng thay đổi của bản **0.3.1** trong lượt này: Google phía Android, tự mở đối tượng vừa tạo, live test theo giao thức P0. Build cuối qua sáu tác vụ; 22 JVM test và 9 instrumentation test API 35 đạt. Đã đăng nhập lại và Resume Supabase; API Edge chặn đúng hai request thiếu/sai token bằng `401 UNAUTHORIZED`. | Cấu hình Google trên Firebase chưa được lưu và chưa có đăng nhập Google thật. Dashboard Supabase cuối lượt báo **Restoration in progress**; hai request bị chặn chưa chứng minh database sẵn sàng. Chưa chứng minh luồng cloud với người dùng thật hoặc tải công ty 100 người. |

[Commit Teable cố định][commit] và [metadata GitHub API][commit-api] xác định phiên bản nguồn tham khảo. Các trích dẫn `S1–S10` dưới đây đều là đường dẫn công khai của chính repository Teable, không phải dữ liệu trích từ tài khoản tại `pm.uptech.vn`.

Skill **reverse-engineering** đã được đọc theo yêu cầu. Mô tả của skill loại trừ quy trình web thuần túy; hiện chưa có binary, mã đóng gói hoặc thuật toán bị che giấu cần dịch ngược. Vì vậy lượt này dùng bước phân loại mục tiêu, đọc mã nguồn công khai và quan sát giao diện thông thường. Không giả lập rằng đã chạy Ghidra/Frida/REA. Một số tệp chỉ dẫn phụ như `tool-index.md` không có tại vị trí skill tham chiếu; không cài công cụ hoặc sửa cấu hình toàn cục để bù cho tài liệu thiếu.

### 1.1. Quan sát trực tiếp sau đăng nhập tại `pm.uptech.vn`

Phần này cập nhật kết quả ban đầu chỉ thấy trang xác thực. Bằng chứng là cây truy cập giao diện và ảnh kiểm tra trong phiên Edge ngày 31/10/2026; không sao chép tên riêng, ID, nội dung công việc hay số lượng dòng của không gian tham khảo.

| Đã quan sát/thao tác chỉ đọc | Hành vi thấy trực tiếp | Chưa kiểm thử |
|---|---|---|
| Không gian → dự án → bảng | Một dự án trống hiển thị phần chào mừng; bảng khác mở Grid có các tab chế độ xem. | Tạo mới, đổi tên, xóa, lưu trạng thái điều hướng. |
| Bảng dữ liệu | Grid chữ nhật có hàng/cột; thấy ảnh thu nhỏ của tệp, lựa chọn có màu và nhãn thành viên. | Sửa ô, tải tệp, độ chính xác của kiểu dữ liệu và hiệu năng. |
| Thanh công cụ | Có Add record, Hidden fields, Filter, Sort, Group, biểu tượng tìm kiếm/undo/redo, Share và công tắc Personal. | Nút hiện không có nghĩa mọi thao tác đã chạy thành công hoặc được cấp quyền. |
| Mở Filter | Hiện trạng thái chưa có điều kiện, Add condition và Add condition group. | Áp điều kiện, nhóm lồng nhau, kết quả truy vấn và việc lưu cấu hình. |
| Mở Sort / Group | Hiện Sort by / Group by cùng bộ chọn cột. | Thứ tự thực tế, nhóm dữ liệu, null, thứ tự kéo và tính bền sau tải lại. |
| Mở Hidden fields | Công tắc từng cột, công tắc cột chính bị vô hiệu hóa, tay nắm kéo thứ tự, Show all và Hide all. | Đổi công tắc/kéo cột, lưu thay đổi hoặc quyền đọc cột phía server. |

Từ bằng chứng này có thể đối chiếu vị trí công cụ, cách chọn view và hình thức các ô dữ liệu. Chưa đủ để khẳng định nội bộ server, persistence, đồng bộ thời gian thực hoặc đầy đủ tính năng của tên miền tham khảo.

## 2. Những hành vi xác minh từ nguồn công khai

| Chủ đề | Sự thật trong mã nguồn đã đọc | Áp dụng dự kiến cho 3DDK |
|---|---|---|
| Tạo và chọn không gian | `SpaceSwitcher` giữ không gian đang chọn, tạo bằng mutation và điều hướng đến không gian mới khi thành công (`L179–204`). [S1] | Mã Android 0.3.1 đã mở đối tượng vừa tạo sau xác nhận server; chặn điều hướng muộn khi màn hình/hộp thoại nguồn đã đóng. Chưa nghiệm thu thao tác tạo trên cloud thật. |
| Dự án bên trong không gian | `SpaceInnerPage` lọc base theo `spaceId`, kiểm khả năng tạo ở UI và điều hướng vào base sau khi tạo (`L68–74`, `L110–125`). [S2] | Tự mở sau tạo đã dùng lại API/workspace/base của 3DDK trong mã 0.3.1. Quyền vẫn kiểm ở backend; chưa dùng quan sát chỉ đọc tại tên miền tham khảo làm bằng chứng tạo thành công. |
| Điều hướng trong dự án | `BaseNodeTree` có cây tài nguyên, mục được chọn, thao tác kéo và điều hướng router (`L239`, `L300–384`). [S3] | Trên Android, hiển thị đường dẫn Không gian → Dự án → Bảng và bộ chọn bảng dễ mở. Không cần thay Single Activity hoặc sao chép sidebar web thành màn hình quá chật. |
| Bảng và chỉnh ô | Grid truyền cột, dòng, chiều cao, cột cố định, nhóm và người cộng tác. Sửa ô gọi `record.updateCell`; mở rộng dòng giữ `recordId`; có callback copy/paste (`L669–695`, `L1090–1225`, `L1282–1300`, `L1756–1816`). [S4] | Thêm sửa một ô bằng editor theo kiểu cột, còn mở chi tiết dòng là thao tác riêng. Giữ cell patch, `expectedVersion`, `expectedSchemaVersion`, `requestId` và nháp Room. |
| Thanh công cụ | `GridViewOperators` chứa ẩn cột, lọc, sắp xếp, nhóm và chiều cao dòng (`L64–180`). [S5] | 3DDK đã có tìm/lọc/sắp xếp/chọn cột; còn cần nhóm và cấu hình trình bày dễ dùng. Các nút phải phản ánh cấu hình hiện tại và quyền thực tế. |
| Chi tiết bản ghi | `RecordEditor` tạo editor từ danh sách field, nhận callback thay đổi theo field ID, có readonly theo field và tùy chọn hiện field bị ẩn (`L14–79`). [S6] | Tái sử dụng RecordEditorFragment; giữ ID cột ổn định và validation. Ẩn cột trong view không trở thành quyền cấm đọc cột. |
| Nhiều chế độ xem | `View` chọn component Grid/Form/Kanban/Gallery/Calendar/Plugin; schema view có filter/sort/group/options/columnMeta và validation options theo loại (`L33–108`). [S7] [S8] | Mở rộng `ws_views` và query hiện có, cùng một nguồn record. Bắt đầu Kanban theo một cột SELECT; mỗi loại view cần quy tắc cấu hình và kiểm thử riêng. |
| Kiểu cột và metadata | Enum field có text, user, attachment, checkbox, select, date, number, rating, formula, link, rollup và các kiểu hệ thống. Prisma tách metadata Space/Base/TableMeta/Field/View, có version và tham chiếu table/field (`L218–270`, `L331–369`, `L612–638`). [S9] [S10] | 3DDK giữ JSONB `cells` và schema hiện có. Linked record/formula/rollup cần thiết kế phụ thuộc, phân quyền và tính lại; không thể thêm chỉ bằng một lựa chọn trong hộp thoại. |

Các callback quyền trong Grid là bằng chứng về **lớp UI**. Bảng trên không dùng chúng để khẳng định backend Teable đã được audit toàn diện hoặc mọi edition có cùng chức năng.

## 3. Khoảng cách chức năng hiện tại

| Cách dùng cần đối chiếu | 3DDK P0 và mã 0.3.1 đang có | Phần còn thiếu / điều kiện nghiệm thu |
|---|---|---|
| Bắt đầu nhanh | Tự đăng ký email, tạo không gian riêng, nhận lời mời; 0.3.1 đã có nút và luồng SDK Google phía Android. | Lưu cấu hình Google trên Firebase; thử đăng nhập, hủy chọn, đăng xuất/đổi tài khoản và tài khoản email đã có. |
| Mở dữ liệu | Không gian → cơ sở dữ liệu → bảng, ba tab Không gian/Lời mời/Cá nhân; mã 0.3.1 tự mở đối tượng mới sau server xác nhận. | Kiểm thử trên cloud, chuyển bảng và quay lại không mất trạng thái. |
| Bảng dùng hằng ngày | Grid cuộn, tìm/lọc/sort, chọn cột, tải thêm, saved view cá nhân/chung. | Sửa từng ô tại bảng, nhóm, chỉnh thứ tự/độ rộng cột, chọn nhiều dòng và thao tác hàng loạt. |
| Cột tùy chỉnh | TEXT/NUMBER/DATE/CHECKBOX/SELECT/USER, editor theo kiểu và kiểm tra server. | Đổi tên/options/thứ tự an toàn; kiểu nhiều lựa chọn, tệp và trường hệ thống khi có nhu cầu đã chốt. |
| Đổi cách xem | Saved view GRID. | Kanban, Calendar, Gallery và Form thực sự dùng chung dữ liệu. Không thêm nút cho view chưa triển khai. |
| Cộng tác | OWNER/EDITOR/VIEWER, mời/đổi/gỡ quyền, lịch sử, chống ghi đè và lưu nháp. | Đồng bộ thay đổi đang xem, bình luận/nhắc tên và thông báo v2; kiểm thử hai tài khoản thật. |
| Dữ liệu liên quan | Mỗi bảng độc lập. | Linked record → lookup/rollup → formula theo thứ tự phụ thuộc; chặn vòng lặp và truy vấn xuyên quyền. |
| Trao đổi dữ liệu | Chưa có luồng import/export hoàn chỉnh trong app. | CSV có xem trước, ánh xạ cột, báo lỗi từng dòng và chống lặp khi gửi lại; xuất đúng quyền truy cập. |

Các mục thiếu được phân biệt với sản phẩm tham khảo; không dùng danh sách này để nói `pm.uptech.vn` đã được kiểm tra có tất cả chúng. Mục tiêu trước mắt là hành vi có thể kiểm chứng trên Android, không phải tỷ lệ “giống” do ước lượng.

## 4. Thứ tự tiếp tục triển khai

1. **Đóng luồng P0 trên thiết bị/cloud thật.** Live test đã được cập nhật sang metadata/query/cell patch; cần khôi phục truy cập Supabase rồi chạy OWNER tạo bảng, EDITOR sửa, VIEWER bị chặn ghi. Thử cùng version, quyền bị gỡ, mất mạng và khởi động lại khi còn nháp. Qua khi không mất nháp hoặc lộ dữ liệu tài khoản cũ.
2. **Hoàn tất cấu hình và kiểm thử Google.** Mã Android dùng Firebase Auth hiện tại đã có; backend chỉ nhận Firebase ID token đã xác minh. Qua khi đăng nhập lần đầu/lần sau, hủy và logout/đổi tài khoản hoạt động; không tự tạo quyền vào không gian người khác.
3. **Làm thao tác bảng gần Teable hơn.** Tự mở không gian/dự án/bảng sau tạo đã có mã 0.3.1. Tiếp theo là đường dẫn quay lại, chọn view rõ và sửa ô theo kiểu. Mỗi lần lưu kiểm version và quyền; lỗi vẫn giữ nội dung. Cần kiểm UI với chữ lớn và bàn phím đang mở.
4. **Mở rộng cách xem và cộng tác.** Kanban SELECT trước, sau đó Calendar theo DATE; cùng record ID với Grid. Thử đổi trạng thái/ngày ở một view rồi mở view khác; người chỉ xem không được ghi.
5. **Cấu hình cột, trao đổi dữ liệu và thông báo.** Chốt từng thao tác đổi metadata, CSV, file/comment/inbox trước khi mở API tương ứng. Migration bổ sung, kiểm dữ liệu cũ, có cách quay về phiên bản app/backend đã biết hoạt động.
6. **Quan hệ và tự động hóa sau lõi.** Linked records, lookup/rollup/formula rồi automation/webhook. Chúng cần thiết kế và test riêng; chưa phải tính năng đã triển khai bởi báo cáo này.

Việc giống Teable không yêu cầu chuyển sang Next.js/NestJS hoặc thay Firebase/Supabase. `ws_*`, field UUID, record version, saved query và Room draft vẫn là phần tái sử dụng. Giới hạn hiện tại **500 dòng mỗi bảng** và mục tiêu khoảng **20 người cùng thao tác** cần được đo trên môi trường thật trước khi phục vụ công ty.

## 5. Giấy phép và khả năng tái sử dụng

Metadata `LICENSE` của đúng SHA ghi core `apps/nextjs-app`/`apps/nestjs-backend` thuộc AGPL-3.0, còn `packages/*` theo MIT; `packages/core/LICENSE` và `packages/sdk/LICENSE` đều ghi MIT. File LICENSE gốc cũng có phần điều khoản về tài sản thương hiệu. [Giấy phép gốc][license] · [core][core-license] · [sdk][sdk-license]. Đây là ghi nhận nội dung file, không phải kết luận pháp lý về bản tùy chỉnh tại `pm.uptech.vn`.

Lượt này chỉ đọc để viết đối chiếu, không chép mã hoặc tài nguyên Teable vào app. 3DDK giữ tên/logo riêng. Nếu sau này chọn dùng lại mã cụ thể, phải xem đúng license/notices của mã và phụ thuộc đó trước khi tích hợp.

## 6. Bằng chứng có thể kiểm lại

- Bản tải tĩnh: `artifacts/2026-10-31/teable-public/manifest.json` ghi SHA, URL, số dòng và tên file của 18 nguồn đã tải; `tree-paths.json` chỉ chứa tên tệp công khai, không clone repository hoặc cài dependency Teable.
- Backend QA cục bộ: `artifacts/2026-10-31/backend-qa-summary.md`, Node 28/28 và Edge 12/12; không thay thế thử nghiệm người dùng thật.
- Quan sát tên miền tham khảo: phiên Edge đã đăng nhập, mở Grid và các menu chỉ đọc được liệt kê tại mục 1.1. Không thử thay đổi dữ liệu hoặc đưa dữ liệu hàng vào tài liệu.
- Supabase đã bắt đầu khôi phục project hiện có; `artifacts/2026-10-31/cloud-auth-negative.json` ghi hai kiểm tra chặn thiếu/sai token. Chưa chạy mutation nghiệp vụ hay migration trong đợt này.
- Còn thiếu: kiểm thử lưu/chỉnh sửa tại tên miền tham khảo, Google cloud của 3DDK, database Supabase khôi phục xong và luồng app với tài khoản thật. Chưa có bằng chứng để tuyên bố app giống toàn bộ Teable hoặc tên miền tham khảo.

[commit]: https://github.com/teableio/teable/commit/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9
[commit-api]: https://api.github.com/repos/teableio/teable/commits/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9
[S1]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/apps/nextjs-app/src/features/app/blocks/space/space-side-bar/SpaceSwitcher.tsx#L179
[S2]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/apps/nextjs-app/src/features/app/blocks/space/SpaceInnerPage.tsx#L110
[S3]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/apps/nextjs-app/src/features/app/blocks/base/base-side-bar/BaseNodeTree.tsx#L239
[S4]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/apps/nextjs-app/src/features/app/blocks/view/grid/GridViewBaseInner.tsx#L669
[S5]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/apps/nextjs-app/src/features/app/blocks/view/tool-bar/components/GridViewOperators.tsx#L64
[S6]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/packages/sdk/src/components/expand-record/RecordEditor.tsx#L14
[S7]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/apps/nextjs-app/src/features/app/blocks/view/View.tsx#L62
[S8]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/packages/core/src/models/view/view.schema.ts#L33
[S9]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/packages/core/src/models/field/constant.ts#L2
[S10]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/packages/db-main-prisma/prisma/postgres/schema.prisma#L218
[license]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/LICENSE
[core-license]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/packages/core/LICENSE
[sdk-license]: https://github.com/teableio/teable/blob/b6ae0a1ab8a9c7ce8b6eef16b1af27cc770c2cd9/packages/sdk/LICENSE
