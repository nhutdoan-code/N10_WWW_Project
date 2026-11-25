-- ROLLBACK MIGRATION 22: bỏ phí vận chuyển theo vùng / ngày giao dự kiến / đường dẫn thông báo.
-- Phải triển khai lại backend phiên bản trước M22 TRƯỚC khi chạy (code mới cần các cột này).
-- Bảng phí trở về 4 dòng mặc định ban đầu (TP.HCM, Hà Nội, Đà Nẵng, mặc định); các dòng do M22 thêm
-- (maPhi PVCT<maTinh>) bị xóa. Chỉnh sửa bảng phí của Admin sau M22 không được giữ lại.
-- Dữ liệu đã ghi vào đơn (phiKhaiGia, phuPhiCod) đã nằm trong DonHang.phiVanChuyen nên tổng tiền không đổi.

-- 1. Thông báo
ALTER TABLE ThongBao DROP COLUMN duongDan;

-- 2. Đơn hàng
ALTER TABLE DonHang
    DROP COLUMN ngayGiaoDuKienDen,
    DROP COLUMN ngayGiaoDuKienTu,
    DROP COLUMN phuPhiCod,
    DROP COLUMN phiKhaiGia;

-- 3. Voucher vận chuyển về cấu hình cũ
UPDATE KhuyenMai
SET soTienGiamToiDa = 30000, donToiThieu = 500000,
    dieuKienApDung = 'Đơn hàng từ 500.000đ, giảm tối đa 30.000đ phí giao hàng'
WHERE maKm = 'FREESHIP';

UPDATE KhuyenMai
SET soTienGiamToiDa = 25000, donToiThieu = 0, dieuKienApDung = 'Áp dụng cho mọi đơn hàng'
WHERE maKm = 'SHIP50';

-- 4. Bảng phí vận chuyển
DELETE FROM PhiVanChuyen WHERE maPhi LIKE 'PVCT%';
UPDATE PhiVanChuyen SET phiCoBan = 25000, mienPhiTu = 3000000, thoiGianGiao = '1 - 2 ngày' WHERE maTinh = '79';
UPDATE PhiVanChuyen SET phiCoBan = 40000, mienPhiTu = 5000000, thoiGianGiao = '2 - 3 ngày' WHERE maTinh IN ('01', '48');
UPDATE PhiVanChuyen SET phiCoBan = 50000, mienPhiTu = 10000000, thoiGianGiao = '3 - 5 ngày' WHERE maTinh IS NULL;

ALTER TABLE PhiVanChuyen
    DROP CONSTRAINT CK_PhiVanChuyen_SoNgay,
    DROP CONSTRAINT CK_PhiVanChuyen_Vung,
    DROP COLUMN soNgayGiaoToiDa,
    DROP COLUMN soNgayGiaoToiThieu,
    DROP COLUMN phuPhiDacKhu,
    DROP COLUMN phuPhiXa,
    DROP COLUMN vung;
