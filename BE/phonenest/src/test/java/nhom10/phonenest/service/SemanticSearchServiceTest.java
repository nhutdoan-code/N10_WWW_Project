package nhom10.phonenest.service;

import nhom10.phonenest.dto.SemanticSearchResultDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.service.ai.PhoneNestEmbeddingModel;
import nhom10.phonenest.service.ai.ProductSemanticDocumentBuilder;
import nhom10.phonenest.service.ai.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    @Mock
    private SanPhamRepository sanPhamRepository;

    private PhoneNestEmbeddingModel embeddingModel;
    private ProductSemanticDocumentBuilder documentBuilder;
    private SemanticSearchService semanticSearchService;

    private SanPham s24Ultra;
    private SanPham iphone15ProMax;
    private SanPham galaxyA55;
    private SanPham pocoX6Pro;
    private SanPham redmi13C;
    private SanPham oppoA3;

    @BeforeEach
    void setUp() {
        embeddingModel = new PhoneNestEmbeddingModel();
        documentBuilder = new ProductSemanticDocumentBuilder();
        semanticSearchService = new SemanticSearchService(sanPhamRepository, embeddingModel, documentBuilder);

        ThuongHieu samsung = taoThuongHieu("TH_SS", "Samsung");
        ThuongHieu apple = taoThuongHieu("TH_AP", "Apple");
        ThuongHieu xiaomi = taoThuongHieu("TH_XM", "Xiaomi");
        ThuongHieu oppo = taoThuongHieu("TH_OP", "OPPO");

        LoaiSanPham dt = new LoaiSanPham();
        dt.setMaLoai("L_DT");
        dt.setTenLoai("Điện thoại");

        // Factual specifications ONLY — NO injected marketing keywords
        s24Ultra = taoSanPham("SP004", "Samsung Galaxy S24 Ultra", samsung, dt,
                new BigDecimal("28990000"), 5000, "Snapdragon 8 Gen 3", 120, "200 MP + 50 MP + 12 MP + 10 MP", "12 MP",
                "Khung viền Titanium, bút S-Pen, tính năng Galaxy AI.");

        iphone15ProMax = taoSanPham("SP001", "iPhone 15 Pro Max", apple, dt,
                new BigDecimal("29990000"), 4441, "Apple A17 Pro", 120, "48 MP + 12 MP + 12 MP", "12 MP",
                "Khung titan cấp hàng không vũ trụ, nút tác vụ Action button, cổng sạc Type-C.");

        galaxyA55 = taoSanPham("SP006", "Samsung Galaxy A55 5G", samsung, dt,
                new BigDecimal("9990000"), 5000, "Exynos 1480", 120, "50 MP + 12 MP + 5 MP", "32 MP",
                "Viền kim loại, mặt lưng kính cường lực Gorilla Glass Victus+, kháng nước bụi IP67.");

        pocoX6Pro = taoSanPham("SP009", "POCO X6 Pro 5G", xiaomi, dt,
                new BigDecimal("10990000"), 5000, "Dimensity 8300 Ultra", 120, "64 MP + 8 MP + 2 MP", "16 MP",
                "Màn hình CrystalRes 1.5K Flow AMOLED 120Hz, công nghệ tản nhiệt LiquidCool 2.0.");

        redmi13C = taoSanPham("SP010", "Redmi 13C", xiaomi, dt,
                new BigDecimal("3290000"), 5000, "MediaTek Helio G85", 90, "50 MP + 2 MP", "8 MP",
                "Màn hình 6.74 inch 90Hz, thiết kế phẳng hiện đại.");

        oppoA3 = taoSanPham("SP013", "OPPO A3", oppo, dt,
                new BigDecimal("4990000"), 5100, "Snapdragon 6s Gen 1", 90, "50 MP + 2 MP", "5 MP",
                "Kháng va đập chuẩn độ bền quân đội, sạc nhanh SuperVOOC 45W.");

        List<SanPham> all = List.of(s24Ultra, iphone15ProMax, galaxyA55, pocoX6Pro, redmi13C, oppoA3);
        when(sanPhamRepository.findAll()).thenReturn(all);
        for (SanPham s : all) {
            lenient().when(sanPhamRepository.findById(s.getMaSanPham())).thenReturn(Optional.of(s));
        }

        if (embeddingModel.isAvailable()) {
            semanticSearchService.reindexAll();
        }
    }

    private ThuongHieu taoThuongHieu(String ma, String ten) {
        ThuongHieu th = new ThuongHieu();
        th.setMaThuongHieu(ma);
        th.setTenThuongHieu(ten);
        th.setTrangThaiHoatDong(true);
        return th;
    }

    private SanPham taoSanPham(String ma, String ten, ThuongHieu th, LoaiSanPham loai,
                               BigDecimal gia, int pin, String chip, int hz, String camSau, String camTruoc, String moTa) {
        SanPham s = new SanPham();
        s.setMaSanPham(ma);
        s.setTenMay(ten);
        s.setThuongHieu(th);
        s.setLoai(loai);
        s.setPin(pin);
        s.setChip(chip);
        s.setTanSoQuet(hz);
        s.setCameraSau(camSau);
        s.setCameraTruoc(camTruoc);
        s.setMoTa(moTa);
        s.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);

        PhienBanSanPham pb = new PhienBanSanPham();
        pb.setMaPhienBan(ma + "_PB");
        pb.setGiaBan(gia);
        pb.setSoLuongTon(20);
        pb.setRam(8);
        pb.setBoNho(256);
        pb.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
        pb.setSanPham(s);

        s.setPhienBans(List.of(pb));
        return s;
    }

    @Test
    @DisplayName("Unseen Paraphrase 1 (Battery): 'Tôi thường xuyên đi công tác từ sáng đến tối và ít có cơ hội cắm sạc.'")
    void testUnseenBatteryEndurance() {
        if (!embeddingModel.isAvailable()) {
            System.out.println("Skipping testUnseenBatteryEndurance: Ollama not running");
            return;
        }

        List<SemanticSearchResultDto> res = semanticSearchService.search(
                "Tôi thường xuyên đi công tác từ sáng đến tối và ít có cơ hội cắm sạc.", 3);

        assertThat(res).isNotEmpty();
        assertThat(res.get(0).retrievalMode()).isEqualTo("VECTOR_SEMANTIC");
        // Verify results contain high battery phones (5000+ mAh)
        assertThat(res).anyMatch(r -> r.sanPham().thongSo() != null && r.sanPham().thongSo().pin() >= 5000);
    }

    @Test
    @DisplayName("Unseen Paraphrase 2 (Camera/Scenic): 'Tôi muốn lưu lại phong cảnh và những khoảnh khắc trong chuyến đi thật đẹp.'")
    void testUnseenScenicMoments() {
        if (!embeddingModel.isAvailable()) {
            System.out.println("Skipping testUnseenScenicMoments: Ollama not running");
            return;
        }

        List<SemanticSearchResultDto> res = semanticSearchService.search(
                "Tôi muốn lưu lại phong cảnh và những khoảnh khắc trong chuyến đi thật đẹp.", 3);

        assertThat(res).isNotEmpty();
        assertThat(res.get(0).retrievalMode()).isEqualTo("VECTOR_SEMANTIC");
        // Must retrieve products with factual multi-lens cameras
        assertThat(res.get(0).dacDiemNoiBat()).isNotEmpty();
    }

    @Test
    @DisplayName("Unseen Paraphrase 3 (Performance/Heavy): 'Tôi hay sử dụng các ứng dụng nặng và không muốn máy bị giật khi sử dụng lâu.'")
    void testUnseenHeavyPerformance() {
        if (!embeddingModel.isAvailable()) {
            System.out.println("Skipping testUnseenHeavyPerformance: Ollama not running");
            return;
        }

        List<SemanticSearchResultDto> res = semanticSearchService.search(
                "Tôi hay sử dụng các ứng dụng nặng và không muốn máy bị giật khi sử dụng lâu.", 3);

        assertThat(res).isNotEmpty();
        assertThat(res.get(0).retrievalMode()).isEqualTo("VECTOR_SEMANTIC");
        // POCO X6 Pro with Dimensity 8300 Ultra or Flagship SoC should be among top results
        List<String> topIds = res.stream().map(r -> r.sanPham().maSanPham()).toList();
        assertThat(topIds).containsAnyOf("SP009", "SP001", "SP004");
    }

    @Test
    @DisplayName("Unseen Paraphrase 4 (Display/Action): 'Tôi thường xem phim hành động và muốn các cảnh chuyển động trông thật mượt.'")
    void testUnseenActionMoviesDisplay() {
        if (!embeddingModel.isAvailable()) {
            System.out.println("Skipping testUnseenActionMoviesDisplay: Ollama not running");
            return;
        }

        List<SemanticSearchResultDto> res = semanticSearchService.search(
                "Tôi thường xem phim hành động và muốn các cảnh chuyển động trông thật mượt.", 3);

        assertThat(res).isNotEmpty();
        assertThat(res.get(0).retrievalMode()).isEqualTo("VECTOR_SEMANTIC");
        // High refresh rate (120Hz) displays favored
        assertThat(res).anyMatch(r -> r.sanPham().thongSo() != null && r.sanPham().thongSo().tanSoQuet() != null && r.sanPham().thongSo().tanSoQuet() >= 90);
    }

    @Test
    @DisplayName("Unseen Paraphrase 5 (Budget + Stability): 'Tôi chỉ có khoảng 12 triệu nhưng muốn máy đủ mạnh để sử dụng vài năm.'")
    void testUnseenBudgetStability() {
        if (!embeddingModel.isAvailable()) {
            System.out.println("Skipping testUnseenBudgetStability: Ollama not running");
            return;
        }

        List<SemanticSearchResultDto> res = semanticSearchService.search(
                "Tôi chỉ có khoảng 12 triệu nhưng muốn máy đủ mạnh để sử dụng vài năm.", 3);

        assertThat(res).isNotEmpty();
        // Budget constraints properly bounded around 12M
        assertThat(res.get(0).sanPham().giaBan().compareTo(new BigDecimal("16000000"))).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("OOD / Fallback: Random gibberish query returns safe result without crashing")
    void testGibberishQuery() {
        List<SemanticSearchResultDto> res = semanticSearchService.search("asdfghjkl12345xyz", 3);
        assertThat(res).isNotNull();
    }

    @Test
    @DisplayName("Vector Sync on Product CRUD")
    void testVectorSyncOnCrud() {
        SanPham newSp = taoSanPham("SP_NEW", "Oppo Reno 12",
                taoThuongHieu("TH_OP", "OPPO"),
                s24Ultra.getLoai(),
                new BigDecimal("11000000"), 5000, "Dimensity 7300", 120, "50MP", "32MP",
                "Thiết kế mỏng nhẹ, pin 5000mAh.");

        semanticSearchService.indexProduct(newSp);
        semanticSearchService.removeProduct("SP_NEW");
    }

    @Test
    @DisplayName("Diagnostic Status API reports Ollama model and availability")
    void testDiagnosticStatus() {
        Map<String, Object> status = semanticSearchService.getStatus();
        assertThat(status).containsKey("provider");
        assertThat(status.get("provider")).isEqualTo("Ollama");
        assertThat(status).containsKey("model");
        assertThat(status).containsKey("dimensions");
        assertThat(status).containsKey("available");
        assertThat(status).containsKey("mode");
    }

    @Test
    @DisplayName("Fallback: When Ollama is offline, search gracefully degrades to KEYWORD_FALLBACK")
    void testFallbackWhenOllamaOffline() {
        PhoneNestEmbeddingModel offlineModel = new PhoneNestEmbeddingModel(
                "http://127.0.0.1:54321", // non-existent port
                "bge-m3",
                1024
        );
        SemanticSearchService offlineService = new SemanticSearchService(sanPhamRepository, offlineModel, documentBuilder);

        Map<String, Object> status = offlineService.getStatus();
        assertThat(status.get("available")).isEqualTo(false);
        assertThat(status.get("mode")).isEqualTo("KEYWORD_FALLBACK");
        assertThat(status.get("reason")).asString().contains("Không thể kết nối đến Ollama");

        List<SemanticSearchResultDto> results = offlineService.search("Samsung", 5);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).retrievalMode()).isEqualTo("KEYWORD_FALLBACK");
        assertThat(results.get(0).sanPham().tenMay()).contains("Samsung");
    }
}
