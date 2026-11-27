package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckoutPaymentRefundRequest(
        @NotBlank(message = "Vui lòng nhập mã giao dịch hoàn tiền")
        @Size(max = 100, message = "Mã giao dịch hoàn tiền tối đa 100 ký tự")
        String maGiaoDichHoanTien) {
}
