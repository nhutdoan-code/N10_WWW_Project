package nhom10.phonenest.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ThongKeDto(
        BigDecimal doanhThu,
        BigDecimal giaTriDangXuLy,
        int soDon,
        int soDonThanhCong,
        int soDonHuy,
        int soDonDangXuLy,
        int soYeuCauDoiTra,
        int soSapHetHang,
        int soHetHang,
        int soConHang,
        Map<String, Integer> donTheoTrangThai,
        List<BanChay> banChay,
        List<Ngay> doanhThuTheoNgay,
        List<Hang> theoHang) {

    public record BanChay(String maSanPham, String tenMay, int daBan) {
    }

    public record Ngay(String ngay, BigDecimal doanhThu, int soDon) {
    }

    public record Hang(String ten, BigDecimal doanhThu, int daBan) {
    }
}
