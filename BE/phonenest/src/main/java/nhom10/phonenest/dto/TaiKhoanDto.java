package nhom10.phonenest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    public record Create(
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 150) String hoTen,
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không đúng định dạng") @Size(max = 150) String email,
            @NotBlank(message = "Vui lòng nhập số điện thoại") @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0") String soDienThoai,
            @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8 đến 100 ký tự") String matKhau,
            LocalDate ngaySinh) {
    }

    public record Update(
            @jakarta.validation.constraints.NotBlank(message = "Vui lòng nhập họ tên") String hoTen,
            @jakarta.validation.constraints.Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0") String soDienThoai) {
    }
}
