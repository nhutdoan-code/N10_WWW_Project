package nhom10.phonenest.dto;

/*
 * @description
 * @author: LuuVanQuoc
 * @date: 10/29/2026
 * @version: 1.0
 */

import jakarta.validation.constraints.*;
import nhom10.phonenest.enums.Role;
import java.time.LocalDate;

public final class AuthDto {
    private AuthDto() {}

    public record Login(
            @NotBlank(message = "Vui lòng nhập email hoặc số điện thoại") String taiKhoan,
            @NotBlank(message = "Vui lòng nhập mật khẩu") String matKhau) {}

    public record Register(
            @NotBlank(message = "Vui lòng nhập họ tên")
            String hoTen,

            @NotBlank(message = "Vui lòng nhập email")
            @Email(message = "Email không đúng định dạng")

            String email,
            @NotBlank(message = "Vui lòng nhập số điện thoại")
            @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0") String soDienThoai,

            LocalDate ngaySinh,

            @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(min = 8, message = "Mật khẩu phải từ 8 ký tự") String matKhau,
            String maGioiThieu
    ) {}

    public record ForgotPassword(
            @NotBlank(message = "Vui lòng nhập email")
            @Email(message = "Email không đúng định dạng") String email
    ) {}

    public record VerifyResetOtp(
            @NotBlank(message = "Vui lòng nhập email")
            @Email(message = "Email không đúng định dạng") String email,
            @NotBlank(message = "Vui lòng nhập mã OTP")
            @Pattern(regexp = "^\\d{6}$", message = "OTP phải gồm 6 chữ số") String otp
    ) {}

    public record ResetToken(String resetToken) {}

    public record ResetPassword(
            @NotBlank(message = "Token đặt lại mật khẩu không hợp lệ")
            String resetToken,
            @NotBlank(message = "Vui lòng nhập mật khẩu mới")
            @Size(min = 8, message = "Mật khẩu phải từ 8 ký tự") String matKhau
    ) {}

    public record Token(String token, String maTk, String hoTen, String email, Role role) {}
}