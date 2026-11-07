package nhom10.phonenest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import nhom10.phonenest.dto.HanhViDto;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.exception.GlobalExceptionHandler;
import nhom10.phonenest.service.HanhViNguoiDungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HanhViControllerTest {

    @Mock
    private HanhViNguoiDungService hanhViService;

    private MockMvc trackingMockMvc;
    private MockMvc adminMockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        HanhViController trackingController = new HanhViController(hanhViService);
        AdminHanhViController adminController = new AdminHanhViController(hanhViService);

        trackingMockMvc = MockMvcBuilders.standaloneSetup(trackingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        adminMockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/tracking/events ghi nhận sự kiện hợp lệ thành công")
    void trackEvent_validRequest_returnsOk() throws Exception {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.PRODUCT_VIEW,
                "SP001",
                null,
                null,
                "DETAIL",
                null
        );

        when(hanhViService.ghiNhan(any(HanhViDto.EventRequest.class), any(), any()))
                .thenReturn(new HanhViDto.EventResponse("HV123", true, "Ghi nhận thành công"));

        trackingMockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.maHanhVi").value("HV123"));
    }

    @Test
    @DisplayName("POST /api/tracking/events chặn client gửi PURCHASE trả về 400 Bad Request")
    void trackEvent_purchaseSpoof_returnsBadRequest() throws Exception {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.PURCHASE,
                "SP001",
                null,
                null,
                "SPOOF",
                null
        );

        when(hanhViService.ghiNhan(any(HanhViDto.EventRequest.class), any(), any()))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Hành vi PURCHASE chỉ được ghi nhận từ hệ thống xử lý giao dịch"));

        trackingMockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Hành vi PURCHASE chỉ được ghi nhận từ hệ thống xử lý giao dịch"));
    }

    @Test
    @DisplayName("POST /api/tracking/events trả về 429 khi bị Rate Limit")
    void trackEvent_rateLimited_returnsTooManyRequests() throws Exception {
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                "SESSION_123",
                LoaiHanhViNguoiDung.SEARCH,
                null,
                null,
                "test",
                "SEARCH",
                null
        );

        when(hanhViService.ghiNhan(any(HanhViDto.EventRequest.class), any(), any()))
                .thenReturn(new HanhViDto.EventResponse("RATE_LIMITED", false, "Tần suất gửi sự kiện vượt giới hạn cho phép"));

        trackingMockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.maHanhVi").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("GET /api/admin/analytics/behavior trả về thống kê 200")
    void getBehaviorStats_returnsOk() throws Exception {
        HanhViDto.BehaviorStatsResponse mockStats = new HanhViDto.BehaviorStatsResponse(
                100L,
                Map.of("PRODUCT_VIEW", 80L, "PURCHASE", 20L),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        when(hanhViService.layThongKe(any(), any(), any(), any())).thenReturn(mockStats);

        adminMockMvc.perform(get("/api/admin/analytics/behavior"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tongLuot").value(100L))
                .andExpect(jsonPath("$.theoLoai.PRODUCT_VIEW").value(80L));
    }

    @Test
    @DisplayName("GET /api/admin/analytics/preferences/{maKh} trả về thông tin sở thích người dùng")
    void getUserPreferences_returnsOk() throws Exception {
        HanhViDto.UserPreferenceDto mockPref = new HanhViDto.UserPreferenceDto(
                "KH001",
                List.of("Apple", "Samsung"),
                List.of("Smartphone"),
                null,
                null,
                List.of("iphone 15"),
                List.of("iPhone 15 Pro Max"),
                15,
                "Khách hàng ưa thích Apple"
        );

        when(hanhViService.laySoThichNguoiDung("KH001")).thenReturn(mockPref);

        adminMockMvc.perform(get("/api/admin/analytics/preferences/KH001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maKh").value("KH001"))
                .andExpect(jsonPath("$.topThuongHieu[0]").value("Apple"))
                .andExpect(jsonPath("$.tomTatAi").value("Khách hàng ưa thích Apple"));
    }

    @Test
    @DisplayName("DELETE /api/admin/analytics/cleanup dọn dẹp sự kiện cũ thành công với số ngày hợp lệ")
    void cleanupOldEvents_returnsOk() throws Exception {
        when(hanhViService.xoaHanhViCu(60)).thenReturn(35);

        adminMockMvc.perform(delete("/api/admin/analytics/cleanup").param("soNgayLuuTru", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thanhCong").value(true))
                .andExpect(jsonPath("$.soBanGhiDaXoa").value(35))
                .andExpect(jsonPath("$.soNgayLuuTru").value(60));
    }

    @Test
    @DisplayName("DELETE /api/admin/analytics/cleanup từ chối khi soNgayLuuTru < 7")
    void cleanupOldEvents_invalidDays_returnsBadRequest() throws Exception {
        adminMockMvc.perform(delete("/api/admin/analytics/cleanup").param("soNgayLuuTru", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số ngày lưu trữ không hợp lệ. Phải từ 7 đến 3650 ngày để đảm bảo an toàn dữ liệu."));
    }
}
