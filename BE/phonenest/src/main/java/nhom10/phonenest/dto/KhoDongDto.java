package nhom10.phonenest.dto;

import nhom10.phonenest.enums.TrangThaiSanPham;

import java.math.BigDecimal;

public record KhoDongDto(
        String maSanPham,
        String tenMay,
        String maPhienBan,
        String sku,
        int ram,
        int boNho,
        String mauSac,
        BigDecimal giaBan,
        int soLuongTon,
        TrangThaiSanPham trangThai) {
}
