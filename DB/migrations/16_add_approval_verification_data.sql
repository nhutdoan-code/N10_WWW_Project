-- ==============================================================================
-- MIGRATION 16 (Giai doan 2B.7B): DU LIEU XAC MINH PHE DUYET VA LY DO KHOA BAN
-- ------------------------------------------------------------------------------
-- Yeu cau: Migration 15 da ap dung (co cot trangThaiPheDuyet...).
-- Them cot vao phienbansanpham:
--   giaPheDuyet         DECIMAL(15,2)  gia ban da duoc xac nhan khi APPROVED
--   giaNiemYetPheDuyet  DECIMAL(15,2)  gia niem yet da duoc xac nhan khi APPROVED
--   nguonXacMinh        VARCHAR(500)   can cu / nguon xac minh gia
--   dauVanTayPheDuyet   CHAR(64)       SHA-256 cua du lieu ban (gia, cau hinh) luc duyet
--   lyDoKhoa            ENUM           nguyen nhan BLOCKED (co cau truc)
--   giaBanKhiKhoa       DECIMAL(15,2)  gia ban tai thoi diem bi khoa
-- Backfill CHI ghi vao cot moi dang NULL:
--   - BLOCKED: lyDoKhoa = NGUNG_KINH_DOANH / GIA_SEED_CHUA_XAC_MINH / KHAC; giaBanKhiKhoa = giaBan.
--   - KHONG tao dau van tay cho cac phe duyet cu (vd. 3 bien the Galaxy A56 cua
--     M15 v1). Ung dung coi APPROVED thieu dau van tay la CHUA DUOC BAN
--     (fail-closed) cho den khi Admin duyet lai theo quy trinh moi.
-- Khong doi trangThaiPheDuyet cua bat ky dong nao (hau kiem so sanh phan bo).
-- Moi lenh ghi trong mot khoi BEGIN NOT ATOMIC (an toan voi --force).
-- Database chinh can: SET @pn_confirm_migration_16 = CONCAT('APPLY-16-ON-', DATABASE());
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_run_id      VARCHAR(40);
    DECLARE v_db          VARCHAR(64);
    DECLARE v_m15_cols    INT DEFAULT 0;
    DECLARE v_last_state  VARCHAR(30) DEFAULT NULL;
    DECLARE v_has_log     INT DEFAULT 0;
    DECLARE v_total       INT DEFAULT 0;
    DECLARE v_backup_rows INT DEFAULT 0;
    DECLARE v_dist_before VARCHAR(200);
    DECLARE v_dist_after  VARCHAR(200);
    DECLARE v_legacy      INT DEFAULT 0;
    DECLARE v_ready       INT DEFAULT 0;
    DECLARE v_in_dml      INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_m16_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_ready = 1 THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('16', v_run_id, 'FAILED', LEFT(@pn_m16_err, 500));
        END IF;
        SELECT 'FAILED' AS ketQua, @pn_m16_err AS chiTiet;
    END;

    pn: BEGIN
        SET v_db = DATABASE();
        IF v_db IS NULL THEN
            SELECT 'REFUSED' AS ketQua, 'Chua chon database' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_db = 'bandienthoai'
           AND (@pn_confirm_migration_16 IS NULL
                OR @pn_confirm_migration_16 <> CONCAT('APPLY-16-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua, 'Database chinh: can bien xac nhan @pn_confirm_migration_16' AS chiTiet;
            LEAVE pn;
        END IF;

        -- (2B.7C) Chong chay dong thoi: MOT khoa chung cho M15/M16/rollback/restore tren
        -- cung database. Khoa thuoc phien; tu giai phong khi phien ket thuc.
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua,
                   'Mot tien trinh khac dang chay Migration 15/16, rollback hoac restore tren database nay. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_m15_cols FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham'
          AND COLUMN_NAME IN ('trangThaiPheDuyet', 'nguoiPheDuyet', 'ngayPheDuyet', 'lyDoPheDuyet');
        IF v_m15_cols <> 4 THEN
            SELECT 'REFUSED' AS ketQua, 'Migration 15 chua duoc ap dung day du' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_has_log FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'pn_migration_log';
        IF v_has_log > 0 THEN
            SELECT trang_thai INTO v_last_state FROM pn_migration_log
            WHERE migration_id = '16' ORDER BY log_id DESC LIMIT 1;
        END IF;
        IF v_last_state = 'COMPLETED' THEN
            -- (2B.7C) Log COMPLETED nhung schema thieu: tu choi, khong tu sua.
            IF (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham'
                  AND COLUMN_NAME IN ('giaPheDuyet', 'giaNiemYetPheDuyet', 'nguonXacMinh',
                                      'dauVanTayPheDuyet', 'lyDoKhoa', 'giaBanKhiKhoa')) <> 6 THEN
                SELECT 'INCONSISTENT_SCHEMA' AS ketQua,
                       'Log ghi COMPLETED nhung thieu cot cua Migration 16. Khong thay doi gi; can kiem tra va xu ly thu cong.' AS chiTiet;
                LEAVE pn;
            END IF;
            SELECT 'ALREADY_COMPLETED' AS ketQua, 'Migration 16 da hoan tat. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;

        SET v_run_id = CONCAT('M16-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
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

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('16', v_run_id, 'STARTED', CONCAT('database=', v_db));

        ALTER TABLE phienbansanpham
            ADD COLUMN IF NOT EXISTS giaPheDuyet DECIMAL(15,2) NULL AFTER lyDoPheDuyet,
            ADD COLUMN IF NOT EXISTS giaNiemYetPheDuyet DECIMAL(15,2) NULL AFTER giaPheDuyet,
            ADD COLUMN IF NOT EXISTS nguonXacMinh VARCHAR(500) NULL AFTER giaNiemYetPheDuyet,
            ADD COLUMN IF NOT EXISTS dauVanTayPheDuyet CHAR(64) NULL AFTER nguonXacMinh,
            ADD COLUMN IF NOT EXISTS lyDoKhoa ENUM('GIA_SEED_CHUA_XAC_MINH', 'SAI_CAU_HINH', 'QUYET_DINH_QUAN_TRI',
                                                  'NGUNG_KINH_DOANH', 'KHAC') NULL AFTER dauVanTayPheDuyet,
            ADD COLUMN IF NOT EXISTS giaBanKhiKhoa DECIMAL(15,2) NULL AFTER lyDoKhoa;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai) VALUES ('16', v_run_id, 'DDL_DONE');

        INSERT INTO pn_pheduyet_backup (run_id, giai_doan, maPhienBan, maSanPham, sku, ram, boNho, mauSac,
                                        giaBan, giaNiemYet, soLuongTon, trangThai,
                                        trangThaiPheDuyet, nguoiPheDuyet, ngayPheDuyet, lyDoPheDuyet,
                                        giaPheDuyet, giaNiemYetPheDuyet, nguonXacMinh, dauVanTayPheDuyet,
                                        lyDoKhoa, giaBanKhiKhoa)
        SELECT v_run_id, 'M16_TRUOC_DML', maPhienBan, maSanPham, sku, ram, boNho, mauSac,
               giaBan, giaNiemYet, soLuongTon, trangThai,
               trangThaiPheDuyet, nguoiPheDuyet, ngayPheDuyet, lyDoPheDuyet,
               giaPheDuyet, giaNiemYetPheDuyet, nguonXacMinh, dauVanTayPheDuyet,
               lyDoKhoa, giaBanKhiKhoa
        FROM phienbansanpham;
        SET v_backup_rows = ROW_COUNT();
        SELECT COUNT(*) INTO v_total FROM phienbansanpham;
        IF v_backup_rows <> v_total THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sao luu truoc DML khong du so dong';
        END IF;

        SET v_in_dml = 1;
        START TRANSACTION;

        SELECT GROUP_CONCAT(CONCAT(trangThaiPheDuyet, '=', c) ORDER BY trangThaiPheDuyet) INTO v_dist_before
        FROM (SELECT trangThaiPheDuyet, COUNT(*) c FROM phienbansanpham GROUP BY trangThaiPheDuyet) x;

        UPDATE phienbansanpham p
        JOIN sanpham s ON s.maSanPham = p.maSanPham
        SET p.lyDoKhoa = CASE
                WHEN p.trangThai = 'NGUNG_KINH_DOANH' OR s.trangThai = 'NGUNG_KINH_DOANH' THEN 'NGUNG_KINH_DOANH'
                WHEN p.lyDoPheDuyet LIKE '%seed%' THEN 'GIA_SEED_CHUA_XAC_MINH'
                ELSE 'KHAC'
            END,
            p.giaBanKhiKhoa = COALESCE(p.giaBanKhiKhoa, p.giaBan)
        WHERE p.trangThaiPheDuyet = 'BLOCKED'
          AND p.lyDoKhoa IS NULL;

        SELECT GROUP_CONCAT(CONCAT(trangThaiPheDuyet, '=', c) ORDER BY trangThaiPheDuyet) INTO v_dist_after
        FROM (SELECT trangThaiPheDuyet, COUNT(*) c FROM phienbansanpham GROUP BY trangThaiPheDuyet) x;

        IF NOT (v_dist_before <=> v_dist_after) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Hau kiem that bai: phan bo trangThaiPheDuyet bi thay doi';
        END IF;

        COMMIT;
        SET v_in_dml = 0;

        SELECT COUNT(*) INTO v_legacy FROM phienbansanpham
        WHERE trangThaiPheDuyet = 'APPROVED' AND dauVanTayPheDuyet IS NULL;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('16', v_run_id, 'COMPLETED',
                CONCAT('sao luu=', v_backup_rows, '; phan bo=', v_dist_after,
                       '; APPROVED thieu dau van tay (khong ban duoc, can duyet lai)=', v_legacy));
        SELECT 'COMPLETED' AS ketQua, v_run_id AS runId, v_dist_after AS phanBo, v_legacy AS approvedCanDuyetLai;
    END pn;
END$$
DELIMITER ;
