package nhom10.phonenest.service;

import nhom10.phonenest.dto.ChatDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.dto.SemanticSearchResultDto;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.PhiVanChuyenRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.TinhThanhRepository;
import nhom10.phonenest.service.ai.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceRecommendationTest {

    private static final String VISITOR_A = "visitor-user-a";
    private static final String VISITOR_B = "visitor-user-b";

    @Mock
    private SanPhamService sanPhamService;

    @Mock
    private SanPhamRepository sanPhamRepository;

    @Mock
    private KhuyenMaiRepository khuyenMaiRepository;

    @Mock
    private PhiVanChuyenRepository phiVanChuyenRepository;

    @Mock
    private TinhThanhRepository tinhThanhRepository;

    @Mock
    private TrackingService trackingService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private UserPreferenceAggregationService userPreferenceService;

    @InjectMocks
    private ChatService chatService;

    private SanPhamDto iphone15ProMax;
    private SanPhamDto samsungS24Ultra;
    private SanPhamDto xiaomi14;
    private SanPhamDto redmiNote13Pro;
    private SanPhamDto samsungA15;
    private SanPhamDto vivoY28;
    private SanPhamDto exact10MPhone;
    private List<SanPhamDto> fullCatalog;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(chatService, "apiKey", "");

        // 1. iPhone 15 Pro Max (Apple, 30M, Flagship Camera & Gaming)
        iphone15ProMax = createProduct("SP_IP15", "Apple", "iPhone 15 Pro Max",
                new BigDecimal("30000000"), 10,
                new SanPhamDto.ThongSo(new BigDecimal("6.7"), "OLED", 120, "Apple A17 Pro", 8, 256,
                        "48MP + 12MP + 12MP", 12, 4422, 27, "iOS", true, true, 221));

        // 2. Samsung Galaxy S24 Ultra (Samsung, 28M, 200MP Camera, 12GB RAM)
        samsungS24Ultra = createProduct("SP_S24U", "Samsung", "Samsung Galaxy S24 Ultra",
                new BigDecimal("28000000"), 8,
                new SanPhamDto.ThongSo(new BigDecimal("6.8"), "AMOLED", 120, "Snapdragon 8 Gen 3", 12, 512,
                        "200MP + 50MP + 12MP + 10MP", 12, 5000, 45, "Android", true, true, 232));

        // 3. Xiaomi 14 (Xiaomi, 18M, Leica 50MP, 12GB RAM)
        xiaomi14 = createProduct("SP_MI14", "Xiaomi", "Xiaomi 14",
                new BigDecimal("18000000"), 5,
                new SanPhamDto.ThongSo(new BigDecimal("6.36"), "AMOLED", 120, "Snapdragon 8 Gen 3", 12, 256,
                        "50MP + 50MP + 50MP", 32, 4610, 90, "Android", true, true, 193));

        // 4. Redmi Note 13 Pro (Xiaomi, 7.5M, 200MP Camera, Budget camera king)
        redmiNote13Pro = createProduct("SP_RN13P", "Xiaomi", "Redmi Note 13 Pro",
                new BigDecimal("7500000"), 15,
                new SanPhamDto.ThongSo(new BigDecimal("6.67"), "AMOLED", 120, "Helio G99-Ultra", 8, 128,
                        "200MP + 8MP + 2MP", 16, 5000, 67, "Android", true, false, 188));

        // 5. Samsung Galaxy A15 (Samsung, 4.5M, Budget)
        samsungA15 = createProduct("SP_A15", "Samsung", "Samsung Galaxy A15",
                new BigDecimal("4500000"), 20,
                new SanPhamDto.ThongSo(new BigDecimal("6.5"), "Super AMOLED", 90, "Helio G99", 8, 128,
                        "50MP + 5MP + 2MP", 13, 5000, 25, "Android", true, false, 200));

        // 6. Vivo Y28 (Vivo, 5.2M, 6000mAh Battery King)
        vivoY28 = createProduct("SP_Y28", "Vivo", "Vivo Y28",
                new BigDecimal("5200000"), 12,
                new SanPhamDto.ThongSo(new BigDecimal("6.68"), "LCD", 90, "Helio G85", 8, 128,
                        "50MP + 2MP", 8, 6000, 44, "Android", false, false, 199));

        // 7. Samsung Exactly 10M (Samsung, exact 10.000.000 VND, highest rating 5.0)
        exact10MPhone = createProduct("SP_EXACT10M", "Samsung", "Samsung Galaxy 10M Exact",
                new BigDecimal("10000000"), 25,
                new SanPhamDto.ThongSo(new BigDecimal("6.6"), "AMOLED", 90, "Exynos 1380", 8, 128,
                        "50MP + 8MP", 13, 5000, 25, "Android", true, false, 195),
                BigDecimal.valueOf(5.0));

        fullCatalog = List.of(iphone15ProMax, samsungS24Ultra, xiaomi14, redmiNote13Pro, samsungA15, vivoY28, exact10MPhone);

        lenient().when(sanPhamService.list(any(), any(), any(), any(), any(), eq(false)))
                .thenReturn(fullCatalog);
        lenient().when(trackingService.preferences(anyString()))
                .thenReturn(new TrackingService.Preferences(List.of(), List.of()));
    }

    @Test
    @DisplayName("1. Ranh giới giá: 'Dưới 10 triệu' LOẠI BỎ máy có giá đúng 10.000.000 ₫ (< 10tr)")
    void testUnder10MillionExcludesExact10Million() {
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Điện thoại dưới 10 triệu", List.of(), VISITOR_A)
        );

        assertThat(res.products()).isNotEmpty();
        // Tất cả máy phải có giá strictly < 10.000.000 VND
        for (SanPhamDto sp : res.products()) {
            assertThat(sp.giaBan()).isLessThan(new BigDecimal("10000000"));
        }
        assertThat(res.products()).extracting(SanPhamDto::maSanPham)
                .doesNotContain("SP_EXACT10M", "SP_IP15", "SP_S24U", "SP_MI14");
    }

    @Test
    @DisplayName("2. Ranh giới giá: 'Không quá 10 triệu' CHẤP NHẬN máy có giá đúng 10.000.000 ₫ (<= 10tr)")
    void testNotExceeding10MillionIncludesExact10Million() {
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Điện thoại không quá 10 triệu", List.of(), VISITOR_A)
        );

        assertThat(res.products()).isNotEmpty();
        for (SanPhamDto sp : res.products()) {
            assertThat(sp.giaBan()).isLessThanOrEqualTo(new BigDecimal("10000000"));
        }
        assertThat(res.products()).extracting(SanPhamDto::maSanPham)
                .contains("SP_EXACT10M");
    }

    @Test
    @DisplayName("3. Ranh giới giá: 'Trên 10 triệu' LOẠI BỎ máy có giá đúng 10.000.000 ₫ (> 10tr)")
    void testAbove10MillionExcludesExact10Million() {
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Điện thoại trên 10 triệu", List.of(), VISITOR_A)
        );

        assertThat(res.products()).isNotEmpty();
        // Tất cả máy phải có giá strictly > 10.000.000 VND
        for (SanPhamDto sp : res.products()) {
            assertThat(sp.giaBan()).isGreaterThan(new BigDecimal("10000000"));
        }
        assertThat(res.products()).extracting(SanPhamDto::maSanPham)
                .doesNotContain("SP_EXACT10M", "SP_RN13P", "SP_A15", "SP_Y28");
    }

    @Test
    @DisplayName("4. Ranh giới giá: 'Từ 10 triệu' CHẤP NHẬN máy có giá đúng 10.000.000 ₫ (>= 10tr)")
    void testFrom10MillionIncludesExact10Million() {
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Điện thoại từ 10 triệu", List.of(), VISITOR_A)
        );

        assertThat(res.products()).isNotEmpty();
        for (SanPhamDto sp : res.products()) {
            assertThat(sp.giaBan()).isGreaterThanOrEqualTo(new BigDecimal("10000000"));
        }
        assertThat(res.products()).extracting(SanPhamDto::maSanPham)
                .contains("SP_EXACT10M");
    }

    @Test
    @DisplayName("5. Lọc thương hiệu: 'iPhone dưới 10 triệu' không đưa Android vào kết quả")
    void testIPhoneUnder10MillionExcludesAndroid() {
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("iPhone dưới 10 triệu chơi game tốt", List.of(), VISITOR_A)
        );

        // Kho không có iPhone nào < 10 triệu -> Phải trả về thông báo PRICE_FILTER, không được nhồi máy Android (Samsung/Xiaomi)
        assertThat(res.products()).isEmpty();
        assertThat(res.mode()).isEqualTo("PRICE_FILTER");
    }

    @Test
    @DisplayName("6. Lịch sử quan tâm Samsung không làm sai lệch câu hỏi iPhone")
    void testSamsungBrowsingHistoryDoesNotBleedIntoIPhoneQuery() {
        // Khách A có lịch sử xem và tìm kiếm toàn máy Samsung
        when(trackingService.preferences(VISITOR_A))
                .thenReturn(new TrackingService.Preferences(List.of("SP_S24U", "SP_A15"), List.of("Samsung", "Galaxy")));
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("iPhone chụp ảnh đẹp", List.of(), VISITOR_A)
        );

        // Kết quả TUYỆT ĐỐI chỉ chứa Apple, lịch sử Samsung không được rò rỉ vào
        assertThat(res.products()).isNotEmpty();
        assertThat(res.products()).extracting(SanPhamDto::tenThuongHieu)
                .containsOnly("Apple");
        assertThat(res.products()).extracting(SanPhamDto::maSanPham)
                .containsOnly("SP_IP15");
    }

    @Test
    @DisplayName("7. ChatService loại bỏ ứng viên hết hàng từ Semantic Search")
    void testChatServiceFiltersOutOfStockCandidates() {
        SanPhamDto outOfStock = createProduct("SP_OOS", "Xiaomi", "Xiaomi Out Of Stock",
                new BigDecimal("5000000"), 0, // SO LUONG TON = 0
                new SanPhamDto.ThongSo(new BigDecimal("6.5"), "LCD", 90, "Helio G85", 6, 128, "50MP", 8, 5000, 18, "Android", false, false, 190));

        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of(
                new SemanticSearchResultDto(outOfStock, 0.95, "Máy hết hàng", List.of("50MP"), "VECTOR_SEMANTIC"),
                new SemanticSearchResultDto(redmiNote13Pro, 0.90, "Máy còn hàng", List.of("200MP"), "VECTOR_SEMANTIC")
        ));

        ChatDto.Response res = chatService.chat(new ChatDto.Request("Điện thoại giá tốt", List.of(), VISITOR_A));

        // Không bao giờ chứa máy hết hàng
        assertThat(res.products()).extracting(SanPhamDto::maSanPham)
                .contains("SP_RN13P")
                .doesNotContain("SP_OOS");
    }

    @Test
    @DisplayName("8. Hỏi phí ship khi toàn bộ kho hết hàng: trả về biểu phí bình thường")
    void testShippingInquiryWhenInventoryIsEmpty() {
        when(sanPhamService.list(any(), any(), any(), any(), any(), eq(false)))
                .thenReturn(List.of()); // TOÀN BỘ KHO HẾT HÀNG

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Phí giao hàng ra Hà Nội bao nhiêu?", List.of(), VISITOR_A)
        );

        // Không được trả về OUT_OF_STOCK
        assertThat(res.mode()).isNotEqualTo("OUT_OF_STOCK");
        assertThat(res.reply()).contains("vận chuyển");
    }

    @Test
    @DisplayName("9. Hỏi voucher khi toàn bộ kho hết hàng: trả về danh sách voucher bình thường")
    void testVoucherInquiryWhenInventoryIsEmpty() {
        when(sanPhamService.list(any(), any(), any(), any(), any(), eq(false)))
                .thenReturn(List.of()); // TOÀN BỘ KHO HẾT HÀNG

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Shop có voucher gì không?", List.of(), VISITOR_A)
        );

        // Không được trả về OUT_OF_STOCK
        assertThat(res.mode()).isNotEqualTo("OUT_OF_STOCK");
        assertThat(res.reply()).contains("voucher");
    }

    @Test
    @DisplayName("10. Chụp ảnh đẹp giá tốt: kết hợp thông số camera thực tế và mức giá hợp lý")
    void testCameraAndValueCombinationPrioritizesValueForMoney() {
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Điện thoại chụp ảnh đẹp giá tốt", List.of(), VISITOR_A)
        );

        assertThat(res.products()).isNotEmpty();
        // Redmi Note 13 Pro (200MP, giá 7.5 triệu) là lựa chọn camera giá tốt nhất, phải đứng vị trí số 1
        assertThat(res.products().get(0).maSanPham()).isEqualTo("SP_RN13P");
        assertThat(res.products().get(0).giaBan()).isLessThan(new BigDecimal("10000000"));
    }

    @Test
    @DisplayName("11. Giá bao nhiêu cũng được: không áp bất kỳ giới hạn trần ngân sách nào")
    void testUnlimitedBudgetAllowsAllFlagships() {
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Máy pin trâu giá bao nhiêu cũng được", List.of(), VISITOR_A)
        );

        assertThat(res.products()).isNotEmpty();
        assertThat(res.reply()).contains("Không giới hạn mức giá");
        // Không bị chặn bởi trần ngân sách, máy flagship giá cao như Xiaomi 14 (18 triệu) vẫn được giới thiệu
        assertThat(res.products()).extracting(SanPhamDto::giaBan)
                .anyMatch(p -> p.compareTo(new BigDecimal("15000000")) > 0);
    }

    @Test
    @DisplayName("12. Cấu trúc response tuân thủ hợp đồng ChatDto.Response")
    void testResponseStructureIntegrity() {
        ChatDto.Response res = chatService.chat(
                new ChatDto.Request("Có điện thoại nào tốt không?", List.of(), VISITOR_A)
        );

        assertThat(res).isNotNull();
        assertThat(res.reply()).isNotBlank();
        assertThat(res.products()).isNotNull();
        assertThat(res.products().size()).isLessThanOrEqualTo(3);
        assertThat(res.mode()).isNotBlank();
    }

    private SanPhamDto createProduct(String id, String brand, String name, BigDecimal price, int stock,
                                     SanPhamDto.ThongSo thongSo, BigDecimal rating) {
        return new SanPhamDto(
                id, "L01", "Điện thoại", "TH_" + brand, brand, name,
                price, "Mô tả cho " + name, thongSo,
                stock, TrangThaiSanPham.DANG_KINH_DOANH, rating,
                null, List.of(), List.of()
        );
    }

    private SanPhamDto createProduct(String id, String brand, String name, BigDecimal price, int stock,
                                     SanPhamDto.ThongSo thongSo) {
        return createProduct(id, brand, name, price, stock, thongSo, BigDecimal.valueOf(4.8));
    }
}
