package nhom10.phonenest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record KhoNhapHangLoatDto(
        @NotEmpty(message = "Danh sách nhập kho không được rỗng")
        List<@Valid DongNhap> items,
        String ghiChu) {

    public record DongNhap(
            @NotBlank(message = "SKU không được để trống")
            String sku,
            @Positive(message = "Số lượng nhập phải lớn hơn 0")
            int soLuong) {
    }
}
