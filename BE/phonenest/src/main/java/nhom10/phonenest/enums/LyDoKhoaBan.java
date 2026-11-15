package nhom10.phonenest.enums;

/**
 * Nguyen nhan co cau truc cua trang thai BLOCKED (Migration 16).
 * Quyet dinh quy trinh mo khoa: xem PheDuyetBanHangService#moKhoa.
 */
public enum LyDoKhoaBan {
    /** Gia seed chua xac minh: chi mo khoa khi gia ban da khac gia luc khoa VA co can cu. */
    GIA_SEED_CHUA_XAC_MINH,
    /** Sai cau hinh (RAM/ROM/mau...): mo khoa khi co can cu xac minh cau hinh. */
    SAI_CAU_HINH,
    /** Quan tri vien chu dong khoa. */
    QUYET_DINH_QUAN_TRI,
    /** San pham/bien the ngung kinh doanh: phai khoi phuc kinh doanh truoc khi mo khoa. */
    NGUNG_KINH_DOANH,
    KHAC
}
