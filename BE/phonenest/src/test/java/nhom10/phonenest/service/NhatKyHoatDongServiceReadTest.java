package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import nhom10.phonenest.dto.NhatKyHoatDongChiTietDto;
import nhom10.phonenest.dto.NhatKyHoatDongDto;
import nhom10.phonenest.dto.TrangDto;
import nhom10.phonenest.entity.NhatKyHoatDong;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.NhatKyHoatDongRepository;
import nhom10.phonenest.repository.QuanTriVienRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import nhom10.phonenest.util.AuditDiffUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NhatKyHoatDongServiceReadTest {

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
    }

    @Test
    @DisplayName("shouldApplyDefaultPaginationAndSecondarySort")
    void shouldApplyDefaultPaginationAndSecondarySort() {
        when(nhatKyHoatDongRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        TrangDto<NhatKyHoatDongDto> result = nhatKyHoatDongService.list(
                0, 20, "thoiGian", "desc", null, null, null, null, null, null
        );

        assertThat(result).isNotNull();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(nhatKyHoatDongRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);

        Sort.Order primary = pageable.getSort().getOrderFor("thoiGian");
        assertThat(primary).isNotNull();
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);

        Sort.Order secondary = pageable.getSort().getOrderFor("maNhatKy");
        assertThat(secondary).isNotNull();
        assertThat(secondary.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("shouldRejectNegativePageNumber")
    void shouldRejectNegativePageNumber() {
        assertThatThrownBy(() -> nhatKyHoatDongService.list(-1, 20, "thoiGian", "desc", null, null, null, null, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("shouldRejectZeroOrNegativePageSize")
    void shouldRejectZeroOrNegativePageSize() {
        assertThatThrownBy(() -> nhatKyHoatDongService.list(0, 0, "thoiGian", "desc", null, null, null, null, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("shouldRejectPageSizeGreaterThan100")
    void shouldRejectPageSizeGreaterThan100() {
        assertThatThrownBy(() -> nhatKyHoatDongService.list(0, 101, "thoiGian", "desc", null, null, null, null, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("shouldRejectInvalidSortField")
    void shouldRejectInvalidSortField() {
        assertThatThrownBy(() -> nhatKyHoatDongService.list(0, 20, "unsupportedColumn", "desc", null, null, null, null, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("shouldRejectInvalidSortDirection")
    void shouldRejectInvalidSortDirection() {
        assertThatThrownBy(() -> nhatKyHoatDongService.list(0, 20, "thoiGian", "diagonal", null, null, null, null, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("shouldRejectWhenFromDateIsAfterToDate")
    void shouldRejectWhenFromDateIsAfterToDate() {
        LocalDateTime from = LocalDateTime.of(2026, 10, 30, 12, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 29, 12, 0, 0);

        assertThatThrownBy(() -> nhatKyHoatDongService.list(0, 20, "thoiGian", "desc", from, to, null, null, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("shouldRejectInvalidActionEnum")
    void shouldRejectInvalidActionEnum() {
        assertThatThrownBy(() -> nhatKyHoatDongService.list(0, 20, "thoiGian", "desc", null, null, null, "INVALID_ACTION", null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("shouldAcceptCaseInsensitiveActionEnum")
    void shouldAcceptCaseInsensitiveActionEnum() {
        when(nhatKyHoatDongRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        TrangDto<NhatKyHoatDongDto> result = nhatKyHoatDongService.list(
                0, 20, "thoiGian", "desc", null, null, null, "create_product", null, null
        );

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("shouldRejectInvalidEntityEnum")
    void shouldRejectInvalidEntityEnum() {
        assertThatThrownBy(() -> nhatKyHoatDongService.list(0, 20, "thoiGian", "desc", null, null, null, null, "INVALID_ENTITY", null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("shouldAcceptCaseInsensitiveEntityEnum")
    void shouldAcceptCaseInsensitiveEntityEnum() {
        when(nhatKyHoatDongRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        TrangDto<NhatKyHoatDongDto> result = nhatKyHoatDongService.list(
                0, 20, "thoiGian", "desc", null, null, null, null, "product", null
        );

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("shouldConvertDetailPayloadToObjectNodeWhenValidJson")
    void shouldConvertDetailPayloadToObjectNodeWhenValidJson() {
        NhatKyHoatDong nhatKy = new NhatKyHoatDong();
        nhatKy.setMaNhatKy("NK001");
        nhatKy.setHanhDong(HanhDongAudit.UPDATE_PRODUCT);
        nhatKy.setLoaiDoiTuong(LoaiDoiTuongAudit.PRODUCT);
        nhatKy.setDuLieuTruoc("{\"giaBan\":20000000}");
        nhatKy.setDuLieuSau("{\"giaBan\":22000000}");
        nhatKy.setThoiGian(LocalDateTime.now());

        when(nhatKyHoatDongRepository.findById("NK001")).thenReturn(Optional.of(nhatKy));

        NhatKyHoatDongChiTietDto dto = nhatKyHoatDongService.get("NK001");

        assertThat(dto.duLieuTruoc()).isInstanceOf(ObjectNode.class);
        assertThat(dto.duLieuTruoc().get("giaBan").asLong()).isEqualTo(20000000L);
        assertThat(dto.duLieuSau()).isInstanceOf(ObjectNode.class);
        assertThat(dto.duLieuSau().get("giaBan").asLong()).isEqualTo(22000000L);
    }

    @Test
    @DisplayName("shouldConvertDetailPayloadToArrayNodeWhenValidJsonArray")
    void shouldConvertDetailPayloadToArrayNodeWhenValidJsonArray() {
        NhatKyHoatDong nhatKy = new NhatKyHoatDong();
        nhatKy.setMaNhatKy("NK002");
        nhatKy.setHanhDong(HanhDongAudit.UPDATE_PRODUCT);
        nhatKy.setLoaiDoiTuong(LoaiDoiTuongAudit.PRODUCT);
        nhatKy.setDuLieuSau("[{\"ram\":8},{\"ram\":12}]");
        nhatKy.setThoiGian(LocalDateTime.now());

        when(nhatKyHoatDongRepository.findById("NK002")).thenReturn(Optional.of(nhatKy));

        NhatKyHoatDongChiTietDto dto = nhatKyHoatDongService.get("NK002");

        assertThat(dto.duLieuSau()).isInstanceOf(ArrayNode.class);
        assertThat(((ArrayNode) dto.duLieuSau()).size()).isEqualTo(2);
    }

    @Test
    @DisplayName("shouldConvertDetailPayloadToTextNodeWhenMalformedLegacyJson")
    void shouldConvertDetailPayloadToTextNodeWhenMalformedLegacyJson() {
        NhatKyHoatDong nhatKy = new NhatKyHoatDong();
        nhatKy.setMaNhatKy("NK003");
        nhatKy.setHanhDong(HanhDongAudit.UPDATE_CATEGORY);
        nhatKy.setLoaiDoiTuong(LoaiDoiTuongAudit.CATEGORY);
        nhatKy.setDuLieuTruoc("{malformed json: without quotes");
        nhatKy.setThoiGian(LocalDateTime.now());

        when(nhatKyHoatDongRepository.findById("NK003")).thenReturn(Optional.of(nhatKy));

        NhatKyHoatDongChiTietDto dto = nhatKyHoatDongService.get("NK003");

        // Must NOT throw JsonProcessingException, falls back to TextNode safely
        assertThat(dto.duLieuTruoc()).isInstanceOf(TextNode.class);
        assertThat(dto.duLieuTruoc().asText()).isEqualTo("{malformed json: without quotes");
    }

    @Test
    @DisplayName("shouldReturnNullWhenDetailPayloadIsNull")
    void shouldReturnNullWhenDetailPayloadIsNull() {
        NhatKyHoatDong nhatKy = new NhatKyHoatDong();
        nhatKy.setMaNhatKy("NK004");
        nhatKy.setHanhDong(HanhDongAudit.CREATE_PRODUCT);
        nhatKy.setLoaiDoiTuong(LoaiDoiTuongAudit.PRODUCT);
        nhatKy.setDuLieuTruoc(null);
        nhatKy.setDuLieuSau(null);
        nhatKy.setThoiGian(LocalDateTime.now());

        when(nhatKyHoatDongRepository.findById("NK004")).thenReturn(Optional.of(nhatKy));

        NhatKyHoatDongChiTietDto dto = nhatKyHoatDongService.get("NK004");

        assertThat(dto.duLieuTruoc()).isNull();
        assertThat(dto.duLieuSau()).isNull();
    }
}
