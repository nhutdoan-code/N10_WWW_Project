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
        List<Voucher> vouchers,
        List<Dong> items,
        // Chi tiết phí (phí vùng, khai giá, COD, freeship) và ngày giao dự kiến.
        KetQuaPhiVanChuyen vanChuyen) {

    public record Dong(String maSanPham, String tenMay, String anhChinh, int soLuong, BigDecimal donGia) {
    }

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
            // Nhãn "Khách hàng mới": mã chỉ dành cho đơn đầu tiên của tài khoản.
            boolean chiKhachMoi,
            boolean apDungDuoc,
            String lyDo,
            BigDecimal soTienGiam,
            // M23: PHAN_TRAM | SO_TIEN; soTienGiamCoDinh chỉ có với SO_TIEN
            String kieuGiam,
            BigDecimal soTienGiamCoDinh) {
    }
}
