package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.CheckoutPaymentDto;
import nhom10.phonenest.dto.CheckoutPaymentStatusDto;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.PhienThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiPhienThanhToan;
import nhom10.phonenest.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrepaidCheckoutService {

    private final CheckoutPaymentSessionService sessionService;
    private final MomoPaymentService momoPaymentService;
    private final VnPayPaymentService vnPayPaymentService;

    public CheckoutPaymentDto start(
            String customerId,
            String idempotencyKey,
            DonHangDto.Checkout checkout,
            List<String> selectedVariants,
            String clientIp) {
        PhienThanhToan session = sessionService.prepare(
                customerId, idempotencyKey, checkout, selectedVariants);
        if (session.getTrangThai() == TrangThaiPhienThanhToan.DA_TAO_DON) {
            return new CheckoutPaymentDto(session.getMaPhienThanhToan(),
                    session.getTrangThai().name(), null, session.getMaDonHang());
        }
        if (session.getPayUrl() != null && !session.getPayUrl().isBlank()) {
            return new CheckoutPaymentDto(session.getMaPhienThanhToan(),
                    session.getTrangThai().name(), session.getPayUrl(), session.getMaDonHang());
        }

        try {
            String payUrl;
            if (checkout.phuongThucTt() == PhuongThucTt.VI_DIEN_TU) {
                payUrl = momoPaymentService.createCheckoutPayment(
                        session.getMaPhienThanhToan(), session.getSoTien()).payUrl();
            } else {
                LocalDateTime createDate = sessionService.getOrCreateVnPayCreateDate(
                        session.getMaPhienThanhToan());
                payUrl = vnPayPaymentService.createCheckoutPayment(
                        session.getMaPhienThanhToan(), session.getSoTien(), clientIp, createDate).payUrl();
            }
            if (payUrl == null || payUrl.isBlank()) {
                throw new AppException(HttpStatus.BAD_GATEWAY,
                        "Cổng thanh toán không trả về đường dẫn thanh toán");
            }
            PhienThanhToan updated = sessionService.savePayUrl(session.getMaPhienThanhToan(), payUrl);
            return new CheckoutPaymentDto(
                    updated.getMaPhienThanhToan(),
                    updated.getTrangThai().name(),
                    updated.getTrangThai() == TrangThaiPhienThanhToan.DA_TAO_DON ? null : payUrl,
                    updated.getMaDonHang());
        } catch (AppException e) {
            if (!"PAYMENT_PROVIDER_UNCERTAIN".equals(e.getCode())) {
                sessionService.markProviderCreationFailed(session.getMaPhienThanhToan());
            }
            throw e;
        }
    }

    public CheckoutPaymentStatusDto status(String customerId, String sessionId) {
        return sessionService.status(customerId, sessionId);
    }
}
