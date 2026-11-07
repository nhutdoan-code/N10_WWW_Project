-- Migration: 06_payment_refund_status.sql
-- Muc tieu: 
-- 1. Bo sung trang thai CAN_HOAN_TIEN vao ENUM cot trangThai bang ThanhToan (PNE-001).
-- 2. Bo sung cot maGiaoDichMoMo de luu vet transId tu MoMo, phuc vu doi soat va truy vet (Prompt 04).

ALTER TABLE ThanhToan
MODIFY COLUMN trangThai ENUM('CHO_THANH_TOAN','DA_THANH_TOAN','THAT_BAI','DA_HOAN_TIEN','DA_HUY','CAN_HOAN_TIEN') NOT NULL DEFAULT 'CHO_THANH_TOAN';

ALTER TABLE ThanhToan
ADD COLUMN IF NOT EXISTS maGiaoDichMoMo VARCHAR(100);
