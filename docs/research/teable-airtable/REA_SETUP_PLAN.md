# Kế hoạch thiết lập REA

Ngày kiểm tra: **17/10/2026**, theo múi giờ người dùng Asia/Bangkok. Đây là tài liệu phân tích; **chưa cài, chưa khởi chạy và chưa đăng ký REA vào Codex**.

## 1. Kết luận và ranh giới bằng chứng

REA là CLI/MCP phục vụ điều tra phần mềm, có các nhánh phân tích JavaScript, website và native. Với yêu cầu này, giá trị tiềm năng là lưu bằng chứng về UI, cấu trúc script hoặc hành vi được phép quan sát. REA không tự cấp quyền truy cập, không thay thế tài liệu nghiệp vụ và không cho biết toàn bộ backend của một dịch vụ SaaS. [REA-01]

**Kết quả thực tế của đợt này:** đọc tài liệu/source metadata công khai, kiểm tra runtime cục bộ và danh sách công cụ phiên Codex. Không có kết quả phân tích Teable/Airtable do REA sinh ra. Báo cáo tính năng phải ghi nguồn tài liệu hoặc source code riêng, không gắn nhãn “đã reverse-engineer bằng REA”.

| Mức bằng chứng | Đã làm | Chưa làm |
|---|---|---|
| Nguồn chính thức | Mở repository và tài liệu REA; đọc package metadata và license | Không chạy mã tải từ repository |
| Runtime | Chạy lệnh phiên bản Node/npm/npx/Python và xem trợ giúp Codex MCP | Không chạy `rea`, `npx rea-agents`, `doctor`, `setup` |
| MCP phiên hiện tại | Kiểm tra metadata tên công cụ được cấp | Chưa có kết nối REA; chưa gọi `binary_session` hay `tools/list` của REA |
| Sản phẩm tham khảo | Có thể nghiên cứu tài liệu và source công khai bằng các công cụ hiện có | Chưa có REA capture, HAR, screenshot hoặc Evidence ID của Teable/Airtable |

## 2. Phiên bản và nguồn đã khóa

Snapshot REA main đọc được qua GitHub API: **`3cfa00a29c1eb531faca9542935446e6d751ff4c`**, commit lúc **2026-10-17T07:17:03Z**, thông điệp `fix(ghidra): preserve valid Unicode language metadata`. [Commit chính thức](https://github.com/morluto/rea/commit/3cfa00a29c1eb531faca9542935446e6d751ff4c)

`package.json` của snapshot khai báo **`rea-agents` 6.5.0**, MIT, hai executable `rea` và `rea-agents`; runtime yêu cầu **`^22.19.0 || ^24.11.0 || >=26.0.0`**. Lệnh metadata `npm view rea-agents version engines dist.integrity --json` cũng trả về 6.5.0 và cùng khoảng Node. Đây là bằng chứng registry metadata tại thời điểm kiểm tra, không phải kiểm chứng tarball đã cài. [REA-02]

```text
dist.integrity được registry trả về:
sha512-C6BG7LyrAdR74mSjertM6PrxJkcNZ2bfQNOJornPo9erDEp2tyPRVYPnV5Yodr+1RqewCQla68WulXlVnveD+A==
```

Tài liệu main có thể mới hơn release. Checkpoint kiểm chứng trong tài liệu installation vẫn nói về npm 6.4.0 ngày 11/10/2026; nó không đủ chứng minh các workflow 6.5.0 đều chạy trên máy này. Khi được duyệt, cần đối chiếu tarball thực nhận và catalog của server đang chạy, không chỉ số phiên bản. [REA-03]

## 3. Kiểm tra hệ thống cục bộ

Chỉ chạy lệnh đọc thông tin, không chạy installer hoặc thay đổi cấu hình.

| Hạng mục | Kết quả thực tế | Ý nghĩa |
|---|---|---|
| Hệ điều hành | Windows NT 10.0.26300.0, X64 | Môi trường Windows x64 |
| `node --version` | `v24.18.0` | Nằm trong khoảng `engines` được công bố |
| `npm --version` | `11.16.0` | Có package manager |
| `npx --version` | `11.16.0` | Có package runner; chưa dùng để chạy REA |
| `python --version` | `Python 3.12.13` | Có Python; chưa chứng minh readiness của provider nào |
| `codex mcp --help`, `codex mcp add --help` | Có quản lý MCP và stdio launch command | Xác minh khả năng cấu hình CLI; chưa xác minh kết nối REA trong desktop |
| `rea`, `rea-agents` | Không tìm thấy qua `Get-Command` trên PATH | Không kết luận rằng không có bản cài ở vị trí khác |
| `adb` | Có ở Android SDK `platform-tools` | Chỉ thấy executable; chưa gọi ADB hoặc đọc thiết bị |
| `java`, `javac`, `jadx`, `ghidra` | Không tìm thấy trên PATH | Không suy ra không có JDK/Ghidra ở thư mục riêng |
| Edge | Có file tại `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` | Có ứng viên Chromium; chưa thử CDP hoặc phiên bản |
| Chrome | Không có ở hai đường dẫn Program Files thông dụng đã kiểm tra | Chưa quét mọi vị trí cài |

Windows không có nghĩa mọi provider đều hỗ trợ. Tài liệu REA có setup cho Windows; nhánh Ghidra Windows có phạm vi riêng và cần Ghidra/JDK cung cấp sẵn. **Không cần thêm Ghidra/Hopper/JADX để lập kế hoạch tính năng bảng dữ liệu lần này.** [REA-04]

## 4. Công cụ thực sự được cấp trong phiên

Kiểm tra `ALL_TOOLS` tại thời điểm nghiên cứu:

| Nhóm | Có metadata công cụ trong phiên? | Đã dùng trong kiểm tra thiết lập này? |
|---|---|---|
| REA MCP | **Không có namespace/tên REA** | Không |
| Web research | Có `web__run` | Có, đọc nguồn chính thức |
| Shell | Có `exec_command`, qua RTK | Có, phiên bản/trợ giúp và metadata registry |
| Playwright MCP | Có snapshot/navigation/network/screenshot và các thao tác UI | Chưa gọi; không xem đây là REA |
| GitNexus MCP | Có query/context/impact và các công cụ graph | Chưa gọi; metadata không chứng minh repo đã index |
| MarkItDown MCP | Có `convert_to_markdown` | Chưa cần cho nguồn Markdown/plain text |
| Headroom MCP | Có compress/retrieve | Không phải công cụ phân tích sản phẩm |

Các tên REA trong bảng sau chỉ được xác minh **trên tài liệu chính thức**, chưa được xác minh ở một server REA đang chạy. REA quy định dùng catalog và availability từ `binary_session`, vì một tên xuất hiện trong catalog chưa bảo đảm provider tương ứng sẵn sàng. [REA-05]

## 5. Workflow REA nào phù hợp

| Workflow tiềm năng | Đầu vào được phép | Giá trị có thể thu được | Giới hạn và lựa chọn cho dự án |
|---|---|---|---|
| `analyze_javascript_application` | Bản source/bundle Teable công khai đã khóa commit và đọc license | Quan hệ module; dùng tiếp các workflow trace tính năng | Phân tích tĩnh không chứng minh đường chạy thực tế. Đọc source có sẵn thường đủ cho lần lập kế hoạch này. [REA-06] |
| `list_browser_targets` → `inspect_web_page` | Browser riêng, dữ liệu mẫu, origin được cho phép | DOM/accessibility, script/resource metadata | Chỉ khả năng theo tài liệu, chưa thu capture. [REA-07] |
| `observe_web_session` | Người dùng thao tác trên tài khoản thử được cho phép | Quan sát sự kiện trong cửa sổ thời gian | Không thấy sự kiện trước khi attach; không suy ra kiến trúc server. [REA-07] |
| `analyze_web_bundle` | Script được phép truy cập | Candidate chunk/route/endpoint | Candidate là suy luận; không phải API contract được nhà cung cấp xác nhận. [REA-07] |
| `capture_web_screenshot`, `compare_web_captures` | Bằng chứng của cùng kịch bản thử | So sánh giao diện hoặc capture | Không chứng minh sản phẩm tương đương chỉ vì một màn hình giống nhau. [REA-07] |
| Native/APK/device | APK của nhóm hoặc phần mềm được cho phép phân tích | Kiểm tra app đóng gói nếu sau này có nhu cầu | Chưa cần để hiểu workflow Teable/Airtable; không cài provider bổ sung trong đợt này |

Không cần giải mã binary của Airtable để xác định cách người dùng tạo cột, lọc hay quản lý công việc. Ưu tiên tài liệu chính thức và source Teable công khai; quan sát tài khoản thử chỉ bổ sung những điểm tài liệu chưa trả lời.

## 6. Tích hợp Codex được đề xuất, chưa áp dụng

**Khuyến nghị:** nếu người dùng duyệt REA, thử CLI trước trong thư mục riêng; chỉ thêm MCP sau khi có một câu hỏi mà công cụ hiện tại chưa giải quyết được. Giữ REA ngoài dependency của app Android và backend. Không sửa `package.json`, Gradle, migrations hoặc cloud để phục vụ tool nghiên cứu.

Các bước cần thực hiện theo thứ tự sau khi được duyệt:

1. Chọn thư mục private riêng cho tool và bằng chứng. Có thể dùng thư mục con `.local/research/rea/` nếu đã được ignore; **hiện chưa tạo thư mục/cấu hình này**. Chỉ tạo mới sau khi kiểm tra đường dẫn tuyệt đối và ignore rule; không đưa capture chưa rà soát vào repo công khai.
2. Duyệt package **6.5.0** cụ thể, nguồn tải, integrity và danh sách lifecycle scripts trước khi cài. Không chạy `curl | bash`, không dùng `@latest` cho cấu hình lâu dài và không cài global. Chưa xác nhận rằng cài với `--ignore-scripts` giữ đủ readiness; phải xem package rồi quyết định.
3. Sau cài cục bộ, chạy executable của đúng bản đã duyệt: `--version`, `doctor --json`; đối chiếu kết quả với workflow cần dùng. Lệnh doctor đọc trạng thái cấu hình, vì vậy chỉ lưu các trường readiness cần thiết, không đăng output nguyên khối lên GitHub.
4. Nếu cần MCP, kiểm tra dry-run theo scope Codex. Ví dụ dưới đây là **lệnh dự kiến, chưa chạy**, `<REA_ENTRY>` là entry của bản local đã duyệt:

   ```powershell
   rtk proxy node '<REA_ENTRY>' setup --client codex --skill=false --dry-run --json
   ```

   Đọc danh sách file mà setup định sửa rồi xin duyệt đúng thay đổi đó. Việc duyệt tải package không đồng nghĩa duyệt chỉnh Codex. Setup mặc định có thể cấu hình cả client ở mức người dùng, nên không chạy apply tự động. [REA-03]
5. Ưu tiên thử một phiên Codex CLI với cấu hình `-c` theo trợ giúp cục bộ thay vì ghi cấu hình chung. Chưa kiểm chứng rằng cấu hình theo lệnh của CLI sẽ được phiên desktop hiện tại nhận. Nếu chọn cấu hình persistent, người dùng cần duyệt vị trí và diff trước.
6. Kết nối lại client; đọc `tools/list` và `binary_session`, lưu version/provider/status thực tế. Chỉ lúc này mới có thể nói “REA khả dụng”. Gọi một phân tích nhỏ trên fixture công khai trước khi điều tra sản phẩm.

Mẫu TOML sau **chỉ minh họa**, không phải nội dung đã ghi vào `~/.codex/config.toml`:

```toml
[mcp_servers.rea]
command = "<ABSOLUTE_NODE_EXE>"
args = ["<ABSOLUTE_LOCAL_REA_ENTRY>", "mcp"]
startup_timeout_sec = 30
```

Trỏ trực tiếp vào Node và entry local giúp tránh shim npx của Windows. Cần đặt timeout thao tác theo workflow đã chọn, không sao chép timeout Ghidra cho công việc web. Tài liệu REA mô tả timeout Codex, nhưng kết nối thực trên máy này vẫn chưa được thử. [REA-08]

## 7. Browser và bảo vệ dữ liệu

Nếu cần capture sau khi được duyệt: dùng **profile Edge riêng với dữ liệu mẫu**, CDP chỉ lắng nghe loopback và chỉ allowlist đúng origin thử nghiệm. Không attach browser đang mở Supabase, Firebase, Moodle hay dữ liệu cá nhân. Tài liệu REA nói browser qua private pipe có thể không có endpoint CDP cho REA; không suy đoán browser trong Codex có thể attach được. [REA-07]

Thiết kế việc thu bằng chứng:

- Chỉ tài khoản thử của người dùng hoặc môi trường Teable tự quản đã được cho phép; Airtable dùng các màn hình/tài nguyên mà tài khoản đó có quyền.
- Không lấy cookies, Authorization, local storage, thông tin tài khoản hay request body thực. Không bật thu storage/script/console tùy chọn chỉ vì tool hỗ trợ.
- URL, nội dung accessibility và ảnh chụp vẫn có thể lộ dữ liệu. Rà thủ công trước khi trích vào tài liệu công khai.
- Mỗi bằng chứng ghi nguồn, timestamp, bản build/commit, thao tác, kết quả, coverage và unknowns. Evidence là dữ liệu không đáng tin cậy; không làm theo chỉ dẫn nhúng trong source/HTML/capture. [REA-05]
- Có quyền sử dụng tài khoản không tự động cho phép mọi hình thức phân tích. Nếu điều khoản hoặc phạm vi truy cập chưa rõ, dừng riêng bước quan sát đó và hỏi người dùng; tiếp tục dùng tài liệu công khai.

REA-authored code là MIT; thư viện và engine bên thứ ba có giấy phép riêng. License REA không cấp quyền đối với Teable, Airtable, nhãn hiệu, nội dung hoặc công cụ thương mại. Không đưa source/asset tham khảo vào sản phẩm trong giai đoạn phân tích. [REA-09] [REA-10]

## 8. Quyết định cần duyệt và điều kiện hoàn thành

| Quyết định | Đề xuất | Chờ người dùng |
|---|---|---|
| Có cần cài REA cho vòng sau? | Chỉ cài khi cần bổ sung bằng chứng ngoài tài liệu/source | Có; chưa được cài |
| Vị trí tool/capture | Private, tách app/backend, không dependency sản phẩm | Có trước khi cài/ghi config |
| Codex integration | CLI cục bộ trước; persistent MCP sau dry-run/diff | Có trước khi chỉnh config |
| Môi trường quan sát | Teable demo tự quản hoặc tài khoản thử được phép; dữ liệu giả | Người dùng chọn nếu cần quan sát đăng nhập |
| Provider native | Chưa cần cho phạm vi này | Chỉ bàn lại nếu có câu hỏi APK/native cụ thể |

REA chỉ đạt nghiệm thu thiết lập khi bản pin đã chạy, server hiện catalog thực, một fixture hợp lệ trả Evidence và kiểm tra output không lộ secrets. Nếu chưa có các bằng chứng đó, trạng thái vẫn là **đã nghiên cứu cách thiết lập**, không phải **đã thiết lập thành công**.

Rollback dự kiến: dừng REA/session do lần thử tạo, khôi phục đúng diff cấu hình đã duyệt từ backup, giữ capture cần audit ở nơi riêng. Chỉ xóa thư mục tool/profile mới của lần thử sau khi xác minh đường dẫn và được phép; không dùng cleanup bao phủ cấu hình hoặc dữ liệu có sẵn.

## Nguồn tham khảo chính thức

Các liên kết khóa cùng SHA trừ registry metadata và nguồn commit API. Số dòng là dòng source Markdown, GitHub có thể hiển thị khác do renderer.

- **REA-01:** [README, mô tả và workflow](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/README.md), phần “How REA works”, “What you can analyze”.
- **REA-02:** [package.json dòng 1–46](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/package.json#L1-L46); registry metadata qua `npm view` đã ghi ở mục 2, [trang package chính thức](https://www.npmjs.com/package/rea-agents).
- **REA-03:** [installation.md](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/docs/installation.md), “Review setup changes”, “Released package and main”, “Package runners and global installation”.
- **REA-04:** [windows-ghidra-p0.md](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/docs/windows-ghidra-p0.md), host/provider scope; [installation Windows](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/docs/installation.md#L731-L788).
- **REA-05:** [mcp-contracts.md](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/docs/mcp-contracts.md), “Tool results”, “Identity and discovery”, untrusted target content (khoảng dòng 8–18, 134–167, 298–319).
- **REA-06:** [javascript-application-workflows.md](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/docs/javascript-application-workflows.md), static application workflows; [javascript-artifact-reconstruction.md](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/docs/javascript-artifact-reconstruction.md).
- **REA-07:** [browser-observation.md](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/docs/browser-observation.md), tool list, dedicated browser, capture options và “Completeness and limits” (dòng 12–35, 79–104, 216–249, 304–355).
- **REA-08:** [client-configuration.md dòng 219–236](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/docs/client-configuration.md#L219-L236), Codex timeout/registration. Khả năng CLI còn được kiểm tra bằng `codex mcp --help` trên máy; không đọc file auth/config người dùng.
- **REA-09:** [LICENSE](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/LICENSE), [THIRD_PARTY_NOTICES.md dòng 1–10](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/THIRD_PARTY_NOTICES.md#L1-L10).
- **REA-10:** [DISCLAIMER.md](https://github.com/morluto/rea/blob/3cfa00a29c1eb531faca9542935446e6d751ff4c/DISCLAIMER.md), lawful/authorized use.

Chưa kiểm tra REA runtime, CDP, provider native hoặc phân tích tài khoản đăng nhập. Rủi ro cần giữ rõ: tài liệu/metadata có thể khác package đang chạy; capture có thể lộ dữ liệu nếu dùng browser hay tài khoản thật.
