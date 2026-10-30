package nhom10.phonenest.service;

import nhom10.phonenest.dto.ThongKeDto;
import nhom10.phonenest.entity.LoaiSanPham;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.ChiTietDonHangRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.LoaiSanPhamRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThongKeServiceTest {

    @Mock
    private DonHangRepository donHangRepository;

    @Mock
    private ChiTietDonHangRepository chiTietDonHangRepository;

    @Mock
    private PhienBanSanPhamRepository phienBanSanPhamRepository;

    @Mock
    private YeuCauDoiTraRepository yeuCauDoiTraRepository;

    @Mock
    private LoaiSanPhamRepository loaiSanPhamRepository;

    @Mock
    private DonHangMapper donHangMapper;

    @InjectMocks
    private ThongKeService thongKeService;

    @BeforeEach
    void setUp() {
        lenient().when(yeuCauDoiTraRepository.countByNgayTaoRange(any(), any())).thenReturn(0L);
        lenient().when(phienBanSanPhamRepository.countVariantInventorySummary(anyInt()))
                .thenReturn(Collections.singletonList(new Object[]{3L, 19L, 12L}));
        lenient().when(chiTietDonHangRepository.findTopSellingDeliveredAllTime(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(chiTietDonHangRepository.findTopSellingDeliveredBetweenDeliveryDates(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(chiTietDonHangRepository.findDeliveredSalesByCategoryAllTime())
                .thenReturn(Collections.emptyList());
        lenient().when(chiTietDonHangRepository.findDeliveredSalesByCategoryBetweenDeliveryDates(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(loaiSanPhamRepository.findAll()).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Revenue must query DA_GIAO and separate pipeline GMV via database-level aggregation")
    void shouldCalculateRevenueAndPipelineGmvViaRepositoryQueries() {
        LocalDateTime start = LocalDate.of(2026, 10, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 10, 31).plusDays(1).atStartOfDay();

        when(donHangRepository.sumDeliveredRevenueBetweenDeliveryDates(start, end)).thenReturn(new BigDecimal("10000000"));
        when(donHangRepository.countDeliveredOrdersBetweenDeliveryDates(start, end)).thenReturn(1L);

        when(donHangRepository.sumPipelineGmv(anyCollection(), eq(start), eq(end))).thenReturn(new BigDecimal("20000000"));
        when(donHangRepository.countPipelineOrders(anyCollection(), eq(start), eq(end))).thenReturn(4L);

        when(donHangRepository.countOrdersByStatus(start, end)).thenReturn(List.<Object[]>of(
                new Object[]{TrangThaiDonHang.DA_GIAO, 1L},
                new Object[]{TrangThaiDonHang.CHO_XAC_NHAN, 2L},
                new Object[]{TrangThaiDonHang.DANG_GIAO, 2L},
                new Object[]{TrangThaiDonHang.DA_HUY, 1L}
        ));

        ThongKeDto stats = thongKeService.thongKe(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        // Recognized revenue: ONLY DA_GIAO
        assertThat(stats.doanhThu()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(stats.soDonThanhCong()).isEqualTo(1);

        // Pipeline GMV
        assertThat(stats.giaTriDangXuLy()).isEqualByComparingTo(new BigDecimal("20000000"));
        assertThat(stats.soDonDangXuLy()).isEqualTo(4);

        // Created orders: sum of status rows (1 + 2 + 2 + 1 = 6)
        assertThat(stats.soDon()).isEqualTo(6);
        assertThat(stats.soDonHuy()).isEqualTo(1);

        // Status map has all 6 keys
        assertThat(stats.donTheoTrangThai()).containsKeys(
                "CHO_XAC_NHAN", "DA_XAC_NHAN", "DANG_CHUAN_BI", "DANG_GIAO", "DA_GIAO", "DA_HUY"
        );
        assertThat(stats.donTheoTrangThai().get("DA_XAC_NHAN")).isEqualTo(0);
        assertThat(stats.donTheoTrangThai().get("CHO_XAC_NHAN")).isEqualTo(2);

        // Verify no findAll() was called on DonHangRepository
        verify(donHangRepository, never()).findAll();
        verify(donHangRepository, never()).findAllByOrderByNgayMuaDesc();
    }

    @Test
    @DisplayName("Revenue date must pass delivery-date range to sumDeliveredRevenue")
    void shouldQueryDeliveredRevenueByDeliveryDate() {
        LocalDateTime start = LocalDate.of(2026, 10, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 10, 31).plusDays(1).atStartOfDay();

        when(donHangRepository.sumDeliveredRevenueBetweenDeliveryDates(start, end)).thenReturn(new BigDecimal("15000000"));
        when(donHangRepository.countDeliveredOrdersBetweenDeliveryDates(start, end)).thenReturn(1L);
        when(donHangRepository.getDailyRevenueAndDeliveredCount(start, end)).thenReturn(Collections.singletonList(
                new Object[]{"2026-10-02", new BigDecimal("15000000"), 1L}
        ));
        when(donHangRepository.countOrdersByStatus(start, end)).thenReturn(Collections.emptyList());
        when(donHangRepository.sumPipelineGmv(anyCollection(), eq(start), eq(end))).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countPipelineOrders(anyCollection(), eq(start), eq(end))).thenReturn(0L);

        ThongKeDto stats = thongKeService.thongKe(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(stats.doanhThu()).isEqualByComparingTo(new BigDecimal("15000000"));
        assertThat(stats.soDonThanhCong()).isEqualTo(1);
        assertThat(stats.doanhThuTheoNgay()).hasSize(1);
        assertThat(stats.doanhThuTheoNgay().get(0).ngay()).isEqualTo("2026-10-02");
        assertThat(stats.doanhThuTheoNgay().get(0).doanhThu()).isEqualByComparingTo(new BigDecimal("15000000"));
        assertThat(stats.doanhThuTheoNgay().get(0).soDon()).isEqualTo(1);
    }

    @Test
    @DisplayName("Top-selling products must query from ChiTietDonHangRepository with PageRequest.of(0, 5)")
    void shouldQueryTopSellingDeliveredViaRepository() {
        LocalDateTime start = LocalDate.of(2026, 10, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 10, 31).plusDays(1).atStartOfDay();

        when(chiTietDonHangRepository.findTopSellingDeliveredBetweenDeliveryDates(start, end, PageRequest.of(0, 5)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"SP_IPHONE", "iPhone 15", 5L},
                        new Object[]{"SP_SAMSUNG", "Galaxy S24", 3L}
                ));

        when(donHangRepository.sumDeliveredRevenueBetweenDeliveryDates(any(), any())).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countDeliveredOrdersBetweenDeliveryDates(any(), any())).thenReturn(0L);
        when(donHangRepository.sumPipelineGmv(anyCollection(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countPipelineOrders(anyCollection(), any(), any())).thenReturn(0L);
        when(donHangRepository.countOrdersByStatus(any(), any())).thenReturn(Collections.emptyList());
        when(donHangRepository.getDailyRevenueAndDeliveredCount(any(), any())).thenReturn(Collections.emptyList());

        ThongKeDto stats = thongKeService.thongKe(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(stats.banChay()).hasSize(2);
        assertThat(stats.banChay().get(0).maSanPham()).isEqualTo("SP_IPHONE");
        assertThat(stats.banChay().get(0).tenMay()).isEqualTo("iPhone 15");
        assertThat(stats.banChay().get(0).daBan()).isEqualTo(5);
        assertThat(stats.banChay().get(1).maSanPham()).isEqualTo("SP_SAMSUNG");
        assertThat(stats.banChay().get(1).daBan()).isEqualTo(3);
    }

    @Test
    @DisplayName("Inventory KPI must be extracted from repository summary query")
    void shouldExtractInventoryFromRepositorySummary() {
        when(phienBanSanPhamRepository.countVariantInventorySummary(KhoService.NGUONG_SAP_HET))
                .thenReturn(Collections.singletonList(new Object[]{3L, 19L, 12L}));

        when(donHangRepository.sumDeliveredRevenueAllTime()).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countDeliveredOrdersAllTime()).thenReturn(0L);
        when(donHangRepository.sumPipelineGmv(anyCollection(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countPipelineOrders(anyCollection(), any(), any())).thenReturn(0L);
        when(donHangRepository.countOrdersByStatus(any(), any())).thenReturn(Collections.emptyList());
        when(donHangRepository.getDailyRevenueAndDeliveredCount(any(), any())).thenReturn(Collections.emptyList());

        ThongKeDto stats = thongKeService.thongKe(null, null);

        assertThat(stats.soHetHang()).isEqualTo(3);
        assertThat(stats.soSapHetHang()).isEqualTo(19);
        assertThat(stats.soConHang()).isEqualTo(12);

        // Verify PhienBanSanPhamRepository.findAll() is never called
        verify(phienBanSanPhamRepository, never()).findAll();
    }

    @Test
    @DisplayName("Brand gross sales must map category hierarchy to root brand")
    void shouldMapCategoryHierarchyToRootBrand() {
        LoaiSanPham rootApple = new LoaiSanPham();
        rootApple.setMaLoai("L10");
        rootApple.setTenLoai("Apple");
        rootApple.setMaLoaiCha(null);

        LoaiSanPham subPro = new LoaiSanPham();
        subPro.setMaLoai("L11");
        subPro.setTenLoai("iPhone Pro");
        subPro.setMaLoaiCha("L10");

        when(loaiSanPhamRepository.findAll()).thenReturn(List.of(rootApple, subPro));
        when(chiTietDonHangRepository.findDeliveredSalesByCategoryAllTime())
                .thenReturn(Collections.singletonList(
                        new Object[]{"L11", new BigDecimal("40000000"), 2L}
                ));

        when(donHangRepository.sumDeliveredRevenueAllTime()).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countDeliveredOrdersAllTime()).thenReturn(0L);
        when(donHangRepository.sumPipelineGmv(anyCollection(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countPipelineOrders(anyCollection(), any(), any())).thenReturn(0L);
        when(donHangRepository.countOrdersByStatus(any(), any())).thenReturn(Collections.emptyList());
        when(donHangRepository.getDailyRevenueAndDeliveredCount(any(), any())).thenReturn(Collections.emptyList());

        ThongKeDto stats = thongKeService.thongKe(null, null);

        assertThat(stats.theoHang()).hasSize(1);
        assertThat(stats.theoHang().get(0).ten()).isEqualTo("Apple");
        assertThat(stats.theoHang().get(0).doanhThu()).isEqualByComparingTo(new BigDecimal("40000000"));
        assertThat(stats.theoHang().get(0).daBan()).isEqualTo(2);
    }

    @Test
    @DisplayName("Date range validation must throw 400 Bad Request when tu > den")
    void shouldThrowBadRequestWhenTuAfterDen() {
        LocalDate tu = LocalDate.of(2026, 10, 20);
        LocalDate den = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> thongKeService.thongKe(tu, den))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException appException = (AppException) e;
                    assertThat(appException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(appException.getMessage()).contains("Ngày bắt đầu không được sau ngày kết thúc");
                });

        assertThatThrownBy(() -> thongKeService.xuatCsv(tu, den))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException appException = (AppException) e;
                    assertThat(appException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("Legacy delivered order with null ngayGiao: included in all-time, excluded from date-filtered periods and daily series")
    void shouldHandleLegacyDeliveredOrderWithNullNgayGiaoCorrectly() {
        // Case A: All-time (tu == null, den == null)
        // A legacy order (trangThai = DA_GIAO, ngayMua = 2026-09-30, ngayGiao = null, tongThanhToan = 10,000,000)
        // contributes to all-time revenue, top selling, and brand sales
        when(donHangRepository.sumDeliveredRevenueAllTime()).thenReturn(new BigDecimal("10000000"));
        when(donHangRepository.countDeliveredOrdersAllTime()).thenReturn(1L);
        when(donHangRepository.sumPipelineGmv(anyCollection(), isNull(), isNull())).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countPipelineOrders(anyCollection(), isNull(), isNull())).thenReturn(0L);
        when(donHangRepository.countOrdersByStatus(isNull(), isNull())).thenReturn(List.<Object[]>of(
                new Object[]{TrangThaiDonHang.DA_GIAO, 1L}
        ));
        // Daily series has ngayGiao IS NOT NULL, so legacy order with null ngayGiao does NOT produce a row
        when(donHangRepository.getDailyRevenueAndDeliveredCount(isNull(), isNull())).thenReturn(Collections.emptyList());
        when(chiTietDonHangRepository.findTopSellingDeliveredAllTime(PageRequest.of(0, 5)))
                .thenReturn(List.<Object[]>of(new Object[]{"SP_LEGACY", "Legacy Phone", 1L}));

        LoaiSanPham root = new LoaiSanPham();
        root.setMaLoai("L_LEGACY");
        root.setTenLoai("LegacyBrand");
        when(loaiSanPhamRepository.findAll()).thenReturn(List.of(root));
        when(chiTietDonHangRepository.findDeliveredSalesByCategoryAllTime())
                .thenReturn(List.<Object[]>of(new Object[]{"L_LEGACY", new BigDecimal("10000000"), 1L}));

        ThongKeDto allTimeStats = thongKeService.thongKe(null, null);

        // 1. All-time revenue includes 10,000,000
        assertThat(allTimeStats.doanhThu()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(allTimeStats.soDonThanhCong()).isEqualTo(1);
        // Daily series does NOT contain 2026-09-30
        assertThat(allTimeStats.doanhThuTheoNgay()).isEmpty();
        // Top-selling includes legacy item
        assertThat(allTimeStats.banChay()).hasSize(1);
        assertThat(allTimeStats.banChay().get(0).maSanPham()).isEqualTo("SP_LEGACY");
        // Brand sales includes legacy brand
        assertThat(allTimeStats.theoHang()).hasSize(1);
        assertThat(allTimeStats.theoHang().get(0).ten()).isEqualTo("LegacyBrand");

        // Case B: September date filter (tu = 2026-09-01, den = 2026-09-30)
        // Since ngayGiao is NULL, the order does NOT have a delivery date in September
        LocalDateTime sepStart = LocalDate.of(2026, 9, 1).atStartOfDay();
        LocalDateTime sepEnd = LocalDate.of(2026, 9, 30).plusDays(1).atStartOfDay();

        when(donHangRepository.sumDeliveredRevenueBetweenDeliveryDates(sepStart, sepEnd)).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countDeliveredOrdersBetweenDeliveryDates(sepStart, sepEnd)).thenReturn(0L);
        when(donHangRepository.sumPipelineGmv(anyCollection(), eq(sepStart), eq(sepEnd))).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countPipelineOrders(anyCollection(), eq(sepStart), eq(sepEnd))).thenReturn(0L);
        when(donHangRepository.countOrdersByStatus(sepStart, sepEnd)).thenReturn(Collections.emptyList());
        when(donHangRepository.getDailyRevenueAndDeliveredCount(sepStart, sepEnd)).thenReturn(Collections.emptyList());
        when(chiTietDonHangRepository.findTopSellingDeliveredBetweenDeliveryDates(sepStart, sepEnd, PageRequest.of(0, 5)))
                .thenReturn(Collections.emptyList());
        when(chiTietDonHangRepository.findDeliveredSalesByCategoryBetweenDeliveryDates(sepStart, sepEnd))
                .thenReturn(Collections.emptyList());

        ThongKeDto sepStats = thongKeService.thongKe(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        // 2. September date-filtered revenue does NOT include the 10,000,000
        assertThat(sepStats.doanhThu()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(sepStats.soDonThanhCong()).isEqualTo(0);
        assertThat(sepStats.doanhThuTheoNgay()).noneMatch(d -> "2026-09-30".equals(d.ngay()));
        assertThat(sepStats.banChay()).isEmpty();
        assertThat(sepStats.theoHang()).isEmpty();

        // Case C: October date filter (tu = 2026-10-01, den = 2026-10-31)
        LocalDateTime octStart = LocalDate.of(2026, 10, 1).atStartOfDay();
        LocalDateTime octEnd = LocalDate.of(2026, 10, 31).plusDays(1).atStartOfDay();

        when(donHangRepository.sumDeliveredRevenueBetweenDeliveryDates(octStart, octEnd)).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countDeliveredOrdersBetweenDeliveryDates(octStart, octEnd)).thenReturn(0L);
        when(donHangRepository.sumPipelineGmv(anyCollection(), eq(octStart), eq(octEnd))).thenReturn(BigDecimal.ZERO);
        when(donHangRepository.countPipelineOrders(anyCollection(), eq(octStart), eq(octEnd))).thenReturn(0L);
        when(donHangRepository.countOrdersByStatus(octStart, octEnd)).thenReturn(Collections.emptyList());
        when(donHangRepository.getDailyRevenueAndDeliveredCount(octStart, octEnd)).thenReturn(Collections.emptyList());
        when(chiTietDonHangRepository.findTopSellingDeliveredBetweenDeliveryDates(octStart, octEnd, PageRequest.of(0, 5)))
                .thenReturn(Collections.emptyList());
        when(chiTietDonHangRepository.findDeliveredSalesByCategoryBetweenDeliveryDates(octStart, octEnd))
                .thenReturn(Collections.emptyList());

        ThongKeDto octStats = thongKeService.thongKe(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        // 3. October date-filtered revenue does NOT include it
        assertThat(octStats.doanhThu()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(octStats.soDonThanhCong()).isEqualTo(0);
        assertThat(octStats.doanhThuTheoNgay()).noneMatch(d -> "2026-09-30".equals(d.ngay()));
        assertThat(octStats.banChay()).isEmpty();
        assertThat(octStats.theoHang()).isEmpty();
    }
}
