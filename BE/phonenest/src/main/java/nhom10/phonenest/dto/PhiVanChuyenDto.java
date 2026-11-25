package nhom10.phonenest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PhiVanChuyenDto(
        String maPhi,
        String maTinh,
        String tenKhuVuc,
        @Min(value = 0, message = "Vùng giao hàng từ 0 đến 3") @Max(value = 3, message = "Vùng giao hàng từ 0 đến 3")
        Integer vung,
        String tenVung,
        @NotNull(message = "Vui lòng nhập phí vận chuyển")
        @DecimalMin(value = "0", message = "Phí vận chuyển không được âm") BigDecimal phiCoBan,
        @DecimalMin(value = "0", message = "Phụ phí không được âm") BigDecimal phuPhiXa,
        @DecimalMin(value = "0", message = "Phụ phí không được âm") BigDecimal phuPhiDacKhu,
        @DecimalMin(value = "0", message = "Ngưỡng miễn phí không được âm") BigDecimal mienPhiTu,
        @NotNull(message = "Vui lòng nhập số ngày giao tối thiểu")
        @Min(value = 0, message = "Số ngày giao không được âm") @Max(value = 30, message = "Số ngày giao tối đa 30") Integer soNgayGiaoToiThieu,
        @NotNull(message = "Vui lòng nhập số ngày giao tối đa")
        @Min(value = 0, message = "Số ngày giao không được âm") @Max(value = 30, message = "Số ngày giao tối đa 30") Integer soNgayGiaoToiDa,
        String thoiGianGiao) {
}
