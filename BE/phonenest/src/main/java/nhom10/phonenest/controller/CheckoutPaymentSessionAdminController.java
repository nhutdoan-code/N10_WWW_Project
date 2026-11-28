package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.CheckoutPaymentRefundRequest;
import nhom10.phonenest.dto.CheckoutPaymentStatusDto;
import nhom10.phonenest.service.CheckoutPaymentSessionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/checkout-payment-sessions")
@RequiredArgsConstructor
public class CheckoutPaymentSessionAdminController {
    private final CheckoutPaymentSessionService sessionService;

    @PutMapping("/{sessionId}/refund")
    public CheckoutPaymentStatusDto recordManualRefund(
            @PathVariable String sessionId,
            @Valid @RequestBody CheckoutPaymentRefundRequest request) {
        return sessionService.recordManualRefund(sessionId, request.maGiaoDichHoanTien());
    }
}
