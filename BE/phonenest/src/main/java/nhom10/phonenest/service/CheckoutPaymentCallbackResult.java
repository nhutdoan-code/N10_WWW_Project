package nhom10.phonenest.service;

public record CheckoutPaymentCallbackResult(
        Status status,
        String orderId) {

    public enum Status {
        AMOUNT_MISMATCH,
        PAYMENT_FAILED,
        ORDER_CREATED,
        REFUND_REQUIRED,
        ALREADY_HANDLED
    }
}
