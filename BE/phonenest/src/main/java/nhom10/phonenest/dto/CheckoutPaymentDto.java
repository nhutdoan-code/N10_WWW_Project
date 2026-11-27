package nhom10.phonenest.dto;

public record CheckoutPaymentDto(
        String sessionId,
        String status,
        String payUrl,
        String orderId) {
}
