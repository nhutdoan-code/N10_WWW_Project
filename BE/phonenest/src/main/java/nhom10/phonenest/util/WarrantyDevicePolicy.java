package nhom10.phonenest.util;

import java.util.List;

/** The store determines the actual warranty resolution. */
public final class WarrantyDevicePolicy {
    private WarrantyDevicePolicy() {}
    public static final List<String> OUTCOMES = List.of("Sửa chữa miễn phí", "Thay linh kiện miễn phí", "Đổi máy tương đương (không thể sửa chữa)");
    public static boolean validResolution(String note) {
        if (note == null) return false;
        for (String outcome : OUTCOMES) {
            String prefix = "Kết quả bảo hành: " + outcome + ". ";
            if (note.startsWith(prefix) && !note.substring(prefix.length()).isBlank()) return true;
        }
        return false;
    }
}
