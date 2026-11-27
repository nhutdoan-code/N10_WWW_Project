-- Rollback migration 24.
-- Chuyen cac buoc moi ve DANG_XU_LY de khong mat lich su khi thu hep ENUM.
UPDATE LichSuHauMai
SET trangThai = 'DANG_XU_LY'
WHERE trangThai IN ('DANG_KIEM_TRA', 'DANG_XAC_MINH');

UPDATE YeuCauHauMai
SET trangThai = 'DANG_XU_LY'
WHERE trangThai IN ('DANG_KIEM_TRA', 'DANG_XAC_MINH');

ALTER TABLE LichSuHauMai
    MODIFY COLUMN trangThai ENUM('CHO_TIEP_NHAN', 'DANG_XU_LY', 'HOAN_TAT', 'TU_CHOI') NOT NULL;

ALTER TABLE YeuCauHauMai
    MODIFY COLUMN trangThai ENUM('CHO_TIEP_NHAN', 'DANG_XU_LY', 'HOAN_TAT', 'TU_CHOI')
        NOT NULL DEFAULT 'CHO_TIEP_NHAN',
    DROP COLUMN ketQuaXuLy;
