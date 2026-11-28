package nhom10.phonenest.service;

import nhom10.phonenest.enums.PhuongThucTt;

import java.math.BigDecimal;

public record CheckoutRefundRequiredEvent(
        String sessionId,
        String customerId,
        PhuongThucTt phuongThuc,
        BigDecimal amount,
        String providerTransactionId,
        String reason) {
}
