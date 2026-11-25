package nhom10.phonenest.enums;

import java.util.Locale;

/**
 * Loại đơn vị hành chính cấp xã của địa chỉ giao hàng (sau sáp nhập 2025 chỉ còn phường / xã / đặc khu).
 * Dùng để cộng phụ phí giao hàng: xã = ngoại thành, đặc khu = huyện đảo (Côn Đảo, Phú Quốc, Lý Sơn...).
 */
public enum LoaiDiaBan {
    PHUONG, XA, DAC_KHU;

    /** Suy ra loại từ tên chính thức trong bảng PhuongXa ("Phường ...", "Xã ...", "Đặc khu ..."). */
    public static LoaiDiaBan tuTen(String tenPhuong) {
        if (tenPhuong == null) return PHUONG;
        String t = tenPhuong.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("đặc khu")) return DAC_KHU;
        if (t.startsWith("xã")) return XA;
        return PHUONG;
    }
}
