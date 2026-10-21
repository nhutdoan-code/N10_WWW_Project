package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DiaChiDto(
        String maDiaChi,
        @NotBlank(message = "Vui lòng nhập tên người nhận") String tenNguoiNhan,
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0") String soDienThoaiNhan,
        @NotBlank(message = "Vui lòng chọn tỉnh / thành phố") String maTinh,
        @NotBlank(message = "Vui lòng chọn phường / xã") String maPhuong,
        @NotBlank(message = "Vui lòng nhập số nhà, tên đường") String diaChiNhanHang,
        String tenTinh,
        String tenPhuong,
        boolean macDinh) {
}
