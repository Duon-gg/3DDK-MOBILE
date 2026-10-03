# Cấu hình và chạy 3DDK Tasks

## Yêu cầu
Android Studio, JDK 21, SDK 35, Node.js 24. Repo có Gradle Wrapper 8.13; không cần cài Gradle toàn hệ thống.
Android: minSdk 24, targetSdk 34, compileSdk 35, applicationId `com.threeddk.tasks`.
Dùng máy Android có Google Play Services cho Firebase Cloud Messaging.

## 1. Tạo Firebase miễn phí
1. Tạo Firebase project trên Spark, bật Authentication → Email/Password. Không cần bật Analytics.
2. Đăng ký ứng dụng Android với package `com.threeddk.tasks`.
3. Từ cấu hình Firebase Android, lấy API key, App ID, Project ID và Project number (Sender ID).
4. Sao chép `local.properties.example` thành `local.properties`, điền bốn giá trị Firebase tương ứng. Không commit file này.
5. Bật Cloud Messaging API v1 trong project. Tạo service account key dùng phía server, lưu ở nơi riêng. Không đưa vào Android hoặc Git.
6. Khi đã có APK kết nối cấu hình, nhóm trưởng đăng ký, xác minh email. Ghi Firebase UID trong Authentication → Users để seed database ở bước 3.
7. Kiểm tra template email xác minh/reset và authorized domains trong Firebase Console. App dùng email action mặc định của Firebase.

Firebase tự giữ phiên đăng nhập. EncryptedSharedPreferences chỉ lưu email khi người dùng chọn nhớ email; không lưu mật khẩu.

## 2. Tạo Supabase Free
Tạo project Free; giữ database password ngoài repository. Sao chép Project URL vào `SUPABASE_URL` trong local.properties.
Chạy migration `supabase/migrations/202610030001_core.sql` một lần trong SQL Editor.
Có thể dùng Supabase CLI với `supabase link` và `supabase db push` thay cho SQL Editor. Không chạy lại migration bằng tay nếu đã áp dụng.

Các bảng bật RLS và không cấp quyền trực tiếp cho anon/authenticated. Android chỉ gọi Edge API.

## 3. Seed nhóm trưởng
Sau khi đăng ký và xác minh tài khoản Firebase, dùng SQL Editor chạy:
```sql
insert into public.members(uid,email,display_name,role)
values ('UID_TU_FIREBASE','email-da-xac-minh@example.com','Tên nhóm trưởng','LEADER');
```
Thay ba giá trị bằng tài khoản thật của nhóm trưởng. Chỉ một LEADER được phép tồn tại.
Không tạo endpoint tự nâng quyền. Người dùng khác vào app nhờ email có trong bảng invites do nhóm trưởng quản lý.

## 4. Deploy Edge Functions
Cài/chạy Supabase CLI, đăng nhập tài khoản của nhóm và link đúng project.
```sh
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase functions deploy api
supabase functions deploy dispatch
```
Trong Edge Functions → Secrets đặt:
- `FIREBASE_PROJECT_ID`: project Firebase vừa tạo.
- `FIREBASE_SERVICE_ACCOUNT`: toàn bộ JSON service account dưới dạng một chuỗi JSON.
- `DISPATCH_SECRET`: chuỗi ngẫu nhiên ít nhất 32 byte, giữ bí mật.

SUPABASE_URL và SUPABASE_SERVICE_ROLE_KEY được môi trường hosted cung cấp.
Cả hai function có `verify_jwt=false`: API **tự xác minh Firebase JWT** bằng jose, dispatch **tự xác minh secret**. Không xóa middleware này.
API kiểm tra issuer, audience, chữ ký RS256, thời hạn, auth_time và email_verified, rồi lấy UID từ token.

## 5. Lập lịch push
Trong Supabase Vault tạo hai secret: `project_url` và `dispatch_secret`; giá trị phải khớp Project URL và DISPATCH_SECRET.
Chạy `supabase/setup-scheduler.sql` trong SQL Editor. Script thiết lập cron mỗi phút và thay thế job cùng tên nếu có.
Không gõ secret vào file SQL đã commit. Kiểm tra `cron.job_run_details` và Edge logs sau khi chạy.

Push dùng outbox có lease, retry tối đa 8 lần, backoff tối đa một giờ. Theo dõi bản ghi chưa sent và attempts=8.
Worker chỉ nhận một thông báo trước mỗi lần gửi; các thiết bị của người nhận được gửi đồng thời với timeout 10 giây. Một lượt xử lý tối đa 20 thông báo và ngừng nhận thêm sau 45 giây; phần còn lại được xử lý ở lượt sau.
Nhắc hạn dựa theo UTC server; UI nhập/hiển thị giờ Việt Nam. Nếu việc được tạo khi còn dưới một giờ, chỉ gửi nhắc 1 giờ.
FCM là best effort: không bảo đảm đúng giây, và force-stop ứng dụng ở Settings có thể ngăn nhận push cho tới khi mở lại.

## 6. Dựng Android
```powershell
$env:JAVA_HOME='DUONG_DAN_JDK_21'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```
Cài APK trong `app/build/outputs/apk/debug`.
Không có cấu hình cloud, app hiển thị màn hình hướng dẫn cấu hình; đây không phải chế độ giả lập nghiệp vụ.

## 7. Ký bản release
Tạo keystore bằng keytool, lưu ngoài repo. Tạo file local `keystore.properties`:
```properties
storeFile=C:/duong-dan-rieng/release.p12
storePassword=MAT_KHAU_RIENG
keyAlias=3ddk-tasks
keyPassword=MAT_KHAU_RIENG
```
```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```
APK: `app/build/outputs/apk/release`. AAB: `app/build/outputs/bundle/release`.
Nếu không có keystore.properties, release chưa được ký; không phát hành như bản cài đã ký.
Giữ keystore và mật khẩu an toàn để ký mọi bản cập nhật. Tăng versionCode trước khi phát hành bản tiếp theo.

## Quyền và dữ liệu riêng tư
Liên kết kết quả mở bên ngoài app; người nộp tự bảo đảm quyền truy cập của link.
Thu hồi lời mời không vô hiệu hóa thành viên đã tham gia; dùng Ngừng quyền trong màn hình Nhóm.
Ngừng quyền chỉ áp dụng khi thiết bị kết nối lại; cache offline không thể bị server xóa từ xa tức thì.
Khi đăng xuất, app xóa cache/nháp và cố gắng hủy token push. Push tồn đọng chỉ chứa nội dung chung, không chứa nội dung công việc.
Khi token hết hiệu lực, nháp được giữ theo UID để khôi phục sau khi đăng nhập lại; dữ liệu nhóm không hiển thị cho tới khi xác thực lại thành công.

## Xử lý lỗi thường gặp

| Hiện tượng | Kiểm tra |
|---|---|
| Màn hình chưa cấu hình | Cả năm giá trị Firebase/Supabase trong local.properties; dựng lại APK sau khi sửa |
| UNAUTHORIZED | Firebase Project ID của app và Edge giống nhau; giờ thiết bị; middleware nhận Firebase ID token, không phải Supabase token |
| UNVERIFIED | Bấm link xác minh email rồi chọn kiểm tra lại trong app |
| NOT_INVITED | Email lời mời khớp email Firebase đã xác minh; nhóm trưởng phải được seed bằng UID thật |
| FORBIDDEN | Vai trò, active và người đại diện hiện tại; không đổi quyền bằng dữ liệu client |
| Push không tới | Quyền thông báo, Play Services, thiết bị còn đăng nhập, service account, FCM API, cron và outbox attempts |
| Lỗi build JDK/SDK | JDK 21, SDK platform 35 và Build Tools 35.0.0; dùng Gradle Wrapper của repo |
| INSTALL_FAILED_UPDATE_INCOMPATIBLE | Khóa ký khác bản đã cài; dùng lại khóa cũ để giữ dữ liệu khi nâng cấp |
