package nhom10.phonenest.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public record GioHangDto(
        List<Item> items,
        BigDecimal tongTien
) {
    public record Item(
            SanPhamDto sanPham,
            SanPhamDto.PhienBan phienBan,
            int soLuong
    ) {}

    public record Request(
            @NotBlank(message = "Mã phiên bản không được để trống")
            @JsonAlias({"maSanPham", "maPhienBan"})
            String maPhienBan,
            @Min(value = 1, message = "Số lượng phải lớn hơn 0")
            int soLuong
    ) {
        public String maSanPham() {
            return maPhienBan;
        }
    }
}
