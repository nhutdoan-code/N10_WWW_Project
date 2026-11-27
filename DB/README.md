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
│   ├── 11_user_behavior_tracking.sql       # User Behavior Tracking phục vụ Spring AI
│   ├── 17_after_sales.sql                  # Yêu cầu bảo hành/khiếu nại và lịch sử (+ _rollback)
│   ├── 18_after_sales_update_time.sql      # Thời điểm cập nhật và ghi chú nội bộ hậu mãi (+ _rollback)
│   ├── 19_new_customer_voucher.sql         # Voucher KHACHMOI10: khách mới giảm 10% đơn đầu tiên (+ _rollback)
│   ├── 20_return_refund_restock_admin.sql  # Đối soát hoàn tiền và nhập lại kho khi đổi trả (+ _rollback)
│   ├── 21_chat_history_vector_store.sql    # Lịch sử ChatBox + vector store MariaDB (+ _rollback)
│   ├── 22_shipping_zone_eta_notification_link.sql # Phí ship theo vùng, ngày giao dự kiến, link thông báo (+ _rollback)
│   ├── 23_voucher_discount_type.sql        # Voucher % có trần / tiền mặt theo bậc, bộ voucher mới (+ _rollback)
│   ├── 24_vnpay_payment_transaction.sql    # Lưu mã giao dịch ngân hàng do VNPay trả về (+ _rollback)
│   ├── 25_order_payment_first_state.sql   # Trạng thái đơn chờ thanh toán online (+ _rollback)
│   └── 26_prepaid_checkout_sessions.sql   # Tạo đơn online sau khi IPN xác nhận thanh toán (+ _rollback)
├── phonenest_full.sql                      # File SQL hợp nhất toàn bộ (Schema + Migrations + Seeds)
└── README.md                               # Hướng dẫn này
```

---

## 2. Cách khởi tạo Database

### Phương án A: Dùng file hợp nhất `phonenest_full.sql` (Khuyến nghị cho Setup mới)

File `DB/phonenest_full.sql` (cập nhật 15/11/2026) là **file duy nhất cần import** cho máy mới: 43 bảng (schema đã gồm migration 03, 05–09, 11, 12, 15, 16, 17, 18, 19, 20, 24–26; `pn_migration_log` có M12/M15/M16 nên backend qua được `SchemaCompatibilityVerifier`), 58 khóa ngoại, và dữ liệu demo:

| Dữ liệu | Số lượng |
|---|---|
| Sản phẩm đang bán (ảnh theo màu, giá tham khảo có nguồn, đã phê duyệt bán) | 75 sản phẩm / 433 phiên bản / 249 ảnh chụp thật (không ảnh quảng cáo, logo AI) |
| Sản phẩm ngừng bán (dữ liệu mẫu) | 100 |
| Tài khoản (`admin@phonenest.vn`, `khach1@`, `khach2@`, tài khoản thành viên + bộ cũ `admin@phonenest.com`, `an@`, `binh@`, `hoang@` — mật khẩu giữ nguyên) + khách hàng + địa chỉ | 8 / 5 / 7 |
| Tỉnh / phường xã / phí vận chuyển / voucher | 34 / 3.321 / 35 dòng theo 4 vùng (M22) / 9 (M23: tiền mặt theo bậc GIAM150K/400K/800K, % có trần NEST5/FLAGSHIP7, KHACHMOI150K, FREESHIP/SHIP50; `HETHAN20` cố ý hết hạn) |
| Đơn hàng, giỏ hàng, đánh giá, nhật ký cũ | 0 (dữ liệu thử nghiệm đã dọn) |

- Ảnh gốc catalog nằm trong `BE/phonenest/src/main/resources/catalog` (phục vụ tại `/catalog/...`), **tách riêng** khỏi ảnh Admin tải lên (`uploads/`, `/uploads/...`). Nguồn từng ảnh: `catalog/nguon-anh.csv`.
- Giá: `PhienBanSanPham.nguonXacMinh` ghi trang nguồn (CellphoneS / Thế Giới Di Động, ngày 15/11/2026); `lyDoPheDuyet` ghi rõ "dữ liệu demo". Admin sửa giá/cấu hình/màu thì biến thể tự về chờ duyệt như quy trình bình thường.
- Test tích hợp `*IntegrationTest` (khi đặt `PHONENEST_IT_DB_URL`) vẫn dựng clone từ **bản dump database cũ** (`DB/tests/create_it_clone.sh <dump>`), vì chúng dùng mã cố định của dữ liệu cũ (`PB001`, `PB500500`...).

> [!WARNING] **QUY TẮC AN TOÀN KHI IMPORT:**
> 1. **Khuyến cáo sao lưu**: Luôn sao lưu database hiện có trước khi thực hiện bất kỳ thao tác nạp dữ liệu nào (`mariadb-dump -u root -p --databases BanDienThoai > backup_bandienthoai.sql`).
> 2. **Chỉ dùng cho database mới/trống**: `phonenest_full.sql` hoàn toàn **KHÔNG** chứa lệnh `DROP DATABASE` hay `CREATE DATABASE`, và **KHÔNG** chỉ định cứng `USE <db>`.
> 3. **Cơ chế bảo vệ 2 lớp ngăn chặn tác động database không trống:**
>    - **Lớp 1 (Fail-fast Pre-check):** Ngay đầu file `phonenest_full.sql`, một truy vấn kiểm tra tự động `information_schema.TABLES` sẽ kích hoạt lỗi hủy lệnh ngay lập tức (`ERROR 1103`) nếu database đích đang chứa bất kỳ bảng nào, ngăn chặn triệt để tình trạng ghi đè hoặc tạo bảng chắp vá trên database có sẵn dữ liệu.
>    - **Lớp 2 (Strict CREATE TABLE):** Toàn bộ 43 bảng sử dụng cú pháp `CREATE TABLE <name>` nghiêm ngặt (không có `IF NOT EXISTS`). Nếu bảng đã tồn tại, MariaDB dừng khẩn cấp với lỗi `ERROR 1050 (42S01)`.
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
12. *(sau 12–18)* `mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/19_new_customer_voucher.sql` — chỉ thêm dữ liệu voucher khách hàng mới, không đổi schema.
13. *(sau 17–18)* `mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/20_return_refund_restock_admin.sql` — thêm cột đối soát hoàn tiền / nhập lại kho cho đổi trả.
14. *(sau 20)* `mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/21_chat_history_vector_store.sql` — bảng lịch sử ChatBox (`CuocHoiThoaiChat`, `TinNhanChat`) và vector store sản phẩm trong MariaDB (`phonenest_vector_store`, `product_embedding_sync`). Cần MariaDB ≥ 11.7 (kiểu `VECTOR`). Bắt buộc để dùng ChatBox khi đã đăng nhập; vector store tự quay về bộ nhớ nếu thiếu bảng. Rollback: `21_chat_history_vector_store_rollback.sql`.
15. *(sau 21)* `mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/22_shipping_zone_eta_notification_link.sql` — phí vận chuyển theo 4 vùng từ kho TP.HCM cho đủ 34 tỉnh/thành (phụ phí xã ngoại thành / đặc khu, phí khai giá 0,5% tối đa 50.000đ cho đơn từ 3 triệu, phụ phí COD 15.000đ cho đơn trên 10 triệu, freeship 3–5 triệu), số ngày giao + ngày giao dự kiến lưu trên đơn, voucher `FREESHIP`/`SHIP50` chỉ giảm phí vùng, cột `ThongBao.duongDan` để bấm thông báo mở đúng trang. **Bắt buộc** với backend hiện tại. Rollback: `22_shipping_zone_eta_notification_link_rollback.sql`.
16. *(sau 22)* `mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/23_voucher_discount_type.sql` — thêm `KhuyenMai.kieuGiam` (`PHAN_TRAM` bắt buộc có `soTienGiamToiDa` / `SO_TIEN` dùng `soTienGiamCoDinh`), tắt mã cũ NEST10/SALE10TR/KHACHMOI10/WELCOME5 (giữ lịch sử) và thêm bộ voucher mới. **Bắt buộc** với backend hiện tại. Rollback: `23_voucher_discount_type_rollback.sql`.
17. *(sau 23)* `mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/24_vnpay_payment_transaction.sql` — thêm `ThanhToan.maGiaoDichNganHang` và unique index để lưu mã giao dịch VNPay. Bắt buộc với bản backend có tích hợp ngân hàng. Rollback: `24_vnpay_payment_transaction_rollback.sql`.
18. *(sau 24)* `mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/25_order_payment_first_state.sql` — thêm trạng thái `CHO_THANH_TOAN` cho đơn hàng và lịch sử trạng thái. Bắt buộc với bản backend chỉ tiếp nhận đơn MoMo/VNPay sau khi thanh toán thành công. Rollback: `25_order_payment_first_state_rollback.sql`.
19. *(sau 25)* `mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/26_prepaid_checkout_sessions.sql` — tạo bảng phiên thanh toán tạm, lưu snapshot giỏ hàng và chỉ tạo `DonHang` sau IPN thành công. Bắt buộc với checkout trả trước hiện tại. Chỉ rollback sau khi đã đối soát hết các phiên đang chờ thanh toán/hoàn tiền: `26_prepaid_checkout_sessions_rollback.sql`.

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

> Bản `phonenest_full.sql` ngày 15/11/2026 được kiểm chứng: import vào database trống thành công; import lần 2 bị chặn bởi guard; `CHECKSUM TABLE` 43/43 bảng khớp database nguồn; 58/58 khóa ngoại; backend chạy `SCHEMA_CHECK OK`. Phần dưới mô tả lần kiểm chứng của bản cũ (33 bảng).

Cơ chế tạo `phonenest_full.sql` được kiểm chứng bằng script so sánh tự động giữa cơ sở dữ liệu chạy tuần tự (`test_phonenest_source`) và cơ sở dữ liệu import từ `phonenest_full.sql` (`test_phonenest_full`).

Kết quả xác thực: **`FULL SQL: VERIFIED 100% EQUIVALENT`**
- Bảng: 33/33 bảng khớp tuyệt đối.
- Cột & Kiểu dữ liệu: Khớp 100%.
- Khóa ngoại & Ràng buộc: Khớp 100%.
- Chỉ mục (Indexes): Khớp 100%.
- Số lượng bản ghi (Row Counts): 1.703 bản ghi khớp 100%.
