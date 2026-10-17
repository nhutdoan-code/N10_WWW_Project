package nhom11.phonenest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record DanhGiaDto(
        String maDanhGia,
        String maSanPham,
        String tenSanPham,
        String tenKhach,
        int diemSao,
        String noiDungBinhLuan,
        String hinhAnhThucTe,
        LocalDate ngayDanhGia) {

    public record Request(
            @NotBlank(message = "Vui lòng chọn sản phẩm") String maSanPham,
            @Min(value = 1, message = "Điểm đánh giá từ 1 đến 5 sao") @Max(value = 5, message = "Điểm đánh giá từ 1 đến 5 sao") int diemSao,
            String noiDungBinhLuan,
            String hinhAnhThucTe) {
    }

    public record Eligible(boolean duocDanhGia, String lyDo) {
    }
}
