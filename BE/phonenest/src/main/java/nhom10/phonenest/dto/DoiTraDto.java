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
        boolean daNhapKho, LocalDateTime ngayTaoChinhXac) {

    @com.fasterxml.jackson.annotation.JsonProperty("hinhAnhMinhChung")
    public String hinhAnhMinhChung() { return anhMinhChung().stream().findFirst().orElse(null); }
    @com.fasterxml.jackson.annotation.JsonProperty("anhMinhChung")
    public List<String> anhMinhChung() { return hinhAnhMinhChung == null ? List.of() : hinhAnhMinhChung.lines().filter(x -> !x.isBlank()).toList(); }

    /** Customer responses must not disclose the full refund bank account. Admin DTOs remain unchanged. */
    public DoiTraDto customerView() {
        return new DoiTraDto(maYeuCau, maDonHang, tenKhach, soDienThoai, maskAccount(lyDo), hinhAnhMinhChung,
                ngayTao, ngayCapNhat, trangThai, loaiYeuCau, tenSanPham, soLuong, maskAccount(ghiChuXuLy), chiTiet,
                soTienHoanDuKien, hinhThucHoanTien, maGiaoDichHoanTien, ngayHoanTien, daNhapKho, ngayTaoChinhXac);
    }

    static String maskAccount(String text) {
        if (text == null) return null;
        return java.util.regex.Pattern.compile("(?m)^(Số tài khoản:[ \\t]*)([0-9]{4,30})(?=[ \\t]*\\r?$)")
                .matcher(text).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(m.group(1) + "•••• " + m.group(2).substring(m.group(2).length() - 4)));
    }

    public record ChiTietDto(String maPhienBan, String tenSanPham, int soLuong,
                             LoaiYeuCauDoiTra loaiYeuCau, boolean daNhapKho,
                             LocalDateTime ngayNhapKho, BigDecimal soTienHoan) {}

    public record XacNhanHoanTienRequest(
            @NotBlank(message = "Vui lòng chọn hình thức hoàn tiền") @Size(max = 60) String hinhThuc,
            @NotBlank(message = "Vui lòng nhập mã giao dịch/đối soát") @Size(max = 150) String maGiaoDich,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) Integer phanTram, BigDecimal soTien, @Size(max = 500) String lyDoDieuChinh, LocalDateTime thoiDiemHoan) {
        public XacNhanHoanTienRequest(String hinhThuc, String maGiaoDich, Integer phanTram) { this(hinhThuc, maGiaoDich, phanTram, null, null, null); }
        public XacNhanHoanTienRequest(String hinhThuc, String maGiaoDich) { this(hinhThuc, maGiaoDich, 100, null, null, null); }
    }

    public record Request(
            @NotBlank(message = "Vui lòng chọn đơn hàng") String maDonHang,
            @NotBlank(message = "Vui lòng nhập lý do đổi trả")
            @Size(max = 2000, message = "Lý do tối đa 2000 ký tự") String lyDo,
            @Size(max = 500, message = "Link ảnh minh chứng quá dài") String hinhAnhMinhChung,
            LoaiYeuCauDoiTra loaiYeuCau,
            String maPhienBan,
            Integer soLuong) {}
}
