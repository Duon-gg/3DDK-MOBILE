# 3DDK — bảng dữ liệu và cộng tác

Ứng dụng Android cho phép người dùng tự tạo không gian làm việc, cơ sở dữ liệu, bảng và cột tùy chỉnh; nhập/sửa bản ghi và mời người khác cùng làm. Bản dùng thử `0.2.0-preview` giữ Kotlin/XML và Firebase Authentication. Xem [cách dùng và giới hạn bản mới](docs/PREVIEW-V2.md).

Mục tiêu tham chiếu là Teable, nhưng bản hiện tại chưa có toàn bộ chức năng Teable. API v1 quản lý công việc một nhóm vẫn được giữ để tương thích dữ liệu cũ. Tài liệu v1 bên dưới chỉ áp dụng cho luồng cũ; hướng dẫn v2 nằm trong PREVIEW-V2.

Kotlin · XML/View Binding · MVVM/StateFlow · Room · Retrofit/Gson · Firebase Auth/FCM · Supabase PostgreSQL/Edge Functions.

## Chạy dự án

1. Dùng JDK 21 và Android SDK 35.
2. Sao chép `local.properties.example` thành `local.properties`, điền cấu hình cloud theo [hướng dẫn](docs/SETUP.md).
3. Chạy `./gradlew :app:assembleDebug` (Windows: `gradlew.bat`).
4. Cài APK từ `app/build/outputs/apk/debug/`.

Khi chưa có cấu hình Firebase/Supabase, app hiển thị hướng dẫn cấu hình. Dữ liệu nghiệp vụ không được giả lập.

## Tài liệu

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

Source có luồng công việc, backend và hướng dẫn cấu hình. Project Firebase/Supabase chưa được cung cấp; đăng nhập/push thật, nghiệm thu nhiều tài khoản và thử với sinh viên cần thực hiện sau cấu hình. Chưa công bố bản phát hành đã nghiệm thu `v1.0.0`.
