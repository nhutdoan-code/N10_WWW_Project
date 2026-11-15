-- ==============================================================================
-- MIGRATION 15 (v2 - Giai doan 2B.7B): TRANG THAI PHE DUYET BAN HANG
-- ------------------------------------------------------------------------------
-- Muc tieu:
--   Them 4 cot phe duyet vao phienbansanpham va khoi tao trang thai an toan:
--     - Bien the ngung kinh doanh            -> BLOCKED
--     - 11 bien the gia seed bat thuong      -> BLOCKED
--     - Toan bo bien the dang kinh doanh con lai -> PENDING_REVIEW
--   KHONG tu dong APPROVED bat ky bien the nao (moi phe duyet phai qua API
--   PUT /api/admin/variants/{ma}/approval voi day du can cu xac minh).
--
-- Thiet ke an toan (khac phien ban v1):
--   1. Moi lenh ghi nam trong MOT khoi BEGIN NOT ATOMIC duy nhat. Khi mot dieu
--      kien tien quyet khong dat, khoi thoat bang LEAVE truoc moi lenh ghi.
--      Vi vay chay bang `mariadb --force` hoac cong cu GUI "continue on error"
--      cung khong the ghi de du lieu: khong co lenh DML nao nam ngoai khoi.
--   2. Khong tao stored procedure (khong ghi vao mysql.proc).
--   3. Khong gan cung so luong ban ghi/don hang; hau kiem so sanh truoc/sau.
--   4. Trang thai migration luu trong bang pn_migration_log
--      (STARTED / DDL_DONE / COMPLETED / FAILED / SKIPPED_* / ROLLED_BACK).
--   5. Sao luu append-only vao pn_pheduyet_backup (khong CREATE OR REPLACE).
--   6. Chi khoi tao nhung dong con o trang thai mac dinh chua bi dong cham
--      (nguoiPheDuyet IS NULL AND ngayPheDuyet IS NULL) -> khong ghi de quyet
--      dinh cua Quan tri vien.
--   7. Database chinh 'bandienthoai' bi tu choi tru khi phien dat bien xac nhan:
--        SET @pn_confirm_migration_15 = CONCAT('APPLY-15-ON-', DATABASE());
--      (Chi lam viec nay sau khi da sao luu va duoc phe duyet.)
--
-- GIOI HAN DA BIET: DDL (ALTER TABLE) trong MariaDB gay implicit commit. Neu DML
-- that bai sau DDL, cac cot da ton tai voi gia tri mac dinh PENDING_REVIEW
-- (fail-closed: khong bien the nao ban duoc). Chay lai file se tiep tuc DML.
--
-- Cach chay: mariadb -h <host> -P <port> -u <user> -p -D <database> < 15_add_sales_approval_status.sql
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_run_id        VARCHAR(40);
    DECLARE v_db            VARCHAR(64);
    DECLARE v_has_table     INT DEFAULT 0;
    DECLARE v_has_col       INT DEFAULT 0;
    DECLARE v_last_state    VARCHAR(30) DEFAULT NULL;
    DECLARE v_touched       INT DEFAULT 0;
    DECLARE v_total_before  INT DEFAULT 0;
    DECLARE v_total_after   INT DEFAULT 0;
    DECLARE v_orders_before INT DEFAULT 0;
    DECLARE v_orders_after  INT DEFAULT 0;
    DECLARE v_lines_before  INT DEFAULT 0;
    DECLARE v_lines_after   INT DEFAULT 0;
    DECLARE v_backup_rows   INT DEFAULT 0;
    DECLARE v_approved      INT DEFAULT 0;
    DECLARE v_null_status   INT DEFAULT 0;
    DECLARE v_in_dml        INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_m15_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_run_id IS NOT NULL AND v_has_table > 0 THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('15', v_run_id, 'FAILED', LEFT(@pn_m15_err, 500));
        END IF;
        SELECT 'FAILED' AS ketQua, @pn_m15_err AS chiTiet;
    END;

    pn: BEGIN
        SET v_db = DATABASE();

        -- (1) Database dich ------------------------------------------------------
        IF v_db IS NULL THEN
            SELECT 'REFUSED' AS ketQua, 'Chua chon database (dung -D <db>)' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_db = 'bandienthoai'
           AND (@pn_confirm_migration_15 IS NULL
                OR @pn_confirm_migration_15 <> CONCAT('APPLY-15-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua,
                   'Database chinh bandienthoai: can SET @pn_confirm_migration_15 = CONCAT(''APPLY-15-ON-'', DATABASE()) sau khi sao luu va duoc phe duyet' AS chiTiet;
            LEAVE pn;
        END IF;

        -- (2B.7C) Chong chay dong thoi: MOT khoa chung cho M15/M16/rollback/restore tren
        -- cung database. Khoa thuoc phien; tu giai phong khi phien ket thuc.
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua,
                   'Mot tien trinh khac dang chay Migration 15/16, rollback hoac restore tren database nay. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;

        -- (2) Dieu kien tien quyet (chi doc) -------------------------------------
        IF (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = v_db AND TABLE_NAME IN ('phienbansanpham', 'sanpham', 'donhang', 'chitietdonhang')) <> 4 THEN
            SELECT 'REFUSED' AS ketQua, 'Thieu bang phienbansanpham/sanpham/donhang/chitietdonhang' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_has_table FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'pn_migration_log';

        IF v_has_table > 0 THEN
            SELECT trang_thai INTO v_last_state FROM pn_migration_log
            WHERE migration_id = '15' ORDER BY log_id DESC LIMIT 1;
        END IF;
        IF v_last_state = 'COMPLETED' THEN
            -- (2B.7C) Log COMPLETED nhung schema thieu (vd. cot bi xoa tay): KHONG tu sua,
            -- khong ghi log (tranh lan chay sau tu "hoan tat" lai). Nguoi van hanh xu ly theo runbook.
            SELECT COUNT(*) INTO v_has_col FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham'
              AND COLUMN_NAME IN ('trangThaiPheDuyet', 'nguoiPheDuyet', 'ngayPheDuyet', 'lyDoPheDuyet');
            IF v_has_col <> 4 THEN
                SELECT 'INCONSISTENT_SCHEMA' AS ketQua,
                       CONCAT('Log ghi COMPLETED nhung chi co ', v_has_col, '/4 cot phe duyet. Khong thay doi gi; can kiem tra va xu ly thu cong.') AS chiTiet;
                LEAVE pn;
            END IF;
            SELECT 'ALREADY_COMPLETED' AS ketQua, 'Migration 15 da hoan tat truoc do. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_has_col FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham' AND COLUMN_NAME = 'trangThaiPheDuyet';

        IF v_has_col > 0 THEN
            -- Cot da ton tai: chi cho phep tiep tuc neu MOI dong con o trang thai
            -- mac dinh chua dong cham (truong hop DDL xong nhung DML that bai).
            SELECT COUNT(*) INTO v_touched FROM phienbansanpham
            WHERE trangThaiPheDuyet <> 'PENDING_REVIEW'
               OR nguoiPheDuyet IS NOT NULL
               OR ngayPheDuyet IS NOT NULL
               OR lyDoPheDuyet IS NOT NULL;
            IF v_touched > 0 THEN
                SET v_run_id = CONCAT('M15-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
                CREATE TABLE IF NOT EXISTS pn_migration_log (
                    log_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
                    migration_id VARCHAR(20)  NOT NULL,
                    run_id       VARCHAR(40)  NOT NULL,
                    trang_thai   VARCHAR(30)  NOT NULL,
                    thoi_diem    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    ghi_chu      VARCHAR(500) NULL,
                    INDEX idx_pn_migration_log_mid (migration_id, log_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                SET v_has_table = 1;
                INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
                VALUES ('15', v_run_id, 'SKIPPED_EXISTING_DATA',
                        CONCAT('Cot phe duyet da co du lieu tren ', v_touched, ' dong (ban v1 hoac quyet dinh Admin). Khong ghi de.'));
                SELECT 'SKIPPED_EXISTING_DATA' AS ketQua,
                       CONCAT('Da co du lieu phe duyet tren ', v_touched, ' dong. Khong thay doi gi.') AS chiTiet;
                LEAVE pn;
            END IF;
        END IF;

        -- (3) Bat dau: bang log + bang sao luu append-only ------------------------
        SET v_run_id = CONCAT('M15-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
        CREATE TABLE IF NOT EXISTS pn_migration_log (
            log_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
            migration_id VARCHAR(20)  NOT NULL,
            run_id       VARCHAR(40)  NOT NULL,
            trang_thai   VARCHAR(30)  NOT NULL,
            thoi_diem    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
            ghi_chu      VARCHAR(500) NULL,
            INDEX idx_pn_migration_log_mid (migration_id, log_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
        SET v_has_table = 1;

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

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('15', v_run_id, 'STARTED', CONCAT('database=', v_db));

        -- (4) DDL idempotent -----------------------------------------------------
        ALTER TABLE phienbansanpham
            ADD COLUMN IF NOT EXISTS trangThaiPheDuyet ENUM('APPROVED', 'PENDING_REVIEW', 'BLOCKED')
                NOT NULL DEFAULT 'PENDING_REVIEW' AFTER trangThai,
            ADD COLUMN IF NOT EXISTS nguoiPheDuyet VARCHAR(50) NULL AFTER trangThaiPheDuyet,
            ADD COLUMN IF NOT EXISTS ngayPheDuyet DATETIME NULL AFTER nguoiPheDuyet,
            ADD COLUMN IF NOT EXISTS lyDoPheDuyet VARCHAR(255) NULL AFTER ngayPheDuyet;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai) VALUES ('15', v_run_id, 'DDL_DONE');

        -- (5) Sao luu truoc DML (append-only) -------------------------------------
        INSERT INTO pn_pheduyet_backup (run_id, giai_doan, maPhienBan, maSanPham, sku, ram, boNho, mauSac,
                                        giaBan, giaNiemYet, soLuongTon, trangThai,
                                        trangThaiPheDuyet, nguoiPheDuyet, ngayPheDuyet, lyDoPheDuyet)
        SELECT v_run_id, 'M15_TRUOC_DML', maPhienBan, maSanPham, sku, ram, boNho, mauSac,
               giaBan, giaNiemYet, soLuongTon, trangThai,
               trangThaiPheDuyet, nguoiPheDuyet, ngayPheDuyet, lyDoPheDuyet
        FROM phienbansanpham;
        SET v_backup_rows = ROW_COUNT();

        SELECT COUNT(*) INTO v_total_before FROM phienbansanpham;
        IF v_backup_rows <> v_total_before THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sao luu truoc DML khong du so dong';
        END IF;

        -- (6) DML trong mot giao dich -----------------------------------------------
        SET v_in_dml = 1;
        START TRANSACTION;

        SELECT COUNT(*) INTO v_total_before  FROM phienbansanpham;
        SELECT COUNT(*) INTO v_orders_before FROM donhang;
        SELECT COUNT(*) INTO v_lines_before  FROM chitietdonhang;

        -- 6.1 Bien the ngung kinh doanh (hoac thuoc san pham ngung kinh doanh) -> BLOCKED
        UPDATE phienbansanpham p
        JOIN sanpham s ON s.maSanPham = p.maSanPham
        SET p.trangThaiPheDuyet = 'BLOCKED',
            p.nguoiPheDuyet = 'SYSTEM:M15',
            p.ngayPheDuyet = NOW(),
            p.lyDoPheDuyet = 'Khoa tu dong: san pham/bien the da ngung kinh doanh'
        WHERE (p.trangThai = 'NGUNG_KINH_DOANH' OR s.trangThai = 'NGUNG_KINH_DOANH')
          AND p.trangThaiPheDuyet = 'PENDING_REVIEW'
          AND p.nguoiPheDuyet IS NULL AND p.ngayPheDuyet IS NULL;

        -- 6.2 11 bien the gia seed bat thuong -> BLOCKED
        UPDATE phienbansanpham p
        SET p.trangThaiPheDuyet = 'BLOCKED',
            p.nguoiPheDuyet = 'SYSTEM:M15',
            p.ngayPheDuyet = NOW(),
            p.lyDoPheDuyet = 'Khoa tu dong: gia seed bat thuong, chua xac minh gia thi truong'
        WHERE p.maPhienBan IN ('PB500476', 'PB500481', 'PB500448', 'PB500455', 'PB500138',
                               'PB500310', 'PB500345', 'PB500361', 'PB500376', 'PB500391', 'PB500488')
          AND p.trangThaiPheDuyet = 'PENDING_REVIEW'
          AND p.nguoiPheDuyet IS NULL AND p.ngayPheDuyet IS NULL;

        -- 6.3 Con lai: PENDING_REVIEW (gia tri mac dinh), chi ghi ly do
        UPDATE phienbansanpham p
        SET p.lyDoPheDuyet = 'Chua tham dinh theo quy trinh phe duyet ban hang'
        WHERE p.trangThaiPheDuyet = 'PENDING_REVIEW'
          AND p.nguoiPheDuyet IS NULL AND p.ngayPheDuyet IS NULL
          AND p.lyDoPheDuyet IS NULL;

        -- (7) Hau kiem tuong doi (khong gan cung so luong) ---------------------------
        SELECT COUNT(*) INTO v_total_after  FROM phienbansanpham;
        SELECT COUNT(*) INTO v_orders_after FROM donhang;
        SELECT COUNT(*) INTO v_lines_after  FROM chitietdonhang;
        SELECT COUNT(*) INTO v_approved     FROM phienbansanpham WHERE trangThaiPheDuyet = 'APPROVED';
        SELECT COUNT(*) INTO v_null_status  FROM phienbansanpham WHERE trangThaiPheDuyet IS NULL;

        IF v_total_after <> v_total_before OR v_orders_after <> v_orders_before OR v_lines_after <> v_lines_before THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Hau kiem that bai: so dong bien the/don hang/chi tiet don thay doi';
        END IF;
        IF v_approved <> 0 OR v_null_status <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Hau kiem that bai: migration khong duoc tao APPROVED hoac trang thai NULL';
        END IF;

        COMMIT;
        SET v_in_dml = 0;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('15', v_run_id, 'COMPLETED',
                CONCAT('bien the=', v_total_after, ', don hang=', v_orders_after, ', sao luu=', v_backup_rows));
        SELECT 'COMPLETED' AS ketQua, v_run_id AS runId, v_total_after AS bienThe, v_backup_rows AS dongSaoLuu;
    END pn;
END$$
DELIMITER ;

-- Bao cao (chi doc)
SELECT trangThai, trangThaiPheDuyet, COUNT(*) AS soLuong
FROM phienbansanpham
GROUP BY trangThai, trangThaiPheDuyet
ORDER BY trangThai, trangThaiPheDuyet;
