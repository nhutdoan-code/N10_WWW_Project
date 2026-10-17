package nhom11.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class HoSoDto {

    private HoSoDto() {
    }

    public record Profile(String maKh, String hoTen, String email, String soDienThoai, LocalDate ngaySinh,
                          String anhDaiDien, int diemTichLuy, String hangThanhVien, String maGioiThieu) {
    }

    public record Update(
            @NotBlank(message = "Vui lòng nhập họ tên") String hoTen,
            @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0") String soDienThoai,
            LocalDate ngaySinh,
            String anhDaiDien) {
    }

    public record Password(
            @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String matKhauCu,
            @Size(min = 8, message = "Mật khẩu mới phải từ 8 ký tự") String matKhauMoi) {
    }
}
