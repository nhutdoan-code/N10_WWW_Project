package nhom10.phonenest.dto;

import nhom10.phonenest.enums.LoaiDiaBan;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kết quả tính phí vận chuyển cho một đơn (xem {@code PhiVanChuyenService.tinh}).
 *
 * @param phiVung      phí giao hàng theo vùng của địa chỉ (phí cơ bản + phụ phí xã / đặc khu), chưa xét freeship
 * @param phiKhaiGia   phí khai giá (bảo hiểm hàng hóa), 0 khi được miễn phí vận chuyển
 * @param phuPhiCod    phụ phí thu hộ COD cho đơn giá trị lớn, 0 khi được miễn phí vận chuyển
 * @param tongPhi      tổng phí khách phải trả = 0 nếu miễn phí, ngược lại phiVung + phiKhaiGia + phuPhiCod
 * @param mienPhiTu    ngưỡng freeship của khu vực (null = không áp dụng)
 */
public record KetQuaPhiVanChuyen(
        Integer vung,
        String tenVung,
        LoaiDiaBan loaiDiaBan,
        BigDecimal phiVung,
        BigDecimal phiKhaiGia,
        BigDecimal phuPhiCod,
        BigDecimal tongPhi,
        boolean mienPhi,
        BigDecimal mienPhiTu,
        int soNgayToiThieu,
        int soNgayToiDa,
        String thoiGianGiao,
        LocalDate ngayGiaoDuKienTu,
        LocalDate ngayGiaoDuKienDen) {

    /**
     * Phần phí voucher vận chuyển được giảm: chỉ phí giao hàng theo vùng.
     * Phí khai giá và phụ phí COD là chi phí bắt buộc của đơn vị vận chuyển, voucher không giảm.
     */
    public BigDecimal phiDuocGiam() {
        return mienPhi ? BigDecimal.ZERO : phiVung;
    }
}
