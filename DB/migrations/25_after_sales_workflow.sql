-- M25: Mo rong quy trinh bao hanh / khieu nai va luu ket qua xu ly.
-- Chay sau M24_after_sales_optional_order.sql.
ALTER TABLE YeuCauHauMai
    MODIFY COLUMN trangThai ENUM(
        'CHO_TIEP_NHAN', 'DANG_KIEM_TRA', 'DANG_XAC_MINH', 'DANG_XU_LY', 'HOAN_TAT', 'TU_CHOI'
    ) NOT NULL DEFAULT 'CHO_TIEP_NHAN',
    ADD COLUMN IF NOT EXISTS ketQuaXuLy VARCHAR(1000) NULL AFTER ghiChuXuLy;

ALTER TABLE LichSuHauMai
    MODIFY COLUMN trangThai ENUM(
        'CHO_TIEP_NHAN', 'DANG_KIEM_TRA', 'DANG_XAC_MINH', 'DANG_XU_LY', 'HOAN_TAT', 'TU_CHOI'
    ) NOT NULL;
