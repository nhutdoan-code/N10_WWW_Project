package nhom10.phonenest.service;

import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.PhienThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiPhienThanhToan;
import nhom10.phonenest.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrepaidCheckoutServiceTest {
    private static final String CUSTOMER_ID = "KH001";
    private static final String SESSION_ID = "PC001";
    private static final String IDEMPOTENCY_KEY = "123e4567-e89b-42d3-a456-426614174000";
    private static final BigDecimal AMOUNT = new BigDecimal("500000");

    @Mock
    private CheckoutPaymentSessionService sessionService;
    @Mock
    private MomoPaymentService momoPaymentService;
    @Mock
    private VnPayPaymentService vnPayPaymentService;
    @InjectMocks
    private PrepaidCheckoutService service;

    @Test
    void uncertainProviderResultKeepsSessionOpenForSameKeyRetry() {
        PhienThanhToan session = pendingSession();
        when(sessionService.prepare(CUSTOMER_ID, IDEMPOTENCY_KEY, checkout(), null)).thenReturn(session);
        AppException timeout = new AppException(
                HttpStatus.BAD_GATEWAY, "MoMo chưa phản hồi", "PAYMENT_PROVIDER_UNCERTAIN");
        when(momoPaymentService.createCheckoutPayment(SESSION_ID, AMOUNT)).thenThrow(timeout);

        assertThatThrownBy(() -> service.start(CUSTOMER_ID, IDEMPOTENCY_KEY, checkout(), null, "127.0.0.1"))
                .isSameAs(timeout);

        verify(sessionService, never()).markProviderCreationFailed(SESSION_ID);
    }

    @Test
    void definitiveProviderFailureMarksSessionFailed() {
        PhienThanhToan session = pendingSession();
        when(sessionService.prepare(CUSTOMER_ID, IDEMPOTENCY_KEY, checkout(), null)).thenReturn(session);
        AppException rejected = new AppException(HttpStatus.BAD_GATEWAY, "MoMo từ chối tạo thanh toán");
        when(momoPaymentService.createCheckoutPayment(SESSION_ID, AMOUNT)).thenThrow(rejected);

        assertThatThrownBy(() -> service.start(CUSTOMER_ID, IDEMPOTENCY_KEY, checkout(), null, "127.0.0.1"))
                .isSameAs(rejected);

        verify(sessionService).markProviderCreationFailed(SESSION_ID);
    }

    private static DonHangDto.Checkout checkout() {
        return new DonHangDto.Checkout(PhuongThucTt.VI_DIEN_TU, "DC001", null, null, null, null);
    }

    private static PhienThanhToan pendingSession() {
        PhienThanhToan session = new PhienThanhToan();
        session.setMaPhienThanhToan(SESSION_ID);
        session.setSoTien(AMOUNT);
        session.setTrangThai(TrangThaiPhienThanhToan.CHO_THANH_TOAN);
        return session;
    }
}
