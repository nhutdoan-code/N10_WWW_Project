package nhom10.phonenest.support;

import nhom10.phonenest.dto.KetQuaPhiVanChuyen;
import nhom10.phonenest.enums.LoaiDiaBan;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Kết quả phí vận chuyển cố định cho test mock PhiVanChuyenService.tinhChoDiaChi. */
public final class TestShipping {

    private TestShipping() {
    }

    /** Phí giao hàng theo vùng = {@code phi}, không khai giá / COD; phi = 0 coi như đơn được miễn phí. */
    public static KetQuaPhiVanChuyen phi(long phi) {
        BigDecimal p = BigDecimal.valueOf(phi);
        LocalDate homNay = LocalDate.now();
        return new KetQuaPhiVanChuyen(0, "Nội tỉnh TP.HCM", LoaiDiaBan.PHUONG, p, BigDecimal.ZERO, BigDecimal.ZERO,
                p, phi == 0, null, 0, 1, "Trong ngày - 1 ngày", homNay, homNay.plusDays(1));
    }
}
