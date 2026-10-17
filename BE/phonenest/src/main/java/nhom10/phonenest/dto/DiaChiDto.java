package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DiaChiDto(
        String maDiaChi,
        @NotBlank(message = "Vui lòng nhập tên người nhận") String tenNguoiNhan,
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0") String soDienThoaiNhan,
        @NotBlank(message = "Vui lòng nhập địa chỉ nhận hàng") String diaChiNhanHang,
        String tinhThanhPho,
        boolean macDinh) {
}
