# Hướng dẫn cấu hình môi trường local — PhoneNest

Áp dụng từ Giai đoạn 2B.7B/2B.7C. Tài liệu **không** chứa mật khẩu, JWT secret hay API key thật.
Mọi giá trị trong `<...>` là chỗ để mỗi thành viên tự điền trên máy mình.

## 1. Công cụ cần có

| Công cụ | Phiên bản đã kiểm chứng | Ghi chú |
|---|---|---|
| JDK | 21 trở lên (máy kiểm thử: JDK 22) | `java -version` |
| Maven | 3.9.x | Repo **không** có Maven wrapper. Có thể dùng Maven đi kèm IntelliJ: `<IntelliJ>/plugins/maven/lib/maven3/bin/mvn` |
| Node.js / npm | Node 22.x / npm 10.x | `node -v`, `npm -v` |
| MariaDB | 12.x (máy kiểm thử: 12.3, cổng 3307) | Code mới cần cột của Migration 15 + 16 |
| Google Chrome | bản cài sẵn trên máy | Chỉ cần cho E2E (Playwright dùng `channel: 'chrome'`, không tải trình duyệt) |
| Ollama + `bge-m3` | tùy chọn | Thiếu Ollama thì tìm kiếm ngữ nghĩa tự rơi về tìm theo từ khóa |

## 2. Database

1. **Không bao giờ** trỏ ứng dụng, test hay E2E vào database chính `bandienthoai` khi chưa được phê duyệt.
2. Code hiện tại (entity `PhienBanSanPham`) cần các cột của **Migration 15 và 16**. Database chưa có
   các cột này → ứng dụng lỗi khi đọc/ghi biến thể. Xem `docs/DANH_GIA_TRIEN_KHAI_M12_M16.md`.
3. Để làm việc hằng ngày, dùng một database riêng trên máy mình (ví dụ `phonenest_dev_<ten>`), nạp từ
   bản dump được nhóm cung cấp rồi chạy migration theo thứ tự:

```bash
mariadb -h 127.0.0.1 -P 3307 -u <user> -p -e "CREATE DATABASE phonenest_dev_<ten> CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mariadb -h 127.0.0.1 -P 3307 -u <user> -p -D phonenest_dev_<ten> < <dump>.sql
mariadb -h 127.0.0.1 -P 3307 -u <user> -p -D phonenest_dev_<ten> < DB/migrations/15_add_sales_approval_status.sql
mariadb -h 127.0.0.1 -P 3307 -u <user> -p -D phonenest_dev_<ten> < DB/migrations/16_add_approval_verification_data.sql
```

Kết quả dòng đầu tiên của mỗi migration: `COMPLETED`, `ALREADY_COMPLETED`, `SKIPPED_EXISTING_DATA`,
`REFUSED*` hoặc `INCONSISTENT_SCHEMA`. Gặp `REFUSED*`/`INCONSISTENT_SCHEMA`/`FAILED` thì **dừng lại và báo nhóm**,
không tự sửa schema.

**Kiểm tra schema khi khởi động (2B.7D).** Backend đọc `information_schema` (chỉ đọc) trước khi mở cổng HTTP.
Thiếu cột/bảng của M15/M16, sai kiểu, thiếu giá trị ENUM, hoặc `pn_migration_log` không khớp schema → ứng dụng
**dừng** với thông báo `SCHEMA_CHECK FAILED` liệt kê từng thành phần. Ứng dụng không tự chạy migration, không sửa schema.
Kiểm tra độc lập (không mở web server):

```bash
java -jar BE/phonenest/target/phonenest-1.0.0.jar --spring.main.web-application-type=none --app.schema-check.only=true
# -> "SCHEMA_CHECK OK" và thoát 0, hoặc thoát lỗi kèm danh sách vấn đề
```

## 3. Biến môi trường / file cấu hình backend

Backend đọc cấu hình theo thứ tự: biến môi trường → `BE/phonenest/application-local.properties`
(đã `.gitignore`, chỉ nạp khi chạy với thư mục làm việc `BE/phonenest`) → giá trị mặc định trong
`application.properties`.

Cách nhanh nhất: sao chép mẫu rồi điền giá trị.

```bash
cd BE/phonenest
cp application-local.properties.example application-local.properties   # KHÔNG commit file này
```

| Khóa | Bắt buộc | Ý nghĩa |
|---|---|---|
| `DB_URL` | nên đặt | Mặc định `jdbc:mariadb://localhost:3306/BanDienThoai` (MySQL/MariaDB cổng 3306). Máy nhóm dùng MariaDB cổng **3307** → đặt rõ, ví dụ `jdbc:mariadb://127.0.0.1:3307/phonenest_dev_<ten>` |
| `DB_USERNAME` | nên đặt | Mặc định `root`. Nên dùng tài khoản riêng chỉ có quyền trên database dev của mình |
| `DB_PASSWORD` | **có** | Không còn giá trị mặc định trong repo |
| `JWT_SECRET` | **có** | Chuỗi ngẫu nhiên ≥ 32 ký tự, mỗi máy/môi trường một giá trị. Thiếu → ứng dụng **không khởi động** |
| `GEMINI_API_KEY` | **có** | Thiếu → không khởi động. Không cần chat thật thì đặt một giá trị giả (chat sẽ báo lỗi khi gọi) |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | không | Mặc định `http://127.0.0.1:11434`, `bge-m3` |
| `SPRING_MAIL_*`, `APP_MAIL_FROM` | không | Chỉ cần khi thử gửi OTP thật |
| `MOMO_*` | không | Chỉ cần khi thử thanh toán MoMo (IPN phải là URL HTTPS công khai) |
| `APPROVAL_SEPARATION_OF_DUTIES` | không | `true` = Admin vừa mở khóa biến thể không được tự phê duyệt nó. Mặc định `false` (hiện mới có một Admin) |

**Thử thanh toán MoMo sandbox.** Lấy `partnerCode`, `accessKey` và `secretKey` từ tài khoản merchant/test do MoMo cấp; không dùng khóa production và không gửi khóa vào chat hoặc commit vào Git. Điền các biến `MOMO_*` trong `application-local.properties.example` vào `application-local.properties`. Mặc định backend gọi endpoint sandbox; `MOMO_REDIRECT_URL` trỏ về FE, còn `MOMO_IPN_URL` phải là URL HTTPS công khai trỏ tới backend (localhost không nhận được IPN). Khi MoMo chuyển khách quay lại, FE hiển thị trạng thái chờ và tự kiểm tra IPN tối đa khoảng một phút; trạng thái thanh toán trong backend chỉ được xác nhận từ IPN có chữ ký hợp lệ, không dựa vào URL redirect.

**Tài khoản database.** Không dùng `root` cho ứng dụng, test hay E2E. Dùng tài khoản chỉ có `SELECT, INSERT, UPDATE, DELETE`
trên database của mình (xem `DB/admin/create_restricted_accounts_DRAFT.sql`). Toàn bộ test backend (334) và E2E (20) đã chạy
được bằng tài khoản như vậy. Ứng dụng dùng mức cô lập `READ COMMITTED` (cấu hình sẵn): tính đúng đắn dựa vào khóa dòng
`SELECT ... FOR UPDATE`; với MariaDB ≥ 11.6, mức `REPEATABLE READ` mặc định làm đơn hàng song song thất bại hàng loạt.

Tạo JWT secret ngẫu nhiên:

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
# hoặc PowerShell (dùng bộ sinh số ngẫu nhiên mật mã):
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

## 4. Chạy ứng dụng

```bash
# Backend (cổng 8080)
cd BE/phonenest
mvn spring-boot:run

# Frontend (cổng 5173, proxy /api và /uploads sang 8080)
cd FE
npm install
npm run dev
```

## 5. Kiểm thử

### 5.1 Unit test (không cần database)

```bash
cd BE/phonenest && mvn test          # test tích hợp tự bỏ qua khi không có PHONENEST_IT_DB_URL
cd FE && npm test                    # node --test tests/*.test.js
```

### 5.2 Integration test backend (cần clone dùng một lần)

Test tích hợp **ghi dữ liệu**. Chỉ chạy trên clone có tên `phonenest_it_*` (hoặc `claude_2b7x_*`) tại localhost;
`ItDatabaseGuard` chặn mọi tên khác **trước khi** tạo kết nối.

```bash
export PN_DB_PASSWORD=<mat_khau>                 # không in ra màn hình
export PN_MARIADB_BIN="<thu_muc_chua_mariadb>"   # nếu mariadb không nằm trong PATH
PN_IT_DB_NAME=phonenest_it_$(date +%Y%m%d%H%M%S) bash DB/tests/create_it_clone.sh <dump_da_co_M15>.sql
# script in ra lệnh export PHONENEST_IT_DB_URL=...
export PHONENEST_IT_DB_URL=jdbc:mariadb://127.0.0.1:3307/phonenest_it_<...>
export PHONENEST_IT_DB_USER=<tai_khoan_runtime_cua_clone>   # không dùng root
export PHONENEST_IT_DB_PASSWORD=<mat_khau>
cd BE/phonenest && mvn test
```

Mỗi lần chạy nên tạo clone mới từ cùng một dump để kết quả tái lập được. Clone không tự xóa; chỉ xóa khi nhóm đồng ý.

### 5.3 Kiểm thử migration 15/16

```bash
bash DB/tests/test_migrations_15_16.sh <dump_chua_M15>.sql <dump_da_co_M15_v1>.sql
```

Script tự tạo 4 clone `claude_2b7c_mig_*` (hoặc tiền tố `PN_CLONE_PREFIX=phonenest_it_mig_`), không kết nối `bandienthoai`.

Kiểm thử migration 12–14 (bản v2) và quyền database / kiểm tra schema khi khởi động:

```bash
bash DB/tests/test_migrations_12_14.sh <dump_truoc_M12>.sql <dump_da_chay_ban_v1>.sql
bash DB/admin/setup_test_accounts_on_clone.sh          # tạo tài khoản thử nghiệm pn7d_* CHỈ cho clone claude_2b7d_*
PN_JAR=<duong_dan_jar> bash DB/tests/test_db_privileges_and_startup.sh <dump_chua_M15>.sql <dump_clean>.sql
```

### 5.4 E2E (Playwright, trình duyệt thật)

1. Tạo clone E2E và đặt **mật khẩu E2E ngẫu nhiên** cho TK001 (admin) và TK002 (khách) **chỉ trên clone**:

```bash
PN_IT_DB_NAME=phonenest_e2e_<ts> bash DB/tests/create_it_clone.sh <dump_da_co_M15>.sql
# bcrypt của mật khẩu E2E (ví dụ sinh bằng BCryptPasswordEncoder của spring-security-crypto)
export PN_E2E_ADMIN_HASH='<bcrypt>' PN_E2E_CUSTOMER_HASH='<bcrypt>'
bash DB/tests/prepare_e2e_clone.sh phonenest_e2e_<ts>
```

2. Build và chạy backend trỏ vào clone (script từ chối mọi `DB_URL` không phải clone E2E trên localhost):

```bash
cd BE/phonenest && mvn -DskipTests package && cd ../..
DB_URL=jdbc:mariadb://127.0.0.1:3307/phonenest_e2e_<ts> DB_USERNAME=<tai_khoan_runtime> DB_PASSWORD=<...> JWT_SECRET=<ngau_nhien> \
  bash FE/e2e/start-backend-on-clone.sh
```

3. Chạy frontend (`npm run dev`) rồi chạy E2E:

```bash
cd FE
E2E_ADMIN_EMAIL=admin@phonenest.vn E2E_ADMIN_PASSWORD=<mat_khau_E2E> \
E2E_CUSTOMER_EMAIL=khach1@phonenest.vn E2E_CUSTOMER_PASSWORD=<mat_khau_E2E> \
npx playwright test
```

Bằng chứng (screenshot, `evidence.json`, `results.json`) nằm trong `FE/e2e-results/` (đã `.gitignore`) hoặc thư mục `E2E_OUT`.
Bộ E2E giả định trạng thái ban đầu của dump `clean_acceptance` (PB010 đang chờ duyệt); chạy lại phải dùng clone mới.

## 6. Kế hoạch thay secret (cập nhật 2B.7D, theo kết quả quét — không ghi giá trị)

Kết quả quét ngày 14/11/2026 (tracked, untracked, ignored, lịch sử Git mọi nhánh, thư mục backup, log kiểm thử):

| Loại | Vị trí | Trạng thái |
|---|---|---|
| Mật khẩu DB **đang dùng** | Lịch sử Git: `application.properties` (4 commit), một test tích hợp cũ (1 commit); đã đẩy lên remote | **Còn hiệu lực — ưu tiên cao nhất** |
| Mật khẩu DB đang dùng | `scratch/*.py` (3 file, bị `.gitignore`, chỉ trên máy) | Còn hiệu lực, cục bộ |
| JWT secret cũ (mặc định trước 2B.7B) | Lịch sử Git (1 commit), `scratch/*.py` (2 file) | Code hiện tại không còn dùng; còn hiệu lực ở môi trường nào vẫn đặt giá trị cũ |
| Gemini / MoMo / private key | Không phát hiện | — |
| File đang theo dõi | 3 kết quả là tên biến / placeholder trong test | Không phải secret |

Thứ tự thay an toàn (không tự đổi khi nhóm chưa thống nhất thời điểm):

| Bước | Việc cần làm | Ghi chú |
|---|---|---|
| 1 | Tạo tài khoản `pn_app` / `pn_migrate` / `pn_readonly` theo `DB/admin/create_restricted_accounts_DRAFT.sql` | Cần phê duyệt; mật khẩu mới, lưu trong trình quản lý mật khẩu |
| 2 | Mỗi thành viên chuyển `DB_USERNAME/DB_PASSWORD` sang tài khoản mới, kiểm tra ứng dụng + test | Chưa đổi root ở bước này để có đường lùi |
| 3 | **Đổi mật khẩu root** (đã lộ trong lịch sử Git) | Chỉ sau khi bước 2 ổn định; thông báo trước |
| 4 | Sinh `JWT_SECRET` mới cho từng môi trường | Mọi phiên đăng nhập cũ hết hiệu lực |
| 5 | Sửa `scratch/*.py` đọc mật khẩu từ biến môi trường (hoặc xóa khi được phép) | Không xóa khi chưa được phép |
| 6 | (Tùy chọn) Viết lại lịch sử Git | Ảnh hưởng mọi bản clone/fork; dù viết lại vẫn **phải** đổi secret |

## 7. Thay đổi hành vi cần biết (2B.7D)

- **Giá công khai:** API công khai, tìm kiếm, bộ lọc giá, giỏ hàng, wishlist, chatbot và vector store chỉ dùng giá của biến thể
  được phép bán. Biến thể chưa duyệt không có trường `giaBan`/`giaNiemYet` trong JSON công khai; FE hiển thị "Giá đang cập nhật".
  Admin vẫn xem giá nội bộ qua `/api/admin/**`.
- **Tồn kho:** form sản phẩm không còn sửa tồn kho biến thể đã có (giá trị gửi lên bị bỏ qua). Tồn ban đầu của biến thể mới
  được ghi sổ kho (`NHAP_KHO`). Điều chỉnh kiểm kê bắt buộc gửi `soLuongHienTai` (số tồn đang thấy); khác thực tế → 409.
- **Sửa sản phẩm đồng thời:** mã kiểm tra cấp sản phẩm bao gồm tên, mô tả, thông số, ảnh, trạng thái; form cũ → 409.
- **Migration 13b (giá) và 14** đã bị vô hiệu hóa (chỉ trả `REFUSED_DEPRECATED`); giá chỉ nhập qua Admin + phê duyệt có nguồn.
