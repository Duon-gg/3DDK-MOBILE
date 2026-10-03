# Bàn giao mã và bản cài thử cấu hình

Người dùng chọn chuẩn bị source và hướng dẫn trước; Firebase/Supabase chưa được tạo. Không gắn tag phát hành chính thức v1.0.0 khi chưa nghiệm thu cloud.

## Bộ cài cục bộ

Trong thư mục `artifacts/` của checkout triển khai (không commit binary):

| File | SHA-256 |
|---|---|
| `3ddk-tasks-config-preview.apk` | `eb7f3df8273c06c06f7bb9352e3a4df4c1465bf5a40269713e8b1e0b5d739e69` |
| `3ddk-tasks-config-preview.aab` | `d1b94354c4b10e6774ed81ea95a018058f13e1e9234715e09061f21680ac8b45` |

APK dùng applicationId `com.threeddk.tasks`, versionCode 1. Bản cài mở màn hình yêu cầu cấu hình cloud. APK đã xác minh chữ ký v2, cài và mở trên API35; AAB đã kiểm tra bằng jarsigner. Jarsigner có cảnh báo chứng chỉ tự ký, không timestamp và thứ tự ZIP khi đọc dạng stream; chưa kiểm tra bằng Google Play (ngoài phạm vi).

Keystore và mật khẩu nằm ngoài repo, trong nơi lưu riêng trên máy bàn giao. Giữ bản sao riêng trước khi đổi máy. Không tạo lại khóa khi muốn nâng cấp bản cài đã phát hành.

## Đã chạy

- PostgreSQL/PGlite: 9 kiểm thử đạt.
- JWT: 2 kiểm thử đạt; Deno typecheck đạt.
- Android: 7 unit test đạt, lint/debug/release APK/AAB build đạt.
- Emulator API24 và API35: mỗi máy 4 instrumentation test đạt.
- Review độc lập: sửa cả 4 lỗi quan trọng; chi tiết trong [nhật ký](IMPLEMENTATION.md).

## Để vận hành thật

Làm theo [SETUP](SETUP.md), cấu hình cloud rồi dựng lại với cùng khóa ký và versionCode tăng. Chạy checklist [TESTING](TESTING.md) trên hai thiết bị, gồm một điện thoại thật cho FCM. Kiểm tra nâng cấp, font lớn, toàn bộ màn hình khi có dữ liệu thật, backup và nhắc hạn trước khi công bố hoàn thành nghiệm thu.

PGlite giúp chạy kiểm thử SQL mà không cần Docker; chưa thay thế kiểm tra extension/cron trên Supabase. Backend khóa ghi theo một nhóm để bảo đảm giao dịch; chỉ cần thay bằng khóa theo tài nguyên nếu số nhóm hoặc lưu lượng tăng.
