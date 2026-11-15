package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nhom10.phonenest.enums.LyDoKhoaBan;
import nhom10.phonenest.enums.TrangThaiPheDuyet;

import java.math.BigDecimal;

/**
 * Yeu cau doi trang thai phe duyet: PUT /api/admin/variants/{maPhienBan}/approval.
 *
 * <ul>
 *   <li>APPROVED: chi tu PENDING_REVIEW. Bat buoc xac nhan lai DUNG du lieu hien tai
 *       (giaPheDuyet, giaNiemYetPheDuyet, ramPheDuyet, boNhoPheDuyet, mauSacPheDuyet)
 *       cung nguonXacMinh va lyDo. Du lieu khac hien tai -> 409.</li>
 *   <li>PENDING_REVIEW: chi tu APPROVED (thu hoi). Tu BLOCKED phai dung /unblock.</li>
 *   <li>BLOCKED: bat buoc lyDoKhoa va lyDo.</li>
 * </ul>
 */
public record AdminApprovalDto(
        @NotNull(message = "Trạng thái phê duyệt không được để trống")
        TrangThaiPheDuyet trangThaiPheDuyet,

        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String lyDo,

        @Size(max = 500, message = "Nguồn xác minh tối đa 500 ký tự")
        String nguonXacMinh,

        BigDecimal giaPheDuyet,
        BigDecimal giaNiemYetPheDuyet,
        Integer ramPheDuyet,
        Integer boNhoPheDuyet,

        @Size(max = 100, message = "Màu sắc tối đa 100 ký tự")
        String mauSacPheDuyet,

        LyDoKhoaBan lyDoKhoa,

        /** Ma kiem tra (maKiemTraPheDuyet) Admin nhan duoc khi tai chi tiet. Khac hien tai -> 409. */
        @Size(max = 64, message = "Mã kiểm tra không hợp lệ")
        String maKiemTra
) {
    /** Tuong thich nguoc (khong gui ma kiem tra). */
    public AdminApprovalDto(TrangThaiPheDuyet trangThaiPheDuyet, String lyDo, String nguonXacMinh,
                            BigDecimal giaPheDuyet, BigDecimal giaNiemYetPheDuyet, Integer ramPheDuyet,
                            Integer boNhoPheDuyet, String mauSacPheDuyet, LyDoKhoaBan lyDoKhoa) {
        this(trangThaiPheDuyet, lyDo, nguonXacMinh, giaPheDuyet, giaNiemYetPheDuyet, ramPheDuyet,
                boNhoPheDuyet, mauSacPheDuyet, lyDoKhoa, null);
    }
}
