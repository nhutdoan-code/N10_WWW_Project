package nhom10.phonenest.service;

import nhom10.phonenest.dto.HanhViDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.HanhViNguoiDungRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HanhViNguoiDungServiceTest {

    @Mock
    private HanhViNguoiDungRepository hanhViRepository;

    @Mock
    private SanPhamRepository sanPhamRepository;

    @Mock
    private PhienBanSanPhamRepository phienBanRepository;

    @Mock
    private GioHangRepository gioHangRepository;

    private UserPreferenceAggregationService userPreferenceService;
    private HanhViNguoiDungService hanhViService;

    @BeforeEach
    void setUp() {
        userPreferenceService = new UserPreferenceAggregationService(hanhViRepository, sanPhamRepository, gioHangRepository);
        hanhViService = new HanhViNguoiDungService(hanhViRepository, sanPhamRepository, phienBanRepository, userPreferenceService);
    }

    @Test
    @DisplayName("Ghi nhận sự kiện người dùng ẩn danh tự động sinh sessionId fallback")
    void ghiNhan_anonymousUser_generatesFallbackSession() {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                null,
                LoaiHanhViNguoiDung.PRODUCT_CLICK,
                "SP001",
                null,
                null,
                "HOME_BANNER",
                null
        );

        when(sanPhamRepository.existsById("SP001")).thenReturn(true);
        when(hanhViRepository.save(any(HanhViNguoiDung.class))).thenAnswer(inv -> inv.getArgument(0));

        HanhViDto.EventResponse res = hanhViService.ghiNhan(req, null);

        assertThat(res.success()).isTrue();
        assertThat(res.maHanhVi()).startsWith("HV");

        ArgumentCaptor<HanhViNguoiDung> captor = ArgumentCaptor.forClass(HanhViNguoiDung.class);
        verify(hanhViRepository).save(captor.capture());

        HanhViNguoiDung saved = captor.getValue();
        assertThat(saved.getSessionId()).startsWith("ANON_");
        assertThat(saved.getMaKh()).isNull();
        assertThat(saved.getLoaiHanhVi()).isEqualTo(LoaiHanhViNguoiDung.PRODUCT_CLICK);
    }

    @Test
    @DisplayName("Ghi nhận sự kiện người dùng đã đăng nhập lưu đúng maKh")
    void ghiNhan_authenticatedUser_recordsMaKh() {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.SEARCH,
                null,
                null,
                "iPhone 15 Pro Max",
                "SEARCH_BAR",
                null
        );

        when(hanhViRepository.save(any(HanhViNguoiDung.class))).thenAnswer(inv -> inv.getArgument(0));

        HanhViDto.EventResponse res = hanhViService.ghiNhan(req, "KH001");

        assertThat(res.success()).isTrue();
        ArgumentCaptor<HanhViNguoiDung> captor = ArgumentCaptor.forClass(HanhViNguoiDung.class);
        verify(hanhViRepository).save(captor.capture());

        HanhViNguoiDung saved = captor.getValue();
        assertThat(saved.getSessionId()).isEqualTo("SESSION_123");
        assertThat(saved.getMaKh()).isEqualTo("KH001");
        assertThat(saved.getTuKhoa()).isEqualTo("iPhone 15 Pro Max");
    }

    @Test
    @DisplayName("Throttling chặn ghi nhận PRODUCT_VIEW trùng lặp trong khoảng thời gian ngắn")
    void ghiNhan_productView_throttledWhenDuplicateRecent() {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.PRODUCT_VIEW,
                "SP001",
                null,
                null,
                "PRODUCT_DETAIL",
                null
        );

        when(sanPhamRepository.existsById("SP001")).thenReturn(true);
        when(hanhViRepository.existsRecentView(
                eq("KH001"), eq("SESSION_123"), eq("SP001"), eq(LoaiHanhViNguoiDung.PRODUCT_VIEW), any(LocalDateTime.class)))
                .thenReturn(true);

        HanhViDto.EventResponse res = hanhViService.ghiNhan(req, "KH001");

        assertThat(res.success()).isTrue();
        assertThat(res.maHanhVi()).isEqualTo("THROTTLED");
        verify(hanhViRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tự động cắt ngắn từ khóa và metadata dài quá giới hạn cột")
    void ghiNhan_truncatesLongKeywordsAndMetadata() {
        String longKeyword = "a".repeat(300);
        String longMetadata = "m".repeat(1200);

        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.SEARCH,
                null,
                null,
                longKeyword,
                "SEARCH_BAR",
                longMetadata
        );

        when(hanhViRepository.save(any(HanhViNguoiDung.class))).thenAnswer(inv -> inv.getArgument(0));

        hanhViService.ghiNhan(req, "KH001");

        ArgumentCaptor<HanhViNguoiDung> captor = ArgumentCaptor.forClass(HanhViNguoiDung.class);
        verify(hanhViRepository).save(captor.capture());

        HanhViNguoiDung saved = captor.getValue();
        assertThat(saved.getTuKhoa()).hasSize(255);
        assertThat(saved.getMetadata()).hasSize(1000);
    }

    @Test
    @DisplayName("Ghi nhận hành vi mua hàng PURCHASE từ backend thành công")
    void ghiNhanPurchase_recordsPurchaseEvent() {
        when(hanhViRepository.save(any(HanhViNguoiDung.class))).thenAnswer(inv -> inv.getArgument(0));

        hanhViService.ghiNhanPurchase("KH001", "SP001", "PB001", "DH12345", BigDecimal.valueOf(25000000));

        ArgumentCaptor<HanhViNguoiDung> captor = ArgumentCaptor.forClass(HanhViNguoiDung.class);
        verify(hanhViRepository).save(captor.capture());

        HanhViNguoiDung saved = captor.getValue();
        assertThat(saved.getMaKh()).isEqualTo("KH001");
        assertThat(saved.getMaSanPham()).isEqualTo("SP001");
        assertThat(saved.getMaPhienBan()).isEqualTo("PB001");
        assertThat(saved.getLoaiHanhVi()).isEqualTo(LoaiHanhViNguoiDung.PURCHASE);
        assertThat(saved.getMetadata()).contains("DH12345");
    }

    @Test
    @DisplayName("Idempotent PURCHASE: Không ghi nhận trùng sự kiện PURCHASE khi checkout retry")
    void ghiNhanPurchase_skipsDuplicateWhenAlreadyRecorded() {
        when(hanhViRepository.existsBySessionIdAndMaSanPhamAndLoaiHanhVi(
                "BACKEND_ORDER_DH12345", "SP001", LoaiHanhViNguoiDung.PURCHASE))
                .thenReturn(true);

        hanhViService.ghiNhanPurchase("KH001", "SP001", "PB001", "DH12345", BigDecimal.valueOf(25000000));

        verify(hanhViRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thống kê hành vi tổng hợp đúng lượt và danh sách top")
    void layThongKe_aggregatesCorrectly() {
        List<Object[]> grouped = new ArrayList<>();
        grouped.add(new Object[]{"PRODUCT_VIEW", 10L});
        grouped.add(new Object[]{"PURCHASE", 2L});

        List<Object[]> keywordRows = new ArrayList<>();
        keywordRows.add(new Object[]{"iphone", 5L});

        when(hanhViRepository.countGroupedByLoaiHanhVi(any(), any(), any())).thenReturn(grouped);
        when(hanhViRepository.findTopProductsByLoai(eq(LoaiHanhViNguoiDung.PRODUCT_VIEW), any(), any(), any())).thenReturn(List.of());
        when(hanhViRepository.findTopProductsByLoai(eq(LoaiHanhViNguoiDung.PRODUCT_CLICK), any(), any(), any())).thenReturn(List.of());
        when(hanhViRepository.findTopKeywords(any(), any(), any())).thenReturn(keywordRows);
        when(hanhViRepository.findAllByOrderByThoiGianDesc(any())).thenReturn(List.of());

        HanhViDto.BehaviorStatsResponse stats = hanhViService.layThongKe(LocalDate.now().minusDays(7), LocalDate.now(), null, null);

        assertThat(stats.tongLuot()).isEqualTo(12L);
        assertThat(stats.theoLoai()).containsEntry("PRODUCT_VIEW", 10L);
        assertThat(stats.theoLoai()).containsEntry("PURCHASE", 2L);
        assertThat(stats.topTuKhoa()).hasSize(1);
        assertThat(stats.topTuKhoa().get(0).tuKhoa()).isEqualTo("iphone");
    }

    @Test
    @DisplayName("Tổng hợp sở thích khách hàng tạo context ngữ cảnh cho Spring AI")
    void userPreferenceAggregation_buildsAiPromptContext() {
        String maKh = "KH001";
        HanhViNguoiDung event = HanhViNguoiDung.builder()
                .maHanhVi("HV001")
                .maKh(maKh)
                .sessionId("S1")
                .maSanPham("SP001")
                .loaiHanhVi(LoaiHanhViNguoiDung.PURCHASE)
                .thoiGian(LocalDateTime.now())
                .build();

        when(hanhViRepository.countByMaKh(maKh)).thenReturn(1L);
        when(hanhViRepository.findByMaKhOrderByThoiGianDesc(eq(maKh), any(PageRequest.class)))
                .thenReturn(List.of(event));

        SanPham sp = new SanPham();
        sp.setMaSanPham("SP001");
        sp.setTenMay("iPhone 15 Pro Max");

        ThuongHieu th = new ThuongHieu();
        th.setTenThuongHieu("Apple");
        sp.setThuongHieu(th);

        LoaiSanPham loai = new LoaiSanPham();
        loai.setTenLoai("Smartphone");
        sp.setLoai(loai);

        PhienBanSanPham pb = new PhienBanSanPham();
        pb.setGiaBan(BigDecimal.valueOf(30000000));
        pb.setTrangThai(nhom10.phonenest.enums.TrangThaiSanPham.DANG_KINH_DOANH);
        sp.getPhienBans().add(pb);

        when(sanPhamRepository.findAllById(any())).thenReturn(List.of(sp));
        when(gioHangRepository.findByKhachHangMaKh(maKh)).thenReturn(Optional.empty());
        when(hanhViRepository.findRecentKeywordsByMaKh(eq(maKh), any(PageRequest.class)))
                .thenReturn(List.of("iphone 15 pro"));

        HanhViDto.UserPreferenceDto pref = userPreferenceService.aggregatePreferences(maKh);

        assertThat(pref.topThuongHieu()).contains("Apple");
        assertThat(pref.topDanhMuc()).contains("Smartphone");
        assertThat(pref.giaThapNhat()).isEqualByComparingTo(BigDecimal.valueOf(30000000));
        assertThat(pref.tuKhoaGanDay()).contains("iphone 15 pro");
        assertThat(pref.tomTatAi()).contains("Apple");

        String aiContext = userPreferenceService.buildAiPromptContext(maKh);
        assertThat(aiContext).contains("SỞ THÍCH VÀ HÀNH VI KHÁCH HÀNG HIỆN TẠI");
        assertThat(aiContext).contains("Apple");
    }

    @Test
    @DisplayName("Client gửi hành vi PURCHASE bị từ chối với 400 Bad Request")
    void ghiNhan_clientSubmitsPurchase_throwsBadRequest() {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.PURCHASE,
                "SP001",
                null,
                null,
                "CLIENT_SPOOF",
                null
        );

        assertThatThrownBy(() -> hanhViService.ghiNhan(req, "KH001"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getMessage()).contains("Hành vi PURCHASE chỉ được ghi nhận");
                });

        verify(hanhViRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sản phẩm không tồn tại bị từ chối 400 Bad Request (không âm thầm đổi thành null)")
    void ghiNhan_nonExistentProduct_throwsBadRequest() {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.PRODUCT_CLICK,
                "SP_NON_EXISTENT",
                null,
                null,
                "BANNER",
                null
        );

        when(sanPhamRepository.existsById("SP_NON_EXISTENT")).thenReturn(false);

        assertThatThrownBy(() -> hanhViService.ghiNhan(req, "KH001"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getMessage()).contains("Sản phẩm không tồn tại");
                });

        verify(hanhViRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sự kiện yêu cầu sản phẩm nhưng truyền null/blank bị từ chối 400 Bad Request")
    void ghiNhan_missingRequiredProduct_throwsBadRequest() {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.PRODUCT_VIEW,
                null,
                null,
                null,
                "BANNER",
                null
        );

        assertThatThrownBy(() -> hanhViService.ghiNhan(req, "KH001"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getMessage()).contains("bắt buộc phải có mã sản phẩm");
                });

        verify(hanhViRepository, never()).save(any());
    }

    @Test
    @DisplayName("Phiên bản không thuộc sản phẩm đã chọn bị từ chối 400 Bad Request")
    void ghiNhan_variantNotBelongingToProduct_throwsBadRequest() {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.ADD_TO_CART,
                "SP001",
                "PB_OF_ANOTHER_PRODUCT",
                null,
                "DETAIL",
                null
        );

        when(sanPhamRepository.existsById("SP001")).thenReturn(true);

        SanPham otherSp = new SanPham();
        otherSp.setMaSanPham("SP002");

        PhienBanSanPham pb = new PhienBanSanPham();
        pb.setMaPhienBan("PB_OF_ANOTHER_PRODUCT");
        pb.setSanPham(otherSp);

        when(phienBanRepository.findById("PB_OF_ANOTHER_PRODUCT")).thenReturn(Optional.of(pb));

        assertThatThrownBy(() -> hanhViService.ghiNhan(req, "KH001"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getMessage()).contains("không thuộc sản phẩm");
                });

        verify(hanhViRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rate limiting chặn client xoay sessionId liên tục từ cùng IP (Anonymous IP Rate Limiting)")
    void ghiNhan_rateLimiting_blocksRotatingSessionFromSameIp() {
        String clientIp = "192.168.1.100";
        when(hanhViRepository.save(any(HanhViNguoiDung.class))).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < 60; i++) {
            HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                    "ROTATING_SESSION_" + i,
                    LoaiHanhViNguoiDung.SEARCH,
                    null,
                    null,
                    "search_" + i,
                    "SEARCH_BAR",
                    null
            );
            HanhViDto.EventResponse res = hanhViService.ghiNhan(req, null, clientIp);
            assertThat(res.success()).isTrue();
        }

        // 61st event with yet another brand-new sessionId from same IP MUST be blocked
        HanhViDto.EventRequest req61 = new HanhViDto.EventRequest(
                "ROTATING_SESSION_NEW",
                LoaiHanhViNguoiDung.SEARCH,
                null,
                null,
                "search_61",
                "SEARCH_BAR",
                null
        );
        HanhViDto.EventResponse throttledRes = hanhViService.ghiNhan(req61, null, clientIp);
        assertThat(throttledRes.success()).isFalse();
        assertThat(throttledRes.maHanhVi()).isEqualTo("RATE_LIMITED");
        assertThat(throttledRes.message()).contains("vượt giới hạn");
    }

    @Test
    @DisplayName("Dọn dẹp hành vi cũ gọi repository deleteByThoiGianBefore khi số ngày hợp lệ")
    void xoaHanhViCu_deletesOlderEvents() {
        when(hanhViRepository.deleteByThoiGianBefore(any(LocalDateTime.class))).thenReturn(42);

        int deleted = hanhViService.xoaHanhViCu(90);

        assertThat(deleted).isEqualTo(42);
        verify(hanhViRepository).deleteByThoiGianBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Dọn dẹp hành vi cũ ném lỗi khi số ngày lưu trữ < 7")
    void xoaHanhViCu_invalidDays_throwsBadRequest() {
        assertThatThrownBy(() -> hanhViService.xoaHanhViCu(3))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getMessage()).contains("Số ngày lưu trữ không hợp lệ");
                });

        verify(hanhViRepository, never()).deleteByThoiGianBefore(any());
    }
}
