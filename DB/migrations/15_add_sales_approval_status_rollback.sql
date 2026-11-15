-- ==============================================================================
-- ROLLBACK MIGRATION 15 (v2 - Giai doan 2B.7B)
-- ------------------------------------------------------------------------------
--   - Tu choi neu cot cua Migration 16 con ton tai (phai rollback 16 truoc).
--   - Sao luu TOAN BO du lieu phe duyet vao pn_pheduyet_backup (append-only,
--     giai_doan = 'M15_TRUOC_ROLLBACK') va chi DROP COLUMN khi so dong sao luu
--     khop so dong bien the.
--   - Khong xoa bang sao luu / bang log. Khoi phuc bang
--     15_16_restore_approval_from_backup.sql.
--   - Database chinh can: SET @pn_confirm_rollback_15 = CONCAT('ROLLBACK-15-ON-', DATABASE());
-- Moi lenh ghi nam trong mot khoi BEGIN NOT ATOMIC (an toan voi --force).
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_run_id      VARCHAR(40);
    DECLARE v_db          VARCHAR(64);
    DECLARE v_has_col     INT DEFAULT 0;
    DECLARE v_has_m16     INT DEFAULT 0;
    DECLARE v_total       INT DEFAULT 0;
    DECLARE v_backup_rows INT DEFAULT 0;
    DECLARE v_approved    INT DEFAULT 0;
    DECLARE v_ready       INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_rb15_err = MESSAGE_TEXT;
        IF v_ready = 1 THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('15', v_run_id, 'ROLLBACK_FAILED', LEFT(@pn_rb15_err, 500));
        END IF;
        SELECT 'FAILED' AS ketQua, @pn_rb15_err AS chiTiet;
    END;

    pn: BEGIN
        SET v_db = DATABASE();
        IF v_db IS NULL THEN
            SELECT 'REFUSED' AS ketQua, 'Chua chon database' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_db = 'bandienthoai'
           AND (@pn_confirm_rollback_15 IS NULL
                OR @pn_confirm_rollback_15 <> CONCAT('ROLLBACK-15-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua, 'Database chinh: can bien xac nhan @pn_confirm_rollback_15' AS chiTiet;
            LEAVE pn;
        END IF;

        -- (2B.7C) Chong chay dong thoi: MOT khoa chung cho M15/M16/rollback/restore tren
        -- cung database. Khoa thuoc phien; tu giai phong khi phien ket thuc.
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua,
                   'Mot tien trinh khac dang chay Migration 15/16, rollback hoac restore tren database nay. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_has_col FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham'
          AND COLUMN_NAME IN ('trangThaiPheDuyet', 'nguoiPheDuyet', 'ngayPheDuyet', 'lyDoPheDuyet');
        IF v_has_col = 0 THEN
            SELECT 'NOTHING_TO_ROLLBACK' AS ketQua, 'Khong con cot phe duyet cua Migration 15' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_has_col <> 4 THEN
            SELECT 'REFUSED' AS ketQua, 'Trang thai cot dang do (khong du 4 cot). Can kiem tra thu cong.' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_has_m16 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham'
          AND COLUMN_NAME IN ('giaPheDuyet', 'giaNiemYetPheDuyet', 'nguonXacMinh', 'dauVanTayPheDuyet', 'lyDoKhoa', 'giaBanKhiKhoa');
        IF v_has_m16 > 0 THEN
            SELECT 'REFUSED' AS ketQua, 'Cot cua Migration 16 con ton tai. Chay 16_..._rollback.sql truoc.' AS chiTiet;
            LEAVE pn;
        END IF;

        SET v_run_id = CONCAT('RB15-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
        CREATE TABLE IF NOT EXISTS pn_migration_log (
            log_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
            migration_id VARCHAR(20)  NOT NULL,
            run_id       VARCHAR(40)  NOT NULL,
            trang_thai   VARCHAR(30)  NOT NULL,
            thoi_diem    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
            ghi_chu      VARCHAR(500) NULL,
            INDEX idx_pn_migration_log_mid (migration_id, log_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
        CREATE TABLE IF NOT EXISTS pn_pheduyet_backup (
            backup_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
            run_id             VARCHAR(40)   NOT NULL,
            giai_doan          VARCHAR(40)   NOT NULL,
            thoi_diem          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
            maPhienBan         VARCHAR(50)   NOT NULL,
            maSanPham          VARCHAR(50)   NOT NULL,
            sku                VARCHAR(100)  NULL,
            ram                INT           NULL,
            boNho              INT           NULL,
            mauSac             VARCHAR(100)  NULL,
            giaBan             DECIMAL(15,2) NULL,
            giaNiemYet         DECIMAL(15,2) NULL,
            soLuongTon         INT           NULL,
            trangThai          VARCHAR(30)   NULL,
            trangThaiPheDuyet  VARCHAR(20)   NULL,
            nguoiPheDuyet      VARCHAR(50)   NULL,
            ngayPheDuyet       DATETIME      NULL,
            lyDoPheDuyet       VARCHAR(255)  NULL,
            giaPheDuyet        DECIMAL(15,2) NULL,
            giaNiemYetPheDuyet DECIMAL(15,2) NULL,
            nguonXacMinh       VARCHAR(500)  NULL,
            dauVanTayPheDuyet  CHAR(64)      NULL,
            lyDoKhoa           VARCHAR(40)   NULL,
            giaBanKhiKhoa      DECIMAL(15,2) NULL,
            INDEX idx_pn_pheduyet_backup_run (run_id, giai_doan),
            INDEX idx_pn_pheduyet_backup_pb (maPhienBan)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
        SET v_ready = 1;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai) VALUES ('15', v_run_id, 'ROLLBACK_STARTED');

        INSERT INTO pn_pheduyet_backup (run_id, giai_doan, maPhienBan, maSanPham, sku, ram, boNho, mauSac,
                                        giaBan, giaNiemYet, soLuongTon, trangThai,
                                        trangThaiPheDuyet, nguoiPheDuyet, ngayPheDuyet, lyDoPheDuyet)
        SELECT v_run_id, 'M15_TRUOC_ROLLBACK', maPhienBan, maSanPham, sku, ram, boNho, mauSac,
               giaBan, giaNiemYet, soLuongTon, trangThai,
               trangThaiPheDuyet, nguoiPheDuyet, ngayPheDuyet, lyDoPheDuyet
        FROM phienbansanpham;
        SET v_backup_rows = ROW_COUNT();

        SELECT COUNT(*) INTO v_total FROM phienbansanpham;
        SELECT COUNT(*) INTO v_approved FROM phienbansanpham WHERE trangThaiPheDuyet = 'APPROVED';
        IF v_backup_rows <> v_total THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sao luu khong du so dong. Huy rollback, khong DROP COLUMN.';
        END IF;

        ALTER TABLE phienbansanpham
            DROP COLUMN IF EXISTS lyDoPheDuyet,
            DROP COLUMN IF EXISTS ngayPheDuyet,
            DROP COLUMN IF EXISTS nguoiPheDuyet,
            DROP COLUMN IF EXISTS trangThaiPheDuyet;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('15', v_run_id, 'ROLLED_BACK',
                CONCAT('sao luu=', v_backup_rows, ' dong (APPROVED=', v_approved, '); run_id sao luu=', v_run_id));
        SELECT 'ROLLED_BACK' AS ketQua, v_run_id AS runIdSaoLuu, v_backup_rows AS dongSaoLuu, v_approved AS approvedDaSaoLuu;
    END pn;
END$$
DELIMITER ;
