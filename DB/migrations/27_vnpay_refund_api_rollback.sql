-- Roll back migration 27. In-flight VNPay refunds become eligible for manual reconciliation.
UPDATE ThanhToan
SET trangThai = 'CAN_HOAN_TIEN'
WHERE trangThai = 'DANG_HOAN_TIEN';

ALTER TABLE PhienThanhToan
    DROP COLUMN ngayTaoGiaoDichVnPay;

ALTER TABLE ThanhToan
    DROP COLUMN nguoiYeuCauHoanVnPay,
    DROP COLUMN ngayYeuCauHoanVnPay,
    DROP COLUMN maGiaoDichHoanVnPay,
    DROP COLUMN maYeuCauHoanVnPay,
    DROP COLUMN ngayTaoGiaoDichVnPay,
    MODIFY COLUMN trangThai ENUM(
        'CHO_THANH_TOAN',
        'DA_THANH_TOAN',
        'THAT_BAI',
        'DA_HOAN_TIEN',
        'DA_HUY',
        'CAN_HOAN_TIEN'
    ) NOT NULL DEFAULT 'CHO_THANH_TOAN';
