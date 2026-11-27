package nhom10.phonenest.dto;

import java.time.LocalDateTime;

public record AdminAfterSalesRowDto(
        String source,
        String maYeuCau,
        String maDonHang,
        String tenKhach,
        String soDienThoai,
        String loai,
        String lyDo,
        String tenSanPham,
        int soLuong,
        LocalDateTime ngayTao,
        LocalDateTime ngayCapNhat,
        String trangThai,
        String ghiChuXuLy,
        String assigneeId,
        String assigneeName,
        LocalDateTime dueAt) {}
