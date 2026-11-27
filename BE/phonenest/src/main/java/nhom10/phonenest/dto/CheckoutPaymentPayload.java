package nhom10.phonenest.dto;

import java.util.List;

public record CheckoutPaymentPayload(
        DonHangDto.Checkout checkout,
        List<CheckoutLine> items) {

    public record CheckoutLine(String maPhienBan, int soLuong) {
    }
}
