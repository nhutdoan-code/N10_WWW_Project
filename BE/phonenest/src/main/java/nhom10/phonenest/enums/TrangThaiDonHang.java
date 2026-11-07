package nhom10.phonenest.enums;

public enum TrangThaiDonHang {
    CHO_XAC_NHAN,
    DA_XAC_NHAN,
    DANG_CHUAN_BI,
    DANG_GIAO,
    CHO_KHACH_XAC_NHAN,
    DA_GIAO,
    DA_HUY;

    /**
     * State transition matrix:
     *
     * CHO_XAC_NHAN
     * -> DA_XAC_NHAN, DA_HUY
     *
     * DA_XAC_NHAN
     * -> DANG_CHUAN_BI, DA_HUY
     *
     * DANG_CHUAN_BI
     * -> DANG_GIAO, DA_HUY
     *
     * DANG_GIAO
     * -> CHO_KHACH_XAC_NHAN, DA_HUY
     *
     * CHO_KHACH_XAC_NHAN
     * -> DA_GIAO
     *
     * DA_GIAO, DA_HUY
     * -> Terminal
     */
    public boolean coTheChuyenSang(TrangThaiDonHang moi) {
        if (moi == null || moi == this) {
            return false;
        }

        return switch (this) {
            case CHO_XAC_NHAN ->
                    moi == DA_XAC_NHAN || moi == DA_HUY;

            case DA_XAC_NHAN ->
                    moi == DANG_CHUAN_BI || moi == DA_HUY;

            case DANG_CHUAN_BI ->
                    moi == DANG_GIAO || moi == DA_HUY;

            case DANG_GIAO ->
                    moi == CHO_KHACH_XAC_NHAN || moi == DA_HUY;

            case CHO_KHACH_XAC_NHAN ->
                    moi == DA_GIAO;

            case DA_GIAO, DA_HUY ->
                    false;
        };
    }
}