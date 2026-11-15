-- ==============================================================================
-- KHOI PHUC DU LIEU PHE DUYET TU pn_pheduyet_backup (Giai doan 2B.7B)
-- ------------------------------------------------------------------------------
-- Dung sau khi rollback roi ap lai Migration 15 (va 16 neu co) de lay lai quyet
-- dinh phe duyet da sao luu.
--
-- Cach dung (bat buoc chon dung run_id sao luu, xem bang pn_migration_log / pn_pheduyet_backup):
--   SET @pn_restore_run_id = 'RB15-2026...';
--   SOURCE 15_16_restore_approval_from_backup.sql;
-- Database chinh can them: SET @pn_confirm_restore = CONCAT('RESTORE-ON-', DATABASE());
--
-- Quy tac (fail-closed):
--   1. Chi ghi vao dong ma trang thai phe duyet hien tai do HE THONG dat
--      (nguoiPheDuyet IS NULL hoac bat dau bang 'SYSTEM:'). Dong da duoc Admin
--      quyet dinh sau thoi diem sao luu KHONG bi ghi de.
--   2. Neu du lieu ban (maSanPham, ram, boNho, mauSac, giaBan, giaNiemYet) hien tai
--      KHOP voi ban sao luu -> khoi phuc nguyen trang thai va du lieu xac minh.
--   3. Neu KHONG khop:
--        - Ban sao luu APPROVED  -> dat PENDING_REVIEW (khong duyet du lieu da doi).
--        - Ban sao luu BLOCKED   -> khoi phuc BLOCKED (huong an toan).
--        - Ban sao luu PENDING   -> giu PENDING_REVIEW.
--   4. Phe duyet cu khong co dau van tay van khong ban duoc (ung dung fail-closed).
-- Moi lenh ghi nam trong mot khoi BEGIN NOT ATOMIC.
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_db        VARCHAR(64);
    DECLARE v_log_run   VARCHAR(40);
    DECLARE v_src_run   VARCHAR(40);
    DECLARE v_src_rows  INT DEFAULT 0;
    DECLARE v_m15_cols  INT DEFAULT 0;
    DECLARE v_m16_cols  INT DEFAULT 0;
    DECLARE v_total_b   INT DEFAULT 0;
    DECLARE v_total_a   INT DEFAULT 0;
    DECLARE v_same      INT DEFAULT 0;
    DECLARE v_demoted   INT DEFAULT 0;
    DECLARE v_blocked   INT DEFAULT 0;
    DECLARE v_in_dml    INT DEFAULT 0;
    DECLARE v_ready     INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_rs_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_ready = 1 THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('RESTORE', v_log_run, 'FAILED', LEFT(@pn_rs_err, 500));
        END IF;
        SELECT 'FAILED' AS ketQua, @pn_rs_err AS chiTiet;
    END;

    pn: BEGIN
        SET v_db = DATABASE();
        SET v_src_run = @pn_restore_run_id;
        IF v_db IS NULL OR v_src_run IS NULL THEN
            SELECT 'REFUSED' AS ketQua, 'Can chon database va SET @pn_restore_run_id truoc' AS chiTiet;
            LEAVE pn;
        END IF;
        IF v_db = 'bandienthoai'
           AND (@pn_confirm_restore IS NULL OR @pn_confirm_restore <> CONCAT('RESTORE-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua, 'Database chinh: can bien xac nhan @pn_confirm_restore' AS chiTiet;
            LEAVE pn;
        END IF;

        -- (2B.7C) Chong chay dong thoi: MOT khoa chung cho M15/M16/rollback/restore tren
        -- cung database. Khoa thuoc phien; tu giai phong khi phien ket thuc.
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua,
                   'Mot tien trinh khac dang chay Migration 15/16, rollback hoac restore tren database nay. Khong thay doi gi.' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = v_db AND TABLE_NAME IN ('pn_migration_log', 'pn_pheduyet_backup')) <> 2 THEN
            SELECT 'REFUSED' AS ketQua, 'Khong co bang pn_migration_log / pn_pheduyet_backup' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_src_rows FROM pn_pheduyet_backup WHERE run_id = v_src_run;
        IF v_src_rows = 0 THEN
            SELECT 'REFUSED' AS ketQua, CONCAT('Khong co ban sao luu voi run_id = ', v_src_run) AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(DISTINCT maPhienBan) FROM pn_pheduyet_backup WHERE run_id = v_src_run) <> v_src_rows THEN
            SELECT 'REFUSED' AS ketQua, 'Ban sao luu co nhieu dong cho cung mot bien the' AS chiTiet;
            LEAVE pn;
        END IF;

        SELECT COUNT(*) INTO v_m15_cols FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham'
          AND COLUMN_NAME IN ('trangThaiPheDuyet', 'nguoiPheDuyet', 'ngayPheDuyet', 'lyDoPheDuyet');
        SELECT COUNT(*) INTO v_m16_cols FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'phienbansanpham'
          AND COLUMN_NAME IN ('giaPheDuyet', 'giaNiemYetPheDuyet', 'nguonXacMinh', 'dauVanTayPheDuyet', 'lyDoKhoa', 'giaBanKhiKhoa');
        IF v_m15_cols <> 4 OR v_m16_cols NOT IN (0, 6) THEN
            SELECT 'REFUSED' AS ketQua, 'Can ap dung lai Migration 15 (va 16 neu dung) truoc khi khoi phuc' AS chiTiet;
            LEAVE pn;
        END IF;

        SET v_log_run = CONCAT('RS-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
        SET v_ready = 1;
        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('RESTORE', v_log_run, 'STARTED', CONCAT('nguon=', v_src_run));

        SET v_in_dml = 1;
        START TRANSACTION;
        SELECT COUNT(*) INTO v_total_b FROM phienbansanpham;

        -- (a) Du lieu ban khop -> khoi phuc nguyen trang
        IF v_m16_cols = 6 THEN
            UPDATE phienbansanpham p
            JOIN pn_pheduyet_backup b ON b.maPhienBan = p.maPhienBan AND b.run_id = v_src_run
            SET p.trangThaiPheDuyet = b.trangThaiPheDuyet, p.nguoiPheDuyet = b.nguoiPheDuyet,
                p.ngayPheDuyet = b.ngayPheDuyet, p.lyDoPheDuyet = b.lyDoPheDuyet,
                p.giaPheDuyet = b.giaPheDuyet, p.giaNiemYetPheDuyet = b.giaNiemYetPheDuyet,
                p.nguonXacMinh = b.nguonXacMinh, p.dauVanTayPheDuyet = b.dauVanTayPheDuyet,
                p.lyDoKhoa = b.lyDoKhoa, p.giaBanKhiKhoa = b.giaBanKhiKhoa
            WHERE (p.nguoiPheDuyet IS NULL OR p.nguoiPheDuyet LIKE 'SYSTEM:%')
              AND p.maSanPham = b.maSanPham AND p.ram = b.ram AND p.boNho = b.boNho
              AND p.mauSac = b.mauSac AND p.giaBan = b.giaBan AND p.giaNiemYet <=> b.giaNiemYet;
        ELSE
            UPDATE phienbansanpham p
            JOIN pn_pheduyet_backup b ON b.maPhienBan = p.maPhienBan AND b.run_id = v_src_run
            SET p.trangThaiPheDuyet = b.trangThaiPheDuyet, p.nguoiPheDuyet = b.nguoiPheDuyet,
                p.ngayPheDuyet = b.ngayPheDuyet, p.lyDoPheDuyet = b.lyDoPheDuyet
            WHERE (p.nguoiPheDuyet IS NULL OR p.nguoiPheDuyet LIKE 'SYSTEM:%')
              AND p.maSanPham = b.maSanPham AND p.ram = b.ram AND p.boNho = b.boNho
              AND p.mauSac = b.mauSac AND p.giaBan = b.giaBan AND p.giaNiemYet <=> b.giaNiemYet;
        END IF;
        SET v_same = ROW_COUNT();

        -- (b) Du lieu ban da doi, ban sao luu BLOCKED -> giu BLOCKED
        --     (dong da duoc mot lan khoi phuc truoc dat 'SYSTEM:RESTORE' thi bo qua -> idempotent)
        IF v_m16_cols = 6 THEN
            UPDATE phienbansanpham p
            JOIN pn_pheduyet_backup b ON b.maPhienBan = p.maPhienBan AND b.run_id = v_src_run
            SET p.trangThaiPheDuyet = 'BLOCKED', p.nguoiPheDuyet = 'SYSTEM:RESTORE', p.ngayPheDuyet = NOW(),
                p.lyDoPheDuyet = LEFT(CONCAT('Khoi phuc BLOCKED tu ', v_src_run, ' (du lieu ban da thay doi)'), 255),
                p.lyDoKhoa = COALESCE(b.lyDoKhoa, 'KHAC'), p.giaBanKhiKhoa = p.giaBan,
                p.giaPheDuyet = NULL, p.giaNiemYetPheDuyet = NULL, p.nguonXacMinh = NULL, p.dauVanTayPheDuyet = NULL
            WHERE (p.nguoiPheDuyet IS NULL OR (p.nguoiPheDuyet LIKE 'SYSTEM:%' AND p.nguoiPheDuyet <> 'SYSTEM:RESTORE'))
              AND b.trangThaiPheDuyet = 'BLOCKED'
              AND NOT (p.maSanPham = b.maSanPham AND p.ram = b.ram AND p.boNho = b.boNho
                       AND p.mauSac = b.mauSac AND p.giaBan = b.giaBan AND p.giaNiemYet <=> b.giaNiemYet);
        ELSE
            UPDATE phienbansanpham p
            JOIN pn_pheduyet_backup b ON b.maPhienBan = p.maPhienBan AND b.run_id = v_src_run
            SET p.trangThaiPheDuyet = 'BLOCKED', p.nguoiPheDuyet = 'SYSTEM:RESTORE', p.ngayPheDuyet = NOW(),
                p.lyDoPheDuyet = LEFT(CONCAT('Khoi phuc BLOCKED tu ', v_src_run, ' (du lieu ban da thay doi)'), 255)
            WHERE (p.nguoiPheDuyet IS NULL OR (p.nguoiPheDuyet LIKE 'SYSTEM:%' AND p.nguoiPheDuyet <> 'SYSTEM:RESTORE'))
              AND b.trangThaiPheDuyet = 'BLOCKED'
              AND NOT (p.maSanPham = b.maSanPham AND p.ram = b.ram AND p.boNho = b.boNho
                       AND p.mauSac = b.mauSac AND p.giaBan = b.giaBan AND p.giaNiemYet <=> b.giaNiemYet);
        END IF;
        SET v_blocked = ROW_COUNT();

        -- (c) Du lieu ban da doi, ban sao luu APPROVED -> PENDING_REVIEW
        UPDATE phienbansanpham p
        JOIN pn_pheduyet_backup b ON b.maPhienBan = p.maPhienBan AND b.run_id = v_src_run
        SET p.trangThaiPheDuyet = 'PENDING_REVIEW', p.nguoiPheDuyet = 'SYSTEM:RESTORE', p.ngayPheDuyet = NOW(),
            p.lyDoPheDuyet = LEFT(CONCAT('Phe duyet cu (', v_src_run, ') khong khoi phuc vi du lieu ban da thay doi. Can duyet lai.'), 255)
        WHERE (p.nguoiPheDuyet IS NULL OR (p.nguoiPheDuyet LIKE 'SYSTEM:%' AND p.nguoiPheDuyet <> 'SYSTEM:RESTORE'))
          AND b.trangThaiPheDuyet = 'APPROVED'
          AND p.trangThaiPheDuyet <> 'APPROVED'
          AND NOT (p.maSanPham = b.maSanPham AND p.ram = b.ram AND p.boNho = b.boNho
                   AND p.mauSac = b.mauSac AND p.giaBan = b.giaBan AND p.giaNiemYet <=> b.giaNiemYet);
        SET v_demoted = ROW_COUNT();

        SELECT COUNT(*) INTO v_total_a FROM phienbansanpham;
        IF v_total_a <> v_total_b THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Hau kiem that bai: so dong bien the thay doi';
        END IF;
        COMMIT;
        SET v_in_dml = 0;

        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('RESTORE', v_log_run, 'RESTORED',
                CONCAT('nguon=', v_src_run, '; khop=', v_same, '; giu BLOCKED=', v_blocked, '; ha PENDING=', v_demoted));
        SELECT 'RESTORED' AS ketQua, v_src_run AS nguon, v_same AS khoiPhucNguyenTrang,
               v_blocked AS giuBlocked, v_demoted AS haPendingDoDuLieuDoi;
    END pn;
END$$
DELIMITER ;
