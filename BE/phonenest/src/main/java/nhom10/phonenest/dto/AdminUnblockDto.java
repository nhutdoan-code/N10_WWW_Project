package nhom10.phonenest.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Yeu cau mo khoa bien the BLOCKED: PUT /api/admin/variants/{maPhienBan}/unblock.
 * Mo khoa chi dua bien the ve PENDING_REVIEW (khong bao gio thang APPROVED).
 * Admin phai xac nhan lai du lieu ban hien tai va cung cap can cu.
 */
public record AdminUnblockDto(
        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String lyDo,

        @Size(max = 500, message = "Nguồn xác minh tối đa 500 ký tự")
        String nguonXacMinh,

        BigDecimal giaBanHienTai,
        BigDecimal giaNiemYetHienTai,
        Integer ramHienTai,
        Integer boNhoHienTai,

        @Size(max = 100, message = "Màu sắc tối đa 100 ký tự")
        String mauSacHienTai,

        @Size(max = 64, message = "Mã kiểm tra không hợp lệ")
        String maKiemTra
) {
}
