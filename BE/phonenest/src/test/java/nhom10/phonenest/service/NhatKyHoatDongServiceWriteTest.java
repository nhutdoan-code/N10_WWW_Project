package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nhom10.phonenest.entity.NhatKyHoatDong;
import nhom10.phonenest.entity.QuanTriVien;
import nhom10.phonenest.entity.TaiKhoan;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.repository.NhatKyHoatDongRepository;
import nhom10.phonenest.repository.QuanTriVienRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import nhom10.phonenest.util.AuditDiffUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NhatKyHoatDongServiceWriteTest {

    @Mock
    private NhatKyHoatDongRepository nhatKyHoatDongRepository;

    @Mock
    private QuanTriVienRepository quanTriVienRepository;

    @Mock
    private TaiKhoanRepository taiKhoanRepository;

    private AuditDiffUtil auditDiffUtil;
    private ObjectMapper objectMapper;
    private NhatKyHoatDongService nhatKyHoatDongService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditDiffUtil = new AuditDiffUtil(objectMapper);
        nhatKyHoatDongService = new NhatKyHoatDongService(
                nhatKyHoatDongRepository,
                quanTriVienRepository,
                taiKhoanRepository,
                auditDiffUtil,
                objectMapper
        );

        // Set up mock HTTP request context
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/admin/inventory/PB001");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Set up mock SecurityContext with Admin TK001
        var auth = new UsernamePasswordAuthenticationToken(
                "TK001",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("shouldSaveAuditLogWhenDiffIsMeaningful")
    void shouldSaveAuditLogWhenDiffIsMeaningful() {
        QuanTriVien qtv = new QuanTriVien();
        qtv.setMaQtv("TK001");
        qtv.setHoTen("Quản trị viên PhoneNest");
        when(quanTriVienRepository.findById("TK001")).thenReturn(Optional.of(qtv));

        Map<String, Object> before = Map.of("soLuongTon", 10);
        Map<String, Object> after = Map.of("soLuongTon", 15);

        boolean recorded = nhatKyHoatDongService.ghi(
                HanhDongAudit.STOCK_IMPORT,
                LoaiDoiTuongAudit.INVENTORY,
                "PB001",
                before,
                after
        );

        assertThat(recorded).isTrue();

        ArgumentCaptor<NhatKyHoatDong> captor = ArgumentCaptor.forClass(NhatKyHoatDong.class);
        verify(nhatKyHoatDongRepository, times(1)).save(captor.capture());

        NhatKyHoatDong saved = captor.getValue();
        assertThat(saved.getMaNhatKy()).startsWith("NK");
        assertThat(saved.getMaTk()).isEqualTo("TK001");
        assertThat(saved.getTenNguoiThucHien()).isEqualTo("Quản trị viên PhoneNest");
        assertThat(saved.getHanhDong()).isEqualTo(HanhDongAudit.STOCK_IMPORT);
        assertThat(saved.getLoaiDoiTuong()).isEqualTo(LoaiDoiTuongAudit.INVENTORY);
        assertThat(saved.getMaDoiTuong()).isEqualTo("PB001");
        assertThat(saved.getMoTa()).isEqualTo("Nhập kho PB001");
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getRequestMethod()).isEqualTo("PUT");
        assertThat(saved.getRequestPath()).isEqualTo("/api/admin/inventory/PB001");
        assertThat(saved.getDuLieuTruoc()).contains("\"soLuongTon\":10");
        assertThat(saved.getDuLieuSau()).contains("\"soLuongTon\":15");
        assertThat(saved.getThoiGian()).isNotNull();
    }

    @Test
    @DisplayName("shouldSuppressAuditLogWhenDiffIsNoOp")
    void shouldSuppressAuditLogWhenDiffIsNoOp() {
        Map<String, Object> before = Map.of("tenLoai", "Tai nghe");
        Map<String, Object> after = Map.of("tenLoai", "Tai nghe");

        boolean recorded = nhatKyHoatDongService.ghi(
                HanhDongAudit.UPDATE_CATEGORY,
                LoaiDoiTuongAudit.CATEGORY,
                "L01",
                before,
                after
        );

        assertThat(recorded).isFalse();
        verify(nhatKyHoatDongRepository, never()).save(any());
    }

    @Test
    @DisplayName("shouldFallbackActorToEmailOrMaTkWhenQuanTriVienNotFound")
    void shouldFallbackActorToEmailOrMaTkWhenQuanTriVienNotFound() {
        when(quanTriVienRepository.findById("TK001")).thenReturn(Optional.empty());

        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk("TK001");
        tk.setEmail("admin@phonenest.com");
        when(taiKhoanRepository.findById("TK001")).thenReturn(Optional.of(tk));

        nhatKyHoatDongService.ghi(
                HanhDongAudit.LOCK_ACCOUNT,
                LoaiDoiTuongAudit.ACCOUNT,
                "TK002",
                Map.of("trangThai", true),
                Map.of("trangThai", false)
        );

        ArgumentCaptor<NhatKyHoatDong> captor = ArgumentCaptor.forClass(NhatKyHoatDong.class);
        verify(nhatKyHoatDongRepository).save(captor.capture());
        assertThat(captor.getValue().getTenNguoiThucHien()).isEqualTo("admin@phonenest.com");
    }

    @Test
    @DisplayName("shouldFallbackActorToMaTkWhenBothQtvAndTaiKhoanNotFound")
    void shouldFallbackActorToMaTkWhenBothQtvAndTaiKhoanNotFound() {
        when(quanTriVienRepository.findById("TK001")).thenReturn(Optional.empty());
        when(taiKhoanRepository.findById("TK001")).thenReturn(Optional.empty());

        nhatKyHoatDongService.ghi(
                HanhDongAudit.UNLOCK_ACCOUNT,
                LoaiDoiTuongAudit.ACCOUNT,
                "TK002",
                Map.of("trangThai", false),
                Map.of("trangThai", true)
        );

        ArgumentCaptor<NhatKyHoatDong> captor = ArgumentCaptor.forClass(NhatKyHoatDong.class);
        verify(nhatKyHoatDongRepository).save(captor.capture());
        assertThat(captor.getValue().getTenNguoiThucHien()).isEqualTo("TK001");
    }

    @Test
    @DisplayName("shouldDeclareMandatoryTransactionPropagation")
    void shouldDeclareMandatoryTransactionPropagation() throws NoSuchMethodException {
        Method ghiMethod = NhatKyHoatDongService.class.getMethod(
                "ghi",
                HanhDongAudit.class,
                LoaiDoiTuongAudit.class,
                String.class,
                Map.class,
                Map.class
        );

        Transactional transactional = ghiMethod.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("@Transactional annotation must be present on NhatKyHoatDongService.ghi")
                .isNotNull();
        assertThat(transactional.propagation())
                .as("Propagation MUST be MANDATORY to participate in caller transaction")
                .isEqualTo(Propagation.MANDATORY);
    }
}
