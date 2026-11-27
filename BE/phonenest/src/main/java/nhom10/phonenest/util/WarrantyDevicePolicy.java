package nhom10.phonenest.util;

import java.util.List;
import nhom10.phonenest.exception.AppException;
import org.springframework.http.HttpStatus;

/** Customer identifies the device; the store determines the actual warranty resolution. */
public final class WarrantyDevicePolicy {
    private WarrantyDevicePolicy() {}
    public static final List<String> OUTCOMES = List.of("Sửa chữa miễn phí", "Thay linh kiện miễn phí", "Đổi máy tương đương (không thể sửa chữa)");
    public static boolean validImei(String value) {
        if (value == null || !value.matches("[0-9]{15}")) return false;
        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            int n = (value.charAt(i) - '0') * (i % 2 == 0 ? 1 : 2);
            sum += n > 9 ? n - 9 : n;
        }
        return sum % 10 == 0;
    }
    public static String requireDevice(String kind, String value) {
        String text = value == null ? "" : value.strip();
        // Old structured payloads did not distinguish IMEI and serial.
        if (kind == null || kind.isBlank()) kind = text.matches("[0-9]+") ? "IMEI" : "SERIAL";
        if ("IMEI".equals(kind) && validImei(text)) return "IMEI: " + text;
        if ("SERIAL".equals(kind) && text.matches("[A-Za-z0-9-]{4,60}")) return "Serial: " + text;
        throw new AppException(HttpStatus.BAD_REQUEST, "IMEI phải gồm 15 chữ số và đúng mã kiểm tra; Serial gồm 4–60 chữ cái, chữ số hoặc dấu gạch ngang");
    }
    public static boolean validResolution(String note) {
        if (note == null) return false;
        for (String outcome : OUTCOMES) {
            String prefix = "Kết quả bảo hành: " + outcome + ". ";
            if (note.startsWith(prefix) && !note.substring(prefix.length()).isBlank()) return true;
        }
        return false;
    }
}
