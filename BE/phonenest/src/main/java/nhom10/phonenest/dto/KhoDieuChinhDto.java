package nhom10.phonenest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record KhoDieuChinhDto(
        @Min(value = 0, message = "Số lượng kiểm kê không được âm") int soLuongThucTe,
        @NotBlank(message = "Lý do điều chỉnh là bắt buộc") String lyDo,
        /** 2B.7D: so ton Admin dang thay khi nhap so kiem ke. Khac ton hien tai -> 409 (khong ghi de don vua tru kho). */
        @jakarta.validation.constraints.NotNull(message = "Thiếu số lượng tồn hiện tại đã xem") Integer soLuongHienTai) {
}
