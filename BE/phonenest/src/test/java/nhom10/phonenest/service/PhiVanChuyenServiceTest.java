package nhom10.phonenest.service;

import nhom10.phonenest.dto.KetQuaPhiVanChuyen;
import nhom10.phonenest.entity.PhiVanChuyen;
import nhom10.phonenest.enums.LoaiDiaBan;
import nhom10.phonenest.enums.PhuongThucTt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PhiVanChuyenServiceTest {

    private static final LocalDateTime SANG_THU_TU = LocalDateTime.of(2026, 11, 25, 9, 0);
    private static final LocalDateTime TOI_THU_TU = LocalDateTime.of(2026, 11, 25, 18, 30);

    private static PhiVanChuyen bang(Integer vung, long coBan, long xa, long dacKhu, Long mienPhiTu, int min, int max) {
        PhiVanChuyen p = new PhiVanChuyen();
        p.setVung(vung);
        p.setPhiCoBan(BigDecimal.valueOf(coBan));
        p.setPhuPhiXa(BigDecimal.valueOf(xa));
        p.setPhuPhiDacKhu(BigDecimal.valueOf(dacKhu));
        p.setMienPhiTu(mienPhiTu == null ? null : BigDecimal.valueOf(mienPhiTu));
        p.setSoNgayGiaoToiThieu(min);
        p.setSoNgayGiaoToiDa(max);
        return p;
    }

    private static final PhiVanChuyen HCM = bang(0, 15000, 15000, 30000, 3_000_000L, 0, 1);
    private static final PhiVanChuyen HA_NOI = bang(3, 50000, 0, 15000, 5_000_000L, 3, 4);

    private static BigDecimal vnd(long n) {
        return BigDecimal.valueOf(n);
    }

    @Test
    void noiThanhHcm_phiCoBan_giaoTrongNgay() {
        KetQuaPhiVanChuyen kq = PhiVanChuyenService.tinh(HCM, LoaiDiaBan.PHUONG, vnd(500_000), PhuongThucTt.COD, SANG_THU_TU);
        assertThat(kq.tongPhi()).isEqualByComparingTo("15000");
        assertThat(kq.phiKhaiGia()).isEqualByComparingTo("0");
        assertThat(kq.thoiGianGiao()).isEqualTo("Trong ngày - 1 ngày");
        assertThat(kq.ngayGiaoDuKienTu()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(kq.ngayGiaoDuKienDen()).isEqualTo(LocalDate.of(2026, 11, 26));
    }

    @Test
    void xaNgoaiThanhVaDacKhuHcm_coPhuPhi_dacKhuThemNgay() {
        assertThat(PhiVanChuyenService.tinh(HCM, LoaiDiaBan.XA, vnd(500_000), null, SANG_THU_TU).tongPhi())
                .isEqualByComparingTo("30000");
        KetQuaPhiVanChuyen conDao = PhiVanChuyenService.tinh(HCM, LoaiDiaBan.DAC_KHU, vnd(500_000), null, SANG_THU_TU);
        assertThat(conDao.tongPhi()).isEqualByComparingTo("45000");
        assertThat(conDao.soNgayToiThieu()).isEqualTo(1);
        assertThat(conDao.soNgayToiDa()).isEqualTo(3);
    }

    @Test
    void datTren_nguongFreeship_mienPhiToanBo() {
        KetQuaPhiVanChuyen kq = PhiVanChuyenService.tinh(HA_NOI, LoaiDiaBan.PHUONG, vnd(25_000_000), PhuongThucTt.COD, SANG_THU_TU);
        assertThat(kq.mienPhi()).isTrue();
        assertThat(kq.tongPhi()).isEqualByComparingTo("0");
        assertThat(kq.phuPhiCod()).isEqualByComparingTo("0");
        assertThat(kq.phiDuocGiam()).isEqualByComparingTo("0");
    }

    @Test
    void duoiNguongFreeship_tuBaTrieu_congPhiKhaiGia() {
        // Miền Bắc, 4.000.000đ < 5.000.000đ: 50.000 + 0,5% * 4.000.000 = 70.000
        KetQuaPhiVanChuyen kq = PhiVanChuyenService.tinh(HA_NOI, LoaiDiaBan.PHUONG, vnd(4_000_000), null, SANG_THU_TU);
        assertThat(kq.phiKhaiGia()).isEqualByComparingTo("20000");
        assertThat(kq.tongPhi()).isEqualByComparingTo("70000");
        // Voucher vận chuyển chỉ được giảm phần phí vùng
        assertThat(kq.phiDuocGiam()).isEqualByComparingTo("50000");
    }

    @Test
    void phiKhaiGia_toiDa50k_vaPhuPhiCodChoDonTren10Trieu() {
        PhiVanChuyen khongFreeship = bang(3, 50000, 0, 15000, null, 3, 4);
        KetQuaPhiVanChuyen cod = PhiVanChuyenService.tinh(khongFreeship, LoaiDiaBan.PHUONG, vnd(20_000_000), PhuongThucTt.COD, SANG_THU_TU);
        assertThat(cod.phiKhaiGia()).isEqualByComparingTo("50000");
        assertThat(cod.phuPhiCod()).isEqualByComparingTo("15000");
        assertThat(cod.tongPhi()).isEqualByComparingTo("115000");

        KetQuaPhiVanChuyen momo = PhiVanChuyenService.tinh(khongFreeship, LoaiDiaBan.PHUONG, vnd(20_000_000), PhuongThucTt.VI_DIEN_TU, SANG_THU_TU);
        assertThat(momo.phuPhiCod()).isEqualByComparingTo("0");
    }

    @Test
    void datSauGioChot_tinhNgayGiaoTuHomSau() {
        KetQuaPhiVanChuyen kq = PhiVanChuyenService.tinh(HA_NOI, LoaiDiaBan.PHUONG, vnd(500_000), null, TOI_THU_TU);
        assertThat(kq.ngayGiaoDuKienTu()).isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(kq.ngayGiaoDuKienDen()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(kq.thoiGianGiao()).isEqualTo("3 - 4 ngày");
    }

    @Test
    void loaiDiaBan_suyRaTuTenPhuongXa() {
        assertThat(LoaiDiaBan.tuTen("Phường Sài Gòn")).isEqualTo(LoaiDiaBan.PHUONG);
        assertThat(LoaiDiaBan.tuTen("Xã Cần Giờ")).isEqualTo(LoaiDiaBan.XA);
        assertThat(LoaiDiaBan.tuTen("Đặc khu Phú Quốc")).isEqualTo(LoaiDiaBan.DAC_KHU);
        assertThat(LoaiDiaBan.tuTen(null)).isEqualTo(LoaiDiaBan.PHUONG);
    }
}
