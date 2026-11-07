package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.MomoPaymentDto;
import nhom10.phonenest.dto.PaymentStatusDto;
import nhom10.phonenest.service.MomoPaymentService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final MomoPaymentService momoPaymentService;

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
}
