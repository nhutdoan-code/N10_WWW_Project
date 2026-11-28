package nhom10.phonenest.util;

import nhom10.phonenest.enums.PhuongThucTt;

/** Determine permitted channels from the stored payment, not from user-supplied prose. */
public final class ReturnRefundPolicy {
    private ReturnRefundPolicy() {}
    public static boolean customerAllowed(PhuongThucTt payment, String method) {
        if (payment == null) return false;
        return switch (payment) {
            case COD -> "Chuyển khoản".equals(method);
            case CHUYEN_KHOAN -> "Phương thức thanh toán gốc".equals(method);
            case VI_DIEN_TU -> "Phương thức thanh toán gốc".equals(method) || "Chuyển khoản".equals(method);
        };
    }
    public static String adminMethod(PhuongThucTt payment, String structuredText) {
        if (payment == null) return null;
        return switch (payment) {
            case COD -> "CHUYEN_KHOAN";
            case CHUYEN_KHOAN -> "VNPAY";
            case VI_DIEN_TU -> requestedBank(structuredText) ? "CHUYEN_KHOAN" : "MOMO";
        };
    }
    private static boolean requestedBank(String text) {
        if (text == null || !text.lines().anyMatch(line -> line.startsWith("Mô tả chi tiết: "))) return false;
        // Ignore descriptions: a customer may type arbitrary text on later lines.
        return text.lines().takeWhile(line -> !line.startsWith("Mô tả chi tiết:"))
                .anyMatch(line -> line.equals("Hoàn tiền đề nghị: Chuyển khoản"));
    }
}
