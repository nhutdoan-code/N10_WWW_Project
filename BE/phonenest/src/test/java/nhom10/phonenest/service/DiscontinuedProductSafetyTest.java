package nhom10.phonenest.service;

import nhom10.phonenest.support.TestVariants;
import nhom10.phonenest.dto.*;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.service.ai.PhoneNestEmbeddingModel;
import nhom10.phonenest.service.ai.ProductSemanticDocumentBuilder;
import nhom10.phonenest.service.ai.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DiscontinuedProductSafetyTest {

    // Repositories for SanPhamService
    @Mock private SanPhamRepository sanPhamRepository;
    @Mock private LoaiSanPhamRepository loaiSanPhamRepository;
    @Mock private ThuongHieuRepository thuongHieuRepository;
    @Mock private PhienBanSanPhamRepository phienBanSanPhamRepository;
    @Mock private HinhAnhSanPhamRepository hinhAnhSanPhamRepository;
    @Mock private DonHangRepository donHangRepository;
    @Mock private ChiTietDonHangRepository chiTietDonHangRepository;
    @Mock private ChiTietGioHangRepository chiTietGioHangRepository;
    @Mock private BienDongKhoRepository bienDongKhoRepository;
    @Mock private NhatKyHoatDongService nhatKyHoatDongService;
    @Mock private SemanticSearchService semanticSearchService;

    // Repositories for GioHangService
    @Mock private GioHangRepository gioHangRepository;
    @Mock private KhachHangRepository khachHangRepository;

    // Repositories for ThanhToanService & DonHangService
    @Mock private DiaChiRepository diaChiRepository;
    @Mock private KhuyenMaiRepository khuyenMaiRepository;
    @Mock private KhuyenMaiService khuyenMaiService;
    @Mock private PhiVanChuyenService phiVanChuyenService;
    @Mock private DiaChiService diaChiService;
    @Mock private HuyDonHangRepository huyDonHangRepository;
    @Mock private ThanhToanRepository thanhToanRepository;
    @Mock private LichSuTrangThaiDonHangRepository lichSuTrangThaiDonHangRepository;
    @Mock private TaiKhoanRepository taiKhoanRepository;
    @Mock private DonHangMapper donHangMapper;
    @Mock private ThongBaoService thongBaoService;
    @Mock private EmailService emailService;
    @Mock private PhoneNestEmbeddingModel embeddingModel;
    @Mock private ProductSemanticDocumentBuilder documentBuilder;

    private SanPhamService sanPhamService;
    private GioHangService gioHangService;
    private ThanhToanService thanhToanService;
    private DonHangService donHangService;
    private SemanticSearchService testSemanticSearchService;

    private SanPham activeSp;
    private PhienBanSanPham activePb;
    private SanPham discontinuedSp;
    private PhienBanSanPham discontinuedPb;
    private KhachHang khachHang;
    private DiaChi diaChi;
    private GioHang gioHang;

    @BeforeEach
    void setUp() {
        sanPhamService = new SanPhamService(
                sanPhamRepository, loaiSanPhamRepository, thuongHieuRepository,
                phienBanSanPhamRepository, hinhAnhSanPhamRepository, donHangRepository,
                chiTietDonHangRepository, chiTietGioHangRepository, bienDongKhoRepository, null,
                nhatKyHoatDongService, semanticSearchService
        );

        gioHangService = new GioHangService(
                gioHangRepository, phienBanSanPhamRepository, khachHangRepository
        );

        thanhToanService = new ThanhToanService(
                gioHangRepository, phienBanSanPhamRepository, diaChiRepository,
                donHangRepository, khuyenMaiRepository, khuyenMaiService,
                phiVanChuyenService, diaChiService
        );

        donHangService = new DonHangService(
                donHangRepository, gioHangRepository, phienBanSanPhamRepository,
                khuyenMaiRepository, huyDonHangRepository, thanhToanRepository,
                lichSuTrangThaiDonHangRepository, bienDongKhoRepository, khuyenMaiService,
                diaChiService, phiVanChuyenService, thanhToanService,
                khachHangRepository, taiKhoanRepository, diaChiRepository,
                donHangMapper, gioHangService, thongBaoService, emailService,
                nhatKyHoatDongService
        );

        testSemanticSearchService = new SemanticSearchService(
                sanPhamRepository, embeddingModel, documentBuilder
        );

        // Setup base entities
        LoaiSanPham loai = new LoaiSanPham();
        loai.setMaLoai("L01");
        loai.setTenLoai("Smartphone");

        ThuongHieu th = new ThuongHieu();
        th.setMaThuongHieu("TH01");
        th.setTenThuongHieu("Samsung");

        ThuongHieu thVivo = new ThuongHieu();
        thVivo.setMaThuongHieu("TH06");
        thVivo.setTenThuongHieu("vivo");

        // 1. Active Product: Samsung Galaxy A56 5G (SP1793202488261001)
        activeSp = new SanPham();
        activeSp.setMaSanPham("SP1793202488261001");
        activeSp.setTenMay("Samsung Galaxy A56 5G");
        activeSp.setLoai(loai);
        activeSp.setThuongHieu(th);
        activeSp.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);

        activePb = new PhienBanSanPham();
        activePb.setMaPhienBan("PB1793202488339002");
        activePb.setSanPham(activeSp);
        activePb.setSku("SKU-A56-DEN");
        activePb.setMauSac("Đen");
        activePb.setRam(8);
        activePb.setBoNho(128);
        activePb.setGiaBan(BigDecimal.valueOf(9990000));
        activePb.setSoLuongTon(15);
        activePb.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
        // 2B.7B: bien the moi mac dinh PENDING_REVIEW; fixture can phe duyet hop le (co dau van tay)
        TestVariants.daDuyet(activePb);
        activeSp.setPhienBans(List.of(activePb));

        // 2. Discontinued Product: vivo Y12s (SP500500)
        discontinuedSp = new SanPham();
        discontinuedSp.setMaSanPham("SP500500");
        discontinuedSp.setTenMay("vivo Y12s");
        discontinuedSp.setLoai(loai);
        discontinuedSp.setThuongHieu(thVivo);
        discontinuedSp.setTrangThai(TrangThaiSanPham.NGUNG_KINH_DOANH);

        discontinuedPb = new PhienBanSanPham();
        discontinuedPb.setMaPhienBan("PB500500");
        discontinuedPb.setSanPham(discontinuedSp);
        discontinuedPb.setSku("SKU-Y12S-DEN");
        discontinuedPb.setMauSac("Đen");
        discontinuedPb.setRam(3);
        discontinuedPb.setBoNho(32);
        discontinuedPb.setGiaBan(BigDecimal.valueOf(2290000));
        discontinuedPb.setSoLuongTon(0);
        discontinuedPb.setTrangThai(TrangThaiSanPham.NGUNG_KINH_DOANH);
        discontinuedSp.setPhienBans(List.of(discontinuedPb));

        // Customer & Address
        khachHang = new KhachHang();
        khachHang.setMaKh("TK002");
        khachHang.setHoTen("Nguyễn Văn A");

        diaChi = new DiaChi();
        diaChi.setMaDiaChi("DC001");
        diaChi.setMaKh("TK002");
        diaChi.setMaTinh("01");
        diaChi.setTenNguoiNhan("Nguyễn Văn A");
        diaChi.setSoDienThoaiNhan("0912345678");

        // Cart GH002 containing both active & discontinued lines
        gioHang = new GioHang();
        gioHang.setMaGioHang("GH002");
        gioHang.setKhachHang(khachHang);

        ChiTietGioHang ctActive = new ChiTietGioHang();
        ctActive.setId(new ChiTietGioHang.Id("GH002", activePb.getMaPhienBan()));
        ctActive.setGioHang(gioHang);
        ctActive.setPhienBan(activePb);
        ctActive.setSoLuong(1);

        ChiTietGioHang ctDiscontinued = new ChiTietGioHang();
        ctDiscontinued.setId(new ChiTietGioHang.Id("GH002", discontinuedPb.getMaPhienBan()));
        ctDiscontinued.setGioHang(gioHang);
        ctDiscontinued.setPhienBan(discontinuedPb);
        ctDiscontinued.setSoLuong(1);

        gioHang.setChiTiet(new ArrayList<>(List.of(ctActive, ctDiscontinued)));
    }

    // =========================================================================
    // CASE 1: Xem danh sách chỉ có sản phẩm đang bán
    // =========================================================================
    @Test
    @DisplayName("Test 1: Khách hàng xem danh sách sản phẩm chỉ thấy các sản phẩm DANG_KINH_DOANH")
    void test1_CustomerProductListOnlyContainsActiveProducts() {
        when(sanPhamRepository.findAll()).thenReturn(List.of(activeSp, discontinuedSp));

        List<SanPhamDto> result = sanPhamService.list(null, null, null, null, null, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).maSanPham()).isEqualTo("SP1793202488261001");
        assertThat(result.get(0).trangThai()).isEqualTo(TrangThaiSanPham.DANG_KINH_DOANH);
    }

    // =========================================================================
    // CASE 2: Xem chi tiết sản phẩm ngừng bán qua ID
    // =========================================================================
    @Test
    @DisplayName("Test 2: Khách truy cập trực tiếp ID sản phẩm ngừng bán vẫn xem được thông tin và nhận trạng thái NGUNG_KINH_DOANH")
    void test2_DirectAccessToDiscontinuedProductReturnsDetailsWithDiscontinuedStatus() {
        when(sanPhamRepository.findById("SP500500")).thenReturn(Optional.of(discontinuedSp));

        SanPhamDto dto = sanPhamService.get("SP500500", false);

        assertThat(dto).isNotNull();
        assertThat(dto.maSanPham()).isEqualTo("SP500500");
        assertThat(dto.tenMay()).isEqualTo("vivo Y12s");
        assertThat(dto.trangThai()).isEqualTo(TrangThaiSanPham.NGUNG_KINH_DOANH);
        assertThat(dto.phienBans()).hasSize(1);
        assertThat(dto.phienBans().get(0).trangThai()).isEqualTo(TrangThaiSanPham.NGUNG_KINH_DOANH);
    }

    // =========================================================================
    // CASE 3: Tìm kiếm không trả về sản phẩm ngừng bán
    // =========================================================================
    @Test
    @DisplayName("Test 3: Tìm kiếm từ khóa không bao giờ trả về sản phẩm NGUNG_KINH_DOANH")
    void test3_SearchDoesNotReturnDiscontinuedProducts() {
        when(sanPhamRepository.findAll()).thenReturn(List.of(activeSp, discontinuedSp));

        List<SanPhamDto> searchResults = sanPhamService.timKiem(
                "vivo", null, null, null, null, null, null, null, null, null, false
        );

        assertThat(searchResults).isEmpty();

        List<SanPhamDto> searchSamsung = sanPhamService.timKiem(
                "Samsung", null, null, null, null, null, null, null, null, null, false
        );
        assertThat(searchSamsung).hasSize(1);
        assertThat(searchSamsung.get(0).maSanPham()).isEqualTo("SP1793202488261001");
    }

    // =========================================================================
    // CASE 4: Không thêm được sản phẩm ngừng bán vào giỏ
    // =========================================================================
    @Test
    @DisplayName("Test 4: Không thể thêm vào giỏ sản phẩm có SanPham cha ở trạng thái NGUNG_KINH_DOANH")
    void test4_CannotAddDiscontinuedProductToCart() {
        // Giả sử variant là DANG_KINH_DOANH nhưng SanPham cha là NGUNG_KINH_DOANH
        PhienBanSanPham pbWithDiscontinuedParent = new PhienBanSanPham();
        pbWithDiscontinuedParent.setMaPhienBan("PB_TEST_DISCONTINUED_PARENT");
        pbWithDiscontinuedParent.setSanPham(discontinuedSp);
        pbWithDiscontinuedParent.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
        pbWithDiscontinuedParent.setSoLuongTon(10);

        when(gioHangRepository.findByKhachHangMaKh("TK002")).thenReturn(Optional.of(gioHang));
        when(phienBanSanPhamRepository.findById("PB_TEST_DISCONTINUED_PARENT")).thenReturn(Optional.of(pbWithDiscontinuedParent));

        assertThatThrownBy(() -> gioHangService.add("TK002", "PB_TEST_DISCONTINUED_PARENT", 1))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã ngừng kinh doanh");
    }

    // =========================================================================
    // CASE 5: Không thêm được phiên bản ngừng bán vào giỏ
    // =========================================================================
    @Test
    @DisplayName("Test 5: Không thể thêm phiên bản NGUNG_KINH_DOANH vào giỏ hàng")
    void test5_CannotAddDiscontinuedVariantToCart() {
        when(gioHangRepository.findByKhachHangMaKh("TK002")).thenReturn(Optional.of(gioHang));
        when(phienBanSanPhamRepository.findById("PB500500")).thenReturn(Optional.of(discontinuedPb));

        assertThatThrownBy(() -> gioHangService.add("TK002", "PB500500", 1))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã ngừng kinh doanh");
    }

    // =========================================================================
    // CASE 6: Không tăng số lượng dòng giỏ ngừng bán
    // =========================================================================
    @Test
    @DisplayName("Test 6: Không thể tăng số lượng hoặc cập nhật dòng sản phẩm ngừng kinh doanh trong giỏ")
    void test6_CannotIncreaseQuantityOfDiscontinuedCartItem() {
        when(gioHangRepository.findByKhachHangMaKh("TK002")).thenReturn(Optional.of(gioHang));
        when(phienBanSanPhamRepository.findById("PB500500")).thenReturn(Optional.of(discontinuedPb));

        assertThatThrownBy(() -> gioHangService.update("TK002", "PB500500", 2))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã ngừng kinh doanh");
    }

    // =========================================================================
    // CASE 7: Không checkout giỏ có sản phẩm ngừng bán
    // =========================================================================
    @Test
    @DisplayName("Test 7: Không cho phép checkout giỏ hàng chứa sản phẩm ngừng bán (chặn tại xem trước và đặt hàng)")
    void test7_CannotCheckoutCartWithDiscontinuedProduct() {
        when(gioHangRepository.findByKhachHangMaKh("TK002")).thenReturn(Optional.of(gioHang));
        when(diaChiRepository.findById("DC001")).thenReturn(Optional.of(diaChi));
        when(phienBanSanPhamRepository.findById("PB1793202488339002")).thenReturn(Optional.of(activePb));
        when(phienBanSanPhamRepository.findById("PB500500")).thenReturn(Optional.of(discontinuedPb));

        // 1. Chặn tại xem trước thanh toán (xemTruoc)
        assertThatThrownBy(() -> thanhToanService.xemTruoc("TK002", "DC001", null, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không còn kinh doanh");

        // 2. Chặn tại thời điểm đặt hàng (datHang)
        when(khachHangRepository.findById("TK002")).thenReturn(Optional.of(khachHang));
        when(phienBanSanPhamRepository.findByIdForUpdate("PB1793202488339002")).thenReturn(Optional.of(activePb));
        when(phienBanSanPhamRepository.findByIdForUpdate("PB500500")).thenReturn(Optional.of(discontinuedPb));

        DonHangDto.Checkout checkoutReq = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);
        assertThatThrownBy(() -> donHangService.datHang("TK002", checkoutReq))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không còn kinh doanh");
    }

    // =========================================================================
    // CASE 8: Có thể xóa dòng giỏ ngừng bán
    // =========================================================================
    @Test
    @DisplayName("Test 8: Khách hàng có thể xóa dòng sản phẩm ngừng kinh doanh ra khỏi giỏ hàng")
    void test8_CanRemoveDiscontinuedItemFromCart() {
        when(gioHangRepository.findByKhachHangMaKh("TK002")).thenReturn(Optional.of(gioHang));
        when(phienBanSanPhamRepository.findById("PB500500")).thenReturn(Optional.of(discontinuedPb));

        assertThat(gioHang.getChiTiet()).hasSize(2);

        GioHangDto result = gioHangService.remove("TK002", "PB500500");

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).phienBan().maPhienBan()).isEqualTo("PB1793202488339002");
        assertThat(gioHang.getChiTiet()).hasSize(1);
    }

    // =========================================================================
    // CASE 9: Có thể checkout sau khi loại bỏ dòng không hợp lệ
    // =========================================================================
    @Test
    @DisplayName("Test 9: Sau khi loại bỏ sản phẩm ngừng bán, giỏ hàng có thể checkout bình thường")
    void test9_CanCheckoutAfterRemovingDiscontinuedItem() {
        // Loại bỏ vivo Y12s ra khỏi giỏ GH002
        gioHang.getChiTiet().removeIf(c -> c.getPhienBan().getMaPhienBan().equals("PB500500"));
        assertThat(gioHang.getChiTiet()).hasSize(1);

        when(gioHangRepository.findByKhachHangMaKh("TK002")).thenReturn(Optional.of(gioHang));
        when(diaChiRepository.findById("DC001")).thenReturn(Optional.of(diaChi));
        when(phienBanSanPhamRepository.findById("PB1793202488339002")).thenReturn(Optional.of(activePb));
        when(diaChiService.tenTinh("01")).thenReturn("Hà Nội");

        PhiVanChuyen pvc = new PhiVanChuyen();
        pvc.setPhiCoBan(BigDecimal.valueOf(30000));
        pvc.setMienPhiTu(BigDecimal.valueOf(5000000));
        pvc.setThoiGianGiao("2-3 ngày");
        when(phiVanChuyenService.theoDiaChi("01")).thenReturn(pvc);
        when(phiVanChuyenService.tinhPhi(any(), any())).thenReturn(BigDecimal.ZERO);
        when(khuyenMaiRepository.findAll()).thenReturn(List.of());

        // Xem trước hợp lệ không bị chặn
        CheckoutDto preview = thanhToanService.xemTruoc("TK002", "DC001", null, null);
        assertThat(preview).isNotNull();
        assertThat(preview.items()).hasSize(1);
        assertThat(preview.items().get(0).maSanPham()).isEqualTo("PB1793202488339002");
    }

    // =========================================================================
    // CASE 10: Không tạo đơn trực tiếp bằng API với sản phẩm ngừng bán (Mua ngay)
    // =========================================================================
    @Test
    @DisplayName("Test 10: Không thể tạo đơn trực tiếp bằng API Mua ngay với sản phẩm ngừng bán")
    void test10_CannotCreateDirectOrderWithDiscontinuedProduct() {
        when(phienBanSanPhamRepository.findById("PB500500")).thenReturn(Optional.of(discontinuedPb));

        assertThatThrownBy(() -> thanhToanService.mucMua("TK002", "PB500500", 1))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không còn kinh doanh");
    }

    // =========================================================================
    // CASE 11: Đơn hàng lịch sử vẫn xem được đầy đủ
    // =========================================================================
    @Test
    @DisplayName("Test 11: Đơn hàng lịch sử chứa sản phẩm đã ngừng kinh doanh vẫn xem được đầy đủ")
    void test11_HistoricalOrderWithDiscontinuedProductCanBeViewedInFull() {
        DonHang oldOrder = new DonHang();
        oldOrder.setMaDonHang("DH_OLD_001");
        oldOrder.setKhachHang(khachHang);
        oldOrder.setNgayMua(LocalDateTime.of(2025, 5, 10, 10, 0));
        oldOrder.setTrangThai(TrangThaiDonHang.DA_GIAO);

        ChiTietDonHang ctdh = new ChiTietDonHang();
        ctdh.setMaChiTiet("CTDH_OLD_001");
        ctdh.setDonHang(oldOrder);
        ctdh.setPhienBan(discontinuedPb);
        ctdh.setTenSanPham("vivo Y12s");
        ctdh.setTenPhienBan("3GB/32GB/Đen");
        ctdh.setDonGia(BigDecimal.valueOf(2290000));
        ctdh.setSoLuong(1);
        oldOrder.setChiTiet(List.of(ctdh));

        DonHangDto expectedDto = new DonHangDto(
                "DH_OLD_001", "TK002", null, null, oldOrder.getNgayMua(), PhuongThucTt.COD,
                TrangThaiDonHang.DA_GIAO, BigDecimal.valueOf(2290000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(2290000), "Hà Nội", oldOrder.getNgayMua(),
                oldOrder.getNgayMua(), "Nguyễn Văn A", null, null, null, null,
                List.of(new DonHangDto.Dong("SP500500", "PB500500", "vivo Y12s - 3GB/32GB/Đen", null, 1, BigDecimal.valueOf(2290000)))
        );

        when(donHangRepository.findByKhachHangMaKhOrderByNgayMuaDesc("TK002")).thenReturn(List.of(oldOrder));
        when(donHangMapper.toDto(oldOrder)).thenReturn(expectedDto);

        List<DonHangDto> history = donHangService.lichSu("TK002");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).maDonHang()).isEqualTo("DH_OLD_001");
        assertThat(history.get(0).chiTiet()).hasSize(1);
        assertThat(history.get(0).chiTiet().get(0).maSanPham()).isEqualTo("SP500500");
    }

    // =========================================================================
    // CASE 12: AI không gợi ý mua sản phẩm ngừng bán
    // =========================================================================
    @Test
    @DisplayName("Test 12: AI Semantic Search không gợi ý hoặc lập chỉ mục sản phẩm NGUNG_KINH_DOANH")
    void test12_AiSearchDoesNotRecommendDiscontinuedProducts() {
        when(sanPhamRepository.findAll()).thenReturn(List.of(activeSp, discontinuedSp));
        when(embeddingModel.isAvailable()).thenReturn(false); // test keyword fallback

        var results = testSemanticSearchService.search("điện thoại giá rẻ", 10);

        // Discontinued product must never be recommended
        assertThat(results).noneMatch(r -> r.sanPham().maSanPham().equals("SP500500"));
        assertThat(results).noneMatch(r -> r.sanPham().trangThai() == TrangThaiSanPham.NGUNG_KINH_DOANH);
    }
}
