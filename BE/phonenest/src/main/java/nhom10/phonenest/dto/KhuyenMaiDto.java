package nhom10.phonenest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record KhuyenMaiDto(
        @NotBlank(message = "Vui lòng nhập mã khuyến mãi") String maKm,
        @NotBlank(message = "Vui lòng nhập tên khuyến mãi") String tenKm,
        @NotNull(message = "Vui lòng nhập phần trăm giảm")
        @DecimalMin(value = "0.01", message = "Phần trăm giảm phải lớn hơn 0")
        @DecimalMax(value = "100", message = "Phần trăm giảm tối đa 100") BigDecimal phanTramGiam,
        BigDecimal soTienGiamToiDa,
        @NotNull(message = "Vui lòng chọn ngày bắt đầu") LocalDate ngayBatDau,
        @NotNull(message = "Vui lòng chọn ngày kết thúc") LocalDate ngayKetThuc,
        String dieuKienApDung) {
}
