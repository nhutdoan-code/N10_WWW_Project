package nhom10.phonenest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PhiVanChuyenDto(
        String maPhi,
        String maTinh,
        String tenKhuVuc,
        @NotNull(message = "Vui lòng nhập phí vận chuyển")
        @DecimalMin(value = "0", message = "Phí vận chuyển không được âm") BigDecimal phiCoBan,
        BigDecimal mienPhiTu,
        String thoiGianGiao) {
}
