package nhom10.phonenest.dto;

import nhom10.phonenest.enums.Role;

import java.time.LocalDate;

public record TaiKhoanDto(
        String maTk,
        String hoTen,
        String email,
        String soDienThoai,
        Role role,
        LocalDate ngayTao,
        boolean trangThaiHoatDong) {

    public record Update(
            @jakarta.validation.constraints.NotBlank(message = "Vui lòng nhập họ tên") String hoTen,
            @jakarta.validation.constraints.Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0") String soDienThoai) {
    }
}
