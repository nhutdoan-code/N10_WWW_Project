-- ==============================================================================
-- BAN NHAP (CHUA THUC THI) - Giai doan 2B.7B, cap nhat 2B.7D
-- Tai khoan MariaDB gioi han quyen cho PhoneNest tren MOI TRUONG CHINH.
--
-- KHONG THUC THI khi chua co phe duyet cua chu du an. Chay bang tai khoan quan tri server,
-- SAU KHI sao luu mysql.global_priv. Thay <MAT_KHAU_...> bang mat khau manh ngau nhien
-- (>= 24 ky tu), luu trong trinh quan ly mat khau cua nhom; KHONG commit mat khau.
--
-- Thiet ke da duoc KIEM CHUNG tren clone (2B.7D, DB/tests/test_db_privileges_and_startup.sh,
-- 44/44 PASS) voi tai khoan thu nghiem pn7d_rt / pn7d_mig / pn7d_none gioi han vao `claude\_2b7d\_%`:
--   * runtime doc/ghi du lieu, KHONG ALTER/CREATE/DROP/INDEX (ERROR 1142);
--   * runtime/migration/test KHONG truy cap duoc database khac (ERROR 1044/1142);
--   * migration bang runtime that bai an toan, bang tai khoan migration thi COMPLETED;
--   * thieu quyen -> Backend dung khi khoi dong (SCHEMA_CHECK / ket noi bi tu choi), khong mo cong.
--   * toan bo Backend test (334) va E2E (20) chay duoc bang tai khoan runtime, khong can root.
-- ==============================================================================

-- 1) RUNTIME (Backend dang chay): chi DML tren database chinh.
CREATE USER IF NOT EXISTS 'pn_app'@'localhost' IDENTIFIED BY '<MAT_KHAU_APP>';
GRANT SELECT, INSERT, UPDATE, DELETE ON `bandienthoai`.* TO 'pn_app'@'localhost';
-- KHONG cap: CREATE, ALTER, DROP, INDEX, REFERENCES, TRIGGER, CREATE ROUTINE, EXECUTE, LOCK TABLES, FILE, SUPER.

-- 2) MIGRATION (chi dung trong cua so bao tri da duoc phe duyet; khoa / doi mat khau ngoai cua so do).
CREATE USER IF NOT EXISTS 'pn_migrate'@'localhost' IDENTIFIED BY '<MAT_KHAU_MIGRATE>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, CREATE TEMPORARY TABLES
      ON `bandienthoai`.* TO 'pn_migrate'@'localhost';
-- Khong cap DROP: cac migration 12-16 v2 khong can DROP TABLE/DATABASE.
-- Ghi chu: rollback 15/16 dung ALTER TABLE ... DROP COLUMN (thuoc quyen ALTER).
ALTER USER 'pn_migrate'@'localhost' ACCOUNT LOCK;  -- mo khoa (ACCOUNT UNLOCK) chi trong cua so bao tri

-- 3) CHI DOC (kiem toan, bao cao, AI agent, dump sao luu --single-transaction).
CREATE USER IF NOT EXISTS 'pn_readonly'@'localhost' IDENTIFIED BY '<MAT_KHAU_READONLY>';
GRANT SELECT, SHOW VIEW ON `bandienthoai`.* TO 'pn_readonly'@'localhost';

-- 4) KIEM THU / CLONE: KHONG co quyen nao tren bandienthoai.
--    Tao clone tu dump can CREATE + DROP (dump co DROP TABLE IF EXISTS) -> tai khoan "provisioning" rieng,
--    gioi han theo mau ten clone; integration test / E2E dung tai khoan runtime cua clone.
CREATE USER IF NOT EXISTS 'pn_it_provision'@'localhost' IDENTIFIED BY '<MAT_KHAU_IT_PROVISION>';
GRANT ALL PRIVILEGES ON `phonenest\_it\_%`.*  TO 'pn_it_provision'@'localhost';
GRANT ALL PRIVILEGES ON `phonenest\_e2e\_%`.* TO 'pn_it_provision'@'localhost';
CREATE USER IF NOT EXISTS 'pn_it_runtime'@'localhost' IDENTIFIED BY '<MAT_KHAU_IT_RUNTIME>';
GRANT SELECT, INSERT, UPDATE, DELETE ON `phonenest\_it\_%`.*  TO 'pn_it_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON `phonenest\_e2e\_%`.* TO 'pn_it_runtime'@'localhost';

FLUSH PRIVILEGES;

-- 5) Kiem tra sau khi tao (ky vong: chi pn_app / pn_migrate co quyen GHI tren bandienthoai)
SHOW GRANTS FOR 'pn_app'@'localhost';
SHOW GRANTS FOR 'pn_migrate'@'localhost';
SHOW GRANTS FOR 'pn_readonly'@'localhost';
SHOW GRANTS FOR 'pn_it_provision'@'localhost';
SHOW GRANTS FOR 'pn_it_runtime'@'localhost';

-- 6) Sau khi ung dung chay on dinh bang pn_app: doi mat khau root (da nam trong lich su Git) va
--    ngung dung root cho ung dung / test (xem docs/HUONG_DAN_CAU_HINH_LOCAL.md muc 6).

-- 7) Hoan tac (neu can)
-- DROP USER 'pn_app'@'localhost', 'pn_migrate'@'localhost', 'pn_readonly'@'localhost',
--           'pn_it_provision'@'localhost', 'pn_it_runtime'@'localhost';
