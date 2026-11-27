-- Migration 24: store the provider transaction number returned by VNPay.
-- Run against an existing PhoneNest database after migration 23.
ALTER TABLE ThanhToan
    ADD COLUMN maGiaoDichNganHang VARCHAR(100) NULL AFTER maGiaoDichMoMo,
    ADD UNIQUE KEY uq_ThanhToan_maGiaoDichNganHang (maGiaoDichNganHang);
