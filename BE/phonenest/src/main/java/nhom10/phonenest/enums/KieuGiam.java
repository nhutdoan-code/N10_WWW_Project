package nhom10.phonenest.enums;

/**
 * Cách tính tiền giảm của voucher.
 * PHAN_TRAM: min(cơ sở × phanTramGiam / 100, soTienGiamToiDa) — bắt buộc có mức trần.
 * SO_TIEN:   min(soTienGiamCoDinh, cơ sở) — giảm tiền mặt cố định.
 */
public enum KieuGiam {
    PHAN_TRAM, SO_TIEN
}
