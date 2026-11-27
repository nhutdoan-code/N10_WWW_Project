-- M24: khiếu nại không gắn đơn. Sao lưu trước khi chạy. Chạy trên DB đang dùng, không xóa dữ liệu.
ALTER TABLE YeuCauHauMai ADD COLUMN IF NOT EXISTS maKh VARCHAR(50) NULL;
UPDATE YeuCauHauMai y JOIN DonHang d ON d.maDonHang = y.maDonHang SET y.maKh = d.maKh WHERE y.maKh IS NULL;
ALTER TABLE YeuCauHauMai MODIFY COLUMN maDonHang VARCHAR(50) NULL;
ALTER TABLE YeuCauHauMai ADD INDEX IF NOT EXISTS IDX_HauMai_Khach (maKh, ngayTao);
-- Kiểm tra FK trước khi thêm để chạy lại an toàn nếu lần trước bị gián đoạn.
SET @hm_fk_exists = (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'YeuCauHauMai' AND CONSTRAINT_NAME = 'FK_HauMai_Khach');
SET @hm_sql = IF(@hm_fk_exists > 0, 'SELECT 1', 'ALTER TABLE YeuCauHauMai ADD CONSTRAINT FK_HauMai_Khach FOREIGN KEY (maKh) REFERENCES KhachHang(maKh)');
PREPARE hm_stmt FROM @hm_sql;
EXECUTE hm_stmt;
DEALLOCATE PREPARE hm_stmt;
