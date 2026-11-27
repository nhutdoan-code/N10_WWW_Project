package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.CheckoutPaymentDto;
import nhom10.phonenest.dto.CheckoutPaymentStatusDto;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.MomoPaymentDto;
import nhom10.phonenest.dto.PaymentStatusDto;
import nhom10.phonenest.dto.VnPayIpnResponse;
import nhom10.phonenest.dto.VnPayPaymentDto;
import nhom10.phonenest.service.MomoPaymentService;
import nhom10.phonenest.service.PrepaidCheckoutService;
import nhom10.phonenest.service.VnPayPaymentService;
import nhom10.phonenest.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final MomoPaymentService momoPaymentService;
    private final VnPayPaymentService vnPayPaymentService;
    private final PrepaidCheckoutService prepaidCheckoutService;

    @PostMapping("/api/customer/checkout/payment")
    public CheckoutPaymentDto startPrepaidCheckout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(required = false) List<String> chon,
            @RequestBody @Valid DonHangDto.Checkout request,
            HttpServletRequest servletRequest) {
        return prepaidCheckoutService.start(
                SecurityUtil.currentUserId(),
                idempotencyKey,
                request,
                chon,
                servletRequest.getRemoteAddr());
    }

    @GetMapping("/api/customer/checkout/payment/{sessionId}")
    public CheckoutPaymentStatusDto checkoutPaymentStatus(@PathVariable String sessionId) {
        return prepaidCheckoutService.status(SecurityUtil.currentUserId(), sessionId);
    }

    @PostMapping("/api/customer/orders/{ma}/momo")
    public MomoPaymentDto createMomoPayment(@PathVariable String ma) {
        return momoPaymentService.create(SecurityUtil.currentUserId(), ma);
    }

    @GetMapping("/api/customer/orders/{ma}/payment-status")
    public PaymentStatusDto paymentStatus(@PathVariable String ma) {
        return momoPaymentService.status(SecurityUtil.currentUserId(), ma);
    }

    @PostMapping("/api/payment/momo/ipn")
    public ResponseEntity<Void> momoIpn(@RequestBody Map<String, Object> payload) {
        momoPaymentService.processIpn(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/customer/orders/{ma}/vnpay")
    public VnPayPaymentDto createVnPayPayment(@PathVariable String ma, HttpServletRequest request) {
        return vnPayPaymentService.create(
                SecurityUtil.currentUserId(), ma, request.getRemoteAddr());
    }

    @GetMapping("/api/payment/vnpay/ipn")
    public VnPayIpnResponse vnPayIpn(@RequestParam MultiValueMap<String, String> params) {
        return vnPayPaymentService.processIpn(params.toSingleValueMap());
    }

    @GetMapping("/api/payment/vnpay/return")
    public ResponseEntity<Void> vnPayReturn(@RequestParam MultiValueMap<String, String> params) {
        String redirectUrl = vnPayPaymentService.processReturn(params.toSingleValueMap());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectUrl).toString())
                .build();
    }
}
