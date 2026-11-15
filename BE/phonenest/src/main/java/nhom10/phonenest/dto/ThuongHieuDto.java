package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThuongHieuDto(
        String maThuongHieu,
        String tenThuongHieu,
        String logo,
        boolean trangThaiHoatDong,
        String moTa) {

    public ThuongHieuDto(String maThuongHieu, String tenThuongHieu, String logo) {
        this(maThuongHieu, tenThuongHieu, logo, true, null);
    }

    public record Request(
            @NotBlank @Size(max = 100) String tenThuongHieu,
            @Size(max = 500) String logo,
            String moTa) {
    }
}
