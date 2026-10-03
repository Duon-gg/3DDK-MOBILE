# Vận hành và làm việc nhóm
## PR và Teable
Nhập docs/teable-backlog.csv vào Teable. Gán A/B/C cho đúng thành viên thật.
Trạng thái: Backlog → Ready → Doing → Review → Done.
Nhánh codex/<ma-viec>-<mo-ta>; PR ghi mã Teable, thay đổi và kết quả kiểm thử.
Mỗi người kiểm tra code AI của mình; không thay tác giả commit để giả lập đóng góp.
CI không có secrets production; người khác review trước khi merge master.

## Theo dõi
Kiểm tra Edge logs theo request ID, HTTP 401/403/409/500, cron.job_run_details và hạn mức Supabase.
Outbox attempts >= 8 và sent_at IS NULL cần kiểm tra cấu hình/quota/token; chỉ reset attempts sau khi giải quyết nguyên nhân.
Token UNREGISTERED được xóa tự động; push retry có thể lặp vận chuyển nhưng dùng cùng notification tag.
Supabase Free có thể pause sau một tuần không hoạt động. Mở dashboard kiểm tra trước khi demo.

## Backup và cập nhật
Trước migration/release, dùng supabase db dump hoặc pg_dump với quyền riêng của project; lưu dump ở thư mục ngoài repo công khai.
Thử restore vào database thử trước khi dùng dump để phục hồi.
Giữ tag/source, APK, checksum, mapping R8, migration, keystore và secrets trong nơi có quyền phù hợp.
Nâng versionCode, ký cùng keystore. Thử cài đè và kiểm tra nháp/cache.
Migration cần tương thích với app cũ; rollback API về phiên bản trước chỉ khi vẫn tương thích schema.
Không hạ database bằng xóa bảng để xử lý lỗi. Không công khai database dump hoặc keystore.

## Bản phát hành
Không đặt nhãn hoàn tất nghiệm thu v1.0.0 trước khi kiểm tra cloud/hai thiết bị trong TESTING.md.
APK dựng khi thiếu cloud config chỉ dùng kiểm tra cài đặt và giao diện cấu hình.

