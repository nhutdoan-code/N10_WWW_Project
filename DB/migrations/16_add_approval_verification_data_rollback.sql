-- ==============================================================================
-- ROLLBACK MIGRATION 16 (Giai doan 2B.7B)
--   - Sao luu toan bo du lieu phe duyet (ca cot M15 va M16) vao pn_pheduyet_backup
--     (append-only, giai_doan = 'M16_TRUOC_ROLLBACK'); chi DROP COLUMN khi so dong
--     sao luu khop so dong bien the.
--   - LUU Y: ma nguon ung dung hien tai can cac cot cua M16. Sau khi rollback 16
--     phai dung ban ung dung truoc 2B.7B.
--   - Database chinh can: SET @pn_confirm_rollback_16 = CONCAT('ROLLBACK-16-ON-', DATABASE());
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_run_id      VARCHAR(40);
    DECLARE v_db          VARCHAR(64);
    DECLARE v_m16_cols    INT DEFAULT 0;
    DECLARE v_total       INT DEFAULT 0;
    DECLARE v_backup_rows INT DEFAULT 0;
    DECLARE v_ready       INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_rb16_err = MESSAGE_TEXT;
        IF v_ready = 1 THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('16', v_run_id, 'ROLLBACK_FAILED', LEFT(@pn_rb16_err, 500));
        END IF;
        SELECT 'FAILED' AS ketQua, @pn_rb16_err AS chiTiet;
    END;

    pn: BEGIN
        SET v_db = DATABASE();
        IF v_db IS NULL THEN
            SELECT 'REFUSED' AS ketQua, 'Chua chon database' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_db = 'bandienthoai'
           AND (@pn_confirm_rollback_16 IS NULL
                OR @pn_confirm_rollback_16 <> CONCAT('ROLLBACK-16-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua, 'Database chinh: can bien xac nhan @pn_confirm_rollback_16' AS chiTiet;
            LEAVE pn;
        END IF;

        -- (2B.7C) Chong chay dong thoi: MOT khoa chung cho M15/M16/rollback/restore tren
        -- cung database. Khoa thuoc phien; tu giai phong khi phien ket thuc.
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua,
                   'Mot tien trinh khac dang chay Migration 15/16, rollback hoac restore tren database nay. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_m16_cols FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham'
          AND COLUMN_NAME IN ('giaPheDuyet', 'giaNiemYetPheDuyet', 'nguonXacMinh', 'dauVanTayPheDuyet', 'lyDoKhoa', 'giaBanKhiKhoa');
        IF v_m16_cols = 0 THEN
            SELECT 'NOTHING_TO_ROLLBACK' AS ketQua, 'Khong con cot cua Migration 16' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_m16_cols <> 6 THEN
            SELECT 'REFUSED' AS ketQua, 'Trang thai cot dang do (khong du 6 cot). Can kiem tra thu cong.' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = v_db AND TABLE_NAME IN ('pn_migration_log', 'pn_pheduyet_backup')) <> 2 THEN
            SELECT 'REFUSED' AS ketQua, 'Thieu bang pn_migration_log/pn_pheduyet_backup (Migration 16 tao ra). Kiem tra thu cong.' AS chiTiet;
            LEAVE pn;
        END IF;

        SET v_run_id = CONCAT('RB16-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
        SET v_ready = 1;
        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai) VALUES ('16', v_run_id, 'ROLLBACK_STARTED');

        INSERT INTO pn_pheduyet_backup (run_id, giai_doan, maPhienBan, maSanPham, sku, ram, boNho, mauSac,
                                        giaBan, giaNiemYet, soLuongTon, trangThai,
                                        trangThaiPheDuyet, nguoiPheDuyet, ngayPheDuyet, lyDoPheDuyet,
                                        giaPheDuyet, giaNiemYetPheDuyet, nguonXacMinh, dauVanTayPheDuyet,
                                        lyDoKhoa, giaBanKhiKhoa)
        SELECT v_run_id, 'M16_TRUOC_ROLLBACK', maPhienBan, maSanPham, sku, ram, boNho, mauSac,
               giaBan, giaNiemYet, soLuongTon, trangThai,
               trangThaiPheDuyet, nguoiPheDuyet, ngayPheDuyet, lyDoPheDuyet,
               giaPheDuyet, giaNiemYetPheDuyet, nguonXacMinh, dauVanTayPheDuyet,
               lyDoKhoa, giaBanKhiKhoa
        FROM phienbansanpham;
        SET v_backup_rows = ROW_COUNT();
        SELECT COUNT(*) INTO v_total FROM phienbansanpham;
        IF v_backup_rows <> v_total THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sao luu khong du so dong. Huy rollback, khong DROP COLUMN.';
        END IF;

        ALTER TABLE phienbansanpham
            DROP COLUMN IF EXISTS giaBanKhiKhoa,
            DROP COLUMN IF EXISTS lyDoKhoa,
            DROP COLUMN IF EXISTS dauVanTayPheDuyet,
            DROP COLUMN IF EXISTS nguonXacMinh,
            DROP COLUMN IF EXISTS giaNiemYetPheDuyet,
            DROP COLUMN IF EXISTS giaPheDuyet;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('16', v_run_id, 'ROLLED_BACK', CONCAT('sao luu=', v_backup_rows, ' dong; run_id sao luu=', v_run_id));
        SELECT 'ROLLED_BACK' AS ketQua, v_run_id AS runIdSaoLuu, v_backup_rows AS dongSaoLuu;
    END pn;
END$$
DELIMITER ;
