package nhom10.phonenest.util;

/** Hiển thị dung lượng bộ nhớ (đơn vị gốc GB): 1024 → "1TB", 256 → "256GB". Khớp với formatDungLuong ở FE. */
public final class DungLuongUtil {

    private DungLuongUtil() {
    }

    public static String format(int gb) {
        if (gb <= 0) {
            return "";
        }
        return gb >= 1024 && gb % 1024 == 0 ? (gb / 1024) + "TB" : gb + "GB";
    }
}
