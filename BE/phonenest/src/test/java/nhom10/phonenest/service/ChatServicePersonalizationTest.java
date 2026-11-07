package nhom10.phonenest.service;

import nhom10.phonenest.dto.ChatDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.service.ai.SemanticSearchService;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.PhiVanChuyenRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.TinhThanhRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServicePersonalizationTest {

    private static final String VISITOR_ID = "b51d0e08-1111-4111-8111-001122334455";

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

    @InjectMocks
    private ChatService chatService;

    @Test
    void fallbackRecommendationsUseRecentSearchInterests() {
        SanPhamDto searchedProduct = product("SP001", "Samsung Galaxy A");
        SanPhamDto popularProduct = product("SP002", "Xiaomi Redmi");
        when(trackingService.preferences(VISITOR_ID))
                .thenReturn(new TrackingService.Preferences(List.of(), List.of("Samsung")));
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());
        when(sanPhamService.list(
                nullable(String.class), nullable(String.class), nullable(BigDecimal.class),
                nullable(BigDecimal.class), nullable(String.class), eq(false)))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(0);
                    return "Samsung".equals(query) ? List.of(searchedProduct) : List.of(popularProduct);
                });
        ReflectionTestUtils.setField(chatService, "apiKey", "chua-cau-hinh");

        ChatDto.Response response = chatService.chat(
                new ChatDto.Request("Mình cần tư vấn", List.of(), VISITOR_ID));

        assertThat(response.products()).extracting(SanPhamDto::maSanPham)
                .containsExactly("SP001", "SP002");
        verifyNoInteractions(chatClient);
    }

    private SanPhamDto product(String id, String name) {
        return new SanPhamDto(
                id, "L01", "Điện thoại", "TH01", name.split(" ")[0], name,
                BigDecimal.valueOf(5_000_000), "", new SanPhamDto.ThongSo(
                        null, null, null, null, null, null, null, null, null, null, null,
                        false, false, null),
                5, TrangThaiSanPham.DANG_KINH_DOANH, BigDecimal.valueOf(4.5),
                null, List.of(), List.of());
    }
}
