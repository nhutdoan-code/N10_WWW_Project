package nhom11.phonenest.dto;

import jakarta.validation.constraints.NotBlank;

public record LoaiSanPhamDto(
        String maLoai,
        @NotBlank(message = "Vui lòng nhập tên loại sản phẩm") String tenLoai,
        String moTa,
        String maLoaiCha) {
}
