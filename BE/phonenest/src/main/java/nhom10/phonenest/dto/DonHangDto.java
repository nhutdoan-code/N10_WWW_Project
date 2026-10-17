package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DonHangDto(
        String maDonHang,
        String maKh,
        String maKm,
        LocalDateTime ngayMua,
        PhuongThucTt phuongThucTt,
        TrangThaiDonHang trangThai,
        BigDecimal tamTinh,
        BigDecimal giamGia,
        BigDecimal tongTien,
        String diaChiGiaoHang,
        LocalDateTime ngayCapNhat,
        String tenKhach,
        String trangThaiDoiTra,
        List<Dong> chiTiet) {

    public record Dong(String maSanPham, String tenMay, String anhChinh, int soLuong, BigDecimal donGia) {
    }

    public record Checkout(@NotNull(message = "Vui lòng chọn phương thức thanh toán") PhuongThucTt phuongThucTt,
                           @NotBlank(message = "Vui lòng chọn địa chỉ giao hàng") String maDiaChi,
                           String maKm) {
    }

    public record KhuyenMaiInfo(String maKm, String tenKm, BigDecimal phanTramGiam, BigDecimal soTienGiamToiDa) {
    }
}
