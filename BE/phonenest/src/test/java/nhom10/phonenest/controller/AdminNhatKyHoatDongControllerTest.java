package nhom10.phonenest.controller;

import nhom10.phonenest.dto.NhatKyHoatDongChiTietDto;
import nhom10.phonenest.dto.NhatKyHoatDongDto;
import nhom10.phonenest.dto.TrangDto;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.exception.GlobalExceptionHandler;
import nhom10.phonenest.service.NhatKyHoatDongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminNhatKyHoatDongControllerTest {

    @Mock
    private NhatKyHoatDongService nhatKyHoatDongService;

    @InjectMocks
    private AdminNhatKyHoatDongController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("shouldReturn200ForAuditLogList")
    void shouldReturn200ForAuditLogList() throws Exception {
        NhatKyHoatDongDto item = new NhatKyHoatDongDto(
                "NK001",
                "TK001",
                "Quản trị viên",
                HanhDongAudit.CREATE_PRODUCT,
                LoaiDoiTuongAudit.PRODUCT,
                "SP001",
                "Tạo sản phẩm SP001",
                "127.0.0.1",
                "POST",
                "/api/admin/products",
                LocalDateTime.now()
        );
        TrangDto<NhatKyHoatDongDto> page = new TrangDto<>(List.of(item), 0, 20, 1, 1);

        when(nhatKyHoatDongService.list(anyInt(), anyInt(), anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/audit-logs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tongSo").value(1))
                .andExpect(jsonPath("$.noiDung[0].maNhatKy").value("NK001"))
                .andExpect(jsonPath("$.noiDung[0].hanhDong").value("CREATE_PRODUCT"))
                .andExpect(jsonPath("$.noiDung[0].loaiDoiTuong").value("PRODUCT"));
    }

    @Test
    @DisplayName("shouldReturn200ForAuditLogDetail")
    void shouldReturn200ForAuditLogDetail() throws Exception {
        NhatKyHoatDongChiTietDto detail = new NhatKyHoatDongChiTietDto(
                "NK001",
                "TK001",
                "Quản trị viên",
                HanhDongAudit.UPDATE_PRODUCT,
                LoaiDoiTuongAudit.PRODUCT,
                "SP001",
                "Cập nhật sản phẩm SP001",
                "127.0.0.1",
                "PUT",
                "/api/admin/products/SP001",
                LocalDateTime.now(),
                null,
                null
        );

        when(nhatKyHoatDongService.get("NK001")).thenReturn(detail);

        mockMvc.perform(get("/api/admin/audit-logs/NK001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maNhatKy").value("NK001"))
                .andExpect(jsonPath("$.hanhDong").value("UPDATE_PRODUCT"))
                .andExpect(jsonPath("$.maDoiTuong").value("SP001"));
    }

    @Test
    @DisplayName("shouldReturn404WhenDetailNotFound")
    void shouldReturn404WhenDetailNotFound() throws Exception {
        when(nhatKyHoatDongService.get("NK_NON_EXIST"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy nhật ký hoạt động: NK_NON_EXIST"));

        mockMvc.perform(get("/api/admin/audit-logs/NK_NON_EXIST")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy nhật ký hoạt động: NK_NON_EXIST"));
    }

    @Test
    @DisplayName("shouldReturn400WhenPageParameterIsInvalidType")
    void shouldReturn400WhenPageParameterIsInvalidType() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs?page=not_a_number")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tham số không hợp lệ: page"));
    }

    @Test
    @DisplayName("shouldReturn405ForPostOnAuditLogList")
    void shouldReturn405ForPostOnAuditLogList() throws Exception {
        mockMvc.perform(post("/api/admin/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"fake\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Phương thức HTTP không được hỗ trợ: POST"));
    }

    @Test
    @DisplayName("shouldReturn405ForDeleteOnAuditLogDetail")
    void shouldReturn405ForDeleteOnAuditLogDetail() throws Exception {
        mockMvc.perform(delete("/api/admin/audit-logs/NK001"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Phương thức HTTP không được hỗ trợ: DELETE"));
    }
}
