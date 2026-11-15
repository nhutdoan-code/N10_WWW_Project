package nhom10.phonenest.enums;

/**
 * Trạng thái phê duyệt bán hàng của phiên bản sản phẩm (Giai đoạn 2B.6D).
 * Độc lập hoàn toàn với trạng thái kinh doanh (trangThai) và tồn kho (soLuongTon).
 *
 * - APPROVED: Được phép mua khi còn hàng (soLuongTon > 0) và đang kinh doanh.
 * - PENDING_REVIEW: Chưa được phép mua vì chưa hoàn tất duyệt giá/ảnh thị trường 2026.
 * - BLOCKED: Bị khóa bán khẩn cấp do dữ liệu sai (ví dụ giá seed bất thường 1.99M).
 */
public enum TrangThaiPheDuyet {
    APPROVED,
    PENDING_REVIEW,
    BLOCKED
}
