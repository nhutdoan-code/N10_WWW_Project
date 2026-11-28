-- Migration 30: persist the status and provider references for VNPay refunds.
-- Run after migration 29.
ALTER TABLE ThanhToan
    MODIFY COLUMN trangThai ENUM(
        'CHO_THANH_TOAN',
        'DA_THANH_TOAN',
        'THAT_BAI',
        'DANG_HOAN_TIEN',
        'DA_HOAN_TIEN',
        'DA_HUY',
        'CAN_HOAN_TIEN'
    ) NOT NULL DEFAULT 'CHO_THANH_TOAN',
    ADD COLUMN ngayTaoGiaoDichVnPay DATETIME NULL AFTER maGiaoDichNganHang,
    ADD COLUMN maYeuCauHoanVnPay VARCHAR(32) NULL AFTER ngayTaoGiaoDichVnPay,
    ADD COLUMN maGiaoDichHoanVnPay VARCHAR(100) NULL AFTER maYeuCauHoanVnPay,
    ADD COLUMN ngayYeuCauHoanVnPay DATETIME NULL AFTER maGiaoDichHoanVnPay,
    ADD COLUMN nguoiYeuCauHoanVnPay VARCHAR(245) NULL AFTER ngayYeuCauHoanVnPay;

ALTER TABLE PhienThanhToan
    ADD COLUMN ngayTaoGiaoDichVnPay DATETIME NULL AFTER maGiaoDichNhaCungCap;
