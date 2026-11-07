package nhom10.phonenest.service;

import nhom10.phonenest.dto.TrackingDto;
import nhom10.phonenest.entity.HanhViNguoiDung;
import nhom10.phonenest.enums.LoaiHanhVi;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.HanhViNguoiDungRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    private static final String VISITOR_ID = "b51d0e08-1111-4111-8111-001122334455";

    @Mock
    private HanhViNguoiDungRepository repository;

    @Mock
    private SanPhamRepository sanPhamRepository;

    @InjectMocks
    private TrackingService trackingService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsCustomerIdentityFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "customer-1", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        when(sanPhamRepository.existsById("SP001")).thenReturn(true);

        trackingService.record(new TrackingDto.Event(
                VISITOR_ID.toUpperCase(), LoaiHanhVi.ADD_TO_CART, "SP001", null, null));

        ArgumentCaptor<HanhViNguoiDung> saved = ArgumentCaptor.forClass(HanhViNguoiDung.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMaHanhVi()).isNotBlank();
        assertThat(saved.getValue().getVisitorId()).isEqualTo(VISITOR_ID);
        assertThat(saved.getValue().getMaTk()).isEqualTo("customer-1");
        assertThat(saved.getValue().getLoaiHanhVi().name()).isEqualTo(LoaiHanhVi.ADD_TO_CART.name());
        assertThat(saved.getValue().getProductId()).isEqualTo("SP001");
        assertThat(saved.getValue().getThoiGian()).isNotNull();
    }

    @Test
    void rejectsProductEventsForUnknownProducts() {
        when(sanPhamRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> trackingService.record(
                new TrackingDto.Event(VISITOR_ID, LoaiHanhVi.PRODUCT_VIEW, "missing", null, null)))
                .isInstanceOf(AppException.class)
                .hasMessage("Sản phẩm theo dõi không hợp lệ");
        verify(repository, never()).save(any());
    }

    @Test
    void preferencesPrioritizeCartActivityAndIncludeRecentSearches() {
        HanhViNguoiDung cart = event(LoaiHanhVi.ADD_TO_CART, "SP001", null);
        HanhViNguoiDung view = event(LoaiHanhVi.PRODUCT_VIEW, "SP002", null);
        HanhViNguoiDung search = event(LoaiHanhVi.SEARCH, null, "điện thoại pin tốt");
        when(repository.findRecent(any(), eq(VISITOR_ID), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(List.of(cart, view, search));

        TrackingService.Preferences preferences = trackingService.preferences(VISITOR_ID);

        assertThat(preferences.productIds()).containsExactly("SP001", "SP002");
        assertThat(preferences.searchTerms()).containsExactly("điện thoại pin tốt");
        verify(repository).findRecent(isNull(), eq(VISITOR_ID), any(LocalDateTime.class), eq(PageRequest.of(0, 100)));
    }

    private HanhViNguoiDung event(LoaiHanhVi type, String productId, String searchTerm) {
        HanhViNguoiDung event = new HanhViNguoiDung();
        event.setLoaiHanhVi(type);
        event.setProductId(productId);
        event.setSearchTerm(searchTerm);
        return event;
    }
}
