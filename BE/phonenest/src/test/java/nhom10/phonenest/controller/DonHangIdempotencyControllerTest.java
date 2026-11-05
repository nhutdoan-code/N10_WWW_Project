package nhom10.phonenest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.exception.GlobalExceptionHandler;
import nhom10.phonenest.service.CheckoutIdempotencyService;
import nhom10.phonenest.service.DonHangService;
import nhom10.phonenest.service.ThanhToanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DonHangIdempotencyControllerTest {

    @Mock
    private DonHangService donHangService;

    @Mock
    private ThanhToanService thanhToanService;

    @Mock
    private CheckoutIdempotencyService checkoutIdempotencyService;

    @InjectMocks
    private DonHangController donHangController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String VALID_KEY = "123e4567-e89b-12d3-a456-426614174000";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(donHangController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("KH001", null, List.of())
        );
    }

    @Test
    @DisplayName("API: Thiếu header Idempotency-Key trả về 400 Bad Request")
    void missingIdempotencyKey_Returns400() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        when(checkoutIdempotencyService.executeIdempotentCheckout(eq("KH001"), isNull(), any()))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Thiếu header Idempotency-Key bắt buộc khi đặt hàng"));

        mockMvc.perform(post("/api/customer/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thiếu header Idempotency-Key bắt buộc khi đặt hàng"));
    }

    @Test
    @DisplayName("API: Header Idempotency-Key sai định dạng UUID trả về 400 Bad Request")
    void invalidUuidHeader_Returns400() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        when(checkoutIdempotencyService.executeIdempotentCheckout(eq("KH001"), eq("bad-key"), any()))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Header Idempotency-Key phải là định dạng UUID hợp lệ"));

        mockMvc.perform(post("/api/customer/orders")
                        .header("Idempotency-Key", "bad-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Header Idempotency-Key phải là định dạng UUID hợp lệ"));
    }

    @Test
    @DisplayName("API: Hợp lệ với Idempotency-Key trả về 200 OK với DonHangDto")
    void validIdempotencyKey_Returns200() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);
        DonHangDto dto = new DonHangDto(
                "DH001", "KH001", null, null, LocalDateTime.now(),
                PhuongThucTt.COD, TrangThaiDonHang.CHO_XAC_NHAN,
                new BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000000"), "Địa chỉ", LocalDateTime.now(), null, "Tên",
                null, null, null, null, List.of()
        );

        when(checkoutIdempotencyService.executeIdempotentCheckout(eq("KH001"), eq(VALID_KEY), any()))
                .thenReturn(dto);

        mockMvc.perform(post("/api/customer/orders")
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maDonHang").value("DH001"));
    }

    @Test
    @DisplayName("API: Xung đột Idempotency IN_PROGRESS trả về 409 với mã lỗi IDEMPOTENCY_IN_PROGRESS")
    void idempotencyConflict_Returns409WithInProgressCode() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        when(checkoutIdempotencyService.executeIdempotentCheckout(eq("KH001"), eq(VALID_KEY), any()))
                .thenThrow(new AppException(
                        HttpStatus.CONFLICT,
                        "Yêu cầu đặt hàng của bạn đang được xử lý hoặc đang chờ đối soát, vui lòng không gửi lại",
                        CheckoutIdempotencyService.CODE_IN_PROGRESS
                ));

        mockMvc.perform(post("/api/customer/orders")
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value("Yêu cầu đặt hàng của bạn đang được xử lý hoặc đang chờ đối soát, vui lòng không gửi lại"));
    }

    @Test
    @DisplayName("API: Xung đột Idempotency PAYLOAD_MISMATCH trả về 409 với mã lỗi IDEMPOTENCY_PAYLOAD_MISMATCH")
    void idempotencyConflict_Returns409WithPayloadMismatchCode() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        when(checkoutIdempotencyService.executeIdempotentCheckout(eq("KH001"), eq(VALID_KEY), any()))
                .thenThrow(new AppException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key đã được sử dụng với thông tin đặt hàng khác",
                        CheckoutIdempotencyService.CODE_PAYLOAD_MISMATCH
                ));

        mockMvc.perform(post("/api/customer/orders")
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_PAYLOAD_MISMATCH"))
                .andExpect(jsonPath("$.message").value("Idempotency-Key đã được sử dụng với thông tin đặt hàng khác"));
    }
}
