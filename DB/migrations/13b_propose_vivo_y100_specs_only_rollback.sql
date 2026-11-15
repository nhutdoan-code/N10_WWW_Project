-- ==============================================================================
-- PHONENEST MIGRATION 13b v2 ROLLBACK (2B.7D)
-- Dua PB500488 ve 4GB/64GB/'Den' CHI KHI bien the van dung 8/128/'Den Kim Cuong' do M13b dat va
-- chua phat sinh don hang/gio hang/bien dong kho. Sao luu append-only, khong xoa bang backup.
-- Database chinh: SET @pn_confirm_rollback_13b = CONCAT('ROLLBACK-13B-ON-', DATABASE());
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_db VARCHAR(64);
    DECLARE v_run_id VARCHAR(40) DEFAULT NULL;
    DECLARE v_in_dml INT DEFAULT 0;
    DECLARE v_ref INT DEFAULT 0;
    DECLARE v_done INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_run_id IS NOT NULL THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('13b', v_run_id, 'ROLLBACK_FAILED', LEFT(@pn_err, 500));
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
           AND (@pn_confirm_rollback_13b IS NULL OR @pn_confirm_rollback_13b <> CONCAT('ROLLBACK-13B-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua,
                   'Database chinh: can SET @pn_confirm_rollback_13b = CONCAT(''ROLLBACK-13B-ON-'', DATABASE()) sau khi sao luu va duoc phe duyet' AS chiTiet;
            LEAVE pn;
        END IF;
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua, 'Mot tien trinh migration khac dang chay tren database nay' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = v_db AND TABLE_NAME IN ('pn_migration_log', 'pn_data_backup')) <> 2
           OR (SELECT COUNT(*) FROM pn_migration_log WHERE migration_id = '13b' AND trang_thai = 'COMPLETED') = 0 THEN
            SELECT 'REFUSED' AS ketQua, 'Khong co lan chay M13b v2 COMPLETED (ban v1 can xu ly thu cong)' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT trang_thai FROM pn_migration_log WHERE migration_id = '13b' ORDER BY log_id DESC LIMIT 1) = 'ROLLED_BACK' THEN
            SELECT 'ALREADY_ROLLED_BACK' AS ketQua, 'Da hoan tac. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM phienbansanpham WHERE maPhienBan = 'PB500488' AND ram = 8 AND boNho = 128 AND mauSac = 'Đen Kim Cương') = 0 THEN
            SELECT 'REFUSED' AS ketQua, 'PB500488 da bi sua sau M13b. Khong ghi de.' AS chiTiet;
            LEAVE pn;
        END IF;
        SELECT (SELECT COUNT(*) FROM chitietdonhang WHERE maPhienBan = 'PB500488')
             + (SELECT COUNT(*) FROM chitietgiohang WHERE maPhienBan = 'PB500488')
             + (SELECT COUNT(*) FROM biendongkho WHERE maPhienBan = 'PB500488') INTO v_ref;
        IF v_ref > 0 THEN
            SELECT 'REFUSED' AS ketQua, 'PB500488 da phat sinh giao dich sau M13b: hoan tac se lam sai lich su' AS chiTiet;
            LEAVE pn;
        END IF;
        SET v_run_id = CONCAT('RB13B-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
        SET v_in_dml = 1;
        START TRANSACTION;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi) VALUES
            (v_run_id, '13b', 'M13B_TRUOC_ROLLBACK', 'phienbansanpham', 'PB500488', 'ram', '8', '4'),
            (v_run_id, '13b', 'M13B_TRUOC_ROLLBACK', 'phienbansanpham', 'PB500488', 'boNho', '128', '64'),
            (v_run_id, '13b', 'M13B_TRUOC_ROLLBACK', 'phienbansanpham', 'PB500488', 'mauSac', 'Đen Kim Cương', 'Đen');
        UPDATE phienbansanpham SET ram = 4, boNho = 64, mauSac = 'Đen'
        WHERE maPhienBan = 'PB500488' AND ram = 8 AND boNho = 128 AND mauSac = 'Đen Kim Cương';
        SET v_done = ROW_COUNT();
        IF v_done <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: khong hoan tac dung 1 dong';
        END IF;
        COMMIT;
        SET v_in_dml = 0;
        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu) VALUES ('13b', v_run_id, 'ROLLED_BACK', 'PB500488 ve 4/64/Den');
        SELECT 'ROLLED_BACK' AS ketQua, v_run_id AS runId;
    END pn;
END$$
DELIMITER ;
