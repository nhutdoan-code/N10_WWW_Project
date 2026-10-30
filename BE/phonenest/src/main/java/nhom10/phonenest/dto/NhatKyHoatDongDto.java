package nhom10.phonenest.dto;

import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;

import java.time.LocalDateTime;

/**
 * Dong audit log cho man hinh danh sach.
 * Khong chua duLieuTruoc/duLieuSau de giu payload nho.
 */
public record NhatKyHoatDongDto(
        String maNhatKy,
        String maTk,
        String tenNguoiThucHien,
        HanhDongAudit hanhDong,
        LoaiDoiTuongAudit loaiDoiTuong,
        String maDoiTuong,
        String moTa,
        String ipAddress,
        String requestMethod,
        String requestPath,
        LocalDateTime thoiGian) {
}
