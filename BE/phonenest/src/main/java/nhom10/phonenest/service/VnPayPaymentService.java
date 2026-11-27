package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.PaymentStatusDto;
import nhom10.phonenest.dto.VnPayIpnResponse;
import nhom10.phonenest.dto.VnPayPaymentDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VnPayPaymentService {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DonHangRepository donHangRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final DonHangService donHangService;
    private final ThongBaoService thongBaoService;
    private final CheckoutPaymentSessionService checkoutPaymentSessionService;

    @Value("${vnpay.endpoint:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String endpoint;
    @Value("${vnpay.tmn-code:}")
    private String tmnCode;
    @Value("${vnpay.hash-secret:}")
    private String hashSecret;
    @Value("${vnpay.return-url:http://localhost:8080/api/payment/vnpay/return}")
    private String returnUrl;
    @Value("${vnpay.frontend-return-url:http://localhost:5173/orders}")
    private String frontendReturnUrl;

    public VnPayPaymentDto create(String maKh, String maDonHang, String clientIp) {
        requireConfigured();
        DonHang order = donHangRepository.findByIdForUpdate(maDonHang)
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (order.getTrangThai() == TrangThaiDonHang.DA_HUY) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã bị hủy, không thể tạo thanh toán");
        }
        if (order.getTrangThai() == TrangThaiDonHang.DA_GIAO) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã hoàn thành, không thể tạo thanh toán");
        }

        List<ThanhToan> payments = thanhToanRepository
                .findByDonHangMaDonHangOrderByNgayTaoDesc(maDonHang);
        if (payments.isEmpty() || payments.get(0).getPhuongThuc() != PhuongThucTt.CHUYEN_KHOAN) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng không dùng thanh toán VNPay");
        }
        if (payments.stream().anyMatch(p -> p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN
                || p.getTrangThai() == TrangThaiThanhToan.CAN_HOAN_TIEN
                || p.getTrangThai() == TrangThaiThanhToan.DA_HOAN_TIEN)) {
            throw new AppException(HttpStatus.CONFLICT, "Đơn hàng đã có giao dịch thanh toán thành công");
        }

        ThanhToan payment = payments.get(0);
        if (payment.getMaGiaoDich() != null
                && payment.getTrangThai() != TrangThaiThanhToan.THAT_BAI
                && payment.getTrangThai() != TrangThaiThanhToan.CHO_THANH_TOAN) {
            throw new AppException(HttpStatus.CONFLICT, "Không thể tạo giao dịch VNPay cho trạng thái thanh toán hiện tại");
        }
        if (payment.getMaGiaoDich() != null) {
            payment = new ThanhToan();
            payment.setMaThanhToan(IdGenerator.next("TT"));
            payment.setDonHang(order);
            payment.setPhuongThuc(PhuongThucTt.CHUYEN_KHOAN);
            payment.setSoTien(order.getTongThanhToan());
            payment.setNgayTao(LocalDateTime.now());
        }
        payment.setMaGiaoDich(payment.getMaThanhToan());
        payment.setTrangThai(TrangThaiThanhToan.CHO_THANH_TOAN);
        thanhToanRepository.saveAndFlush(payment);

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", order.getTongThanhToan().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getMaGiaoDich());
        params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getMaDonHang());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", normalizeIp(clientIp));
        params.put("vnp_CreateDate", now.format(VNPAY_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNPAY_DATE));

        String query = encodeParams(params);
        String payUrl = endpoint + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
        return new VnPayPaymentDto(payment.getMaGiaoDich(), payUrl);
    }

    public VnPayPaymentDto createCheckoutPayment(String sessionId, BigDecimal amount, String clientIp) {
        requireConfigured();
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", amount.movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", sessionId);
        params.put("vnp_OrderInfo", "Thanh toan PhoneNest checkout " + sessionId);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", normalizeIp(clientIp));
        params.put("vnp_CreateDate", now.format(VNPAY_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNPAY_DATE));

        String query = encodeParams(params);
        String payUrl = endpoint + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
        return new VnPayPaymentDto(sessionId, payUrl);
    }

    public PaymentStatusDto status(String maKh, String maDonHang) {
        DonHang order = donHangRepository.findById(maDonHang)
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ThanhToan payment = thanhToanRepository
                .findByDonHangMaDonHangOrderByNgayTaoDesc(order.getMaDonHang()).stream()
                .filter(p -> p.getPhuongThuc() == PhuongThucTt.CHUYEN_KHOAN)
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch VNPay"));
        return new PaymentStatusDto(payment.getTrangThai().name(), payment.getMaGiaoDich());
    }

    public VnPayIpnResponse processIpn(Map<String, String> params) {
        if (params == null || tmnCode.isBlank() || hashSecret.isBlank() || !isValidSignature(params)) {
            return response("97", "Invalid signature");
        }
        if (!tmnCode.equals(params.get("vnp_TmnCode"))) {
            return response("97", "Invalid merchant");
        }
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return response("01", "Order not found");
        }
        BigDecimal vnpAmount;
        try {
            vnpAmount = new BigDecimal(params.get("vnp_Amount"));
        } catch (RuntimeException ex) {
            return response("04", "Invalid amount");
        }
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String bankTransactionId = params.get("vnp_TransactionNo");
        boolean paymentSucceeded = "00".equals(responseCode) && "00".equals(transactionStatus);
        if (paymentSucceeded && (bankTransactionId == null || bankTransactionId.isBlank())) {
            return response("99", "Missing provider transaction number");
        }
        var sessionResult = checkoutPaymentSessionService.processCallback(
                txnRef,
                PhuongThucTt.CHUYEN_KHOAN,
                vnpAmount.movePointLeft(2),
                paymentSucceeded,
                bankTransactionId);
        if (sessionResult.isPresent()) {
            if (sessionResult.get().status() == CheckoutPaymentCallbackResult.Status.AMOUNT_MISMATCH) {
                return response("04", "Invalid amount");
            }
            return response("00", "Confirm Success");
        }

        ThanhToan paymentRef = thanhToanRepository.findByMaGiaoDich(txnRef).orElse(null);
        if (paymentRef == null || paymentRef.getPhuongThuc() != PhuongThucTt.CHUYEN_KHOAN) {
            return response("01", "Order not found");
        }
        DonHang order = donHangRepository.findByIdForUpdate(paymentRef.getDonHang().getMaDonHang())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ThanhToan payment = thanhToanRepository.findByMaGiaoDichForUpdate(txnRef)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch VNPay"));

        if (payment.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN
                || payment.getTrangThai() == TrangThaiThanhToan.CAN_HOAN_TIEN
                || payment.getTrangThai() == TrangThaiThanhToan.DA_HOAN_TIEN) {
            return response("00", "Confirm Success");
        }
        if (vnpAmount.compareTo(payment.getSoTien().movePointRight(2)) != 0
                || payment.getSoTien().compareTo(order.getTongThanhToan()) != 0) {
            return response("04", "Invalid amount");
        }

        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            payment.setMaGiaoDichNganHang(bankTransactionId);
            payment.setNgayThanhToan(LocalDateTime.now());
            List<ThanhToan> orderPayments = thanhToanRepository
                    .findByDonHangMaDonHangOrderByNgayTaoDesc(order.getMaDonHang());
            boolean anotherPaid = orderPayments.stream()
                    .anyMatch(p -> !p.getMaThanhToan().equals(payment.getMaThanhToan())
                            && p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN);
            if (order.getTrangThai() == TrangThaiDonHang.DA_HUY || anotherPaid) {
                payment.setTrangThai(TrangThaiThanhToan.CAN_HOAN_TIEN);
                thanhToanRepository.save(payment);
                thongBaoService.guiQuanTri("Giao dịch VNPay của đơn " + order.getMaDonHang()
                        + " cần hoàn tiền/đối soát (mã GD: " + txnRef + ").");
            } else {
                payment.setTrangThai(TrangThaiThanhToan.DA_THANH_TOAN);
                thanhToanRepository.save(payment);
                donHangService.xacNhanSauThanhToan(order);
                thongBaoService.gui(order.getKhachHang().getMaKh(),
                        "Thanh toán VNPay thành công cho đơn hàng " + order.getMaDonHang() + ".",
                        ThongBaoService.linkDonHang(order.getMaDonHang()));
            }
        } else if (payment.getTrangThai() == TrangThaiThanhToan.CHO_THANH_TOAN) {
            payment.setTrangThai(TrangThaiThanhToan.THAT_BAI);
            thanhToanRepository.save(payment);
        }
        return response("00", "Confirm Success");
    }

    public String processReturn(Map<String, String> params) {
        if (params == null || tmnCode.isBlank() || hashSecret.isBlank() || !isValidSignature(params)
                || !tmnCode.equals(params.get("vnp_TmnCode"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chữ ký phản hồi VNPay không hợp lệ");
        }
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef != null && txnRef.startsWith("PC") && checkoutPaymentSessionService.isSession(txnRef)) {
            return appendQuery(frontendReturnUrl, "checkoutPayment", txnRef);
        }
        ThanhToan payment = txnRef == null ? null : thanhToanRepository.findByMaGiaoDich(txnRef).orElse(null);
        if (payment == null || payment.getPhuongThuc() != PhuongThucTt.CHUYEN_KHOAN) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch VNPay");
        }
        return frontendReturnUrl + "/" + payment.getDonHang().getMaDonHang() + "?vnpayReturn=1";
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notifyAdminWhenPaidCheckoutCannotCreateOrder(CheckoutRefundRequiredEvent event) {
        if (event.phuongThuc() != PhuongThucTt.CHUYEN_KHOAN) {
            return;
        }
        thongBaoService.guiQuanTri("Checkout " + event.sessionId()
                + " đã thanh toán qua VNPay nhưng không thể tạo đơn hàng. Số tiền: " + event.amount()
                + " VND. Cần đối soát/hoàn tiền thủ công. "
                + "Mã giao dịch ngân hàng: " + event.providerTransactionId()
                + ". Nguyên nhân: " + event.reason());
        thongBaoService.gui(event.customerId(),
                "Thanh toán thành công nhưng không thể tạo đơn do thông tin hàng hóa đã thay đổi. "
                        + "Cửa hàng đang xử lý hoàn tiền cho bạn.",
                null);
    }

    private boolean isValidSignature(Map<String, String> params) {
        String signature = params.get("vnp_SecureHash");
        if (signature == null || signature.isBlank()) return false;
        Map<String, String> signedParams = new TreeMap<>(params);
        signedParams.remove("vnp_SecureHash");
        signedParams.remove("vnp_SecureHashType");
        return MessageDigest.isEqual(
                hmacSha512(encodeParams(signedParams)).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String encodeParams(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String appendQuery(String url, String name, String value) {
        return url + (url.contains("?") ? "&" : "?") + encode(name) + "=" + encode(value);
    }

    private String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo chữ ký VNPay", ex);
        }
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) return "127.0.0.1";
        String normalized = ip.trim();
        return normalized.length() > 45 ? normalized.substring(0, 45) : normalized;
    }

    private void requireConfigured() {
        if (tmnCode.isBlank() || hashSecret.isBlank()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "VNPay chưa được cấu hình trên máy chủ");
        }
    }

    private VnPayIpnResponse response(String code, String message) {
        return new VnPayIpnResponse(code, message);
    }
}
