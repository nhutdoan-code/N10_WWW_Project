package nhom10.phonenest.dto;

public record VnPayRefundStatusDto(
        String status,
        String transactionId,
        String refundRequestId,
        String refundTransactionId,
        String message) {
}
