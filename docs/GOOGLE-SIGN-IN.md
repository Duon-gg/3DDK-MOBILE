# Đăng nhập Google cho 3DDK

## Trạng thái

Mã ứng dụng đã có nút **Tiếp tục với Google** dùng Android Credential Manager và Firebase Authentication. Google trả về thông tin xác thực cho Firebase SDK; API của 3DDK tiếp tục nhận Firebase ID token và kiểm tra tài khoản như trước. Không gửi Google token trực tiếp tới Supabase và không lưu token vào Room hoặc tùy chọn đăng nhập.

Ngày 31/10/2026, cấu hình `app/google-services.json` trên máy chưa có OAuth client, và `local.properties` chưa có `FIREBASE_WEB_CLIENT_ID`. Chưa thể kiểm thử đăng nhập Google thật. Khi thiếu cấu hình, nút Google giải thích ngắn và người dùng vẫn đăng nhập bằng email được.

## Cấu hình cần hoàn tất một lần

1. Trong Firebase project `ddk-tasks`, mở **Authentication → Sign-in method → Google**, bật nhà cung cấp, chọn email hỗ trợ rồi lưu.
2. Vào **Project settings → General → ứng dụng Android `com.threeddk.tasks`**. Thêm SHA-1 của khóa ký APK dùng kiểm thử. Với cả debug và release, thêm dấu vân tay của cả hai khóa; không cần gửi hoặc thay keystore.
3. Tải lại `google-services.json` sau khi bật Google. Thông tin OAuth cần có Web application client (`client_type: 3`); dùng client ID này, không dùng Android client ID hoặc client secret.
4. Dự án hiện khởi tạo Firebase bằng `BuildConfig`, chưa áp dụng plugin Gradle `google-services`. Vì vậy chỉ thay JSON chưa đủ: điền Web client ID vào `local.properties` hoặc biến môi trường trước khi dựng APK:

   ```properties
   FIREBASE_WEB_CLIENT_ID=<Web application client ID kết thúc bằng .apps.googleusercontent.com>
   ```

   Nếu sau này dự án dùng plugin Google Services và có tài nguyên `default_web_client_id`, ứng dụng đọc tài nguyên đó trước. Không thêm client secret/service-account key vào ứng dụng hoặc Git.

5. Dựng và cài APK với đúng khóa SHA-1 đã đăng ký. Máy ảo cần bản có Google Play Services và người dùng tự chọn/đăng nhập tài khoản Google.

Web client ID là mã định danh công khai; chức năng vẫn cần cấu hình đúng Firebase và chữ ký ứng dụng. Không cần tạo service account mới, đổi Supabase secrets hoặc sửa database để thêm nhà cung cấp đăng nhập.

### Dấu vân tay đã đối chiếu từ APK ngày 31/10/2026

| Bản ký | SHA-1 công khai |
|---|---|
| Debug trên máy hiện tại | `6E:BD:15:2A:A4:91:52:2A:86:F1:AF:C8:8F:3B:E3:4B:8F:49:F7:E8` |
| Release của nhóm | `0A:7E:39:2E:85:0D:AF:2A:36:B9:5D:B5:50:DC:D0:7B:56:66:44:C1` |

Đã đọc bằng `apksigner verify --print-certs` từ APK 0.3.0 lưu trong `artifacts`; bảng SHA trên Firebase lúc kiểm tra còn trống. Nếu đổi khóa ký hoặc dùng máy phát triển khác, phải kiểm tra lại dấu vân tay của APK đó. Không đưa keystore hoặc mật khẩu ký lên Firebase/chat.

## Thao tác cần kiểm tra thật

| Trường hợp | Kết quả cần thấy |
|---|---|
| Tài khoản Google dùng lần đầu | Firebase tạo tài khoản; vào tab Không gian sau khi API chấp nhận phiên. |
| Đăng xuất rồi đăng nhập Google lại | Mở đúng không gian cũ theo Firebase UID, không tạo bản sao dữ liệu. |
| Hủy chọn tài khoản | Giữ màn hình đăng nhập, không hiện thông báo lỗi. |
| Thiết bị không có tài khoản/dịch vụ phù hợp | Thông báo dễ hiểu; email/mật khẩu vẫn dùng được. |
| Google chưa được bật hoặc cấu hình sai | Không vào dữ liệu nhóm; báo lỗi, cho thử lại. |
| Email đã dùng phương thức khác | Nếu Firebase báo xung đột nhà cung cấp, hướng dẫn dùng cách đăng nhập cũ/đặt lại mật khẩu; không xóa hay tự tạo tài khoản thay thế. |
| Mất mạng sau khi xác thực | Giữ cơ chế cache/nháp và phân quyền hiện có; không coi việc chọn Google là bằng chứng API đã hoạt động. |
| Xoay màn hình hoặc thoát trong lúc chọn Google | Không giữ Activity cũ hay tự gửi lại thông tin; có thể bấm Google lại. |

Ứng dụng không tự gộp các tài khoản Firebase khác UID, kể cả có tên/email giống nhau. Không thực hiện liên kết tài khoản bằng cách đoán email.

Đăng xuất dùng luồng xóa cache/nháp hiện tại và gọi `clearCredentialState` để bỏ ưu tiên phiên Google đã chọn. Nếu nhà cung cấp không xóa được trạng thái, ứng dụng vẫn đã đăng xuất Firebase và nhắc kiểm tra tài khoản ở lần tiếp theo.

## Nguồn chính thức

- [Firebase: Google Sign-in trên Android](https://firebase.google.com/docs/auth/android/google-signin).
- [Android: triển khai Credential Manager và nút Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).
- [Quy định hiển thị nút Google](https://developers.google.com/identity/branding-guidelines). Biểu tượng dùng tệp gốc của Google tại `https://developers.google.com/static/identity/images/g-logo.png`.

Giữ Firebase BoM hiện có; thêm Credential Manager `1.3.0` và `googleid:1.1.1` theo ví dụ tích hợp Firebase, phù hợp `minSdk 24` và `compileSdk 35`. Không nâng sang thư viện alpha chỉ để thêm chức năng này.
