package nhom10.phonenest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;

import java.time.LocalDateTime;

/**
 * Chi tiet audit log.
 *
 * <p>duLieuTruoc/duLieuSau la JsonNode (JSON object khi serialize), khong phai
 * chuoi JSON long trong chuoi JSON (khong double serialize).
 */
public record NhatKyHoatDongChiTietDto(
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
        LocalDateTime thoiGian,
        JsonNode duLieuTruoc,
        JsonNode duLieuSau) {
}
