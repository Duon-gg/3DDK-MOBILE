# 3DDK — bảng dữ liệu và cộng tác

Ứng dụng Android cho phép người dùng tự tạo không gian làm việc, cơ sở dữ liệu, bảng công việc hoặc bảng tùy chỉnh; nhập/sửa/lưu trữ dòng và mời người khác cùng làm. Bản dùng thử `0.4.0-mobile-preview` (`versionCode 7`) thêm thẻ phù hợp điện thoại, chuyển sang bảng ngang và form sửa toàn màn hình; giữ Kotlin/XML, Firebase Authentication và đăng nhập Google. Xem [hướng dẫn dùng thử P0](docs/P0-USER-GUIDE.md) và [kết quả kiểm tra mới nhất](docs/PROGRESS-2026-11-01.md).

Mục tiêu tham chiếu là Teable, nhưng bản hiện tại chưa có toàn bộ chức năng Teable. API v1 quản lý công việc một nhóm vẫn được giữ để tương thích dữ liệu cũ. Tài liệu v1 bên dưới chỉ áp dụng cho luồng cũ; hướng dẫn v2 nằm trong PREVIEW-V2.

Kotlin · XML/View Binding · MVVM/StateFlow · Room · Retrofit/Gson · Firebase Auth/FCM · Supabase PostgreSQL/Edge Functions.

## Nhóm thực hiện

Đồ án cuối kỳ môn Lập trình ứng dụng di động (71ITSE30603), Nhóm 03, giảng viên Trần Công Thanh.

| Thành viên | MSSV | Phần phụ trách |
|---|---|---|
| Nguyễn Văn Tùng Dương (nhóm trưởng) | 2374802010086 | Tài khoản, đăng nhập; bảng P0 và sửa dòng |
| Nguyễn Bảo Khang | 2374802010211 | Giao diện bảng, cách hiển thị; bình luận và link mời |
| Hồ Quốc Duy | 2374802010074 | Thông báo đẩy, hộp thông báo; quản trị, thùng rác dòng; kiểm thử |

## Chạy dự án

1. Dùng JDK 21 và Android SDK 35.
2. Sao chép `local.properties.example` thành `local.properties`, điền cấu hình cloud theo [hướng dẫn](docs/SETUP.md).
3. Chạy `./gradlew :app:assembleDebug` (Windows: `gradlew.bat`).
4. Cài APK từ `app/build/outputs/apk/debug/`.

Khi chưa có cấu hình Firebase/Supabase, app hiển thị hướng dẫn cấu hình. Dữ liệu nghiệp vụ không được giả lập.

## Tài liệu

- [Giao diện mobile: triển khai và bằng chứng ngày 01/11](docs/PROGRESS-2026-11-01.md)
- [Căn cứ tham khảo Airtable mobile và phạm vi thay đổi](docs/research/teable-airtable/AIRTABLE-MOBILE-ADAPTATION.md)
- [Hướng dẫn dùng thử P0: bảng, người làm, chế độ xem và nháp](docs/P0-USER-GUIDE.md)
- [Bằng chứng kiểm tra P0 và các bước nghiệm thu còn lại](docs/P0-VERIFICATION.md)
- [Kết quả kiểm thử ngày 31/10 và giới hạn bản 0.3.3](docs/PROGRESS-2026-10-31.md)
- [Cấu hình đăng nhập Google](docs/GOOGLE-SIGN-IN.md)

- [Cấu hình Firebase/Supabase, seed nhóm trưởng và ký release](docs/SETUP.md)
- [API, quyền và luồng dữ liệu](docs/API.md)
- [Kiểm thử tự động và nghiệm thu trên thiết bị](docs/TESTING.md)
- [Vận hành, backup và quy trình PR](docs/OPERATIONS.md)
- [Backlog nhập vào Teable](docs/teable-backlog.csv)
- [Nhật ký triển khai và bằng chứng kiểm tra](docs/IMPLEMENTATION.md)
- [Bộ bàn giao, checksum và phần nghiệm thu còn lại](docs/DELIVERY.md)

## Kiểm tra

```sh
npm ci
npm test
npm run test:edge
npm run check:edge
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Lệnh cuối cần emulator/điện thoại. CI kiểm tra source và bản build không có secrets production.

## Trạng thái bàn giao

Bản 0.4.0 đã dựng đủ APK debug/release và AAB, 22 unit test đạt, lint không có lỗi; API 35 chạy 14 bài đạt/3 bài bỏ qua. Đã sửa lỗi nút tràn khi dùng chữ 200% và đóng bàn phím khi thu gọn tìm kiếm. APK/AAB release đã xác minh chữ ký cùng khóa của nhóm; tệp ký và SHA-256 nằm trong artifacts trên máy phát triển. Xem [bằng chứng bản mobile](docs/PROGRESS-2026-11-01.md).

Đợt mới chưa chạy lại luồng cloud có tài khoản vì đang chờ đăng nhập Google trên máy ảo. Các kết quả Google thật/API và lưu dữ liệu của bản trước được giữ riêng trong [nhật ký 31/10](docs/PROGRESS-2026-10-31.md); không dùng chúng thay cho nghiệm thu bản 0.4.0.

Chưa nghiệm thu hai tài khoản thật, tải 100 người/20 người cùng thao tác, API 24 trong đợt này, điện thoại thật, thông báo bảng v2 hoặc rò rỉ bộ nhớ. Chưa phát hành `v1.0.0` hoặc xác nhận có toàn bộ tính năng Teable.
