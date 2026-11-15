-- ==============================================================================
-- PHONENEST MIGRATION 13 v2 ROLLBACK (2B.7D)
-- Hoan tac lan chay M13 v2 gan nhat (hoac @pn_rollback_run_id) tu pn_data_backup.
-- Chi khoi phuc o ma gia tri hien tai VAN LA gia tri M13 da dien; o Admin da sua -> giu nguyen.
-- Sao luu append-only truoc khi hoan tac; KHONG xoa bang backup. Khong ho tro ban v1 (xu ly thu cong).
-- Database chinh: SET @pn_confirm_rollback_13 = CONCAT('ROLLBACK-13-ON-', DATABASE());
-- ==============================================================================

DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE v_db VARCHAR(64);
    DECLARE v_src_run VARCHAR(40) DEFAULT NULL;
    DECLARE v_run_id VARCHAR(40) DEFAULT NULL;
    DECLARE v_in_dml INT DEFAULT 0;
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_total INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @pn_err = MESSAGE_TEXT;
        IF v_in_dml = 1 THEN
            ROLLBACK;
        END IF;
        IF v_run_id IS NOT NULL THEN
            INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
            VALUES ('13', v_run_id, 'ROLLBACK_FAILED', LEFT(@pn_err, 500));
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
           AND (@pn_confirm_rollback_13 IS NULL OR @pn_confirm_rollback_13 <> CONCAT('ROLLBACK-13-ON-', v_db)) THEN
            SELECT 'REFUSED' AS ketQua,
                   'Database chinh: can SET @pn_confirm_rollback_13 = CONCAT(''ROLLBACK-13-ON-'', DATABASE()) sau khi sao luu va duoc phe duyet' AS chiTiet;
            LEAVE pn;
        END IF;
        IF COALESCE(GET_LOCK(CONCAT('pn_mig_15_16_', v_db), 0), 0) <> 1 THEN
            SELECT 'REFUSED_CONCURRENT' AS ketQua, 'Mot tien trinh migration khac dang chay tren database nay' AS chiTiet;
            LEAVE pn;
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = v_db AND TABLE_NAME IN ('pn_migration_log', 'pn_data_backup')) <> 2 THEN
            SELECT 'REFUSED' AS ketQua, 'Khong co pn_migration_log / pn_data_backup: M13 v2 chua tung chay (ban v1 can xu ly thu cong)' AS chiTiet;
            LEAVE pn;
        END IF;
        SET v_src_run = @pn_rollback_run_id;
        IF v_src_run IS NULL THEN
            IF (SELECT trang_thai FROM pn_migration_log WHERE migration_id = '13' ORDER BY log_id DESC LIMIT 1) = 'ROLLED_BACK' THEN
                SELECT 'ALREADY_ROLLED_BACK' AS ketQua, 'Lan chay M13 gan nhat da duoc hoan tac. Khong thay doi gi.' AS chiTiet;
                LEAVE pn;
            END IF;
            SELECT run_id INTO v_src_run FROM pn_migration_log
            WHERE migration_id = '13' AND trang_thai = 'COMPLETED' ORDER BY log_id DESC LIMIT 1;
        END IF;
        SELECT COUNT(*) INTO v_total FROM pn_data_backup WHERE run_id = v_src_run AND giai_doan = 'M13_TRUOC_DML';
        IF v_src_run IS NULL OR v_total = 0 THEN
            SELECT 'REFUSED' AS ketQua, 'Khong tim thay lan chay M13 COMPLETED co du lieu sao luu' AS chiTiet;
            LEAVE pn;
        END IF;

        SET v_run_id = CONCAT('RB13-', DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'));
        SET v_in_dml = 1;
        START TRANSACTION;
        INSERT INTO pn_data_backup (run_id, migration_id, giai_doan, bang, khoa, cot, gia_tri_cu, gia_tri_moi)
        SELECT v_run_id, '13', 'M13_TRUOC_ROLLBACK', 'sanpham', s.maSanPham, b.cot,
               CASE b.cot WHEN 'chip' THEN CAST(s.chip AS CHAR) WHEN 'pin' THEN CAST(s.pin AS CHAR) WHEN 'kichThuocManHinh' THEN CAST(s.kichThuocManHinh AS CHAR) WHEN 'loaiManHinh' THEN CAST(s.loaiManHinh AS CHAR) WHEN 'tanSoQuet' THEN CAST(s.tanSoQuet AS CHAR) WHEN 'cameraSau' THEN CAST(s.cameraSau AS CHAR) WHEN 'cameraTruoc' THEN CAST(s.cameraTruoc AS CHAR) WHEN 'congSuatSac' THEN CAST(s.congSuatSac AS CHAR) WHEN 'heDieuHanh' THEN CAST(s.heDieuHanh AS CHAR) WHEN 'trongLuong' THEN CAST(s.trongLuong AS CHAR) WHEN 'ho5G' THEN CAST(s.ho5G AS CHAR) WHEN 'nfc' THEN CAST(s.nfc AS CHAR) END, b.gia_tri_cu
        FROM pn_data_backup b JOIN sanpham s ON s.maSanPham = b.khoa
        WHERE b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML';
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'chip' AND b.khoa = s.maSanPham
        SET s.chip = b.gia_tri_cu
        WHERE CAST(s.chip AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'pin' AND b.khoa = s.maSanPham
        SET s.pin = b.gia_tri_cu
        WHERE CAST(s.pin AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'kichThuocManHinh' AND b.khoa = s.maSanPham
        SET s.kichThuocManHinh = b.gia_tri_cu
        WHERE CAST(s.kichThuocManHinh AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'loaiManHinh' AND b.khoa = s.maSanPham
        SET s.loaiManHinh = b.gia_tri_cu
        WHERE CAST(s.loaiManHinh AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'tanSoQuet' AND b.khoa = s.maSanPham
        SET s.tanSoQuet = b.gia_tri_cu
        WHERE CAST(s.tanSoQuet AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'cameraSau' AND b.khoa = s.maSanPham
        SET s.cameraSau = b.gia_tri_cu
        WHERE CAST(s.cameraSau AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'cameraTruoc' AND b.khoa = s.maSanPham
        SET s.cameraTruoc = b.gia_tri_cu
        WHERE CAST(s.cameraTruoc AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'congSuatSac' AND b.khoa = s.maSanPham
        SET s.congSuatSac = b.gia_tri_cu
        WHERE CAST(s.congSuatSac AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'heDieuHanh' AND b.khoa = s.maSanPham
        SET s.heDieuHanh = b.gia_tri_cu
        WHERE CAST(s.heDieuHanh AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'trongLuong' AND b.khoa = s.maSanPham
        SET s.trongLuong = b.gia_tri_cu
        WHERE CAST(s.trongLuong AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'ho5G' AND b.khoa = s.maSanPham
        SET s.ho5G = b.gia_tri_cu
        WHERE CAST(s.ho5G AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        UPDATE sanpham s JOIN pn_data_backup b
            ON b.run_id = v_src_run AND b.giai_doan = 'M13_TRUOC_DML' AND b.bang = 'sanpham' AND b.cot = 'nfc' AND b.khoa = s.maSanPham
        SET s.nfc = b.gia_tri_cu
        WHERE CAST(s.nfc AS CHAR) <=> b.gia_tri_moi;
        SET v_done = v_done + ROW_COUNT();
        COMMIT;
        SET v_in_dml = 0;
        INSERT INTO pn_migration_log (migration_id, run_id, trang_thai, ghi_chu)
        VALUES ('13', v_run_id, 'ROLLED_BACK', CONCAT('nguon=', v_src_run, ', o hoan tac=', v_done, '/', v_total,
                ' (con lai: Admin da sua, giu nguyen)'));
        SELECT 'ROLLED_BACK' AS ketQua, v_src_run AS nguon, v_done AS oHoanTac, v_total - v_done AS oBoQua;
    END pn;
END$$
DELIMITER ;
