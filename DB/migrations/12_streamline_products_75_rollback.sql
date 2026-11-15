-- ==============================================================================
-- PHONENEST MIGRATION 12 v2 ROLLBACK (2B.7D)
--
-- Hoan tac lan chay M12 v2 GAN NHAT (hoac @pn_rollback_run_id) dua tren pn_data_backup.
-- Loi da sua so voi v1: v1 khoi phuc trangThai cho TAT CA san pham/bien the tu bang chup (ghi de moi
-- quyet dinh Admin sau do), gan cung 514/1 va DROP bang backup — voi --force lenh DROP chay ca khi
-- rollback that bai. v2:
--   * chi khoi phuc dong ma gia tri hien tai VAN LA gia tri M12 da dat (Admin da doi -> giu nguyen,
--     bao cao so dong bo qua);
--   * sao luu append-only truoc khi hoan tac; KHONG BAO GIO xoa bang backup;
--   * moi thao tac ghi nam sau buoc xac minh database dich.
-- Khong ho tro hoan tac ban v1 (bang SanPham_backup_pre75): tra REFUSED, xu ly thu cong.
-- Database chinh: SET @pn_confirm_rollback_12 = CONCAT('ROLLBACK-12-ON-', DATABASE());
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_db VARCHAR(64);
    DECLARE v_src_run VARCHAR(40) DEFAULT NULL;
    DECLARE v_run_id VARCHAR(40) DEFAULT NULL;
    DECLARE v_in_dml INT DEFAULT 0;
    DECLARE v_sp INT DEFAULT 0;
    DECLARE v_pb INT DEFAULT 0;
    DECLARE v_sp_skip INT DEFAULT 0;
    DECLARE v_pb_skip INT DEFAULT 0;
    DECLARE v_orders_before INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_rb12_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_run_id IS NOT NULL THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('12', v_run_id, 'ROLLBACK_FAILED', LEFT(@pn_rb12_err, 500));
        END IF;
        SELECT 'FAILED' AS ketQua, @pn_rb12_err AS chiTiet;
    END;

    pn: BEGIN
        SET v_db = DATABASE();
        IF v_db IS NULL THEN
            SELECT 'REFUSED' AS ketQua, 'Chua chon database' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_db = 'bandienthoai'
           AND (@pn_confirm_rollback_12 IS NULL OR @pn_confirm_rollback_12 <> CONCAT('ROLLBACK-12-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua, 'Database chinh: can bien xac nhan @pn_confirm_rollback_12' AS chiTiet;
            LEAVE pn;
        END IF;
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua, 'Mot tien trinh migration khac dang chay' AS chiTiet;
            LEAVE pn;
        END IF;

        IF (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = v_db AND TABLE_NAME IN ('pn_migration_log', 'pn_data_backup')) <> 2 THEN
            IF (SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = v_db AND LOWER(TABLE_NAME) = 'sanpham_backup_pre75') > 0 THEN
                SELECT 'REFUSED' AS ketQua,
                       'Chi co bang backup cua M12 v1 (SanPham_backup_pre75). Rollback v2 khong ho tro ban v1; can xu ly thu cong, khong ghi de du lieu hien tai.' AS chiTiet;
            ELSE
                SELECT 'REFUSED' AS ketQua, 'Khong co pn_migration_log / pn_data_backup: M12 v2 chua tung chay' AS chiTiet;
            END IF;
            LEAVE pn;
        END IF;

        SET v_src_run = @pn_rollback_run_id;
        IF v_src_run IS NULL THEN
            SELECT run_id INTO v_src_run FROM pn_migration_log
            WHERE migration_id = '12' AND trang_thai = 'COMPLETED' ORDER BY log_id DESC LIMIT 1;
        END IF;
        IF v_src_run IS NULL OR (SELECT COUNT(*) FROM pn_data_backup
                                 WHERE run_id = v_src_run AND migration_id = '12' AND giai_doan = 'M12_TRUOC_DML') = 0 THEN
            SELECT 'REFUSED' AS ketQua, 'Khong tim thay lan chay M12 COMPLETED co du lieu sao luu' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT trang_thai FROM pn_migration_log WHERE migration_id = '12' ORDER BY log_id DESC LIMIT 1) = 'ROLLED_BACK'
           AND @pn_rollback_run_id IS NULL THEN
            SELECT 'ALREADY_ROLLED_BACK' AS ketQua, 'Lan chay M12 gan nhat da duoc hoan tac. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;

        SET v_run_id = CONCAT('RB12-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
        SELECT COUNT(*) INTO v_orders_before FROM donhang;

        SET v_in_dml = 1;
        START TRANSACTION;

        -- Sao luu append-only gia tri HIEN TAI cua cac dong se hoan tac
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '12', 'M12_TRUOC_ROLLBACK', b.bang, b.khoa, b.cot, s.trangThai, b.gia_tri_cu
        FROM pn_data_backup b JOIN sanpham s ON b.bang = 'sanpham' AND s.maSanPham = b.khoa
        WHERE b.run_id = v_src_run AND b.giai_doan = 'M12_TRUOC_DML' AND b.cot = 'trangThai' AND s.trangThai = b.gia_tri_moi;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '12', 'M12_TRUOC_ROLLBACK', b.bang, b.khoa, b.cot, p.trangThai, b.gia_tri_cu
        FROM pn_data_backup b JOIN phienbansanpham p ON b.bang = 'phienbansanpham' AND p.maPhienBan = b.khoa
        WHERE b.run_id = v_src_run AND b.giai_doan = 'M12_TRUOC_DML' AND b.cot = 'trangThai' AND p.trangThai = b.gia_tri_moi;

        -- Chi hoan tac dong con dung gia tri M12 da dat
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M12_TRUOC_DML' AND b.bang = 'sanpham'
           AND b.cot = 'trangThai' AND b.khoa = s.maSanPham
        SET s.trangThai = b.gia_tri_cu
        WHERE s.trangThai = b.gia_tri_moi;
        SET v_sp = ROW_COUNT();
        UPDATE phienbansanpham p JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M12_TRUOC_DML' AND b.bang = 'phienbansanpham'
           AND b.cot = 'trangThai' AND b.khoa = p.maPhienBan
        SET p.trangThai = b.gia_tri_cu
        WHERE p.trangThai = b.gia_tri_moi;
        SET v_pb = ROW_COUNT();

        SELECT COUNT(*) - v_sp INTO v_sp_skip FROM pn_data_backup
        WHERE run_id = v_src_run AND giai_doan = 'M12_TRUOC_DML' AND bang = 'sanpham';
        SELECT COUNT(*) - v_pb INTO v_pb_skip FROM pn_data_backup
        WHERE run_id = v_src_run AND giai_doan = 'M12_TRUOC_DML' AND bang = 'phienbansanpham';

        IF (SELECT COUNT(*) FROM donhang) <> v_orders_before THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: so don hang thay doi trong rollback';
        END IF;

        COMMIT;
        SET v_in_dml = 0;
        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('12', v_run_id, 'ROLLED_BACK',
                CONCAT('nguon=', v_src_run, ', san pham=', v_sp, ' (bo qua vi Admin da doi: ', v_sp_skip,
                       '), bien the=', v_pb, ' (bo qua: ', v_pb_skip, ')'));
        SELECT 'ROLLED_BACK' AS ketQua, v_src_run AS nguon, v_sp AS sanPham, v_pb AS bienThe,
               v_sp_skip AS sanPhamBoQua, v_pb_skip AS bienTheBoQua;
    END pn;
END$$
DELIMITER ;
