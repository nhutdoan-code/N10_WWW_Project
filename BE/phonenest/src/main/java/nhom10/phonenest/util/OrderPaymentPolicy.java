package nhom10.phonenest.util;

import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import java.math.BigDecimal;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Cùng tiêu chí hiển thị với danh sách đơn Admin hiện tại. */
public final class OrderPaymentPolicy {
    private OrderPaymentPolicy() {}
    public static final Set<TrangThaiThanhToan> CAPTURED = Set.of(
            TrangThaiThanhToan.DA_THANH_TOAN, TrangThaiThanhToan.CAN_HOAN_TIEN,
            TrangThaiThanhToan.DANG_HOAN_TIEN, TrangThaiThanhToan.DA_HOAN_TIEN);

    public static boolean visible(ThanhToan p) {
        return p.getPhuongThuc() == PhuongThucTt.COD ||
                ((p.getPhuongThuc() == PhuongThucTt.CHUYEN_KHOAN || p.getPhuongThuc() == PhuongThucTt.VI_DIEN_TU)
                        && p.getTrangThai() != null && CAPTURED.contains(p.getTrangThai()));
    }

    public static ThanhToan effective(List<ThanhToan> payments) {
        // Giao dịch hợp lệ cho đơn Admin được ưu tiên hơn lần thử online thất bại.
        return payments.stream().max(Comparator.comparing(OrderPaymentPolicy::visible)
                .thenComparing(ThanhToan::getNgayTao, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ThanhToan::getMaThanhToan, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    public static boolean matchesOrderAmount(ThanhToan p, BigDecimal total) {
        return p != null && p.getSoTien() != null && total != null
                && total.signum() >= 0 && p.getSoTien().compareTo(total) == 0;
    }

    public static boolean canConfirmCod(TrangThaiDonHang status, ThanhToan p, BigDecimal total) {
        return (status == TrangThaiDonHang.DANG_GIAO || status == TrangThaiDonHang.CHO_KHACH_XAC_NHAN
                || status == TrangThaiDonHang.DA_GIAO)
                && p != null && p.getPhuongThuc() == PhuongThucTt.COD
                && p.getTrangThai() == TrangThaiThanhToan.CHO_THANH_TOAN
                && p.getNgayThanhToan() == null && matchesOrderAmount(p, total);
    }

    public static boolean codPaid(ThanhToan p, BigDecimal total) {
        return p != null && p.getPhuongThuc() == PhuongThucTt.COD
                && p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN && matchesOrderAmount(p, total);
    }

    public static boolean canAutoComplete(ThanhToan p, BigDecimal total) {
        return p != null && p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN
                && p.getNgayThanhToan() != null && matchesOrderAmount(p, total);
    }

    public static boolean canEditQuantity(List<ThanhToan> payments) {
        // Không có ngoại lệ cho COD hoặc bất kỳ trạng thái thanh toán nào.
        return false;
    }
}
