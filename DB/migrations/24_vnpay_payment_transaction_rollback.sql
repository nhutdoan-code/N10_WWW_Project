-- Rollback migration 24. Deploy the application version before migration 24 first.
ALTER TABLE ThanhToan
    DROP INDEX uq_ThanhToan_maGiaoDichNganHang,
    DROP COLUMN maGiaoDichNganHang;
