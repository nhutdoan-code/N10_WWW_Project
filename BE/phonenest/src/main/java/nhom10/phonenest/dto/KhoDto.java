package nhom11.phonenest.dto;

import jakarta.validation.constraints.Min;

public record KhoDto(@Min(value = 0, message = "Số lượng không được âm") int soLuong) {
}
