-- MIGRATION 23: voucher giảm theo phần trăm CÓ TRẦN hoặc giảm tiền mặt cố định theo bậc giá trị đơn.
-- Chạy một lần trên database BanDienThoai (sau migration 22):
--   mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/23_voucher_discount_type.sql
-- ADD COLUMN nghiêm ngặt: chạy lại sẽ báo lỗi cột đã tồn tại. INSERT thường: chạy lại báo trùng khóa.
--
-- Công thức (KhuyenMaiService.tinhGiam):
--   Tiền giảm = 0                                              nếu tổng đơn < donToiThieu (mã không dùng được)
--             = min(soTienGiamCoDinh, cơ sở)                    nếu kieuGiam = SO_TIEN
--             = min(cơ sở × phanTramGiam / 100, soTienGiamToiDa) nếu kieuGiam = PHAN_TRAM (bắt buộc có trần)
--   Cơ sở: tạm tính (voucher DON_HANG) hoặc phí giao hàng theo vùng (voucher VAN_CHUYEN, xem M22).
--
-- Bộ voucher mới (dùng được từ hôm nay đến 31/12/2027):
--   GIAM150K     150.000đ  đơn từ 3.000.000đ   (máy tầm trung 3-7 triệu)
--   GIAM400K     400.000đ  đơn từ 7.000.000đ   (máy cận cao cấp 7-15 triệu)
--   GIAM800K     800.000đ  đơn từ 15.000.000đ  (flagship, 200 lượt)
--   NEST5        5% tối đa 300.000đ    đơn từ 5.000.000đ
--   FLAGSHIP7    7% tối đa 1.000.000đ  đơn từ 25.000.000đ (100 lượt)
--   KHACHMOI150K 150.000đ  đơn đầu tiên từ 1.000.000đ (khách mới)
--   FREESHIP / SHIP50 (vận chuyển) giữ nguyên mức của M22, chỉ chuẩn hóa mô tả.
-- Mã cũ NEST10, SALE10TR, KHACHMOI10, WELCOME5 (giảm % cao hoặc không có trần) được TẮT, không xóa,
-- để giữ lịch sử đơn hàng / lượt dùng; mã HETHAN20 (cố ý hết hạn, dùng để thử lỗi) chuyển sang mô hình mới.

-- 1. Cột mới
ALTER TABLE KhuyenMai
    ADD COLUMN kieuGiam ENUM('PHAN_TRAM','SO_TIEN') NOT NULL DEFAULT 'PHAN_TRAM' AFTER loai,
    ADD COLUMN soTienGiamCoDinh DECIMAL(15,2) NULL AFTER phanTramGiam,
    MODIFY COLUMN phanTramGiam DECIMAL(5,2) NULL;

-- 2. Mã cũ: tắt và ghi chú mã thay thế
UPDATE KhuyenMai SET trangThaiHoatDong = 0, soTienGiamToiDa = COALESCE(soTienGiamToiDa, 2000000),
    dieuKienApDung = 'Ngừng áp dụng từ 25/11/2026 (giảm 10% không phù hợp biên lợi nhuận điện thoại). Thay bằng NEST5 / GIAM150K / GIAM400K / GIAM800K'
WHERE maKm = 'NEST10';
UPDATE KhuyenMai SET trangThaiHoatDong = 0, soTienGiamToiDa = COALESCE(soTienGiamToiDa, 1500000),
    dieuKienApDung = 'Ngừng áp dụng từ 25/11/2026. Thay bằng GIAM800K (đơn từ 15 triệu) / FLAGSHIP7 (đơn từ 25 triệu)'
WHERE maKm = 'SALE10TR';
UPDATE KhuyenMai SET trangThaiHoatDong = 0, soTienGiamToiDa = COALESCE(soTienGiamToiDa, 500000),
    dieuKienApDung = 'Ngừng áp dụng từ 25/11/2026 (giảm 10% không giới hạn). Thay bằng KHACHMOI150K'
WHERE maKm = 'KHACHMOI10';
UPDATE KhuyenMai SET trangThaiHoatDong = 0, soTienGiamToiDa = COALESCE(soTienGiamToiDa, 500000),
    dieuKienApDung = 'Ngừng áp dụng từ 25/11/2026. Thay bằng KHACHMOI150K'
WHERE maKm = 'WELCOME5';

UPDATE KhuyenMai SET kieuGiam = 'PHAN_TRAM', phanTramGiam = 5.00, soTienGiamToiDa = 300000, donToiThieu = 5000000,
    dieuKienApDung = 'Mã thử nghiệm đã hết hạn: giảm 5% tối đa 300.000đ cho đơn từ 5.000.000đ'
WHERE maKm = 'HETHAN20';

-- Voucher vận chuyển (mức giảm đã đặt ở M22)
UPDATE KhuyenMai
SET dieuKienApDung = 'Miễn phí giao hàng theo vùng, tối đa 50.000đ, cho đơn từ 1.000.000đ chưa đạt ngưỡng freeship. Không gồm phí khai giá và phụ phí COD'
WHERE maKm = 'FREESHIP';
UPDATE KhuyenMai
SET dieuKienApDung = 'Giảm 50% phí giao hàng theo vùng, tối đa 25.000đ, cho mọi đơn chưa đạt ngưỡng freeship. Không gồm phí khai giá và phụ phí COD'
WHERE maKm = 'SHIP50';

-- Voucher % do Admin tạo trước đây mà chưa có trần: đặt trần an toàn 500.000đ (quy tắc mới bắt buộc có trần).
UPDATE KhuyenMai SET soTienGiamToiDa = 500000 WHERE kieuGiam = 'PHAN_TRAM' AND soTienGiamToiDa IS NULL;

-- 3. Ràng buộc: % phải có trần; tiền mặt phải có số tiền > 0
ALTER TABLE KhuyenMai ADD CONSTRAINT CK_KhuyenMai_KieuGiam CHECK (
    (kieuGiam = 'PHAN_TRAM' AND phanTramGiam > 0 AND phanTramGiam <= 100 AND soTienGiamToiDa IS NOT NULL)
    OR (kieuGiam = 'SO_TIEN' AND soTienGiamCoDinh > 0)
);

-- 4. Bộ voucher mới
INSERT INTO KhuyenMai
    (maKm, tenKm, loai, kieuGiam, phanTramGiam, soTienGiamCoDinh, soTienGiamToiDa, donToiThieu, soLuong, daDung,
     chiKhachMoi, gioiHanMoiKhach, ngayBatDau, ngayKetThuc, dieuKienApDung, trangThaiHoatDong)
VALUES
    ('GIAM150K', 'Giảm 150.000đ máy tầm trung', 'DON_HANG', 'SO_TIEN', NULL, 150000, NULL, 3000000, NULL, 0,
     0, 1, '2026-11-25', '2027-12-31', 'Giảm thẳng 150.000đ cho đơn từ 3.000.000đ. Mỗi tài khoản dùng 1 lần', 1),
    ('GIAM400K', 'Giảm 400.000đ máy cận cao cấp', 'DON_HANG', 'SO_TIEN', NULL, 400000, NULL, 7000000, NULL, 0,
     0, 1, '2026-11-25', '2027-12-31', 'Giảm thẳng 400.000đ cho đơn từ 7.000.000đ. Mỗi tài khoản dùng 1 lần', 1),
    ('GIAM800K', 'Giảm 800.000đ flagship', 'DON_HANG', 'SO_TIEN', NULL, 800000, NULL, 15000000, 200, 0,
     0, 1, '2026-11-25', '2027-12-31', 'Giảm thẳng 800.000đ cho đơn từ 15.000.000đ. Số lượng có hạn (200 lượt)', 1),
    ('NEST5', 'Giảm 5% tối đa 300.000đ', 'DON_HANG', 'PHAN_TRAM', 5.00, NULL, 300000, 5000000, NULL, 0,
     0, 1, '2026-11-25', '2027-12-31', 'Giảm 5%, tối đa 300.000đ, cho đơn từ 5.000.000đ', 1),
    ('FLAGSHIP7', 'Siêu sale flagship giảm 7%', 'DON_HANG', 'PHAN_TRAM', 7.00, NULL, 1000000, 25000000, 100, 0,
     0, 1, '2026-11-25', '2027-12-31', 'Giảm 7%, tối đa 1.000.000đ, cho đơn flagship từ 25.000.000đ. Số lượng có hạn (100 lượt)', 1),
    ('KHACHMOI150K', 'Quà khách hàng mới 150.000đ', 'DON_HANG', 'SO_TIEN', NULL, 150000, NULL, 1000000, NULL, 0,
     1, 1, '2026-11-25', '2030-12-31', 'Dành cho đơn hàng đầu tiên của tài khoản, giảm 150.000đ cho đơn từ 1.000.000đ', 1);
