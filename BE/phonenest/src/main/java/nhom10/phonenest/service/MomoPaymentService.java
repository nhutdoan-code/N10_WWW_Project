package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.MomoPaymentDto;
import nhom10.phonenest.dto.PaymentStatusDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class MomoPaymentService {
    private static final String REQUEST_TYPE = "captureWallet";

    private final DonHangRepository donHangRepository;
    private final ThanhToanRepository thanhToanRepository;

    @Value("${momo.endpoint:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String endpoint;
    @Value("${momo.partner-code:}")
    private String partnerCode;
    @Value("${momo.access-key:}")
    private String accessKey;
    @Value("${momo.secret-key:}")
    private String secretKey;
    @Value("${momo.redirect-url:${app.frontend-url}/orders}")
    private String redirectUrl;
    @Value("${momo.ipn-url:http://localhost:8080/api/payment/momo/ipn}")
    private String ipnUrl;

    public MomoPaymentDto create(String maKh, String maDonHang) {
        requireConfigured();
        DonHang order = donHangRepository.findById(maDonHang)
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ThanhToan payment = findPayment(maDonHang);
        if (payment.getPhuongThuc() != PhuongThucTt.VI_DIEN_TU) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng không dùng MoMo");
        }
        if (payment.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new AppException(HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán");
        }

        String requestId = payment.getTrangThai() == TrangThaiThanhToan.THAT_BAI
                ? payment.getMaThanhToan() + "-" + System.currentTimeMillis()
                : payment.getMaThanhToan();
        payment.setMaGiaoDich(requestId);

        Map<String, String> request = new LinkedHashMap<>();
        request.put("partnerCode", partnerCode);
        request.put("requestType", REQUEST_TYPE);
        request.put("ipnUrl", ipnUrl);
        request.put("redirectUrl", redirectUrl + "/" + maDonHang);
        request.put("orderId", requestId);
        request.put("amount", order.getTongThanhToan().toBigInteger().toString());
        request.put("lang", "vi");
        request.put("requestId", requestId);
        request.put("extraData", "");
        request.put("orderInfo", "Thanh toan don hang " + maDonHang);
        request.put("signature", signCreate(request));

        Map<?, ?> response;
        try {
            response = RestClient.create(endpoint).post().body(request).retrieve().body(Map.class);
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "Không thể kết nối cổng thanh toán MoMo");
        }
        if (response == null || !"0".equals(value(response, "resultCode"))) {
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "MoMo từ chối tạo thanh toán: " + value(response, "message"));
        }
        thanhToanRepository.saveAndFlush(payment);
        return new MomoPaymentDto(
                requestId,
                value(response, "requestId"),
                value(response, "payUrl"),
                value(response, "deeplink"),
                value(response, "qrCodeUrl"),
                value(response, "resultCode"),
                value(response, "message"));
    }

    public PaymentStatusDto status(String maKh, String maDonHang) {
        DonHang order = donHangRepository.findById(maDonHang)
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ThanhToan payment = findPayment(order.getMaDonHang());
        return new PaymentStatusDto(payment.getTrangThai().name(), payment.getMaGiaoDich());
    }

    public void processIpn(Map<String, Object> payload) {
        String orderId = required(payload, "orderId");
        ThanhToan payment = thanhToanRepository.findByMaGiaoDich(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch MoMo"));
        String amount = required(payload, "amount");
        try {
            if (new BigDecimal(amount).compareTo(payment.getDonHang().getTongThanhToan()) != 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán MoMo không hợp lệ");
            }
        } catch (NumberFormatException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán MoMo không hợp lệ");
        }
        if (!constantTimeEquals(signIpn(payload), required(payload, "signature"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chữ ký MoMo không hợp lệ");
        }
        if (payment.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN) return;
        if ("0".equals(required(payload, "resultCode"))) {
            payment.setTrangThai(TrangThaiThanhToan.DA_THANH_TOAN);
            payment.setNgayThanhToan(LocalDateTime.now());
        } else {
            payment.setTrangThai(TrangThaiThanhToan.THAT_BAI);
        }
        thanhToanRepository.save(payment);
    }

    private ThanhToan findPayment(String maDonHang) {
        return thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(maDonHang).stream()
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch thanh toán"));
    }

    private void requireConfigured() {
        if (partnerCode.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "MoMo chưa được cấu hình trên máy chủ");
        }
    }

    private String signCreate(Map<String, String> request) {
        return hmac("accessKey=" + accessKey + "&amount=" + request.get("amount")
                + "&extraData=" + request.get("extraData") + "&ipnUrl=" + request.get("ipnUrl")
                + "&orderId=" + request.get("orderId") + "&orderInfo=" + request.get("orderInfo")
                + "&partnerCode=" + partnerCode + "&redirectUrl=" + request.get("redirectUrl")
                + "&requestId=" + request.get("requestId") + "&requestType=" + REQUEST_TYPE);
    }

    private String signIpn(Map<String, Object> payload) {
        return hmac("accessKey=" + accessKey + "&amount=" + required(payload, "amount")
                + "&extraData=" + required(payload, "extraData") + "&message=" + required(payload, "message")
                + "&orderId=" + required(payload, "orderId") + "&orderInfo=" + required(payload, "orderInfo")
                + "&orderType=" + required(payload, "orderType") + "&partnerCode=" + required(payload, "partnerCode")
                + "&payType=" + required(payload, "payType") + "&requestId=" + required(payload, "requestId")
                + "&responseTime=" + required(payload, "responseTime") + "&resultCode=" + required(payload, "resultCode")
                + "&transId=" + required(payload, "transId"));
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder result = new StringBuilder();
            for (byte value : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo chữ ký MoMo", ex);
        }
    }

    private static String value(Map<?, ?> values, String key) {
        if (values == null) return "";
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String required(Map<String, ?> values, String key) {
        String value = value(values, key);
        if (value.isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "Thiếu trường MoMo: " + key);
        return value;
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
}
