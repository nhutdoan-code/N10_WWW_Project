-- ==============================================================================
-- PHONENEST MIGRATION 13b v2 (2B.7D): CAU HINH & MAU VIVO Y100 5G (PB500488) — BAN NHAP, KHONG DOI GIA
-- Loai: MIGRATION DU LIEU bien the. Gia tri 8GB/128GB/'Den Kim Cuong' la DE XUAT chua xac minh nguon;
-- se duoc xac minh o giai doan chuan hoa catalog. Chua duoc phe duyet cho database chinh.
--
-- Loi da sua so voi v1: backup + procedure tao TRUOC khi kiem tra database dich; khong kiem tra bien the
-- da phat sinh don hang / gio hang / bien dong kho (doi RAM/ROM/mau se lam sai hien thi lich su don);
-- khong xu ly trang thai phe duyet; rollback DROP bang backup.
-- v2:
--   * moi thao tac ghi SAU buoc xac minh database dich, co khoa chung;
--   * chi doi khi bien the VAN o gia tri goc 4/64/'Den' va CHUA duoc tham chieu boi don hang, gio hang,
--     bien dong kho; khong trung to hop (maSanPham, ram, boNho, mauSac);
--   * neu co cot phe duyet (M15/M16): tu choi khi bien the dang APPROVED (doi cau hinh phai qua Admin);
--     khong mo khoa/duyet bien the;
--   * khong doi giaBan / giaNiemYet; sao luu append-only vao pn_data_backup.
-- Database chinh: SET @pn_confirm_migration_13b = CONCAT('APPLY-13B-ON-', DATABASE());
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_db VARCHAR(64);
    DECLARE v_run_id VARCHAR(40) DEFAULT NULL;
    DECLARE v_last_state VARCHAR(30) DEFAULT NULL;
    DECLARE v_in_dml INT DEFAULT 0;
    DECLARE v_ref INT DEFAULT 0;
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_gia_before VARCHAR(64);
    DECLARE v_gia_after VARCHAR(64);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_run_id IS NOT NULL THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('13b', v_run_id, 'FAILED', LEFT(@pn_err, 500));
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
           AND (@pn_confirm_migration_13b IS NULL OR @pn_confirm_migration_13b <> CONCAT('APPLY-13B-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua,
                   'Database chinh: can SET @pn_confirm_migration_13b = CONCAT(''APPLY-13B-ON-'', DATABASE()) sau khi sao luu va duoc phe duyet' AS chiTiet;
            LEAVE pn;
        END IF;
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua, 'Mot tien trinh migration khac dang chay tren database nay' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = v_db
            AND TABLE_NAME IN ('phienbansanpham', 'chitietdonhang', 'chitietgiohang', 'biendongkho')) <> 4 THEN
            SELECT 'REFUSED' AS ketQua, 'Thieu bang phienbansanpham/chitietdonhang/chitietgiohang/biendongkho' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'pn_migration_log') > 0 THEN
            SELECT trang_thai INTO v_last_state FROM pn_migration_log WHERE migration_id = '13b' ORDER BY log_id DESC LIMIT 1;
        END IF;
        IF v_last_state = 'COMPLETED' THEN
            SELECT 'ALREADY_COMPLETED' AS ketQua, 'Migration 13b da hoan tat truoc do. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM phienbansanpham WHERE maPhienBan = 'PB500488' AND maSanPham = 'SP500488') = 0 THEN
            SELECT 'REFUSED' AS ketQua, 'Khong tim thay PB500488 thuoc SP500488' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM phienbansanpham WHERE maPhienBan = 'PB500488' AND ram = 8 AND boNho = 128 AND mauSac = 'Đen Kim Cương') = 1 THEN
            SELECT 'SKIPPED_EXISTING_DATA' AS ketQua, 'PB500488 da la 8GB/128GB/Den Kim Cuong. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM phienbansanpham WHERE maPhienBan = 'PB500488' AND ram = 4 AND boNho = 64 AND mauSac = 'Đen') = 0 THEN
            SELECT 'REFUSED' AS ketQua, 'PB500488 khong con o gia tri goc 4GB/64GB/Den (da bi sua). Khong ghi de.' AS chiTiet;
            LEAVE pn;
        END IF;
        SELECT (SELECT COUNT(*) FROM chitietdonhang WHERE maPhienBan = 'PB500488')
             + (SELECT COUNT(*) FROM chitietgiohang WHERE maPhienBan = 'PB500488')
             + (SELECT COUNT(*) FROM biendongkho WHERE maPhienBan = 'PB500488') INTO v_ref;
        IF v_ref > 0 THEN
            SELECT 'REFUSED' AS ketQua, CONCAT('PB500488 da duoc tham chieu ', v_ref, ' lan (don hang/gio hang/bien dong kho): doi cau hinh se lam sai lich su. Tao bien the moi qua Admin.') AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM phienbansanpham WHERE maSanPham = 'SP500488' AND maPhienBan <> 'PB500488'
            AND ram = 8 AND boNho = 128 AND mauSac = 'Đen Kim Cương') > 0 THEN
            SELECT 'REFUSED' AS ketQua, 'Da co bien the khac 8GB/128GB/Den Kim Cuong cua SP500488 (trung to hop)' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = v_db
            AND TABLE_NAME = 'phienbansanpham' AND COLUMN_NAME = 'trangThaiPheDuyet') > 0 THEN
            IF (SELECT COUNT(*) FROM phienbansanpham WHERE maPhienBan = 'PB500488' AND trangThaiPheDuyet = 'APPROVED') > 0 THEN
                SELECT 'REFUSED' AS ketQua, 'PB500488 dang APPROVED: doi cau hinh phai qua giao dien Admin (tu dong huy phe duyet + audit)' AS chiTiet;
                LEAVE pn;
            END IF;
        END IF;

        SET v_run_id = CONCAT('M13B-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
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
        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu) VALUES ('13b', v_run_id, 'STARTED', 'PB500488 -> 8/128/Den Kim Cuong');
        SELECT CONCAT(giaBan, '|', IFNULL(giaNiemYet, '-')) INTO v_gia_before FROM phienbansanpham WHERE maPhienBan = 'PB500488';

        SET v_in_dml = 1;
        START TRANSACTION;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi) VALUES
            (v_run_id, '13b', 'M13B_TRUOC_DML', 'phienbansanpham', 'PB500488', 'ram', '4', '8'),
            (v_run_id, '13b', 'M13B_TRUOC_DML', 'phienbansanpham', 'PB500488', 'boNho', '64', '128'),
            (v_run_id, '13b', 'M13B_TRUOC_DML', 'phienbansanpham', 'PB500488', 'mauSac', 'Đen', 'Đen Kim Cương');
        UPDATE phienbansanpham SET ram = 8, boNho = 128, mauSac = 'Đen Kim Cương'
        WHERE maPhienBan = 'PB500488' AND ram = 4 AND boNho = 64 AND mauSac = 'Đen';
        SET v_done = ROW_COUNT();
        SELECT CONCAT(giaBan, '|', IFNULL(giaNiemYet, '-')) INTO v_gia_after FROM phienbansanpham WHERE maPhienBan = 'PB500488';
        IF v_done <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: PB500488 khong duoc cap nhat dung 1 dong';
        END IF;
        IF NOT (v_gia_after <=> v_gia_before) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: gia ban / gia niem yet bi thay doi';
        END IF;
        COMMIT;
        SET v_in_dml = 0;
        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu) VALUES ('13b', v_run_id, 'COMPLETED', 'PB500488 cap nhat cau hinh, gia giu nguyen');
        SELECT 'COMPLETED' AS ketQua, v_run_id AS runId;
    END pn;
END$$
DELIMITER ;
