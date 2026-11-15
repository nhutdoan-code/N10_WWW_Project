-- ==============================================================================
-- PHONENEST MIGRATION 13 v2 (2B.7D): BO SUNG THONG SO KY THUAT 5 MODEL (BAN NHAP)
-- Loai: MIGRATION DU LIEU. Gia tri thong so la DE XUAT cua giai doan truoc, CHUA duoc xac minh nguon;
-- se duoc xac minh lai o giai doan chuan hoa catalog. Chua duoc phe duyet cho database chinh.
--
-- Loi da sua so voi v1 (AGY): v1 tao bang backup va stored procedure TRUOC khi kiem tra database dich
-- (ghi vao DB chinh du bi tu choi), GHI DE moi thong so (ke ca gia tri Admin da nhap), backup bang
-- CREATE TABLE IF NOT EXISTS (chay lai giu ban chup cu); rollback v1 DROP bang backup.
-- v2:
--   * moi thao tac ghi nam trong khoi BEGIN NOT ATOMIC, SAU buoc xac minh database dich, co khoa chung;
--   * chi DIEN cot dang NULL — khong bao gio ghi de gia tri da co;
--   * ho5G/nfc (khong NULL duoc) chi doi voi san pham CHUA chuan hoa (chip dang NULL luc bat dau);
--   * sao luu tung o vao pn_data_backup (append-only); trang thai trong pn_migration_log.
-- Database chinh: SET @pn_confirm_migration_13 = CONCAT('APPLY-13-ON-', DATABASE());
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_db VARCHAR(64);
    DECLARE v_run_id VARCHAR(40) DEFAULT NULL;
    DECLARE v_last_state VARCHAR(30) DEFAULT NULL;
    DECLARE v_in_dml INT DEFAULT 0;
    DECLARE v_found INT DEFAULT 0;
    DECLARE v_plan INT DEFAULT 0;
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_other_before VARCHAR(64);
    DECLARE v_other_after VARCHAR(64);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_run_id IS NOT NULL THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('13', v_run_id, 'FAILED', LEFT(@pn_err, 500));
        END IF;
        SELECT 'FAILED' AS ketQua, @pn_err AS chiTiet;
    END;

    pn: BEGIN
        SET v_db = DATABASE();
        IF v_db IS NULL THEN
            SELECT 'REFUSED' AS ketQua, 'Chua chon database (dung -D <db>)' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_db = 'bandienthoai'
           AND (@pn_confirm_migration_13 IS NULL OR @pn_confirm_migration_13 <> CONCAT('APPLY-13-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua,
                   'Database chinh: can SET @pn_confirm_migration_13 = CONCAT(''APPLY-13-ON-'', DATABASE()) sau khi sao luu va duoc phe duyet' AS chiTiet;
            LEAVE pn;
        END IF;
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua, 'Mot tien trinh migration khac dang chay tren database nay' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'sanpham') = 0 THEN
            SELECT 'REFUSED' AS ketQua, 'Thieu bang sanpham' AS chiTiet;
            LEAVE pn;
        END IF;

        CREATE TEMPORARY TABLE pn_tmp_m13 (
            maSanPham VARCHAR(50) PRIMARY KEY,
            chip VARCHAR(255),
            pin INT,
            kichThuocManHinh DECIMAL(4,2),
            loaiManHinh VARCHAR(255),
            tanSoQuet INT,
            cameraSau VARCHAR(255),
            cameraTruoc VARCHAR(255),
            congSuatSac INT,
            heDieuHanh VARCHAR(255),
            trongLuong INT,
            ho5G TINYINT,
            nfc TINYINT
        );
        INSERT INTO pn_tmp_m13 (maSanPham, chip, pin, kichThuocManHinh, loaiManHinh, tanSoQuet, cameraSau, cameraTruoc, congSuatSac, heDieuHanh, trongLuong, ho5G, nfc) VALUES
            ('SP500352', 'MediaTek Dimensity 7300X', 4200, 6.90, 'LTPO AMOLED', 120, '50MP + 13MP', '32MP', 30, 'Android 14', 188, 1, 1),
            ('SP500435', 'MediaTek Helio G88', 5000, 6.72, 'IPS LCD', 90, '64MP + 2MP', '8MP', 33, 'Android 13', 190, 0, 1),
            ('SP500445', 'Qualcomm Snapdragon 685', 5000, 6.72, 'IPS LCD', 90, '108MP + 2MP', '8MP', 33, 'Android 14', 185, 0, 1),
            ('SP500472', 'Qualcomm Snapdragon 680', 5000, 6.64, 'IPS LCD', 90, '50MP + 2MP', '16MP', 44, 'Android 13', 202, 0, 1),
            ('SP500488', 'Qualcomm Snapdragon 4 Gen 2', 5000, 6.67, 'AMOLED', 120, '50MP + 8MP', '8MP', 80, 'Android 14', 186, 1, 1);
        SELECT COUNT(*) INTO v_found FROM pn_tmp_m13 t JOIN sanpham s ON s.maSanPham = t.maSanPham;
        IF v_found <> 5 THEN
            SELECT 'REFUSED' AS ketQua, CONCAT('Chi tim thay ', v_found, '/5 san pham can bo sung thong so') AS chiTiet;
            LEAVE pn;
        END IF;
        -- San pham CHUA chuan hoa luc bat dau (chip NULL): chi nhom nay duoc doi ho5G/nfc
        CREATE TEMPORARY TABLE pn_tmp_m13_cch (maSanPham VARCHAR(50) PRIMARY KEY);
        INSERT INTO pn_tmp_m13_cch SELECT s.maSanPham FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.chip IS NULL;

        IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'pn_migration_log') > 0 THEN
            SELECT trang_thai INTO v_last_state FROM pn_migration_log WHERE migration_id = '13' ORDER BY log_id DESC LIMIT 1;
        END IF;
        IF v_last_state = 'COMPLETED' THEN
            SELECT 'ALREADY_COMPLETED' AS ketQua, 'Migration 13 da hoan tat truoc do. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;

        SET v_plan = (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.chip IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.pin IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.kichThuocManHinh IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.loaiManHinh IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.tanSoQuet IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.cameraSau IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.cameraTruoc IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.congSuatSac IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.heDieuHanh IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.trongLuong IS NULL) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham JOIN pn_tmp_m13_cch u ON u.maSanPham = s.maSanPham WHERE s.ho5G <> t.ho5G) +
            (SELECT COUNT(*) FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham JOIN pn_tmp_m13_cch u ON u.maSanPham = s.maSanPham WHERE s.nfc <> t.nfc);

        SET v_run_id = CONCAT('M13-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
        CREATE TABLE IF NOT EXISTS pn_migration_log (
            log_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
            migration_id VARCHAR(20)  NOT NULL,
            run_id       VARCHAR(40)  NOT NULL,
            trang_thai   VARCHAR(30)  NOT NULL,
            thoi_diem    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
            ghi_chu      VARCHAR(500) NULL,
            INDEX idx_pn_migration_log_mid (migration_id, log_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
        CREATE TABLE IF NOT EXISTS pn_data_backup (
            backup_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
            run_id       VARCHAR(40)  NOT NULL,
            migration_id VARCHAR(20)  NOT NULL,
            giai_doan    VARCHAR(40)  NOT NULL,
            bang         VARCHAR(64)  NOT NULL,
            khoa         VARCHAR(100) NOT NULL,
            cot          VARCHAR(64)  NOT NULL,
            gia_tri_cu   TEXT NULL,
            gia_tri_moi  TEXT NULL,
            thoi_diem    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
            INDEX idx_pn_data_backup_run (run_id, bang, cot)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

        IF v_plan = 0 THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('13', v_run_id, 'SKIPPED_EXISTING_DATA', 'Moi thong so muc tieu da co gia tri; khong ghi de.');
            SELECT 'SKIPPED_EXISTING_DATA' AS ketQua, 'Thong so da co gia tri; khong ghi de' AS chiTiet;
            LEAVE pn;
        END IF;
        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('13', v_run_id, 'STARTED', CONCAT('so o se dien: ', v_plan));
        SELECT MD5(GROUP_CONCAT(CONCAT_WS('|', maSanPham, tenMay, chip, pin, trangThai) ORDER BY maSanPham)) INTO v_other_before
        FROM sanpham WHERE maSanPham NOT IN ('SP500352', 'SP500435', 'SP500445', 'SP500472', 'SP500488');

        SET v_in_dml = 1;
        START TRANSACTION;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'chip', NULL, CAST(t.chip AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.chip IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'pin', NULL, CAST(t.pin AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.pin IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'kichThuocManHinh', NULL, CAST(t.kichThuocManHinh AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.kichThuocManHinh IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'loaiManHinh', NULL, CAST(t.loaiManHinh AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.loaiManHinh IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'tanSoQuet', NULL, CAST(t.tanSoQuet AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.tanSoQuet IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'cameraSau', NULL, CAST(t.cameraSau AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.cameraSau IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'cameraTruoc', NULL, CAST(t.cameraTruoc AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.cameraTruoc IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'congSuatSac', NULL, CAST(t.congSuatSac AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.congSuatSac IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'heDieuHanh', NULL, CAST(t.heDieuHanh AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.heDieuHanh IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'trongLuong', NULL, CAST(t.trongLuong AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham WHERE s.trongLuong IS NULL;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'ho5G', CAST(s.ho5G AS CHAR), CAST(t.ho5G AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham
        JOIN pn_tmp_m13_cch u ON u.maSanPham = s.maSanPham WHERE s.ho5G <> t.ho5G;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_DML', 'sanpham', s.maSanPham, 'nfc', CAST(s.nfc AS CHAR), CAST(t.nfc AS CHAR)
        FROM sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham
        JOIN pn_tmp_m13_cch u ON u.maSanPham = s.maSanPham WHERE s.nfc <> t.nfc;
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.chip = t.chip WHERE s.chip IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.pin = t.pin WHERE s.pin IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.kichThuocManHinh = t.kichThuocManHinh WHERE s.kichThuocManHinh IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.loaiManHinh = t.loaiManHinh WHERE s.loaiManHinh IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.tanSoQuet = t.tanSoQuet WHERE s.tanSoQuet IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.cameraSau = t.cameraSau WHERE s.cameraSau IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.cameraTruoc = t.cameraTruoc WHERE s.cameraTruoc IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.congSuatSac = t.congSuatSac WHERE s.congSuatSac IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.heDieuHanh = t.heDieuHanh WHERE s.heDieuHanh IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham SET s.trongLuong = t.trongLuong WHERE s.trongLuong IS NULL;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham
        JOIN pn_tmp_m13_cch u ON u.maSanPham = s.maSanPham SET s.ho5G = t.ho5G WHERE s.ho5G <> t.ho5G;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_tmp_m13 t ON t.maSanPham = s.maSanPham
        JOIN pn_tmp_m13_cch u ON u.maSanPham = s.maSanPham SET s.nfc = t.nfc WHERE s.nfc <> t.nfc;
        SET v_done = v_done + ROW_COUNT();
        IF v_done <> v_plan THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: so o cap nhat khac ke hoach (co thao tac dong thoi?)';
        END IF;
        SELECT MD5(GROUP_CONCAT(CONCAT_WS('|', maSanPham, tenMay, chip, pin, trangThai) ORDER BY maSanPham)) INTO v_other_after
        FROM sanpham WHERE maSanPham NOT IN ('SP500352', 'SP500435', 'SP500445', 'SP500472', 'SP500488');
        IF NOT (v_other_after <=> v_other_before) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: san pham ngoai pham vi bi thay doi';
        END IF;
        COMMIT;
        SET v_in_dml = 0;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('13', v_run_id, 'COMPLETED', CONCAT('so o da dien: ', v_done));
        SELECT 'COMPLETED' AS ketQua, v_run_id AS runId, v_done AS soODaDien;
    END pn;
END$$
DELIMITER ;
