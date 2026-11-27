package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nhom10.phonenest.enums.LoaiYeuCauDoiTra;
import nhom10.phonenest.enums.TrangThaiDoiTra;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public record DoiTraDto(
        String maYeuCau,
        String maDonHang,
        String tenKhach,
        String soDienThoai,
        String lyDo,
        String hinhAnhMinhChung,
        LocalDate ngayTao,
        LocalDateTime ngayCapNhat,
        TrangThaiDoiTra trangThai,
        LoaiYeuCauDoiTra loaiYeuCau,
        String tenSanPham,
        int soLuong,
        String ghiChuXuLy,
        List<ChiTietDto> chiTiet,
        BigDecimal soTienHoanDuKien,
        String hinhThucHoanTien,
        String maGiaoDichHoanTien,
        LocalDateTime ngayHoanTien,
        boolean daNhapKho) {

    public record ChiTietDto(String maPhienBan, String tenSanPham, int soLuong,
                             LoaiYeuCauDoiTra loaiYeuCau, boolean daNhapKho,
                             LocalDateTime ngayNhapKho, BigDecimal soTienHoan) {}

    public record XacNhanHoanTienRequest(
            @NotBlank(message = "Vui lòng chọn hình thức hoàn tiền") @Size(max = 60) String hinhThuc,
            @Size(max = 150) String maGiaoDich) {}

    public record Request(
            @NotBlank(message = "Vui lòng chọn đơn hàng") String maDonHang,
            @NotBlank(message = "Vui lòng nhập lý do đổi trả")
            @Size(max = 2000, message = "Lý do tối đa 2000 ký tự") String lyDo,
            @Size(max = 500, message = "Link ảnh minh chứng quá dài") String hinhAnhMinhChung,
            LoaiYeuCauDoiTra loaiYeuCau,
            String maPhienBan,
            Integer soLuong) {}
}
