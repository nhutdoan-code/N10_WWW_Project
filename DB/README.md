# PhoneNest Database Management

Tài liệu hướng dẫn cấu trúc, vận hành và quản lý cơ sở dữ liệu MariaDB của dự án **PhoneNest**.

---

## 1. Cấu trúc thư mục

```text
DB/
├── schema/
│   └── 01_schema.sql                       # DDL khởi tạo baseline schema PhoneNest
├── seed/
│   ├── 02_data.sql                         # Dữ liệu ban đầu (tài khoản, voucher, cấu hình)
│   └── 04_data_add.sql                     # Danh mục mở rộng 500 mẫu điện thoại và biến thể
├── migrations/
│   ├── 03_audit_log.sql                    # Nhật ký hoạt động quản trị (NhatKyHoatDong)
│   ├── 05_inventory_migration.sql          # Mở rộng audit biến động kho (BienDongKho)
│   ├── 06_payment_refund_status.sql        # Trạng thái hoàn tiền và MoMo transaction ID
│   ├── 07_password_reset_otp.sql           # Quản lý mã OTP đặt lại mật khẩu
│   ├── 08_checkout_idempotency.sql         # Idempotency cho checkout (PNE-004)
│   ├── 09_voucher_usage_concurrency.sql    # Concurrency và giới hạn lượt voucher (PNE-003)
│   ├── 10_return_refund_restock.sql        # (Dự kiến từ fix/return-refund-restock - PNE-005)
│   └── 11_user_behavior_tracking.sql       # User Behavior Tracking phục vụ Spring AI
├── phonenest_full.sql                      # File SQL hợp nhất toàn bộ (Schema + Migrations + Seeds)
└── README.md                               # Hướng dẫn này
```

---

## 2. Cách khởi tạo Database

### Phương án A: Dùng file hợp nhất `phonenest_full.sql` (Khuyến nghị cho Setup mới)

File `DB/phonenest_full.sql` đã tích hợp đầy đủ 100% schema của 33 bảng, toàn bộ ràng buộc khóa chính, khóa ngoại, chỉ mục, check constraint và 1.703 bản ghi seed ban đầu.

> [!WARNING] **QUY TẮC AN TOÀN KHI IMPORT:**
> 1. **Khuyến cáo sao lưu**: Luôn sao lưu database hiện có trước khi thực hiện bất kỳ thao tác nạp dữ liệu nào (`mariadb-dump -u root -p --databases BanDienThoai > backup_bandienthoai.sql`).
> 2. **Chỉ dùng cho database mới/trống**: `phonenest_full.sql` hoàn toàn **KHÔNG** chứa lệnh `DROP DATABASE` hay `CREATE DATABASE`, và **KHÔNG** chỉ định cứng `USE <db>`.
> 3. **Cơ chế bảo vệ 2 lớp ngăn chặn tác động database không trống:**
>    - **Lớp 1 (Fail-fast Pre-check):** Ngay đầu file `phonenest_full.sql`, một truy vấn kiểm tra tự động `information_schema.TABLES` sẽ kích hoạt lỗi hủy lệnh ngay lập tức (`ERROR 1103`) nếu database đích đang chứa bất kỳ bảng nào, ngăn chặn triệt để tình trạng ghi đè hoặc tạo bảng chắp vá trên database có sẵn dữ liệu.
>    - **Lớp 2 (Strict CREATE TABLE):** Toàn bộ 33 bảng sử dụng cú pháp `CREATE TABLE <name>` nghiêm ngặt (không có `IF NOT EXISTS`). Nếu bảng đã tồn tại, MariaDB dừng khẩn cấp với lỗi `ERROR 1050 (42S01)`.
> 4. Tuyệt đối không dùng `INSERT IGNORE` hay `REPLACE INTO`.

**Quy trình kiểm tra database đích trống và import an toàn:**

1. **Tạo database mới (hoặc kiểm tra database đích):**
```sql
CREATE DATABASE IF NOT EXISTS BanDienThoai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **Kiểm tra xác nhận database đích hoàn toàn trống (bắt buộc trước import):**
```bash
mariadb -h 127.0.0.1 -P 3307 -u root -p -e "SELECT COUNT(*) AS total_existing_tables FROM information_schema.tables WHERE table_schema = 'BanDienThoai';"
```
*Kết quả `total_existing_tables` phải bằng `0` trước khi tiến hành import.*

3. **Chạy lệnh import chỉ định trực tiếp tên database đích:**
```bash
mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/phonenest_full.sql
```

*(Hoặc trên MySQL Workbench / DBeaver: chọn active schema là database rỗng vừa tạo rồi chạy script)*

---

### Phương án B: Chạy tuần tự theo migration (Áp dụng khi nâng cấp từng bước)

Nếu cần kiểm thử hoặc tái hiện từng bước nâng cấp:

1. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/schema/01_schema.sql`
2. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/seed/02_data.sql`
3. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/migrations/03_audit_log.sql`
4. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/seed/04_data_add.sql`
5. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/migrations/05_inventory_migration.sql`
6. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/migrations/06_payment_refund_status.sql`
7. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/migrations/07_password_reset_otp.sql`
8. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/migrations/08_checkout_idempotency.sql`
9. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/migrations/09_voucher_usage_concurrency.sql`
10. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/migrations/10_return_refund_restock.sql` *(khi merge PNE-005)*
11. `mariadb -h 127.0.0.1 -P 3307 -u root -p < DB/migrations/11_user_behavior_tracking.sql`

---

## 3. Bảng theo dõi hành vi người dùng (`HanhViNguoiDung`)

Migration `11_user_behavior_tracking.sql` bổ sung bảng lưu vết toàn bộ hành vi khách hàng:

| Tên cột | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| `maHanhVi` | VARCHAR(50) | Khóa chính (prefix `HV` + timestamp + seq) |
| `maKh` | VARCHAR(50) | Mã khách hàng (NULL nếu là khách vãng lai) |
| `sessionId` | VARCHAR(100) | Định danh phiên duyệt web của trình duyệt |
| `maSanPham` | VARCHAR(50) | Mã sản phẩm liên quan (FK -> SanPham) |
| `maPhienBan` | VARCHAR(50) | Mã phiên bản cấu hình (FK -> PhienBanSanPham) |
| `loaiHanhVi` | ENUM | `PRODUCT_VIEW`, `PRODUCT_CLICK`, `SEARCH`, `ADD_TO_CART`, `REMOVE_FROM_CART`, `PURCHASE`, `RECOMMENDATION_CLICK` |
| `tuKhoa` | VARCHAR(255) | Từ khóa tìm kiếm của khách hàng |
| `metadata` | VARCHAR(1000) | Dữ liệu ngữ cảnh bổ sung |
| `nguonSuKien` | VARCHAR(100) | Vị trí phát sinh sự kiện (`HOME_PAGE`, `SEARCH_BOX`, `AI_CHATBOT`, etc.) |
| `thoiGian` | DATETIME | Thời điểm ghi nhận sự kiện (mặc định CURRENT_TIMESTAMP) |

---

## 4. Kiểm chứng tương đương (Verification)

Cơ chế tạo `phonenest_full.sql` được kiểm chứng bằng script so sánh tự động giữa cơ sở dữ liệu chạy tuần tự (`test_phonenest_source`) và cơ sở dữ liệu import từ `phonenest_full.sql` (`test_phonenest_full`).

Kết quả xác thực: **`FULL SQL: VERIFIED 100% EQUIVALENT`**
- Bảng: 33/33 bảng khớp tuyệt đối.
- Cột & Kiểu dữ liệu: Khớp 100%.
- Khóa ngoại & Ràng buộc: Khớp 100%.
- Chỉ mục (Indexes): Khớp 100%.
- Số lượng bản ghi (Row Counts): 1.703 bản ghi khớp 100%.
