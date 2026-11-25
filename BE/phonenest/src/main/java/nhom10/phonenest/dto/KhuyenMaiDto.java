package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nhom10.phonenest.enums.KieuGiam;
import nhom10.phonenest.enums.LoaiKhuyenMai;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Voucher. kieuGiam PHAN_TRAM dùng phanTramGiam + soTienGiamToiDa (bắt buộc);
 * SO_TIEN dùng soTienGiamCoDinh (giảm tiền mặt cố định). Kiểm tra chi tiết ở KhuyenMaiService.
 */
public record KhuyenMaiDto(
        @NotBlank(message = "Vui lòng nhập mã khuyến mãi") String maKm,
        @NotBlank(message = "Vui lòng nhập tên khuyến mãi") String tenKm,
        LoaiKhuyenMai loai,
        BigDecimal phanTramGiam,
        BigDecimal soTienGiamToiDa,
        BigDecimal donToiThieu,
        Integer soLuong,
        int daDung,
        boolean chiKhachMoi,
        int gioiHanMoiKhach,
        @NotNull(message = "Vui lòng chọn ngày bắt đầu") LocalDate ngayBatDau,
        @NotNull(message = "Vui lòng chọn ngày kết thúc") LocalDate ngayKetThuc,
        String dieuKienApDung,
        Boolean trangThaiHoatDong,
        KieuGiam kieuGiam,
        BigDecimal soTienGiamCoDinh) {

    /** Constructor trước M23 (luôn giảm theo phần trăm). */
    public KhuyenMaiDto(
            String maKm, String tenKm, LoaiKhuyenMai loai, BigDecimal phanTramGiam, BigDecimal soTienGiamToiDa,
            BigDecimal donToiThieu, Integer soLuong, int daDung, boolean chiKhachMoi, int gioiHanMoiKhach,
            LocalDate ngayBatDau, LocalDate ngayKetThuc, String dieuKienApDung, Boolean trangThaiHoatDong) {
        this(maKm, tenKm, loai, phanTramGiam, soTienGiamToiDa, donToiThieu, soLuong, daDung, chiKhachMoi,
                gioiHanMoiKhach, ngayBatDau, ngayKetThuc, dieuKienApDung, trangThaiHoatDong, KieuGiam.PHAN_TRAM, null);
    }

    public KhuyenMaiDto(
            String maKm, String tenKm, LoaiKhuyenMai loai, BigDecimal phanTramGiam, BigDecimal soTienGiamToiDa,
            BigDecimal donToiThieu, Integer soLuong, int daDung, boolean chiKhachMoi,
            LocalDate ngayBatDau, LocalDate ngayKetThuc, String dieuKienApDung) {
        this(maKm, tenKm, loai, phanTramGiam, soTienGiamToiDa, donToiThieu, soLuong, daDung, chiKhachMoi, 1,
                ngayBatDau, ngayKetThuc, dieuKienApDung, true);
    }
}
