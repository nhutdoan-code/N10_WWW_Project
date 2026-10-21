package nhom10.phonenest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DanhGiaDto(
        String maDanhGia,
        String maDonHang,
        String maSanPham,
        String tenSanPham,
        String tenKhach,
        int diemSao,
        String noiDungBinhLuan,
        String hinhAnhThucTe,
        LocalDate ngayDanhGia) {

    public record Request(
            @NotBlank(message = "Vui lòng chọn đơn hàng cần đánh giá") String maDonHang,
            @NotBlank(message = "Vui lòng chọn sản phẩm") String maSanPham,
            @Min(value = 1, message = "Điểm đánh giá từ 1 đến 5 sao") @Max(value = 5, message = "Điểm đánh giá từ 1 đến 5 sao") int diemSao,
            @Size(max = 1000, message = "Nội dung đánh giá tối đa 1000 ký tự") String noiDungBinhLuan,
            @Size(max = 500, message = "Link ảnh tối đa 500 ký tự")
            @Pattern(regexp = "^$|^https?://\\S+$", message = "Link ảnh phải bắt đầu bằng http:// hoặc https://") String hinhAnhThucTe) {
    }

    public record TrangThai(String maSanPham, boolean duocDanhGia, String lyDo, DanhGiaDto danhGia) {
    }
}
