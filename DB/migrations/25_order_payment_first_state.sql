-- Migration 25: keep online-payment orders pending until the provider confirms payment.
-- Run against an existing PhoneNest database after migration 24.
ALTER TABLE DonHang
    MODIFY COLUMN trangThai ENUM(
        'CHO_THANH_TOAN','CHO_XAC_NHAN','DA_XAC_NHAN','DANG_CHUAN_BI',
        'DANG_GIAO','CHO_KHACH_XAC_NHAN','DA_GIAO','DA_HUY'
    ) NOT NULL DEFAULT 'CHO_XAC_NHAN';

ALTER TABLE LichSuTrangThaiDonHang
    MODIFY COLUMN trangThai ENUM(
        'CHO_THANH_TOAN','CHO_XAC_NHAN','DA_XAC_NHAN','DANG_CHUAN_BI',
        'DANG_GIAO','CHO_KHACH_XAC_NHAN','DA_GIAO','DA_HUY'
    ) NOT NULL;

UPDATE DonHang d
SET d.trangThai = 'CHO_THANH_TOAN'
WHERE d.trangThai = 'CHO_XAC_NHAN'
  AND EXISTS (
      SELECT 1
      FROM ThanhToan t
      WHERE t.maDonHang = d.maDonHang
        AND t.phuongThuc IN ('VI_DIEN_TU', 'CHUYEN_KHOAN')
  )
  AND NOT EXISTS (
      SELECT 1
      FROM ThanhToan t
      WHERE t.maDonHang = d.maDonHang
        AND t.trangThai IN ('DA_THANH_TOAN', 'CAN_HOAN_TIEN', 'DA_HOAN_TIEN')
  );

INSERT INTO LichSuTrangThaiDonHang
    (maLichSu, maDonHang, trangThai, thoiGian, ghiChu, nguoiCapNhat)
SELECT LOWER(REPLACE(UUID(), '-', '')), d.maDonHang, 'CHO_THANH_TOAN',
       CURRENT_TIMESTAMP, 'Cập nhật trạng thái: chờ khách hàng thanh toán', NULL
FROM DonHang d
WHERE d.trangThai = 'CHO_THANH_TOAN'
  AND EXISTS (
      SELECT 1
      FROM ThanhToan t
      WHERE t.maDonHang = d.maDonHang
        AND t.phuongThuc IN ('VI_DIEN_TU', 'CHUYEN_KHOAN')
  )
  AND NOT EXISTS (
      SELECT 1
      FROM ThanhToan t
      WHERE t.maDonHang = d.maDonHang
        AND t.trangThai IN ('DA_THANH_TOAN', 'CAN_HOAN_TIEN', 'DA_HOAN_TIEN')
  )
  AND NOT EXISTS (
      SELECT 1
      FROM LichSuTrangThaiDonHang l
      WHERE l.maDonHang = d.maDonHang
        AND l.trangThai = 'CHO_THANH_TOAN'
  );
