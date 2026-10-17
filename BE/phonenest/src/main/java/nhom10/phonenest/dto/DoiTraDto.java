package nhom10.phonenest.dto;

import nhom10.phonenest.enums.TrangThaiDoiTra;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record DoiTraDto(
        String maYeuCau,
        String maDonHang,
        String tenKhach,
        String lyDo,
        String hinhAnhMinhChung,
        LocalDate ngayTao,
        TrangThaiDoiTra trangThai) {

    public record Request(
            @NotBlank(message = "Vui lòng chọn đơn hàng") String maDonHang,
            @NotBlank(message = "Vui lòng nhập lý do đổi trả") String lyDo,
            String hinhAnhMinhChung) {
    }
}
