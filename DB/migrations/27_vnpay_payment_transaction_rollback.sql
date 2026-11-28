-- Rollback migration 27. Deploy the application version before migration 27 first.
ALTER TABLE ThanhToan
    DROP INDEX uq_ThanhToan_maGiaoDichNganHang,
    DROP COLUMN maGiaoDichNganHang;
