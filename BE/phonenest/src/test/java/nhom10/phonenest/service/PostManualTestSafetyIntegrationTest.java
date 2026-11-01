package nhom10.phonenest.service;

import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.KhuyenMaiDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.dto.ThuongHieuDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.LoaiKhuyenMai;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.service.ai.SemanticSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostManualTestSafetyIntegrationTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Phase 2 — Order State Machine Transitions")
    class OrderStateMachineTest {

        @Mock
        private DonHangRepository donHangRepository;

        @Mock
        private ChiTietDonHangRepository chiTietDonHangRepository;

        @Mock
        private SanPhamRepository sanPhamRepository;

        @Mock
        private PhienBanSanPhamRepository phienBanRepository;

        @Mock
        private KhachHangRepository khachHangRepository;

        @Mock
        private KhuyenMaiRepository khuyenMaiRepository;

        @Mock
        private PhiVanChuyenRepository phiVanChuyenRepository;

        @Mock
        private LichSuTrangThaiDonHangRepository lichSuRepository;

        @Mock
        private DonHangMapper mapper;

        @Mock
        private NhatKyHoatDongService nhatKyHoatDongService;

        @Mock
        private ThongBaoService thongBaoService;

        @Mock
        private TaiKhoanRepository taiKhoanRepository;

        @InjectMocks
        private DonHangService donHangService;

        @BeforeEach
        void setUpSecurityContext() {
            var authentication =
                    new UsernamePasswordAuthenticationToken(
                            "ADMIN001",
                            null,
                            List.of()
                    );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
        }

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        private DonHang taoDonHangMau(TrangThaiDonHang trangThai) {
            DonHang dh = new DonHang();
            dh.setMaDonHang("DH001");
            dh.setTrangThai(trangThai);
            dh.setTongThanhToan(new BigDecimal("15000000"));

            KhachHang kh = new KhachHang();
            kh.setMaKh("KH001");
            kh.setHoTen("Nguyễn Văn A");

            dh.setKhachHang(kh);
            dh.setChiTiet(new ArrayList<>());

            return dh;
        }

        private void mockFindByIdForUpdate(DonHang dh) {
            when(donHangRepository.findByIdForUpdate("DH001"))
                    .thenReturn(Optional.of(dh));
        }

        private void mockMapper() {
            when(mapper.toDto(any(DonHang.class))).thenAnswer(inv -> {
                DonHang d = inv.getArgument(0);

                return new DonHangDto(
                        d.getMaDonHang(),
                        "KH001",
                        null,
                        null,
                        null,
                        null,
                        d.getTrangThai(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        d.getTongThanhToan(),
                        null,
                        null,
                        null,
                        "Khách",
                        null,
                        null,
                        null,
                        null,
                        List.of()
                );
            });
        }

        @Test
        @DisplayName("Valid transition: CHO_XAC_NHAN -> DA_XAC_NHAN succeeds")
        void validTransitionChoXacNhanToDaXacNhan() {
            DonHang dh = taoDonHangMau(TrangThaiDonHang.CHO_XAC_NHAN);

            mockFindByIdForUpdate(dh);
            mockMapper();

            DonHangDto dto = donHangService.capNhatTrangThai(
                    "DH001",
                    TrangThaiDonHang.DA_XAC_NHAN
            );

            assertThat(dto.trangThai())
                    .isEqualTo(TrangThaiDonHang.DA_XAC_NHAN);

            assertThat(dh.getTrangThai())
                    .isEqualTo(TrangThaiDonHang.DA_XAC_NHAN);

            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.CHANGE_ORDER_STATUS),
                    eq(LoaiDoiTuongAudit.ORDER),
                    eq("DH001"),
                    anyMap(),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Valid transition: CHO_XAC_NHAN -> DA_HUY succeeds")
        void validTransitionChoXacNhanToDaHuy() {
            DonHang dh = taoDonHangMau(TrangThaiDonHang.CHO_XAC_NHAN);

            mockFindByIdForUpdate(dh);
            mockMapper();

            DonHangDto dto = donHangService.capNhatTrangThai(
                    "DH001",
                    TrangThaiDonHang.DA_HUY
            );

            assertThat(dto.trangThai())
                    .isEqualTo(TrangThaiDonHang.DA_HUY);

            assertThat(dh.getTrangThai())
                    .isEqualTo(TrangThaiDonHang.DA_HUY);
        }

        @Test
        @DisplayName("Valid transition: DANG_GIAO -> CHO_KHACH_XAC_NHAN succeeds")
        void validTransitionDangGiaoToChoKhachXacNhan() {
            DonHang dh = taoDonHangMau(TrangThaiDonHang.DANG_GIAO);

            mockFindByIdForUpdate(dh);
            mockMapper();

            DonHangDto dto = donHangService.danhDauDaGiao("DH001");

            assertThat(dto.trangThai())
                    .isEqualTo(TrangThaiDonHang.CHO_KHACH_XAC_NHAN);

            assertThat(dh.getTrangThai())
                    .isEqualTo(TrangThaiDonHang.CHO_KHACH_XAC_NHAN);

            assertThat(dh.getNgayGiao())
                    .isNotNull();
        }

        @Test
        @DisplayName("Invalid transition: Terminal state DA_GIAO cannot change")
        void invalidTransitionTerminalDaGiaoCannotChange() {
            DonHang dh = taoDonHangMau(TrangThaiDonHang.DA_GIAO);

            mockFindByIdForUpdate(dh);

            assertThatThrownBy(() ->
                    donHangService.capNhatTrangThai(
                            "DH001",
                            TrangThaiDonHang.DANG_GIAO
                    )
            )
                    .isInstanceOf(AppException.class)
                    .matches(e ->
                            ((AppException) e).getStatus()
                                    == HttpStatus.BAD_REQUEST
                    )
                    .hasMessageContaining("Đơn hàng đã kết thúc");

            verify(
                    nhatKyHoatDongService,
                    never()
            ).ghi(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );
        }

        @Test
        @DisplayName("Invalid transition: Terminal state DA_HUY cannot change")
        void invalidTransitionTerminalDaHuyCannotChange() {
            DonHang dh = taoDonHangMau(TrangThaiDonHang.DA_HUY);

            mockFindByIdForUpdate(dh);

            assertThatThrownBy(() ->
                    donHangService.capNhatTrangThai(
                            "DH001",
                            TrangThaiDonHang.CHO_XAC_NHAN
                    )
            )
                    .isInstanceOf(AppException.class)
                    .matches(e ->
                            ((AppException) e).getStatus()
                                    == HttpStatus.BAD_REQUEST
                    )
                    .hasMessageContaining("Đơn hàng đã kết thúc");
        }

        @Test
        @DisplayName("Invalid transition: Cannot skip CHO_XAC_NHAN -> DA_GIAO")
        void invalidTransitionSkipSteps() {
            DonHang dh = taoDonHangMau(TrangThaiDonHang.CHO_XAC_NHAN);

            mockFindByIdForUpdate(dh);

            assertThatThrownBy(() ->
                    donHangService.capNhatTrangThai(
                            "DH001",
                            TrangThaiDonHang.DA_GIAO
                    )
            )
                    .isInstanceOf(AppException.class)
                    .matches(e ->
                            ((AppException) e).getStatus()
                                    == HttpStatus.BAD_REQUEST
                    );
        }

        @Test
        @DisplayName("Invalid transition: Generic update cannot move DANG_GIAO -> CHO_KHACH_XAC_NHAN")
        void invalidGenericTransitionDangGiaoToChoKhachXacNhan() {
            DonHang dh = taoDonHangMau(TrangThaiDonHang.DANG_GIAO);

            mockFindByIdForUpdate(dh);

            assertThatThrownBy(() ->
                    donHangService.capNhatTrangThai(
                            "DH001",
                            TrangThaiDonHang.CHO_KHACH_XAC_NHAN
                    )
            )
                    .isInstanceOf(AppException.class)
                    .matches(e ->
                            ((AppException) e).getStatus()
                                    == HttpStatus.BAD_REQUEST
                    );
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Phase 3 — Inventory Upper Bounds & Safety")
    class InventorySafetyTest {

        @Mock
        private SanPhamRepository spRepo;

        @Mock
        private PhienBanSanPhamRepository pbRepo;

        @Mock
        private NhatKyHoatDongService nhatKyHoatDongService;

        @InjectMocks
        private KhoService khoService;

        private PhienBanSanPham taoPhienBan(String id, int ton) {
            PhienBanSanPham pb = new PhienBanSanPham();
            pb.setMaPhienBan(id);
            pb.setSoLuongTon(ton);
            SanPham sp = new SanPham();
            sp.setMaSanPham("SP001");
            nhom10.phonenest.entity.LoaiSanPham loai = new nhom10.phonenest.entity.LoaiSanPham();
            loai.setMaLoai("L01");
            loai.setTenLoai("Smartphone");
            sp.setLoai(loai);
            pb.setSanPham(sp);
            return pb;
        }

        @Test
        @DisplayName("Import quantity <= 0 is rejected (HTTP 400)")
        void importZeroOrNegativeRejected() {
            assertThatThrownBy(() -> khoService.nhapKho("PB001", 0))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                    .hasMessageContaining("lớn hơn 0");

            assertThatThrownBy(() -> khoService.nhapKho("PB001", -5))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Import quantity exceeding MAX_IMPORT_QUANTITY (100,000) is rejected (HTTP 400)")
        void importExceedingMaxPerTransactionRejected() {
            assertThatThrownBy(() -> khoService.nhapKho("PB001", 100_001))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                    .hasMessageContaining("không vượt quá");
        }

        @Test
        @DisplayName("Import resulting in total stock exceeding MAX_TOTAL_STOCK (1,000,000) is rejected")
        void importExceedingMaxTotalStockRejected() {
            PhienBanSanPham pb = taoPhienBan("PB001", 950_000);
            when(pbRepo.findById("PB001")).thenReturn(Optional.of(pb));

            assertThatThrownBy(() -> khoService.nhapKho("PB001", 60_000))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                    .hasMessageContaining("không được vượt quá");
        }

        @Test
        @DisplayName("Set stock negative is rejected (HTTP 400)")
        void setStockNegativeRejected() {
            assertThatThrownBy(() -> khoService.datTon("PB001", -1))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Set stock exceeding MAX_TOTAL_STOCK is rejected (HTTP 400)")
        void setStockExceedingMaxRejected() {
            assertThatThrownBy(() -> khoService.datTon("PB001", 1_000_001))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                    .hasMessageContaining("từ 0 đến");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Phase 4 — Product Soft Delete, Impact & Restore")
    class ProductArchiveRestoreTest {

        @Mock
        private SanPhamRepository spRepo;

        @Mock
        private LoaiSanPhamRepository loaiRepo;

        @Mock
        private ThuongHieuRepository thRepo;

        @Mock
        private PhienBanSanPhamRepository pbRepo;

        @Mock
        private NhatKyHoatDongService nhatKyHoatDongService;

        @Mock
        private DonHangRepository donRepo;

        @Mock
        private SemanticSearchService semanticSearchService;

        @InjectMocks
        private SanPhamService sanPhamService;

        private SanPham taoSanPhamMau(String ma, TrangThaiSanPham trangThai) {
            SanPham sp = new SanPham();
            sp.setMaSanPham(ma);
            sp.setTenMay("iPhone 15 Pro Max");
            sp.setTrangThai(trangThai);
            nhom10.phonenest.entity.LoaiSanPham loai = new nhom10.phonenest.entity.LoaiSanPham();
            loai.setMaLoai("L01");
            loai.setTenLoai("Smartphone");
            sp.setLoai(loai);
            sp.setPhienBans(new ArrayList<>());
            return sp;
        }

        @Test
        @DisplayName("archive() sets NGUNG_KINH_DOANH, writes audit and deletes from vector index")
        void archiveProductTest() {
            SanPham sp = taoSanPhamMau("SP001", TrangThaiSanPham.DANG_KINH_DOANH);
            when(spRepo.findById("SP001")).thenReturn(Optional.of(sp));
            when(spRepo.save(any(SanPham.class))).thenAnswer(inv -> inv.getArgument(0));

            SanPhamDto dto = sanPhamService.archive("SP001");

            assertThat(dto.trangThai()).isEqualTo(TrangThaiSanPham.NGUNG_KINH_DOANH);
            assertThat(sp.getTrangThai()).isEqualTo(TrangThaiSanPham.NGUNG_KINH_DOANH);

            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.UPDATE_PRODUCT),
                    eq(LoaiDoiTuongAudit.PRODUCT),
                    eq("SP001"),
                    anyMap(),
                    anyMap()
            );

            // Vector store must be synchronized (removed)
            verify(semanticSearchService, times(1)).removeProduct("SP001");
        }

        @Test
        @DisplayName("restore() sets DANG_KINH_DOANH, writes audit and indexes into vector store")
        void restoreProductTest() {
            SanPham sp = taoSanPhamMau("SP001", TrangThaiSanPham.NGUNG_KINH_DOANH);
            when(spRepo.findById("SP001")).thenReturn(Optional.of(sp));
            when(spRepo.save(any(SanPham.class))).thenAnswer(inv -> inv.getArgument(0));

            SanPhamDto dto = sanPhamService.restore("SP001");

            assertThat(dto.trangThai()).isEqualTo(TrangThaiSanPham.DANG_KINH_DOANH);
            assertThat(sp.getTrangThai()).isEqualTo(TrangThaiSanPham.DANG_KINH_DOANH);

            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.UPDATE_PRODUCT),
                    eq(LoaiDoiTuongAudit.PRODUCT),
                    eq("SP001"),
                    anyMap(),
                    anyMap()
            );

            // Vector store must be re-indexed
            verify(semanticSearchService, times(1)).indexProduct(sp);
        }

        @Test
        @DisplayName("getImpact() returns active and total order counts correctly")
        void getImpactProductTest() {
            SanPham sp = taoSanPhamMau("SP001", TrangThaiSanPham.DANG_KINH_DOANH);
            when(spRepo.findById("SP001")).thenReturn(Optional.of(sp));
            when(donRepo.countActiveOrdersBySanPham("SP001")).thenReturn(3L);
            when(donRepo.countAllOrdersBySanPham("SP001")).thenReturn(15L);

            Map<String, Object> impact = sanPhamService.getImpact("SP001");

            assertThat(impact.get("maSanPham")).isEqualTo("SP001");
            assertThat(impact.get("tenMay")).isEqualTo("iPhone 15 Pro Max");
            assertThat(impact.get("activeOrdersCount")).isEqualTo(3L);
            assertThat(impact.get("totalOrdersCount")).isEqualTo(15L);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Phase 5 — Promotion Bounds & Cardinality Safety")
    class PromotionSafetyTest {

        @Mock
        private KhuyenMaiRepository kmRepo;

        @Mock
        private DonHangRepository donHangRepository;

        @Mock
        private NhatKyHoatDongService nhatKyHoatDongService;

        @InjectMocks
        private KhuyenMaiService khuyenMaiService;

        @Test
        @DisplayName("tao() rejects discount percent <= 0 or > 100")
        void discountPercentValidation() {
            KhuyenMaiDto dtoInvalid0 = new KhuyenMaiDto(
                    "KM01", "Giảm 0%", LoaiKhuyenMai.DON_HANG,
                    BigDecimal.ZERO, null, BigDecimal.ZERO,
                    100, 0, false, LocalDate.now(), LocalDate.now().plusDays(10), "DK", true
            );
            assertThatThrownBy(() -> khuyenMaiService.tao(dtoInvalid0))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                    .hasMessageContaining("Phần trăm giảm phải trong khoảng (0, 100]");

            KhuyenMaiDto dtoInvalid101 = new KhuyenMaiDto(
                    "KM01", "Giảm 101%", LoaiKhuyenMai.DON_HANG,
                    new BigDecimal("100.5"), null, BigDecimal.ZERO,
                    100, 0, false, LocalDate.now(), LocalDate.now().plusDays(10), "DK", true
            );
            assertThatThrownBy(() -> khuyenMaiService.tao(dtoInvalid101))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("archive() sets trangThaiHoatDong to false and records audit")
        void archivePromotionTest() {
            KhuyenMai km = new KhuyenMai();
            km.setMaKm("SALE50");
            km.setTenKm("Giảm 50k");
            km.setTrangThaiHoatDong(true);

            when(kmRepo.findById("SALE50")).thenReturn(Optional.of(km));
            when(kmRepo.save(any(KhuyenMai.class))).thenAnswer(inv -> inv.getArgument(0));

            KhuyenMaiDto res = khuyenMaiService.archive("SALE50");

            assertThat(res.trangThaiHoatDong()).isFalse();
            assertThat(km.isTrangThaiHoatDong()).isFalse();
            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.UPDATE_PROMOTION),
                    eq(LoaiDoiTuongAudit.PROMOTION),
                    eq("SALE50"),
                    anyMap(),
                    anyMap()
            );
        }

        @Test
        @DisplayName("restore() sets trangThaiHoatDong to true and records audit")
        void restorePromotionTest() {
            KhuyenMai km = new KhuyenMai();
            km.setMaKm("SALE50");
            km.setTenKm("Giảm 50k");
            km.setTrangThaiHoatDong(false);

            when(kmRepo.findById("SALE50")).thenReturn(Optional.of(km));
            when(kmRepo.save(any(KhuyenMai.class))).thenAnswer(inv -> inv.getArgument(0));

            KhuyenMaiDto res = khuyenMaiService.restore("SALE50");

            assertThat(res.trangThaiHoatDong()).isTrue();
            assertThat(km.isTrangThaiHoatDong()).isTrue();
            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.UPDATE_PROMOTION),
                    eq(LoaiDoiTuongAudit.PROMOTION),
                    eq("SALE50"),
                    anyMap(),
                    anyMap()
            );
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Phase 6 — Brand Soft Delete & No Cascade")
    class BrandSoftDeleteTest {

        @Mock
        private ThuongHieuRepository repo;

        @Mock
        private SanPhamRepository sanPhamRepository;

        @Mock
        private NhatKyHoatDongService nhatKyHoatDongService;

        @InjectMocks
        private ThuongHieuService thuongHieuService;

        @Test
        @DisplayName("archive() sets trangThaiHoatDong to false")
        void archiveBrandTest() {
            ThuongHieu th = new ThuongHieu();
            th.setMaThuongHieu("TH01");
            th.setTenThuongHieu("Apple");
            th.setTrangThaiHoatDong(true);

            when(repo.findById("TH01")).thenReturn(Optional.of(th));
            when(repo.save(any(ThuongHieu.class))).thenAnswer(inv -> inv.getArgument(0));

            ThuongHieuDto dto = thuongHieuService.archive("TH01");

            assertThat(dto.trangThaiHoatDong()).isFalse();
            assertThat(th.isTrangThaiHoatDong()).isFalse();
        }

        @Test
        @DisplayName("restore() sets trangThaiHoatDong to true")
        void restoreBrandTest() {
            ThuongHieu th = new ThuongHieu();
            th.setMaThuongHieu("TH01");
            th.setTenThuongHieu("Apple");
            th.setTrangThaiHoatDong(false);

            when(repo.findById("TH01")).thenReturn(Optional.of(th));
            when(repo.save(any(ThuongHieu.class))).thenAnswer(inv -> inv.getArgument(0));

            ThuongHieuDto dto = thuongHieuService.restore("TH01");

            assertThat(dto.trangThaiHoatDong()).isTrue();
            assertThat(th.isTrangThaiHoatDong()).isTrue();
        }

        @Test
        @DisplayName("getImpact() returns linked product count without deleting products")
        void getBrandImpactTest() {
            ThuongHieu th = new ThuongHieu();
            th.setMaThuongHieu("TH01");
            th.setTenThuongHieu("Apple");

            when(repo.findById("TH01")).thenReturn(Optional.of(th));
            when(sanPhamRepository.countByThuongHieuMaThuongHieu("TH01")).thenReturn(12L);

            Map<String, Object> impact = thuongHieuService.getImpact("TH01");

            assertThat(impact.get("maThuongHieu")).isEqualTo("TH01");
            assertThat(impact.get("tenThuongHieu")).isEqualTo("Apple");
            assertThat(impact.get("productCount")).isEqualTo(12L);
            verify(sanPhamRepository, never()).delete(any());
        }
    }
}
