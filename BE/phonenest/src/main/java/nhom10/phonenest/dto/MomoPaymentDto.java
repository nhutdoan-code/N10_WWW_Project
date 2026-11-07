package nhom10.phonenest.dto;

public record MomoPaymentDto(
        String orderId,
        String requestId,
        String payUrl,
        String deeplink,
        String qrCodeUrl,
        String resultCode,
        String message) {
}
