# Đăng nhập Google cho 3DDK

Bản giao diện mới là `0.4.0-mobile-preview`; mã đăng nhập/cấu hình Google được giữ nguyên. Kết quả ngày 31/10 bên dưới giữ đúng phiên bản đã chạy. Trạng thái kiểm thử của bản mobile, gồm bước đăng nhập thật còn chờ, nằm trong [nhật ký 01/11](PROGRESS-2026-11-01.md).

## Trạng thái

Mã ứng dụng đã có nút **Tiếp tục với Google** dùng Android Credential Manager và Firebase Authentication. Google trả về thông tin xác thực cho Firebase SDK; API của 3DDK tiếp tục nhận Firebase ID token và kiểm tra tài khoản như trước. Không gửi Google token trực tiếp tới Supabase và không lưu token vào Room hoặc tùy chọn đăng nhập.

Trong lượt cấu hình ngày 31/10/2026, đã bật và lưu nhà cung cấp Google trên Firebase project `ddk-tasks`, thêm SHA-1 của bản debug và release, rồi điền Web client ID vào `local.properties` được Git bỏ qua. Web client ID được đối chiếu cùng project number `1073994652496`.

`app/google-services.json` cũ chưa được thay do chưa tải được bản mới. Điều này không thay đổi cách khởi tạo Firebase hiện tại: ứng dụng đọc `BuildConfig`, còn Google đọc `FIREBASE_WEB_CLIENT_ID`. Không cần áp dụng thêm plugin Gradle chỉ để dùng giá trị đã cấu hình. Nếu thiếu Web client ID ở máy dựng khác, nút Google giải thích ngắn và email/mật khẩu vẫn dùng được.

Bản hiện tại là `0.3.3-google-preview` (`versionCode 6`). Lượt dựng trước khi sửa nhãn trạng thái đạt sáu tác vụ Gradle sau 9 phút 2 giây, 153 tác vụ (58 thực thi, 95 đã có kết quả); 22 unit test đạt, lint 0 lỗi/105 cảnh báo, APK/AAB ký hợp lệ. Log giữ riêng ở `artifacts/2026-10-31/google-auth/android-build-0.3.3-before-status-fix.log`. Lượt API 35 thông thường trước sửa nhãn có **10 bài đạt/3 bài bỏ qua trong 13,406 giây**, gồm bài hồi quy điều hướng phiên đăng nhập. Lượt dựng cuối sau sửa nhãn đang chạy, chưa có kết quả tại thời điểm cập nhật này.

Trên signed release 0.3.2 đã tái hiện lỗi: Firebase đã đăng xuất nhưng màn hình Profile vẫn hiện tên, email và avatar. Nguyên nhân là việc đặt lại cùng navigation graph không loại bỏ màn hình riêng tư khỏi back stack. Bản 0.3.3 điều hướng rõ về Auth và xóa stack cũ; `SessionNavigationTest` đi từ RED ở 0.3.2 sang GREEN ở 0.3.3. Test này kiểm tra điều hướng bằng trạng thái UI trong bộ nhớ, không tự đăng xuất Firebase hoặc xóa dữ liệu người dùng.

APK release 0.3.3 đã ký/thu gọn và cài nâng cấp bằng `-r`. Thử trực tiếp trên máy ảo đã đạt: đăng xuất từ Profile về Auth không còn thông tin cá nhân; hủy cửa sổ Google bằng cử chỉ Back về Auth; bấm Google và tự chọn tài khoản đang có để đăng nhập lại, thấy đúng bốn không gian cũ; lưu một dòng kiểm thử và thấy dòng đó trong Grid. Sau lưu, nhãn “đang tải” vẫn còn dù Grid đã hiển thị dữ liệu. Đã sửa cách cập nhật nhãn trong `DataTableFragment`, không đổi backend; chưa có kết quả kiểm tra trực tiếp trên APK dựng lại sau sửa này.

Lượt tự động hủy Google đầu tiên trên 0.3.3 dùng test cũ bị kẹt và phải force-stop, kết quả **INCONCLUSIVE**, không tính là PASSED. Test đã được sửa để chờ cửa sổ tương tác của Google, giới hạn chờ 60 giây; test APK/lint dựng đạt trong 2 phút 6 giây. Lượt chạy lại đã **PASSED trong 7,672 giây**, log `google-picker-cancel-0.3.3-retry.log`; giữ cả log lượt bị dừng. Kết quả hủy thủ công trên release và kết quả tự động vẫn được ghi riêng.

Bằng chứng debug 0.3.2 vẫn giữ nguyên theo phiên bản: hủy Google chạy riêng đạt; sau khi người dùng tự đăng nhập, SDK xác nhận Google/email đã xác minh và API chấp nhận phiên. Hai live test công việc cùng phiên Google đạt tạo bảng, replay không trùng, chống ghi đè/cache và nháp qua xoay màn hình rồi lưu. Mỗi bài dùng không gian kiểm thử riêng; hai không gian được giữ lại. Không dùng các kết quả 0.3.2 để coi mọi live test đã chạy lại trên 0.3.3.

## Quy trình cấu hình cho máy dựng khác

1. Trong Firebase project `ddk-tasks`, mở **Authentication → Sign-in method → Google**, bật nhà cung cấp, chọn email hỗ trợ rồi lưu.
2. Vào **Project settings → General → ứng dụng Android `com.threeddk.tasks`**. Thêm SHA-1 của khóa ký APK dùng kiểm thử. Với cả debug và release, thêm dấu vân tay của cả hai khóa; không cần gửi hoặc thay keystore.
3. Tải lại `google-services.json` sau khi bật Google. Thông tin OAuth cần có Web application client (`client_type: 3`); dùng client ID này, không dùng Android client ID hoặc client secret. Nếu chưa tải được JSON, có thể lấy Web client ID từ phần **Web SDK configuration** của nhà cung cấp Google trong Firebase; đối chiếu đúng project trước khi dùng.
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

Đã đọc bằng `apksigner verify --print-certs` từ APK 0.3.0 lưu trong `artifacts`; cả hai SHA-1 trên đã được lưu vào ứng dụng Android trên Firebase trong lượt cấu hình này. Nếu đổi khóa ký hoặc dùng máy phát triển khác, phải kiểm tra lại dấu vân tay của APK đó. Không đưa keystore hoặc mật khẩu ký lên Firebase/chat.

## Chạy bốn trường hợp kiểm thử Google

Cách cài test APK, lệnh chạy từng trường hợp và cách phân biệt kết quả `SKIPPED`/đã chạy nằm trong [TESTING.md — Đăng nhập Google](TESTING.md#đăng-nhập-google). Các test không nhập mật khẩu, chọn tài khoản hoặc đăng nhập thay người dùng.

| Test | Điều kiện | Kết quả có phiên bản |
|---|---|---|
| `missingGoogleConfigurationKeepsEmailLoginAvailableAfterRecreation` | Bản dựng không có Web client ID; đang đăng xuất | SKIPPED trên 0.3.2 vì đã có Web client ID; đã đạt ở bản 0.3.1 thiếu cấu hình. |
| `configuredGoogleButtonAndEmailLoginSurviveRecreation` | Có Web client ID; đang đăng xuất | PASSED trên API 35, APK 0.3.2. |
| `cancelRealGooglePickerKeepsEmailLoginAvailable` | Có Web client ID; đang đăng xuất; `liveGooglePicker=true`; dịch vụ Google hoạt động | Debug 0.3.2 PASSED khi chạy lại, 7,613 giây, sau lỗi driver Espresso Back ban đầu. 0.3.3 có lượt đầu INCONCLUSIVE do bị kẹt/dừng; sửa test chờ có giới hạn rồi chạy lại PASSED, 7,672 giây. Hủy thủ công trên release 0.3.3 đạt riêng. |
| `manuallySignedInGoogleSessionIsAcceptedByBackend` | Người dùng đã tự đăng nhập Google; `liveGoogleLogin=true`; có mạng | Debug 0.3.2 PASSED, 2,79 giây: SDK xác nhận `signInProvider=google.com`, email đã xác minh và API trả capabilities P0 có `atomicTemplates`; chưa chạy lại test này trên 0.3.3. |

Không tính một test bị bỏ qua là đã kiểm chứng chức năng đó. Bài kiểm tra capabilities chỉ đọc dữ liệu khả năng API, không tự tạo không gian hoặc bảng. Các bài thử công việc thật đã chạy riêng bằng phiên Google: `WorkspaceLiveTest` đạt trong 22,576 giây và `WorkspaceEditLiveTest` đạt trong 31,7 giây.

Log nằm trong `artifacts/2026-10-31/google-auth/`: `google-picker-cancel.log` ghi lỗi driver ban đầu; `google-picker-cancel-retry.log`, `google-session-api.log`, `workspace-api-google.log` và `workspace-ui-google.log` ghi từng lượt đạt của debug 0.3.2. `session-navigation-red.log` giữ lỗi 0.3.2; `instrumentation-normal-0.3.3.log` ghi GREEN điều hướng ở 0.3.3; `google-picker-cancel-0.3.3.log` là lượt không có kết luận, còn `google-picker-cancel-0.3.3-retry.log` ghi lượt chạy lại đạt. Log giữ trên máy phát triển, không chứa token hoặc mật khẩu.

Ảnh kiểm tra trực tiếp release 0.3.3 ở cùng thư mục: `release-logout-fixed-0.3.3.png`, `release-google-cancel-0.3.3.png`, `release-google-login-0.3.3.png`, `release-row-saved-0.3.3.png`. Không dùng việc tạo được ảnh chụp để thay thế mọi trường hợp nghiệm thu còn lại.

Chưa nghiệm thu cộng tác hai tài khoản, tải 100 người/20 người cùng thao tác, API 24 trong đợt này, điện thoại thật, thông báo bảng v2 hoặc rò rỉ bộ nhớ. Các thao tác release nêu trên đã thử trực tiếp trên máy ảo; vẫn cần thử tài khoản email đã có, đổi sang tài khoản khác và các tình huống lỗi/mất mạng.

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

Với 0.3.2, không coi chỉ `FirebaseAuth.currentUser == null` là đủ: lỗi điều hướng đã để Profile cũ tiếp tục hiển thị. Bản 0.3.3 bổ sung xóa back stack khi về Auth; cần giữ bài hồi quy này trong các lần cập nhật tiếp theo.

## Nguồn chính thức

- [Firebase: Google Sign-in trên Android](https://firebase.google.com/docs/auth/android/google-signin).
- [Android: triển khai Credential Manager và nút Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).
- [Quy định hiển thị nút Google](https://developers.google.com/identity/branding-guidelines). Biểu tượng dùng tệp gốc của Google tại `https://developers.google.com/static/identity/images/g-logo.png`.

Giữ Firebase BoM hiện có; thêm Credential Manager `1.3.0` và `googleid:1.1.1` theo ví dụ tích hợp Firebase, phù hợp `minSdk 24` và `compileSdk 35`. Không nâng sang thư viện alpha chỉ để thêm chức năng này.
