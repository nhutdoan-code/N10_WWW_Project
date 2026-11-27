package nhom10.phonenest.util;
public final class AfterSalesStatusLabel {
    private AfterSalesStatusLabel() {}
    public static String label(String status) {
        return switch (status) {
            case "CHO_DUYET", "CHO_TIEP_NHAN" -> "Chờ tiếp nhận";
            case "DA_DUYET" -> "Đã duyệt";
            case "DANG_KIEM_TRA" -> "Đang kiểm tra";
            case "DANG_XAC_MINH" -> "Đang xác minh";
            case "DANG_XU_LY" -> "Đang xử lý";
            case "TU_CHOI" -> "Từ chối";
            case "HOAN_TAT" -> "Hoàn tất";
            default -> status;
        };
    }
}
