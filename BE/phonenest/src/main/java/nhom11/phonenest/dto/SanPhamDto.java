package nhom11.phonenest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import nhom11.phonenest.enums.TrangThaiSanPham;

import java.math.BigDecimal;
import java.util.List;

public record SanPhamDto(
        String maSanPham,
        @NotBlank(message = "Vui lòng chọn loại sản phẩm") String maLoai,
        String tenLoai,
        @NotBlank(message = "Vui lòng nhập tên máy") String tenMay,
        @NotNull(message = "Vui lòng nhập giá bán") @Positive(message = "Giá bán phải lớn hơn 0") BigDecimal giaBan,
        String moTa,
        String thongSoKyThuat,
        @Min(value = 0, message = "Số lượng tồn không được âm") int soLuongTon,
        TrangThaiSanPham trangThai,
        BigDecimal diemDanhGiaTb,
        String anhChinh,
        List<String> hinhAnhs) {
}
