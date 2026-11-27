package nhom10.phonenest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.CheckoutPaymentPayload;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.ThanhToanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CheckoutPaymentOrderFinalizer {

    private final ObjectMapper objectMapper;
    private final DonHangService donHangService;
    private final ThanhToanRepository thanhToanRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DonHangDto finalizePaidCheckout(
            String customerId,
            String sessionId,
            String checkoutPayload,
            BigDecimal paidAmount,
            String providerTransactionId,
            PhuongThucTt paymentMethod) {
        ThanhToan existingPayment = thanhToanRepository.findByMaGiaoDich(sessionId).orElse(null);
        if (existingPayment != null) {
            return donHangService.chiTiet(customerId, existingPayment.getDonHang().getMaDonHang());
        }

        CheckoutPaymentPayload payload;
        try {
            payload = objectMapper.readValue(checkoutPayload, CheckoutPaymentPayload.class);
        } catch (JsonProcessingException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể đọc thông tin phiên thanh toán để tạo đơn hàng");
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        for (CheckoutPaymentPayload.CheckoutLine line : payload.items()) {
            if (line.maPhienBan() == null || line.maPhienBan().isBlank() || line.soLuong() < 1
                    || items.putIfAbsent(line.maPhienBan(), line.soLuong()) != null) {
                throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Thông tin sản phẩm trong phiên thanh toán không hợp lệ");
            }
        }
        return donHangService.datHangDaThanhToan(
                customerId,
                payload.checkout(),
                items,
                paidAmount,
                sessionId,
                providerTransactionId,
                paymentMethod);
    }
}
