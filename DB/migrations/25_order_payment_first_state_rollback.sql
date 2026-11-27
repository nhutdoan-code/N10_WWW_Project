-- Rollback migration 25. Map pending-payment records to the legacy initial state first.
UPDATE DonHang SET trangThai = 'CHO_XAC_NHAN' WHERE trangThai = 'CHO_THANH_TOAN';
UPDATE LichSuTrangThaiDonHang SET trangThai = 'CHO_XAC_NHAN' WHERE trangThai = 'CHO_THANH_TOAN';

ALTER TABLE DonHang
    MODIFY COLUMN trangThai ENUM(
        'CHO_XAC_NHAN','DA_XAC_NHAN','DANG_CHUAN_BI','DANG_GIAO',
        'CHO_KHACH_XAC_NHAN','DA_GIAO','DA_HUY'
    ) NOT NULL DEFAULT 'CHO_XAC_NHAN';

ALTER TABLE LichSuTrangThaiDonHang
    MODIFY COLUMN trangThai ENUM(
        'CHO_XAC_NHAN','DA_XAC_NHAN','DANG_CHUAN_BI','DANG_GIAO',
        'CHO_KHACH_XAC_NHAN','DA_GIAO','DA_HUY'
    ) NOT NULL;
