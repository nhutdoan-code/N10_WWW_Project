-- ROLLBACK MIGRATION 24: bo YeuCauHauMai.maKh, bat buoc lai maDonHang.
-- Phai trien khai lai backend phien ban truoc M24 TRUOC khi chay. Sao luu truoc khi chay.
-- Neu da co yeu cau hau mai KHONG gan don (maDonHang IS NULL) thi KHONG rollback duoc:
-- script dung o buoc kiem tra (SIGNAL) va khong thay doi gi.
SET @hm_orphans = (SELECT COUNT(*) FROM YeuCauHauMai WHERE maDonHang IS NULL);
SET @hm_sql = IF(@hm_orphans > 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''M24 rollback: da co yeu cau hau mai khong gan don hang''',
  'SELECT 1');
PREPARE hm_stmt FROM @hm_sql;
EXECUTE hm_stmt;
DEALLOCATE PREPARE hm_stmt;

ALTER TABLE YeuCauHauMai DROP FOREIGN KEY FK_HauMai_Khach;
ALTER TABLE YeuCauHauMai DROP INDEX IDX_HauMai_Khach;
ALTER TABLE YeuCauHauMai DROP COLUMN maKh;
ALTER TABLE YeuCauHauMai MODIFY COLUMN maDonHang VARCHAR(50) NOT NULL;
