package nhom10.phonenest.dto;

import jakarta.validation.constraints.Positive;

public record KhoDto(@Positive(message = "Số lượng phải lớn hơn 0") int soLuong, String ghiChu) {
}
