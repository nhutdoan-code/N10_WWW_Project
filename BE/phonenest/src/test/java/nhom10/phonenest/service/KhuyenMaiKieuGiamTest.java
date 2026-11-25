package nhom10.phonenest.service;

import nhom10.phonenest.dto.KhuyenMaiDto;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.enums.KieuGiam;
import nhom10.phonenest.enums.LoaiKhuyenMai;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.SuDungKhuyenMaiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** M23: voucher % bắt buộc có trần, voucher tiền mặt cố định theo bậc đơn. */
@ExtendWith(MockitoExtension.class)
class KhuyenMaiKieuGiamTest {

    @Mock private KhuyenMaiRepository khuyenMaiRepository;
    @Mock private SuDungKhuyenMaiRepository suDungKhuyenMaiRepository;
    @Mock private KhachHangRepository khachHangRepository;
    @Mock private DonHangRepository donHangRepository;
    @Mock private NhatKyHoatDongService nhatKyHoatDongService;
    @InjectMocks private KhuyenMaiService service;

    private static KhuyenMai soTien(long giam, long toiThieu) {
        KhuyenMai k = new KhuyenMai();
        k.setKieuGiam(KieuGiam.SO_TIEN);
        k.setSoTienGiamCoDinh(BigDecimal.valueOf(giam));
        k.setDonToiThieu(BigDecimal.valueOf(toiThieu));
        return k;
    }

    private static KhuyenMai phanTram(String pt, long tran) {
        KhuyenMai k = new KhuyenMai();
        k.setKieuGiam(KieuGiam.PHAN_TRAM);
        k.setPhanTramGiam(new BigDecimal(pt));
        k.setSoTienGiamToiDa(BigDecimal.valueOf(tran));
        return k;
    }

    private static KhuyenMaiDto dto(String ma, KieuGiam kieu, String phanTram, Long tran, Long coDinh, long toiThieu) {
        return new KhuyenMaiDto(ma, "Test", LoaiKhuyenMai.DON_HANG,
                phanTram == null ? null : new BigDecimal(phanTram), tran == null ? null : BigDecimal.valueOf(tran),
                BigDecimal.valueOf(toiThieu), null, 0, false, 1, LocalDate.now(), LocalDate.now().plusDays(30), null, true,
                kieu, coDinh == null ? null : BigDecimal.valueOf(coDinh));
    }

    @Test
    @DisplayName("Giảm tiền mặt: trừ đúng số tiền cố định, không vượt cơ sở")
    void tienMatCoDinh() {
        assertThat(service.tinhGiam(soTien(400_000, 7_000_000), BigDecimal.valueOf(12_000_000))).isEqualByComparingTo("400000");
        // voucher vận chuyển tiền mặt 50k trên phí ship 30k -> chỉ giảm 30k
        assertThat(service.tinhGiam(soTien(50_000, 0), BigDecimal.valueOf(30_000))).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("Giảm %: máy 20 triệu với 5% trần 300k chỉ trừ 300k")
    void phanTramCoTran() {
        assertThat(service.tinhGiam(phanTram("5", 300_000), BigDecimal.valueOf(20_000_000))).isEqualByComparingTo("300000");
        assertThat(service.tinhGiam(phanTram("5", 300_000), BigDecimal.valueOf(5_000_000))).isEqualByComparingTo("250000");
    }

    @Test
    @DisplayName("Tạo voucher % không có trần -> 400")
    void phanTramBatBuocCoTran() {
        assertThatThrownBy(() -> service.tao(dto("PT5", KieuGiam.PHAN_TRAM, "5", null, null, 0)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("mức giảm tối đa");
    }

    @Test
    @DisplayName("Voucher tiền mặt: số tiền giảm > đơn tối thiểu -> 400; hợp lệ thì lưu đúng kiểu")
    void tienMatVaDonToiThieu() {
        assertThatThrownBy(() -> service.tao(dto("TM500", KieuGiam.SO_TIEN, null, null, 500_000L, 300_000)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Đơn tối thiểu");

        when(khuyenMaiRepository.existsById("TM150")).thenReturn(false);
        when(khuyenMaiRepository.save(any(KhuyenMai.class))).thenAnswer(inv -> inv.getArgument(0));
        KhuyenMaiDto kq = service.tao(dto("TM150", KieuGiam.SO_TIEN, "7", 999L, 150_000L, 3_000_000));
        assertThat(kq.kieuGiam()).isEqualTo(KieuGiam.SO_TIEN);
        assertThat(kq.soTienGiamCoDinh()).isEqualByComparingTo("150000");
        // field của kiểu % bị bỏ để không gây hiểu nhầm
        assertThat(kq.phanTramGiam()).isNull();
        assertThat(kq.soTienGiamToiDa()).isNull();
    }

    @Test
    @DisplayName("Không đổi được kiểu giảm sau khi tạo")
    void khongDoiKieuGiam() {
        KhuyenMai k = phanTram("5", 300_000);
        k.setMaKm("NEST5");
        k.setLoai(LoaiKhuyenMai.DON_HANG);
        lenient().when(khuyenMaiRepository.findById("NEST5")).thenReturn(Optional.of(k));
        assertThatThrownBy(() -> service.sua("NEST5", dto("NEST5", KieuGiam.SO_TIEN, null, null, 100_000L, 1_000_000)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("kiểu giảm");
    }
}
