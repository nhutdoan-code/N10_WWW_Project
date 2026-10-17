package nhom10.phonenest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import nhom10.phonenest.enums.Role;

import java.time.LocalDate;

public final class AuthDto {

    private AuthDto() {
    }

    public record Login(
            @NotBlank(message = "Vui lòng nhập email hoặc số điện thoại") String taiKhoan,
            @NotBlank(message = "Vui lòng nhập mật khẩu") String matKhau) {
    }

    public record Register(
            @NotBlank(message = "Vui lòng nhập họ tên") String hoTen,
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không đúng định dạng") String email,
            @NotBlank(message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0")
            @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0") String soDienThoai,
            LocalDate ngaySinh,
            @NotBlank(message = "Mật khẩu phải từ 8 ký tự")
            @Size(min = 8, message = "Mật khẩu phải từ 8 ký tự") String matKhau,
            String maGioiThieu) {
    }

    public record ForgotPassword(@NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không đúng định dạng") String email) {
    }

    public record ResetPassword(
            @NotBlank(message = "Liên kết không hợp lệ") String token,
            @NotBlank(message = "Mật khẩu phải từ 8 ký tự")
            @Size(min = 8, message = "Mật khẩu phải từ 8 ký tự") String matKhau) {
    }

    public record Token(String token, String maTk, String hoTen, String email, Role role) {
    }
}
