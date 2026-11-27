-- Mo rong quy trinh bao hanh / khieu nai va luu ket qua xu ly.
-- Chay mot lan tren database da ap dung migration 17 va 18.
ALTER TABLE YeuCauHauMai
    MODIFY COLUMN trangThai ENUM(
        'CHO_TIEP_NHAN', 'DANG_KIEM_TRA', 'DANG_XAC_MINH', 'DANG_XU_LY', 'HOAN_TAT', 'TU_CHOI'
    ) NOT NULL DEFAULT 'CHO_TIEP_NHAN',
    ADD COLUMN ketQuaXuLy VARCHAR(1000) NULL AFTER ghiChuXuLy;

ALTER TABLE LichSuHauMai
    MODIFY COLUMN trangThai ENUM(
        'CHO_TIEP_NHAN', 'DANG_KIEM_TRA', 'DANG_XAC_MINH', 'DANG_XU_LY', 'HOAN_TAT', 'TU_CHOI'
    ) NOT NULL;
