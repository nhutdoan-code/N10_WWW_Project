-- ROLLBACK MIGRATION 23: bỏ kiểu giảm tiền mặt, xóa bộ voucher mới, bật lại mã cũ.
-- Phải triển khai lại backend phiên bản trước M23 TRƯỚC khi chạy.
-- Voucher mới đã được dùng (có SuDungKhuyenMai / DonHang tham chiếu) KHÔNG xóa được: rollback sẽ dừng ở bước
-- DELETE. Khi đó phải giữ M23 (hoặc tự xử lý các đơn liên quan) — số tiền giảm đã lưu trên đơn không đổi.

-- 1. Xóa voucher mới (chưa được dùng)
DELETE FROM KhuyenMai WHERE maKm IN ('GIAM150K', 'GIAM400K', 'GIAM800K', 'NEST5', 'FLAGSHIP7', 'KHACHMOI150K')
  AND NOT EXISTS (SELECT 1 FROM SuDungKhuyenMai s WHERE s.maKm = KhuyenMai.maKm);

-- 2. Bật lại mã cũ với cấu hình trước M23
UPDATE KhuyenMai SET trangThaiHoatDong = 1, soTienGiamToiDa = 2000000, dieuKienApDung = 'Áp dụng cho mọi đơn hàng' WHERE maKm = 'NEST10';
UPDATE KhuyenMai SET trangThaiHoatDong = 1, soTienGiamToiDa = 1500000, dieuKienApDung = 'Đơn hàng từ 10.000.000đ, số lượng có hạn' WHERE maKm = 'SALE10TR';
UPDATE KhuyenMai SET trangThaiHoatDong = 1, soTienGiamToiDa = NULL,
    dieuKienApDung = 'Dành cho khách hàng mới: giảm 10% cho đơn hàng đầu tiên, mỗi tài khoản dùng 1 lần' WHERE maKm = 'KHACHMOI10';
UPDATE KhuyenMai SET trangThaiHoatDong = 1, soTienGiamToiDa = 500000, dieuKienApDung = 'Chỉ dành cho đơn hàng đầu tiên' WHERE maKm = 'WELCOME5';
UPDATE KhuyenMai SET phanTramGiam = 20.00, soTienGiamToiDa = 3000000, donToiThieu = 0, dieuKienApDung = NULL WHERE maKm = 'HETHAN20';
UPDATE KhuyenMai SET dieuKienApDung = 'Đơn từ 1.000.000đ chưa được miễn phí vận chuyển: miễn phí giao hàng theo vùng, tối đa 50.000đ (không gồm phí khai giá, phụ phí COD)' WHERE maKm = 'FREESHIP';
UPDATE KhuyenMai SET dieuKienApDung = 'Giảm 50% phí giao hàng theo vùng, tối đa 25.000đ (không gồm phí khai giá, phụ phí COD). Không áp dụng cho đơn đã được miễn phí vận chuyển' WHERE maKm = 'SHIP50';

-- 3. Schema: voucher tiền mặt còn sót (do Admin tạo) không biểu diễn được ở mô hình cũ -> tắt và quy về 0% để giữ NOT NULL.
UPDATE KhuyenMai SET trangThaiHoatDong = 0, phanTramGiam = 0.01, soTienGiamToiDa = soTienGiamCoDinh
WHERE kieuGiam = 'SO_TIEN';

ALTER TABLE KhuyenMai
    DROP CONSTRAINT CK_KhuyenMai_KieuGiam,
    DROP COLUMN soTienGiamCoDinh,
    DROP COLUMN kieuGiam,
    MODIFY COLUMN phanTramGiam DECIMAL(5,2) NOT NULL;
