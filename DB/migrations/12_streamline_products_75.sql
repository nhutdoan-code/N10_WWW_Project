-- ==============================================================================
-- PHONENEST MIGRATION 12 v2 (2B.7D): THU GON DANH MUC KINH DOANH CON 75 MODEL
-- Loai: MIGRATION DU LIEU (khong doi schema san pham). Chua duoc phe duyet cho database chinh.
--
-- Thay doi so voi v1 (AGY) — cac loi da sua:
--   * v1 tao bang backup (CREATE TABLE ... AS SELECT) va stored procedure TRUOC khi kiem tra
--     database dich -> van ghi vao database chinh du procedure tu choi. v2: MOI thao tac ghi nam
--     trong mot khoi BEGIN NOT ATOMIC, SAU buoc xac minh database dich; khong tao procedure.
--   * v1 gan cung 515/75/440/143 -> khong dung duoc tren du lieu thuc te. v2: hau kiem tuong doi.
--   * v1 tu KICH HOAT LAI bien the/san pham trong danh muc 75 (ghi de quyet dinh ngung kinh doanh
--     cua Admin). v2: chi chuyen DANG_KINH_DOANH -> NGUNG_KINH_DOANH cho san pham NGOAI danh muc;
--     khong bao gio kich hoat lai.
--   * v1 backup bang CREATE TABLE IF NOT EXISTS (chay lai giu ban chup cu). v2: sao luu append-only
--     tung gia tri bi doi vao pn_data_backup (run_id rieng).
--
-- Chay:  mariadb -D <db> < 12_streamline_products_75.sql
-- Database chinh: chi khi duoc phe duyet, sau khi sao luu, voi
--   SET @pn_confirm_migration_12 = CONCAT('APPLY-12-ON-', DATABASE());
-- Ket qua dong dau: REFUSED* / ALREADY_COMPLETED / SKIPPED_EXISTING_DATA / COMPLETED / FAILED.
-- Rollback: 12_streamline_products_75_rollback.sql (chi hoan tac dong con dung gia tri do M12 dat).
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_db VARCHAR(64);
    DECLARE v_run_id VARCHAR(40) DEFAULT NULL;
    DECLARE v_has_log INT DEFAULT 0;
    DECLARE v_last_state VARCHAR(30) DEFAULT NULL;
    DECLARE v_in_dml INT DEFAULT 0;
    DECLARE v_whitelist_found INT DEFAULT 0;
    DECLARE v_sp_plan INT DEFAULT 0;
    DECLARE v_pb_plan INT DEFAULT 0;
    DECLARE v_sp_done INT DEFAULT 0;
    DECLARE v_pb_done INT DEFAULT 0;
    DECLARE v_orders_before INT DEFAULT 0;
    DECLARE v_orders_after INT DEFAULT 0;
    DECLARE v_lines_before INT DEFAULT 0;
    DECLARE v_lines_after INT DEFAULT 0;
    DECLARE v_wl_before VARCHAR(64);
    DECLARE v_wl_after VARCHAR(64);
    DECLARE v_wl_inactive INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_m12_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_run_id IS NOT NULL THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('12', v_run_id, 'FAILED', LEFT(@pn_m12_err, 500));
        END IF;
        SELECT 'FAILED' AS ketQua, @pn_m12_err AS chiTiet;
    END;

    pn: BEGIN
        -- (1) Database dich — TRUOC moi thao tac ghi -------------------------------
        SET v_db = DATABASE();
        IF v_db IS NULL THEN
            SELECT 'REFUSED' AS ketQua, 'Chua chon database (dung -D <db>)' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_db = 'bandienthoai'
           AND (@pn_confirm_migration_12 IS NULL
                OR @pn_confirm_migration_12 <> CONCAT('APPLY-12-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua,
                   'Database chinh: can SET @pn_confirm_migration_12 = CONCAT(''APPLY-12-ON-'', DATABASE()) sau khi sao luu va duoc phe duyet' AS chiTiet;
            LEAVE pn;
        END IF;
        -- Khoa chung cho moi migration PhoneNest tren database nay (giai phong khi phien ket thuc)
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua, 'Mot tien trinh migration khac dang chay tren database nay' AS chiTiet;
            LEAVE pn;
        END IF;

        -- (2) Dieu kien tien quyet (chi doc) -------------------------------------
        IF (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = v_db AND TABLE_NAME IN ('sanpham', 'phienbansanpham', 'donhang', 'chitietdonhang')) <> 4 THEN
            SELECT 'REFUSED' AS ketQua, 'Thieu bang sanpham/phienbansanpham/donhang/chitietdonhang' AS chiTiet;
            LEAVE pn;
        END IF;

        CREATE TEMPORARY TABLE pn_tmp_m12_whitelist (maSanPham VARCHAR(50) PRIMARY KEY);
        INSERT INTO pn_tmp_m12_whitelist VALUES
            ('SP500430'), ('SP001'), ('SP500417'), ('SP500360'), ('SP500404'),
            ('SP500390'), ('SP500344'), ('SP002'), ('SP500375'), ('SP500327'),
            ('SP500309'), ('SP500269'), ('SP003'), ('SP500468'), ('SP500405'),
            ('SP500455'), ('SP004'), ('SP500481'), ('SP500391'), ('SP005'),
            ('SP500376'), ('SP500448'), ('SP500476'), ('SP500361'), ('SP500310'),
            ('SP500290'), ('SP500345'), ('SP1793202488261001'), ('SP006'), ('SP500497'),
            ('SP500392'), ('SP500329'), ('SP500377'), ('SP007'), ('SP500362'),
            ('SP500346'), ('SP500311'), ('SP500291'), ('SP009'), ('SP500498'),
            ('SP008'), ('SP500494'), ('SP010'), ('SP500490'), ('SP500486'),
            ('SP500482'), ('SP500470'), ('SP500187'), ('SP011'), ('SP500097'),
            ('SP500407'), ('SP012'), ('SP500393'), ('SP013'), ('SP500273'),
            ('SP500217'), ('SP500421'), ('SP500408'), ('SP500472'), ('SP500218'),
            ('SP500189'), ('SP500445'), ('SP500435'), ('SP500397'), ('SP500367'),
            ('SP500334'), ('SP500351'), ('SP500315'), ('SP500275'), ('SP500301'),
            ('SP500135'), ('SP500352'), ('SP500138'), ('SP500385'), ('SP500488');

        -- Danh muc phai ton tai day du: neu thieu ma nao thi du lieu khac voi du lieu da duoc ra soat -> dung
        SELECT COUNT(*) INTO v_whitelist_found FROM pn_tmp_m12_whitelist w JOIN sanpham s ON s.maSanPham = w.maSanPham;
        IF v_whitelist_found <> (SELECT COUNT(*) FROM pn_tmp_m12_whitelist) THEN
            SELECT 'REFUSED' AS ketQua,
                   CONCAT('Chi tim thay ', v_whitelist_found, '/', (SELECT COUNT(*) FROM pn_tmp_m12_whitelist),
                          ' ma san pham trong danh muc 75 model. Du lieu khac voi ban da ra soat; khong thay doi gi.') AS chiTiet;
            DROP TEMPORARY TABLE pn_tmp_m12_whitelist;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_has_log FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'pn_migration_log';
        IF v_has_log > 0 THEN
            SELECT trang_thai INTO v_last_state FROM pn_migration_log
            WHERE migration_id = '12' ORDER BY log_id DESC LIMIT 1;
        END IF;
        IF v_last_state = 'COMPLETED' THEN
            SELECT 'ALREADY_COMPLETED' AS ketQua, 'Migration 12 da hoan tat truoc do. Khong thay doi gi.' AS chiTiet;
            DROP TEMPORARY TABLE pn_tmp_m12_whitelist;
            LEAVE pn;
        END IF;

        -- Ke hoach: chi nhung dong DANG kinh doanh NGOAI danh muc
        SELECT COUNT(*) INTO v_sp_plan FROM sanpham s
        WHERE s.trangThai = 'DANG_KINH_DOANH'
          AND s.maSanPham NOT IN (SELECT maSanPham FROM pn_tmp_m12_whitelist);
        SELECT COUNT(*) INTO v_pb_plan FROM phienbansanpham p
        WHERE p.trangThai = 'DANG_KINH_DOANH'
          AND p.maSanPham NOT IN (SELECT maSanPham FROM pn_tmp_m12_whitelist);
        SELECT COUNT(*) INTO v_wl_inactive FROM sanpham s JOIN pn_tmp_m12_whitelist w ON w.maSanPham = s.maSanPham
        WHERE s.trangThai <> 'DANG_KINH_DOANH';

        -- (3) Log + sao luu append-only (DDL tu commit: tao TRUOC khi mo giao dich DML) -------
        SET v_run_id = CONCAT('M12-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
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

        IF v_sp_plan = 0 AND v_pb_plan = 0 THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('12', v_run_id, 'SKIPPED_EXISTING_DATA',
                    CONCAT('Moi san pham/bien the ngoai danh muc da ngung kinh doanh. Khong thay doi. San pham trong danh muc dang ngung: ', v_wl_inactive));
            SELECT 'SKIPPED_EXISTING_DATA' AS ketQua,
                   CONCAT('Khong con dong nao can chuyen. San pham trong danh muc dang ngung (KHONG tu kich hoat lai): ', v_wl_inactive) AS chiTiet;
            DROP TEMPORARY TABLE pn_tmp_m12_whitelist;
            LEAVE pn;
        END IF;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('12', v_run_id, 'STARTED', CONCAT('ke hoach: san pham=', v_sp_plan, ', bien the=', v_pb_plan));

        -- (4) DML trong mot giao dich ----------------------------------------------
        SELECT COUNT(*) INTO v_orders_before FROM donhang;
        SELECT COUNT(*) INTO v_lines_before FROM chitietdonhang;
        SELECT MD5(GROUP_CONCAT(s.maSanPham, ':', s.trangThai ORDER BY s.maSanPham)) INTO v_wl_before
        FROM sanpham s JOIN pn_tmp_m12_whitelist w ON w.maSanPham = s.maSanPham;

        SET v_in_dml = 1;
        START TRANSACTION;

        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '12', 'M12_TRUOC_DML', 'sanpham', s.maSanPham, 'trangThai', s.trangThai, 'NGUNG_KINH_DOANH'
        FROM sanpham s
        WHERE s.trangThai = 'DANG_KINH_DOANH'
          AND s.maSanPham NOT IN (SELECT maSanPham FROM pn_tmp_m12_whitelist);
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '12', 'M12_TRUOC_DML', 'phienbansanpham', p.maPhienBan, 'trangThai', p.trangThai, 'NGUNG_KINH_DOANH'
        FROM phienbansanpham p
        WHERE p.trangThai = 'DANG_KINH_DOANH'
          AND p.maSanPham NOT IN (SELECT maSanPham FROM pn_tmp_m12_whitelist);

        UPDATE sanpham s
        SET s.trangThai = 'NGUNG_KINH_DOANH'
        WHERE s.trangThai = 'DANG_KINH_DOANH'
          AND s.maSanPham NOT IN (SELECT maSanPham FROM pn_tmp_m12_whitelist);
        SET v_sp_done = ROW_COUNT();
        UPDATE phienbansanpham p
        SET p.trangThai = 'NGUNG_KINH_DOANH'
        WHERE p.trangThai = 'DANG_KINH_DOANH'
          AND p.maSanPham NOT IN (SELECT maSanPham FROM pn_tmp_m12_whitelist);
        SET v_pb_done = ROW_COUNT();

        -- (5) Hau kiem TUONG DOI trong giao dich ----------------------------------
        SELECT COUNT(*) INTO v_orders_after FROM donhang;
        SELECT COUNT(*) INTO v_lines_after FROM chitietdonhang;
        SELECT MD5(GROUP_CONCAT(s.maSanPham, ':', s.trangThai ORDER BY s.maSanPham)) INTO v_wl_after
        FROM sanpham s JOIN pn_tmp_m12_whitelist w ON w.maSanPham = s.maSanPham;
        IF v_sp_done <> v_sp_plan OR v_pb_done <> v_pb_plan THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: so dong cap nhat khac ke hoach (co thao tac dong thoi?)';
        END IF;
        IF v_orders_after <> v_orders_before OR v_lines_after <> v_lines_before THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: so don hang / dong don hang thay doi';
        END IF;
        IF NOT (v_wl_after <=> v_wl_before) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: trang thai san pham trong danh muc 75 bi thay doi';
        END IF;
        IF (SELECT COUNT(*) FROM sanpham s
            WHERE s.trangThai = 'DANG_KINH_DOANH' AND s.maSanPham NOT IN (SELECT maSanPham FROM pn_tmp_m12_whitelist)) > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HAU KIEM: con san pham ngoai danh muc dang kinh doanh';
        END IF;

        COMMIT;
        SET v_in_dml = 0;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('12', v_run_id, 'COMPLETED',
                CONCAT('san pham ngung=', v_sp_done, ', bien the ngung=', v_pb_done,
                       ', san pham trong danh muc dang ngung (khong kich hoat lai)=', v_wl_inactive));
        SELECT 'COMPLETED' AS ketQua, v_run_id AS runId, v_sp_done AS sanPhamNgung, v_pb_done AS bienTheNgung,
               v_wl_inactive AS danhMucDangNgungKhongKichHoatLai;
        DROP TEMPORARY TABLE pn_tmp_m12_whitelist;
    END pn;
END$$
DELIMITER ;
