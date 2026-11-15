package nhom10.phonenest.enums;

public enum TrangThaiHauMai {
    CHO_TIEP_NHAN,
    DANG_XU_LY,
    HOAN_TAT,
    TU_CHOI;

    public boolean coTheChuyenSang(TrangThaiHauMai moi) {
        if (moi == null || moi == this) return false;
        return switch (this) {
            case CHO_TIEP_NHAN -> moi == DANG_XU_LY || moi == TU_CHOI;
            case DANG_XU_LY -> moi == HOAN_TAT || moi == TU_CHOI;
            case HOAN_TAT, TU_CHOI -> false;
        };
    }
}
