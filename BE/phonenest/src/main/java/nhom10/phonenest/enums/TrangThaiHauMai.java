package nhom10.phonenest.enums;

public enum TrangThaiHauMai {
    CHO_TIEP_NHAN,
    DANG_KIEM_TRA,
    DANG_XAC_MINH,
    DANG_XU_LY,
    HOAN_TAT,
    TU_CHOI;

    public boolean coTheChuyenSang(LoaiHauMai loai, TrangThaiHauMai moi) {
        if (moi == null || moi == this) return false;
        return switch (this) {
            case CHO_TIEP_NHAN -> moi == TU_CHOI || (loai == LoaiHauMai.BAO_HANH
                    ? moi == DANG_KIEM_TRA : moi == DANG_XAC_MINH);
            case DANG_KIEM_TRA -> loai == LoaiHauMai.BAO_HANH && (moi == DANG_XU_LY || moi == TU_CHOI);
            case DANG_XAC_MINH -> loai == LoaiHauMai.KHIEU_NAI && (moi == DANG_XU_LY || moi == TU_CHOI);
            case DANG_XU_LY -> moi == HOAN_TAT || moi == TU_CHOI;
            case HOAN_TAT, TU_CHOI -> false;
        };
    }
}
