package nhom10.phonenest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CheckoutDto(
        BigDecimal tamTinh,
        BigDecimal phiVanChuyen,
        BigDecimal mienPhiTu,
        String khuVuc,
        String thoiGianGiao,
        List<Voucher> vouchers) {

    public record Voucher(
            String maKm,
            String tenKm,
            String loai,
            BigDecimal phanTramGiam,
            BigDecimal soTienGiamToiDa,
            BigDecimal donToiThieu,
            LocalDate ngayBatDau,
            LocalDate ngayKetThuc,
            String dieuKienApDung,
            boolean apDungDuoc,
            String lyDo,
            BigDecimal soTienGiam) {
    }
}
