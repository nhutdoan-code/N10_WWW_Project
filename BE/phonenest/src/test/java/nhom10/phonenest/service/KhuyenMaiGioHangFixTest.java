package nhom10.phonenest.service;

import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.dto.KhuyenMaiDto;
import nhom10.phonenest.entity.ChiTietGioHang;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.support.TestVariants;
import nhom10.phonenest.enums.LoaiKhuyenMai;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.repository.SuDungKhuyenMaiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm tra các lỗi đã sửa ở Nhiệm vụ 2 (voucher, giỏ hàng). */
class KhuyenMaiGioHangFixTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Voucher")
    class Voucher {
        @Mock private KhuyenMaiRepository kmRepo;
        @Mock private SuDungKhuyenMaiRepository suDungRepo;
        @Mock private KhachHangRepository khRepo;
        @Mock private DonHangRepository dhRepo;
        @Mock private NhatKyHoatDongService nhatKy;
        @InjectMocks private KhuyenMaiService service;

        private KhuyenMai voucher(boolean bat) {
            KhuyenMai k = new KhuyenMai();
            k.setMaKm("NEST10");
            k.setTenKm("Giảm 10%");
            k.setLoai(LoaiKhuyenMai.DON_HANG);
            k.setPhanTramGiam(BigDecimal.TEN);
            k.setDonToiThieu(BigDecimal.ZERO);
            k.setNgayBatDau(LocalDate.now().minusDays(1));
            k.setNgayKetThuc(LocalDate.now().plusDays(10));
            k.setGioiHanMoiKhach(1);
            k.setTrangThaiHoatDong(bat);
            return k;
        }

        @Test
        @DisplayName("Mã đã tắt không được báo là dùng được ở bước xem trước")
        void maDaTatKhongDungDuoc() {
            assertThat(service.lyDoKhongDung(voucher(false), BigDecimal.valueOf(1_000_000), false, null))
                    .isEqualTo("Mã khuyến mãi đã ngừng hoạt động");
            assertThat(service.lyDoKhongDung(voucher(true), BigDecimal.valueOf(1_000_000), false, null)).isNull();
        }

        @Test
        @DisplayName("Sửa thông tin mã đã tắt không tự bật lại mã")
        void suaKhongBatLaiMaDaTat() {
            KhuyenMai k = voucher(false);
            when(kmRepo.findById("NEST10")).thenReturn(Optional.of(k));
            when(kmRepo.save(any(KhuyenMai.class))).thenAnswer(inv -> inv.getArgument(0));
            KhuyenMaiDto d = new KhuyenMaiDto("NEST10", "Giảm 10% (đổi tên)", LoaiKhuyenMai.DON_HANG, BigDecimal.TEN,
                    BigDecimal.valueOf(2_000_000), BigDecimal.ZERO, null, 0, false, 1, k.getNgayBatDau(), k.getNgayKetThuc(), null, null);

            KhuyenMaiDto kq = service.sua("NEST10", d);

            assertThat(kq.tenKm()).isEqualTo("Giảm 10% (đổi tên)");
            assertThat(kq.trangThaiHoatDong()).isFalse();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Giỏ hàng")
    class GioHang_ {
        @Mock private GioHangRepository gioHangRepo;
        @Mock private PhienBanSanPhamRepository pbRepo;
        @Mock private KhachHangRepository khRepo;
        @InjectMocks private GioHangService service;

        @Test
        @DisplayName("Đồng bộ giỏ bỏ qua dòng có số lượng <= 0 (không làm giảm giỏ hiện có)")
        void syncBoQuaSoLuongKhongHopLe() {
            GioHang g = new GioHang();
            g.setMaGioHang("GH1");
            g.setChiTiet(new ArrayList<>());
            when(gioHangRepo.findByKhachHangMaKh("KH1")).thenReturn(Optional.of(g));

            GioHangDto kq = service.sync("KH1", List.of(
                    new GioHangDto.Request("PB1", -3),
                    new GioHangDto.Request("PB2", 0)));

            assertThat(kq.items()).isEmpty();
            verify(pbRepo, never()).findById(anyString());
        }

        private GioHang gioRong() {
            GioHang g = new GioHang();
            g.setMaGioHang("GH1");
            g.setChiTiet(new ArrayList<>());
            when(gioHangRepo.findByKhachHangMaKh("KH1")).thenReturn(Optional.of(g));
            return g;
        }

        private PhienBanSanPham bienThe(String ma, int ton, boolean daDuyet) {
            SanPham sp = new SanPham();
            sp.setMaSanPham("SP_" + ma);
            sp.setTenMay("May " + ma);
            nhom10.phonenest.entity.LoaiSanPham loai = new nhom10.phonenest.entity.LoaiSanPham();
            loai.setMaLoai("L1");
            loai.setTenLoai("Điện thoại");
            sp.setLoai(loai);
            sp.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
            PhienBanSanPham p = new PhienBanSanPham();
            p.setMaPhienBan(ma);
            p.setSanPham(sp);
            p.setRam(8);
            p.setBoNho(256);
            p.setMauSac("Đen");
            p.setGiaBan(new BigDecimal("10000000"));
            p.setGiaNiemYet(new BigDecimal("11000000"));
            p.setSoLuongTon(ton);
            p.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
            return daDuyet ? TestVariants.daDuyet(p) : p;
        }

        @Test
        @DisplayName("Đồng bộ giỏ vượt tồn kho: giới hạn bằng tồn kho và trả cảnh báo, không bỏ cả dòng")
        void syncGioiHanTheoTonKho() {
            gioRong();
            when(pbRepo.findById("PB1")).thenReturn(Optional.of(bienThe("PB1", 3, true)));

            GioHangDto kq = service.sync("KH1", List.of(new GioHangDto.Request("PB1", 5)));

            assertThat(kq.items()).hasSize(1);
            assertThat(kq.items().get(0).soLuong()).isEqualTo(3);
            assertThat(kq.canhBao()).singleElement().asString().contains("chỉ còn 3");
        }

        @Test
        @DisplayName("Đồng bộ giỏ: biến thể hết hàng / chưa duyệt / không tồn tại bị bỏ kèm cảnh báo")
        void syncBoDongKhongMuaDuocKemCanhBao() {
            gioRong();
            when(pbRepo.findById("HET")).thenReturn(Optional.of(bienThe("HET", 0, true)));
            when(pbRepo.findById("CHUA")).thenReturn(Optional.of(bienThe("CHUA", 5, false)));
            when(pbRepo.findById("MAT")).thenReturn(Optional.empty());

            GioHangDto kq = service.sync("KH1", List.of(
                    new GioHangDto.Request("HET", 1),
                    new GioHangDto.Request("CHUA", 1),
                    new GioHangDto.Request("MAT", 1)));

            assertThat(kq.items()).isEmpty();
            assertThat(kq.canhBao()).hasSize(3);
            assertThat(kq.canhBao().get(0)).contains("hết hàng");
        }

        @Test
        @DisplayName("Đồng bộ giỏ không làm giảm dòng đã có khi tồn kho giảm")
        void syncKhongGiamDongDaCo() {
            GioHang g = gioRong();
            PhienBanSanPham p = bienThe("PB1", 2, true);
            ChiTietGioHang c = new ChiTietGioHang();
            c.setId(new ChiTietGioHang.Id("GH1", "PB1"));
            c.setGioHang(g);
            c.setPhienBan(p);
            c.setSoLuong(2);
            g.getChiTiet().add(c);
            when(pbRepo.findById("PB1")).thenReturn(Optional.of(p));

            GioHangDto kq = service.sync("KH1", List.of(new GioHangDto.Request("PB1", 4)));

            assertThat(kq.items().get(0).soLuong()).isEqualTo(2);
            assertThat(kq.canhBao()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Voucher ở bước thanh toán")
    class VoucherThanhToan {
        private KhuyenMai km(boolean bat, LocalDate bd, LocalDate kt, Integer soLuong, int daDung) {
            KhuyenMai k = new KhuyenMai();
            k.setTrangThaiHoatDong(bat);
            k.setNgayBatDau(bd);
            k.setNgayKetThuc(kt);
            k.setSoLuong(soLuong);
            k.setDaDung(daDung);
            return k;
        }

        @Test
        @DisplayName("Chỉ hiện mã đang bật, trong hạn và còn lượt")
        void locMaHienThi() {
            LocalDate h = LocalDate.of(2026, 11, 15);
            assertThat(ThanhToanService.hienThiOThanhToan(km(true, h.minusDays(1), h.plusDays(1), null, 0), h, true)).isTrue();
            assertThat(ThanhToanService.hienThiOThanhToan(km(true, h, h, 10, 9), h, true)).isTrue();
            assertThat(ThanhToanService.hienThiOThanhToan(km(false, h.minusDays(1), h.plusDays(1), null, 0), h, true)).isFalse();
            assertThat(ThanhToanService.hienThiOThanhToan(km(true, h.minusDays(5), h.minusDays(1), null, 0), h, true)).isFalse();
            assertThat(ThanhToanService.hienThiOThanhToan(km(true, h.plusDays(1), h.plusDays(5), null, 0), h, true)).isFalse();
            assertThat(ThanhToanService.hienThiOThanhToan(km(true, h.minusDays(1), h.plusDays(1), 10, 10), h, true)).isFalse();
        }

        @Test
        @DisplayName("Mã khách hàng mới chỉ hiện với tài khoản chưa có đơn")
        void maKhachMoiAnVoiKhachCu() {
            LocalDate h = LocalDate.of(2026, 11, 15);
            KhuyenMai k = km(true, h.minusDays(1), h.plusDays(1), null, 0);
            k.setChiKhachMoi(true);
            assertThat(ThanhToanService.hienThiOThanhToan(k, h, true)).isTrue();
            assertThat(ThanhToanService.hienThiOThanhToan(k, h, false)).isFalse();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Voucher khách hàng mới KHACHMOI10")
    class VoucherKhachMoi {
        @Mock private KhuyenMaiRepository kmRepo;
        @Mock private SuDungKhuyenMaiRepository suDungRepo;
        @Mock private KhachHangRepository khRepo;
        @Mock private DonHangRepository dhRepo;
        @Mock private NhatKyHoatDongService nhatKy;
        @InjectMocks private KhuyenMaiService service;

        /** Giống dữ liệu migration 23 (KHACHMOI150K thay cho KHACHMOI10). */
        private KhuyenMai khachMoi10() {
            KhuyenMai k = new KhuyenMai();
            k.setMaKm("KHACHMOI150K");
            k.setTenKm("Quà khách hàng mới 150.000đ");
            k.setLoai(LoaiKhuyenMai.DON_HANG);
            k.setKieuGiam(nhom10.phonenest.enums.KieuGiam.SO_TIEN);
            k.setSoTienGiamCoDinh(BigDecimal.valueOf(150_000));
            k.setDonToiThieu(BigDecimal.valueOf(1_000_000));
            k.setChiKhachMoi(true);
            k.setGioiHanMoiKhach(1);
            k.setNgayBatDau(LocalDate.now().minusDays(1));
            k.setNgayKetThuc(LocalDate.now().plusYears(1));
            k.setTrangThaiHoatDong(true);
            return k;
        }

        @Test
        @DisplayName("Tài khoản mới: dùng được, giảm cố định 150.000đ dù đơn giá trị cao")
        void taiKhoanMoiDuocGiam10() {
            KhuyenMai k = khachMoi10();
            when(suDungRepo.findByKhuyenMaiMaKmAndKhachHangMaKhForCheck("KHACHMOI150K", "KH1")).thenReturn(List.of());

            assertThat(service.lyDoKhongDung(k, BigDecimal.valueOf(30_000_000), true, "KH1")).isNull();
            assertThat(service.tinhGiam(k, BigDecimal.valueOf(30_000_000))).isEqualByComparingTo("150000");
        }

        @Test
        @DisplayName("Đã có đơn (không tính đơn hủy): không dùng được")
        void khachDaCoDonKhongDung() {
            assertThat(service.lyDoKhongDung(khachMoi10(), BigDecimal.valueOf(1_000_000), false, "KH1"))
                    .isEqualTo("Chỉ dành cho khách hàng mua lần đầu");
        }

        @Test
        @DisplayName("Mỗi tài khoản chỉ dùng 1 lần")
        void moiTaiKhoanMotLan() {
            KhuyenMai k = khachMoi10();
            when(suDungRepo.findByKhuyenMaiMaKmAndKhachHangMaKhForCheck("KHACHMOI150K", "KH1"))
                    .thenReturn(List.of(new nhom10.phonenest.entity.SuDungKhuyenMai()));

            assertThat(service.lyDoKhongDung(k, BigDecimal.valueOf(1_000_000), true, "KH1"))
                    .isEqualTo("Bạn đã đạt giới hạn sử dụng mã này");
        }
    }

    @Test
    @DisplayName("Hiển thị dung lượng: 1024GB → 1TB")
    void dungLuong() {
        assertThat(nhom10.phonenest.util.DungLuongUtil.format(256)).isEqualTo("256GB");
        assertThat(nhom10.phonenest.util.DungLuongUtil.format(1024)).isEqualTo("1TB");
        assertThat(nhom10.phonenest.util.DungLuongUtil.format(2048)).isEqualTo("2TB");
        assertThat(nhom10.phonenest.util.DungLuongUtil.format(0)).isEmpty();
    }
}
