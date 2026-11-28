package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// hanDoiTra/hanBaoHanh la truong tinh toan (chi xuat ra JSON): bo qua khi doc lai, vi du
// CheckoutIdempotencyService doc lai response da luu de tra cho request lap lai.
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value = {"hanDoiTra", "hanBaoHanh"}, allowGetters = true)
public record DonHangDto(
        String maDonHang,
        String maKh,
        String maKm,
        String maKmVanChuyen,
        LocalDateTime ngayMua,
        PhuongThucTt phuongThucTt,
        TrangThaiDonHang trangThai,
        BigDecimal tamTinh,
        BigDecimal giamGia,
        BigDecimal phiVanChuyen,
        BigDecimal giamPhiVanChuyen,
        BigDecimal tongTien,
        String diaChiGiaoHang,
        LocalDateTime ngayCapNhat,
        LocalDateTime ngayGiao,
        String tenKhach,
        String tenNguoiNhan,
        String soDienThoaiNhan,
        String trangThaiDoiTra,
        String lyDoHuy,
        LocalDateTime ngayKhachXacNhan,
        String nguoiXacNhan,
        List<Dong> chiTiet,
        // M22: thành phần đã gồm trong phiVanChuyen + khoảng ngày giao dự kiến
        BigDecimal phiKhaiGia,
        BigDecimal phuPhiCod,
        LocalDate ngayGiaoDuKienTu,
        LocalDate ngayGiaoDuKienDen,
        ThanhToanInfo thanhToan,
        Boolean khoaSuaSoLuong) {

    // Giữ hợp đồng JSON cũ, kể cả khi đọc cache checkout trước V11.
    public DonHangDto {
        khoaSuaSoLuong = true;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("hanDoiTra")
    public LocalDateTime hanDoiTra() { return nhom10.phonenest.util.AfterSalesPolicy.end(ngayGiao, ngayKhachXacNhan, false); }
    @com.fasterxml.jackson.annotation.JsonProperty("hanBaoHanh")
    public LocalDateTime hanBaoHanh() { return nhom10.phonenest.util.AfterSalesPolicy.end(ngayGiao, ngayKhachXacNhan, true); }

    /** Giữ tương thích với caller M22 và cache checkout cũ. */
    public DonHangDto(
            String maDonHang, String maKh, String maKm, String maKmVanChuyen,
            LocalDateTime ngayMua, PhuongThucTt phuongThucTt, TrangThaiDonHang trangThai,
            BigDecimal tamTinh, BigDecimal giamGia, BigDecimal phiVanChuyen,
            BigDecimal giamPhiVanChuyen, BigDecimal tongTien, String diaChiGiaoHang,
            LocalDateTime ngayCapNhat, LocalDateTime ngayGiao, String tenKhach,
            String tenNguoiNhan, String soDienThoaiNhan, String trangThaiDoiTra,
            String lyDoHuy, LocalDateTime ngayKhachXacNhan, String nguoiXacNhan,
            List<Dong> chiTiet, BigDecimal phiKhaiGia, BigDecimal phuPhiCod,
            LocalDate ngayGiaoDuKienTu, LocalDate ngayGiaoDuKienDen) {
        this(maDonHang, maKh, maKm, maKmVanChuyen, ngayMua, phuongThucTt, trangThai,
                tamTinh, giamGia, phiVanChuyen, giamPhiVanChuyen, tongTien,
                diaChiGiaoHang, ngayCapNhat, ngayGiao, tenKhach, tenNguoiNhan, soDienThoaiNhan,
                trangThaiDoiTra, lyDoHuy, ngayKhachXacNhan, nguoiXacNhan, chiTiet,
                phiKhaiGia, phuPhiCod, ngayGiaoDuKienTu, ngayGiaoDuKienDen, null, null);
    }

    public record ThanhToanInfo(PhuongThucTt phuongThuc,
            nhom10.phonenest.enums.TrangThaiThanhToan trangThai, BigDecimal soTien,
            String maGiaoDich, String maGiaoDichNganHang, String maGiaoDichMoMo,
            LocalDateTime ngayThanhToan) {}

    /** Constructor trước M22 (không có chi tiết phí / ngày giao dự kiến), giữ cho test và caller cũ. */
    public DonHangDto(
            String maDonHang, String maKh, String maKm, String maKmVanChuyen,
            LocalDateTime ngayMua, PhuongThucTt phuongThucTt, TrangThaiDonHang trangThai,
            BigDecimal tamTinh, BigDecimal giamGia, BigDecimal phiVanChuyen,
            BigDecimal giamPhiVanChuyen, BigDecimal tongTien, String diaChiGiaoHang,
            LocalDateTime ngayCapNhat, LocalDateTime ngayGiao, String tenKhach,
            String tenNguoiNhan, String soDienThoaiNhan,
            String trangThaiDoiTra, String lyDoHuy, LocalDateTime ngayKhachXacNhan,
            String nguoiXacNhan, List<Dong> chiTiet) {
        this(maDonHang, maKh, maKm, maKmVanChuyen, ngayMua, phuongThucTt, trangThai,
                tamTinh, giamGia, phiVanChuyen, giamPhiVanChuyen, tongTien,
                diaChiGiaoHang, ngayCapNhat, ngayGiao, tenKhach, tenNguoiNhan, soDienThoaiNhan,
                trangThaiDoiTra, lyDoHuy, ngayKhachXacNhan, nguoiXacNhan, chiTiet, null, null, null, null);
    }

    /** Backward-compatible constructor for service tests and older internal callers. */
    public DonHangDto(
            String maDonHang, String maKh, String maKm, String maKmVanChuyen,
            LocalDateTime ngayMua, PhuongThucTt phuongThucTt, TrangThaiDonHang trangThai,
            BigDecimal tamTinh, BigDecimal giamGia, BigDecimal phiVanChuyen,
            BigDecimal giamPhiVanChuyen, BigDecimal tongTien, String diaChiGiaoHang,
            LocalDateTime ngayCapNhat, LocalDateTime ngayGiao, String tenKhach,
            String trangThaiDoiTra, String lyDoHuy, LocalDateTime ngayKhachXacNhan,
            String nguoiXacNhan, List<Dong> chiTiet) {
        this(maDonHang, maKh, maKm, maKmVanChuyen, ngayMua, phuongThucTt, trangThai,
                tamTinh, giamGia, phiVanChuyen, giamPhiVanChuyen, tongTien,
                diaChiGiaoHang, ngayCapNhat, ngayGiao, tenKhach, null, null,
                trangThaiDoiTra, lyDoHuy, ngayKhachXacNhan, nguoiXacNhan, chiTiet);
    }

    public record Dong(String maSanPham, String maPhienBan, String tenMay, String anhChinh, int soLuong, BigDecimal donGia) {
    }

    public record Checkout(@NotNull(message = "Vui lòng chọn phương thức thanh toán") PhuongThucTt phuongThucTt,
                           @NotBlank(message = "Vui lòng chọn địa chỉ giao hàng") String maDiaChi,
                           String maKm,
                           String maKmVanChuyen,
                           String maSanPham,
                           Integer soLuong) {
    }

    public record HuyRequest(
            @NotBlank(message = "Vui lòng chọn lý do hủy đơn") @jakarta.validation.constraints.Size(max = 200) String lyDo,
            @jakarta.validation.constraints.Size(max = 500) String ghiChu) {
    }

}
