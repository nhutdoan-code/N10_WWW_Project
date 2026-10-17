package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public record GioHangDto(List<Item> items, BigDecimal tongTien) {

    public record Item(SanPhamDto sanPham, int soLuong) {
    }

    public record Request(@NotBlank String maSanPham, int soLuong) {
    }
}
