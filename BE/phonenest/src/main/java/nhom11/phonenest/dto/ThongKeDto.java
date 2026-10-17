package nhom11.phonenest.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ThongKeDto(
        BigDecimal doanhThu,
        int soDon,
        int soDonHuy,
        int soYeuCauDoiTra,
        int soSapHetHang,
        int soHetHang,
        Map<String, Integer> donTheoTrangThai,
        List<BanChay> banChay,
        List<Ngay> doanhThuTheoNgay) {

    public record BanChay(String maSanPham, String tenMay, int daBan) {
    }

    public record Ngay(String ngay, BigDecimal doanhThu) {
    }
}
