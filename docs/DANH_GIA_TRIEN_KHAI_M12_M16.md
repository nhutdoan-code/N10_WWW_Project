# Đánh giá khả năng triển khai Migration 12–16 lên database chính (2B.7C, cập nhật 2B.7D)

**Trạng thái: CHỈ LÀ ĐÁNH GIÁ. Chưa triển khai, chưa chạy bất kỳ migration nào trên `bandienthoai`.**
Số liệu database chính bên dưới được đọc bằng phiên `READ ONLY` ngày 14/11/2026.

## 0. Hiện trạng

| Hạng mục | Giá trị |
|---|---|
| `bandienthoai` (MariaDB 12.3, cổng 3307) | 515 sản phẩm, 585 biến thể, 9 đơn hàng, ~2,0 MB dữ liệu + chỉ mục, 33 bảng |
| Migration đã có trên DB chính | Đến 11 (chưa có 12–16) |
| Code hiện tại trên nhánh `Doan` (chưa commit) | **Bắt buộc** có cột của M15 + M16 (`PhienBanSanPham`) → chạy code này với DB chính hiện tại sẽ lỗi |

## 1. Phụ thuộc giữa Migration 12–16

| Migration | Loại | Phụ thuộc | Code cần? | Nhận xét an toàn |
|---|---|---|---|---|
| 12 v2 thu gọn danh mục 75 model | Dữ liệu (SP ngoài danh mục → NGUNG) | Không | Không | **2B.7D viết lại**: mọi thao tác ghi sau chốt chặn DB + khóa chung; không tạo procedure; sao lưu append-only `pn_data_backup`; hậu kiểm tương đối; **không tự kích hoạt lại** SP/biến thể Admin đã ngừng. Clone đã chạy v1 → `SKIPPED_EXISTING_DATA`. 67/67 kịch bản (gồm dữ liệu thật của DB chính qua dump chỉ đọc) |
| 12 rollback v2 | Dữ liệu | M12 v2 | Không | Chỉ hoàn tác dòng còn đúng giá trị M12 đặt (Admin đã đổi → giữ nguyên); **không DROP bảng backup**; không hoàn tác bản v1 (REFUSED) |
| 13 v2 bổ sung thông số 5 model (nháp) | Dữ liệu | Không | Không | Chỉ **điền ô NULL**, không ghi đè giá trị Admin; giá trị thông số **chưa xác minh nguồn** → chờ giai đoạn chuẩn hóa catalog |
| 13b v2 cấu hình vivo Y100 (nháp, không đổi giá) | Dữ liệu biến thể | Không | Không | Từ chối nếu biến thể đã có đơn/giỏ/sổ kho, đang APPROVED, đã bị sửa, hoặc trùng tổ hợp; giá giữ nguyên; giá trị chưa xác minh |
| 13b pricing, 14 pricing (+ rollback) | — | — | — | **Vô hiệu hóa** (chỉ trả `REFUSED_DEPRECATED`, không ghi gì). Giá chỉ nhập qua Admin + phê duyệt có nguồn |
| 15 v2 trạng thái phê duyệt | **Schema** + dữ liệu | Không (nên sau 12) | **Có** | 58/58 kịch bản; khóa chung; phát hiện `INCONSISTENT_SCHEMA` |
| 16 dữ liệu xác minh phê duyệt | **Schema** + dữ liệu | **M15** | **Có** | Cùng chuẩn M15 |

Kết luận phụ thuộc:
- **Về schema, chỉ M15 → M16 là bắt buộc cho code.** M12–14 chỉ thay đổi dữ liệu.
- Nếu chạy M12 **sau** M15: biến thể bị chuyển NGUNG_KINH_DOANH bởi M12 vẫn mang trạng thái phê duyệt cũ (PENDING); vẫn không bán được (vì ngừng kinh doanh) nhưng không có `lyDoKhoa=NGUNG_KINH_DOANH`. Thứ tự khuyến nghị nếu M12 được duyệt: **12 → 15 → 16**.
- 13b-pricing/14 đã bị vô hiệu hóa. 13/13b-specs là bản nháp: giá trị cần xác minh ở giai đoạn chuẩn hóa catalog trước khi đề xuất chạy.
- Lỗi của bản v1 (AGY) đã chứng minh bằng phân tích tĩnh: cả 5 file M12/M12-rollback/M13/13b/14 có lệnh ghi (`CREATE TABLE … AS SELECT`, `DROP/CREATE PROCEDURE`) **trước** chốt chặn database.

## 2. Cần sao lưu những gì

1. **Dump logic đầy đủ** `bandienthoai` (`mariadb-dump --single-transaction --routines --triggers --events`), kiểm tra checksum (SHA-256) và **khôi phục thử** vào một clone trước khi bắt đầu.
2. `CHECKSUM TABLE` toàn bộ 33 bảng (mốc so sánh sau triển khai).
3. Bản sao thư mục `uploads/` (ảnh sản phẩm) — migration không đụng tới nhưng cần cho khôi phục toàn phần.
4. Bản ghi cấu hình ứng dụng đang chạy (không chứa secret trong tài liệu).
5. Trong quá trình chạy, M15/M16 tự ghi sao lưu append-only vào `pn_pheduyet_backup` (để `15_16_restore_approval_from_backup.sql` dùng).

## 3. Thời gian bảo trì dự kiến

Dữ liệu nhỏ (~2 MB, 585 biến thể): mỗi migration chạy dưới vài giây trên clone. Thời gian chủ yếu là thủ tục:

| Bước | Ước lượng |
|---|---|
| Dừng ghi (tắt backend), dump + kiểm tra khôi phục thử | 10–15 phút |
| Chạy M15 + M16 (kèm biến xác nhận), đọc kết quả | 2–5 phút |
| Hậu kiểm SQL + khởi động backend mới + smoke test (Admin, khách, đặt hàng thử?) | 15–25 phút |
| Dự phòng rollback | 15 phút |
| **Tổng cửa sổ đề xuất** | **45–60 phút**, ngoài giờ demo |

## 4. Rủi ro mất dữ liệu

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| DDL (`ALTER TABLE`) tự commit; DML lỗi sau đó | Thấp | Đã kiểm chứng (T10): trạng thái trung gian an toàn (không biến thể nào bán được), chạy lại hoàn tất |
| Chạy nhầm database | TB | Chốt chặn tên DB + biến xác nhận `@pn_confirm_migration_15/16`; người chạy đọc `SELECT DATABASE()` trước |
| Hai người chạy cùng lúc | Thấp | 2B.7C: khóa chung `GET_LOCK` (T11) |
| Log ghi COMPLETED nhưng schema bị sửa tay | Thấp | 2B.7C: trả `INCONSISTENT_SCHEMA`, không tự sửa (T12) |
| Tài khoản root chạy SQL tùy ý | **Cao** | Thiết kế runtime / migration / chỉ đọc đã kiểm chứng trên clone (44/44); SQL cho môi trường chính **chưa thực thi** |
| M12/M13 v1 ghi bảng backup lên DB chính trước chốt chặn | — | **Đã sửa ở v2 (2B.7D)**; kiểm tra tĩnh T0 chạy cho mọi file 12–16 |
| Giá trong 13b/14 chưa xác minh | **Cao** (dữ liệu sai) | Không chạy; nhập giá qua Admin + phê duyệt có nguồn |

Không có migration nào trong 15/16 xóa đơn hàng, dòng đơn, tồn kho hay lịch sử. Hậu kiểm so sánh số biến thể/đơn/dòng đơn trước–sau trong cùng giao dịch.

## 5. Khả năng rollback

- **Ứng dụng**: quay về build cũ **chỉ khi** schema cũng quay về (build cũ không biết cột mới; build mới không chạy với schema cũ).
- **Schema**: `16_…_rollback.sql` rồi `15_…_rollback.sql` (thứ tự bắt buộc; rollback 15 từ chối nếu cột M16 còn). Mỗi rollback ghi sao lưu append-only trước khi xóa cột.
- **Quyết định phê duyệt**: `15_16_restore_approval_from_backup.sql` (cần `@pn_restore_run_id`; DB chính cần `@pn_confirm_restore`). Không ghi đè quyết định Admin đưa ra sau thời điểm sao lưu; dữ liệu bán đã đổi thì APPROVED → PENDING.
- **Toàn phần**: khôi phục dump ở bước 2 (mất mọi đơn phát sinh sau thời điểm dump → chỉ dùng trong cửa sổ bảo trì khi chưa mở bán lại).

## 6. Tác động đến các thành viên khác

1. Sau khi pull code mới: phải đặt `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY` (xem `docs/HUONG_DAN_CAU_HINH_LOCAL.md`).
2. Database dev của từng người phải chạy M15 + M16, nếu không backend lỗi khi đọc biến thể.
3. **Sau M15 không biến thể nào được bán** cho tới khi Admin phê duyệt từng biến thể có nguồn xác minh. Catalog vẫn hiển thị, giá hiển thị "Giá đang cập nhật". Đây là thay đổi hành vi lớn với demo.
4. Form sửa sản phẩm giờ trả 409 nếu dữ liệu đã đổi kể từ khi mở form → phải tải lại.
5. Đổi JWT secret làm phiên đăng nhập hiện tại hết hiệu lực.

## 7. Chạy thử trên bản sao của database chính

1. Dump `bandienthoai` (đọc, `--single-transaction`) → nạp vào `phonenest_it_rehearsal_<ts>` (tên được `ItDatabaseGuard`/script chấp nhận).
2. Chạy M15 → M16 đúng như kế hoạch (không cần biến xác nhận vì không phải DB chính) và lưu toàn bộ output.
3. Chạy `mvn test` với `PHONENEST_IT_DB_URL` trỏ vào bản sao; chạy E2E (`FE/e2e`) với clone tương ứng.
4. Chạy thử rollback 16 → 15 và restore trên **một bản sao khác**, so sánh checksum với bản gốc.
5. Ghi lại thời gian thực tế từng bước để chốt cửa sổ bảo trì.

## 8. Tiêu chí GO / NO-GO

**GO khi tất cả đúng:**
- [ ] Chủ dự án phê duyệt bằng văn bản: thời điểm, người chạy, phạm vi (chỉ M15+M16, hoặc thêm M12 đã gia cố).
- [ ] Dump DB chính đã khôi phục thử thành công, có SHA-256.
- [ ] Diễn tập trên bản sao DB chính: M15/M16 `COMPLETED`, backend 100% test (kể cả tích hợp), E2E 100%.
- [ ] Có ít nhất một Admin sẵn sàng phê duyệt các biến thể ưu tiên **với nguồn xác minh thật** ngay sau triển khai (nếu không, cửa hàng không bán được gì).
- [ ] Các thành viên đã cấu hình secret mới và database dev đã có M15/M16.
- [ ] Ứng dụng chạy bằng tài khoản `pn_app` (không root); chế độ `--app.schema-check.only=true` trên bản sao DB chính trả `SCHEMA_CHECK OK`.
- [ ] Mật khẩu root đã lộ trong lịch sử Git đã được đổi (hoặc có kế hoạch đổi ngay sau triển khai).
- [ ] Không có đơn hàng đang xử lý dở cần sửa trong cửa sổ bảo trì.

**NO-GO nếu bất kỳ điều nào:**
- Diễn tập có `FAILED`, `INCONSISTENT_SCHEMA`, checksum đơn hàng/dòng đơn thay đổi, hoặc test đỏ.
- Muốn chạy kèm 13b/14 (giá chưa xác minh) hoặc M12/M13 bản chưa gia cố.
- Chưa có phương án tài khoản/quyền hoặc người chạy không phải người được phê duyệt.

## 9. Tránh chạy code mới với schema cũ

**Đã làm ở 2B.7D:** `SchemaCompatibilityVerifier` chạy khi khởi tạo bean, **trước khi** web server mở cổng; chỉ đọc
`information_schema`. Kiểm tra bảng bắt buộc, 10 cột M15/M16 (kiểu, nullable, đủ giá trị ENUM mà code dùng), dữ liệu trạng thái
không hợp lệ, và đối chiếu `pn_migration_log` với schema (không chỉ dựa vào log). Sai → ứng dụng dừng, không phục vụ request.
Kiểm chứng bằng tiến trình thật: schema cũ, chỉ có M15, log mất bản ghi M16, tài khoản thiếu quyền → đều dừng, không mở cổng,
không ghi DB. Chạy độc lập: `--spring.main.web-application-type=none --app.schema-check.only=true`.

Quy trình vẫn là: chỉ deploy build mới **sau** khi migration báo `COMPLETED`; chỉ quay về build cũ **sau** khi rollback schema.

## 10. Xác minh sau triển khai

1. `SELECT migration_id, trang_thai FROM pn_migration_log ORDER BY log_id` → bản ghi cuối của 15 và 16 là `COMPLETED`.
2. Số biến thể, số đơn, số dòng đơn, tổng tồn kho **bằng** trước triển khai; `CHECKSUM TABLE donhang, chitietdonhang, biendongkho` không đổi.
3. `SELECT trangThaiPheDuyet, COUNT(*) …` : không có `APPROVED` mới do hệ thống; 11 biến thể giá seed `BLOCKED/GIA_SEED_CHUA_XAC_MINH`.
4. Không có procedure/trigger rác (`information_schema.ROUTINES/TRIGGERS`).
5. Smoke test: Admin đăng nhập → màn hình "Phê duyệt bán hàng" tải được; khách xem sản phẩm → "Giá đang cập nhật"; thêm giỏ biến thể chưa duyệt → bị từ chối; sau khi Admin duyệt một biến thể thật có nguồn → mua thử (đơn test, hủy ngay) → tồn kho trừ đúng và hoàn lại khi hủy.
6. Kiểm tra log ứng dụng không có lỗi 500.

## 11. Diễn tập nâng cấp trên bản chụp DB chính (2B.7E) và GO/NO-GO từng migration

Script: `DB/tests/rehearse_upgrade_main_snapshot.sh` — clone tạo từ dump chỉ đọc `--single-transaction` của `bandienthoai`
(515 SP / 585 biến thể / 9 đơn; đã có M03–M11). Migration chạy bằng tài khoản migration riêng (`pn7d_mig`), kiểm tra khởi động
bằng tài khoản runtime (`pn7d_rt`). **45/45 kiểm tra PASS**: code mới + schema cũ → dừng; 12 v2 → 15 → 16 COMPLETED;
chạy lại / `--force` → không đổi; gián đoạn giữa M15 → FAILED, ứng dụng không khởi động, sửa lỗi rồi chạy lại cho kết quả
nghiệp vụ trùng lần không gián đoạn; rollback 16 → 15 → 12 đưa dữ liệu SP + biến thể **trùng checksum bản chụp**,
giữ thay đổi Admin; 26 bảng lịch sử không đổi ở mọi bước; khôi phục toàn phần từ dump trùng checksum.
Sau nâng cấp: 109 biến thể chờ duyệt, 11 khóa (giá seed), 465 ngừng KD bị khóa; **0 biến thể bán được** cho tới khi Admin
duyệt với nguồn thật. E2E (29 test) chạy trên clone này sau nâng cấp.

| Migration | Phân loại | Quyết định kỹ thuật | Điều kiện còn thiếu |
|---|---|---|---|
| 03–11 | Đã có trên DB chính | — | — |
| 12 v2 | Có thể triển khai về kỹ thuật | **GO kỹ thuật** (diễn tập 45/45, bộ test 67/67) | Phê duyệt chủ dự án + cửa sổ bảo trì |
| 15 v2 | Có thể triển khai về kỹ thuật (schema) | **GO kỹ thuật** | Như trên; tài khoản `pn_migrate`; kế hoạch duyệt giá ngay sau triển khai |
| 16 | Có thể triển khai về kỹ thuật (schema) | **GO kỹ thuật** (sau 15) | Như trên |
| 13 v2 | Cần dữ liệu được xác minh | **NO-GO** | Thông số 5 model chưa xác minh nguồn |
| 13b v2 (specs) | Cần dữ liệu được xác minh | **NO-GO** | Cấu hình/màu vivo Y100 chưa xác minh |
| 14, 13b-pricing (+ rollback) | Không còn được sử dụng (vô hiệu hóa) | **NO-GO / không chạy** | Giá chỉ qua Admin + phê duyệt |
| Rollback 12/15/16, restore 15_16 | Công cụ khôi phục | Sẵn sàng (đã diễn tập) | Chỉ dùng trong cửa sổ bảo trì |

**Quyết định triển khai tổng thể: chưa GO** cho tới khi: chủ dự án phê duyệt; tài khoản `pn_app`/`pn_migrate` được tạo và
nhóm chuyển sang dùng; mật khẩu root đã lộ được đổi (xem `docs/KE_HOACH_TAI_KHOAN_DB_VA_SECRET.md`); có kế hoạch duyệt giá
cho các biến thể ưu tiên (nếu không, cửa hàng không bán được gì sau nâng cấp).
