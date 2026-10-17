package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record HoiDapDto(
        String maCauHoi,
        String maSanPham,
        String tenSanPham,
        String tenKhach,
        String noiDungCauHoi,
        String noiDungTraLoi,
        LocalDate ngayHoi,
        LocalDate ngayTraLoi) {

    public record Hoi(
            @NotBlank(message = "Vui lòng chọn sản phẩm") String maSanPham,
            @NotBlank(message = "Vui lòng nhập nội dung câu hỏi") String noiDung) {
    }

    public record TraLoi(@NotBlank(message = "Vui lòng nhập nội dung trả lời") String noiDung) {
    }
}
