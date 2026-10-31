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
        String maKmVanChuyen,
        LocalDateTime ngayMua,
        PhuongThucTt phuongThucTt,
        TrangThaiDonHang trangThai,
        BigDecimal tamTinh,
        BigDecimal giamGia,
        BigDecimal phiVanChuyen,
        BigDecimal giamPhiVanChuyen,
        BigDecimal tongTien,
        String diaChiGiaoHang,
        LocalDateTime ngayCapNhat,
        LocalDateTime ngayGiao,
        String tenKhach,
        String trangThaiDoiTra,
        String lyDoHuy,
        LocalDateTime ngayKhachXacNhan,
        String nguoiXacNhan,
        List<Dong> chiTiet ) {

    public record Dong(String maSanPham, String maPhienBan, String tenMay, String anhChinh, int soLuong, BigDecimal donGia) {
    }

    public record Checkout(@NotNull(message = "Vui lòng chọn phương thức thanh toán") PhuongThucTt phuongThucTt,
                           @NotBlank(message = "Vui lòng chọn địa chỉ giao hàng") String maDiaChi,
                           String maKm,
                           String maKmVanChuyen,
                           String maSanPham,
                           Integer soLuong) {
    }

    public record HuyRequest(@NotBlank(message = "Vui lòng chọn lý do hủy đơn") String lyDo, String ghiChu) {
    }

}
