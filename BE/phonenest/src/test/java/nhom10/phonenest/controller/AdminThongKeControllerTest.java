package nhom10.phonenest.controller;

import nhom10.phonenest.dto.ThongKeDto;
import nhom10.phonenest.dto.TimKiemDto;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.exception.GlobalExceptionHandler;
import nhom10.phonenest.service.NhatKyHoatDongService;
import nhom10.phonenest.service.ThongKeService;
import nhom10.phonenest.service.TimKiemService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminThongKeControllerTest {

    @Mock
    private ThongKeService thongKeService;

    @Mock
    private TimKiemService timKiemService;

    @Mock
    private NhatKyHoatDongService nhatKyHoatDongService;

    @InjectMocks
    private AdminThongKeController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/stats should return 200 and stats payload")
    void shouldReturn200ForStats() throws Exception {
        ThongKeDto mockDto = new ThongKeDto(
                new BigDecimal("32330000"),
                new BigDecimal("25505000"),
                4,
                2,
                1,
                1,
                0,
                19,
                3,
                12,
                Map.of("DA_GIAO", 2, "DA_HUY", 1, "DANG_GIAO", 1),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        when(thongKeService.thongKe(any(), any())).thenReturn(mockDto);

        mockMvc.perform(get("/api/admin/stats")
                        .param("tu", "2026-10-01")
                        .param("den", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doanhThu").value(32330000))
                .andExpect(jsonPath("$.giaTriDangXuLy").value(25505000))
                .andExpect(jsonPath("$.soDon").value(4))
                .andExpect(jsonPath("$.soDonThanhCong").value(2))
                .andExpect(jsonPath("$.soDonHuy").value(1))
                .andExpect(jsonPath("$.soDonDangXuLy").value(1))
                .andExpect(jsonPath("$.soHetHang").value(3))
                .andExpect(jsonPath("$.soSapHetHang").value(19))
                .andExpect(jsonPath("$.soConHang").value(12));

        // Verify audit log was never called during stats query
        verifyNoInteractions(nhatKyHoatDongService);
    }

    @Test
    @DisplayName("GET /api/admin/stats should return 400 when tu > den")
    void shouldReturn400WhenTuAfterDen() throws Exception {
        when(thongKeService.thongKe(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 10)))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc"));

        mockMvc.perform(get("/api/admin/stats")
                        .param("tu", "2026-10-20")
                        .param("den", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngày bắt đầu không được sau ngày kết thúc"));

        verifyNoInteractions(nhatKyHoatDongService);
    }

    @Test
    @DisplayName("GET /api/admin/stats/export should return 200 with CSV content")
    void shouldReturn200ForCsvExport() throws Exception {
        when(thongKeService.xuatCsv(any(), any())).thenReturn("Mã đơn,Ngày mua\nDH1,2026-10-15\n");

        mockMvc.perform(get("/api/admin/stats/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=bao-cao-don-hang.csv"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));

        verifyNoInteractions(nhatKyHoatDongService);
    }

    @Test
    @DisplayName("GET /api/admin/stats/export should return 400 when tu > den")
    void shouldReturn400ForCsvExportWhenTuAfterDen() throws Exception {
        when(thongKeService.xuatCsv(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 10)))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc"));

        mockMvc.perform(get("/api/admin/stats/export")
                        .param("tu", "2026-10-20")
                        .param("den", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngày bắt đầu không được sau ngày kết thúc"));

        verifyNoInteractions(nhatKyHoatDongService);
    }

    @Test
    @DisplayName("GET /api/admin/search should return 200 and not write audit log")
    void shouldReturn200ForSearchWithoutAudit() throws Exception {
        TimKiemDto dto = new TimKiemDto(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(timKiemService.tim("iphone")).thenReturn(dto);

        mockMvc.perform(get("/api/admin/search")
                        .param("q", "iphone"))
                .andExpect(status().isOk());

        verifyNoInteractions(nhatKyHoatDongService);
    }
}
