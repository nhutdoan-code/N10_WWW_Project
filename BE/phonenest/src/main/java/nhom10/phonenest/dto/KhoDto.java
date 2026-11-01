package nhom10.phonenest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record KhoDto(
        @Min(value = 0, message = "Số lượng không được âm")
        @Max(value = 1_000_000, message = "Số lượng không được vượt quá 1.000.000")
        int soLuong) {
}
