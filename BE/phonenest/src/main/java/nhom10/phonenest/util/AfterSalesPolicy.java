package nhom10.phonenest.util;

import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;

public final class AfterSalesPolicy {
    public static final int RETURN_DAYS = 7;
    public static final int WARRANTY_MONTHS = 12;
    private AfterSalesPolicy() {}
    public static LocalDateTime end(LocalDateTime delivered, LocalDateTime confirmed, boolean warranty) {
        LocalDateTime start = confirmed != null ? confirmed : delivered;
        return start == null ? null : warranty ? start.plusMonths(WARRANTY_MONTHS) : start.plusDays(RETURN_DAYS);
    }
    public static boolean eligible(TrangThaiDonHang status, LocalDateTime delivered, LocalDateTime confirmed,
                                   boolean warranty, LocalDateTime now) {
        LocalDateTime start = confirmed != null ? confirmed : delivered;
        LocalDateTime end = end(delivered, confirmed, warranty);
        return status == TrangThaiDonHang.DA_GIAO && start != null && !start.isAfter(now)
                && (confirmed == null || !confirmed.isAfter(now)) && !now.isAfter(end);
    }
    public static void require(DonHang order, boolean warranty) {
        if (order.getTrangThai() != TrangThaiDonHang.DA_GIAO)
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng xác nhận đã nhận hàng trước khi tạo yêu cầu");
        if (!eligible(order.getTrangThai(), order.getNgayGiao(), order.getNgayKhachXacNhan(), warranty, LocalDateTime.now()))
            throw new AppException(HttpStatus.BAD_REQUEST, warranty ? "Đã hết hạn bảo hành hoặc ngày nhận hàng không hợp lệ" : "Đã hết hạn đổi trả hoặc ngày nhận hàng không hợp lệ");
    }
}
