package nhom10.phonenest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record KhoDieuChinhDto(
        @Min(value = 0, message = "Số lượng kiểm kê không được âm") int soLuongThucTe,
        @NotBlank(message = "Lý do điều chỉnh là bắt buộc") String lyDo) {
}
