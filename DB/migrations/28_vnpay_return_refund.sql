-- Migration 28: persist idempotent VNPay refund requests for after-sales returns.
-- Run after migration 27.
ALTER TABLE YeuCauDoiTra
    ADD COLUMN trangThaiHoanVnPay VARCHAR(20) NULL,
    ADD COLUMN maYeuCauHoanVnPay VARCHAR(32) NULL,
    ADD COLUMN ngayYeuCauHoanVnPay DATETIME NULL,
    ADD COLUMN nguoiYeuCauHoanVnPay VARCHAR(245) NULL,
    ADD COLUMN thongBaoHoanVnPay VARCHAR(500) NULL;
