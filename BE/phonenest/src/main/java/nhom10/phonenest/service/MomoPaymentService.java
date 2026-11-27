package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.MomoPaymentDto;
import nhom10.phonenest.dto.PaymentStatusDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MomoPaymentService {
    private static final String REQUEST_TYPE = "captureWallet";

    private final DonHangRepository donHangRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final DonHangService donHangService;
    private final CheckoutPaymentSessionService checkoutPaymentSessionService;
    private final ThongBaoService thongBaoService;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    @Value("${momo.endpoint:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String endpoint;
    @Value("${momo.refund-endpoint:https://test-payment.momo.vn/v2/gateway/api/refund}")
    private String refundEndpoint;
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
    @Value("${momo.connect-timeout-ms:5000}")
    private int connectTimeoutMs;
    @Value("${momo.read-timeout-ms:15000}")
    private int readTimeoutMs;

    public MomoPaymentDto create(String maKh, String maDonHang) {
        requireConfigured();
        // Khóa bi quan DonHang trước để đồng bộ với processIpn và huy (thứ tự: DonHang -> ThanhToan)
        DonHang order = donHangRepository.findByIdForUpdate(maDonHang)
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (order.getTrangThai() == TrangThaiDonHang.DA_HUY) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã bị hủy, không thể tạo thanh toán");
        }
        if (order.getTrangThai() == TrangThaiDonHang.DA_GIAO) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã hoàn thành, không thể tạo thanh toán");
        }

        List<ThanhToan> existingPayments = thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(maDonHang);
        if (existingPayments.isEmpty()) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch thanh toán");
        }

        // Kiểm tra xem đơn hàng đã có lần thanh toán nào thành công hoặc chờ hoàn tiền chưa
        boolean alreadyPaid = existingPayments.stream()
                .anyMatch(p -> p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN);
        if (alreadyPaid) {
            throw new AppException(HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán");
        }

        boolean awaitingRefund = existingPayments.stream()
                .anyMatch(p -> p.getTrangThai() == TrangThaiThanhToan.CAN_HOAN_TIEN);
        if (awaitingRefund) {
            throw new AppException(HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán và đang chờ hoàn tiền");
        }

        boolean refunded = existingPayments.stream()
                .anyMatch(p -> p.getTrangThai() == TrangThaiThanhToan.DA_HOAN_TIEN);
        if (refunded) {
            throw new AppException(HttpStatus.CONFLICT, "Giao dịch đơn hàng đã được hoàn tiền");
        }

        ThanhToan latestPayment = existingPayments.get(0);
        if (latestPayment.getPhuongThuc() != PhuongThucTt.VI_DIEN_TU) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng không dùng MoMo");
        }

        // Nếu bản ghi thanh toán ban đầu chưa từng tạo yêu cầu tới MoMo (chưa có maGiaoDich) -> dùng luôn bản ghi đó.
        // Nếu đã có maGiaoDich từ trước (lần thử A) -> TẠO BẢN GHI MỚI cho lần thử B.
        // Tuyệt đối không ghi đè mã giao dịch cũ để bảo toàn lịch sử và đối soát IPN đến muộn!
        ThanhToan paymentToUse;
        if (latestPayment.getMaGiaoDich() == null) {
            paymentToUse = latestPayment;
            paymentToUse.setMaGiaoDich(paymentToUse.getMaThanhToan());
            paymentToUse.setTrangThai(TrangThaiThanhToan.CHO_THANH_TOAN);
        } else {
            paymentToUse = new ThanhToan();
            paymentToUse.setMaThanhToan(IdGenerator.next("TT"));
            paymentToUse.setDonHang(order);
            paymentToUse.setPhuongThuc(PhuongThucTt.VI_DIEN_TU);
            paymentToUse.setSoTien(order.getTongThanhToan());
            paymentToUse.setTrangThai(TrangThaiThanhToan.CHO_THANH_TOAN);
            paymentToUse.setNgayTao(LocalDateTime.now());
            paymentToUse.setMaGiaoDich(paymentToUse.getMaThanhToan());
        }

        String requestId = paymentToUse.getMaGiaoDich();

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
            response = postToMomo(endpoint, request);
        } catch (RestClientResponseException ex) {
            log.error("MoMo trả HTTP {} khi tạo thanh toán cho đơn {}", ex.getStatusCode(), maDonHang, ex);
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "MoMo trả lỗi HTTP " + ex.getStatusCode().value()
                            + " khi tạo thanh toán. Kiểm tra cấu hình tài khoản merchant sandbox.");
        } catch (RuntimeException ex) {
            log.error("Không thể kết nối MoMo khi tạo thanh toán cho đơn {}", maDonHang, ex);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Không thể kết nối cổng thanh toán MoMo");
        }
        if (response == null || !"0".equals(value(response, "resultCode"))) {
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "MoMo từ chối tạo thanh toán: " + value(response, "message"));
        }
        thanhToanRepository.saveAndFlush(paymentToUse);
        return new MomoPaymentDto(
                requestId,
                value(response, "requestId"),
                value(response, "payUrl"),
                value(response, "deeplink"),
                value(response, "qrCodeUrl"),
                value(response, "resultCode"),
                value(response, "message"));
    }

    public MomoPaymentDto createCheckoutPayment(String sessionId, BigDecimal amount) {
        requireConfigured();
        String amountText;
        try {
            amountText = amount.toBigIntegerExact().toString();
        } catch (ArithmeticException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán MoMo phải là số nguyên VND");
        }

        Map<String, String> request = new LinkedHashMap<>();
        request.put("partnerCode", partnerCode);
        request.put("requestType", REQUEST_TYPE);
        request.put("ipnUrl", ipnUrl);
        request.put("redirectUrl", appendQuery(redirectUrl, "checkoutPayment", sessionId));
        request.put("orderId", sessionId);
        request.put("amount", amountText);
        request.put("lang", "vi");
        request.put("requestId", sessionId);
        request.put("extraData", "");
        request.put("orderInfo", "Thanh toan PhoneNest checkout " + sessionId);
        request.put("signature", signCreate(request));

        Map<?, ?> response;
        try {
            response = postToMomo(endpoint, request);
        } catch (RestClientResponseException ex) {
            log.error("MoMo trả HTTP {} khi tạo thanh toán cho phiên {}", ex.getStatusCode(), sessionId, ex);
            throw new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "MoMo trả lỗi HTTP " + ex.getStatusCode().value()
                            + " khi tạo thanh toán. Kiểm tra cấu hình tài khoản merchant sandbox.",
                    "PAYMENT_PROVIDER_UNCERTAIN");
        } catch (RuntimeException ex) {
            log.warn("Không xác định được kết quả tạo thanh toán MoMo cho phiên {}", sessionId, ex);
            throw new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Chưa xác định được kết quả tạo thanh toán MoMo. Vui lòng thử lại bằng phiên hiện tại.",
                    "PAYMENT_PROVIDER_UNCERTAIN");
        }
        if (response == null || !"0".equals(value(response, "resultCode"))) {
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "MoMo từ chối tạo thanh toán: " + value(response, "message"));
        }
        return new MomoPaymentDto(
                sessionId,
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

    public String refund(String maDonHang, BigDecimal amount, String returnId) {
        requireConfigured();
        ThanhToan payment = thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(maDonHang).stream()
                .filter(p -> p.getPhuongThuc() == PhuongThucTt.VI_DIEN_TU
                        && p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN
                        && p.getMaGiaoDich() != null
                        && p.getMaGiaoDichMoMo() != null)
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.CONFLICT,
                        "Không tìm thấy giao dịch MoMo đã thanh toán để hoàn tiền"));
        return submitRefund(payment, amount, "RF" + returnId, "Hoan tien yeu cau " + returnId);
    }

    @Transactional(readOnly = true)
    public PaymentStatusDto refundStatus(String maDonHang) {
        return thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(maDonHang).stream()
                .filter(p -> p.getPhuongThuc() == PhuongThucTt.VI_DIEN_TU
                        && p.getTrangThai() == TrangThaiThanhToan.CAN_HOAN_TIEN)
                .findFirst()
                .map(p -> new PaymentStatusDto(p.getTrangThai().name(), p.getMaGiaoDich()))
                .orElse(new PaymentStatusDto("KHONG_CAN_HOAN", null));
    }

    public PaymentStatusDto refundPendingPayment(String maDonHang) {
        DonHang order = donHangRepository.findByIdForUpdate(maDonHang)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ThanhToan pending = thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(maDonHang).stream()
                .filter(p -> p.getPhuongThuc() == PhuongThucTt.VI_DIEN_TU
                        && p.getTrangThai() == TrangThaiThanhToan.CAN_HOAN_TIEN
                        && p.getMaGiaoDich() != null)
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.CONFLICT,
                        "Không có giao dịch MoMo nào đang cần hoàn tiền"));
        ThanhToan payment = thanhToanRepository.findByMaGiaoDichForUpdate(pending.getMaGiaoDich())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch MoMo cần hoàn"));
        if (payment.getTrangThai() != TrangThaiThanhToan.CAN_HOAN_TIEN
                || payment.getMaGiaoDichMoMo() == null) {
            throw new AppException(HttpStatus.CONFLICT, "Giao dịch MoMo không còn ở trạng thái cần hoàn");
        }
        String refundId = "RF" + payment.getMaThanhToan();
        String transactionId = submitRefund(payment, payment.getSoTien(), refundId,
                "Hoan tien don hang " + order.getMaDonHang());
        Map<String, Object> before = Map.of(
                "trangThai", payment.getTrangThai().name(),
                "maGiaoDich", payment.getMaGiaoDich());
        payment.setTrangThai(TrangThaiThanhToan.DA_HOAN_TIEN);
        thanhToanRepository.save(payment);
        nhatKyHoatDongService.ghi(HanhDongAudit.REFUND_PAYMENT, LoaiDoiTuongAudit.PAYMENT,
                payment.getMaThanhToan(), before, Map.of(
                        "trangThai", payment.getTrangThai().name(),
                        "maGiaoDich", payment.getMaGiaoDich(),
                        "maGiaoDichHoanTien", transactionId,
                        "soTienHoan", payment.getSoTien()));
        return new PaymentStatusDto(payment.getTrangThai().name(), transactionId);
    }

    private String submitRefund(ThanhToan payment, BigDecimal amount, String requestId, String description) {
        requireConfigured();
        if (amount == null || amount.signum() <= 0 || amount.compareTo(payment.getSoTien()) > 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền hoàn MoMo không hợp lệ");
        }

        String amountText;
        try {
            amountText = amount.toBigIntegerExact().toString();
        } catch (ArithmeticException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền hoàn MoMo phải là số nguyên VND");
        }
        Map<String, String> request = new LinkedHashMap<>();
        request.put("partnerCode", partnerCode);
        request.put("orderId", payment.getMaGiaoDich());
        request.put("requestId", requestId);
        request.put("amount", amountText);
        request.put("transId", payment.getMaGiaoDichMoMo());
        request.put("lang", "vi");
        request.put("description", description);
        request.put("signature", signRefund(request));

        Map<?, ?> response;
        try {
            response = postToMomo(refundEndpoint, request);
        } catch (RuntimeException ex) {
            log.error("Không thể kết nối MoMo để hoàn tiền với requestId {}", requestId, ex);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Không thể kết nối cổng hoàn tiền MoMo");
        }
        if (response == null || !"0".equals(value(response, "resultCode"))) {
            String message = value(response, "message");
            log.warn("MoMo từ chối hoàn tiền yêu cầu {}: resultCode={}, message={}",
                    requestId, value(response, "resultCode"), message);
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "MoMo từ chối hoàn tiền" + (message.isBlank() ? "" : ": " + message));
        }
        String refundTransactionId = value(response, "transId");
        return refundTransactionId.isBlank() ? requestId : refundTransactionId;
    }

    public void processIpn(Map<String, Object> payload) {
        if (payload == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Payload MoMo không được để trống");
        }
        // 2B.7E: chua cau hinh MoMo -> 503 ro rang (truoc day: khoa rong lam HMAC nem loi -> 500)
        requireConfigured();

        String orderId = required(payload, "orderId");
        String ipnPartnerCode = required(payload, "partnerCode");
        if (partnerCode != null && !partnerCode.isBlank() && !partnerCode.equals(ipnPartnerCode)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Partner code MoMo không hợp lệ");
        }

        String signature = required(payload, "signature");
        if (!constantTimeEquals(signIpn(payload), signature)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chữ ký MoMo không hợp lệ");
        }

        String amountStr = required(payload, "amount");
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
        } catch (NumberFormatException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán MoMo không hợp lệ");
        }

        // Bước 1: Tra cứu bản ghi ThanhToan trước để xác định đơn hàng tương ứng
        ThanhToan paymentRef = thanhToanRepository.findByMaGiaoDich(orderId)
                .orElse(null);

        String resultCode = required(payload, "resultCode");
        String transId = required(payload, "transId");
        var sessionResult = checkoutPaymentSessionService.processCallback(
                orderId,
                PhuongThucTt.VI_DIEN_TU,
                amount,
                "0".equals(resultCode),
                transId);
        if (sessionResult.isPresent()) {
            if (sessionResult.get().status() == CheckoutPaymentCallbackResult.Status.AMOUNT_MISMATCH) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán MoMo không hợp lệ");
            }
            return;
        }
        if (paymentRef == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch MoMo");
        }

        // Bước 2: KHÓA DonHang TRƯỚC (tuân thủ nghiêm ngặt thứ tự khóa toàn hệ thống: DonHang -> ThanhToan để triệt tiêu Deadlock)
        DonHang order = donHangRepository.findByIdForUpdate(paymentRef.getDonHang().getMaDonHang())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));

        // Bước 3: KHÓA ThanhToan SAU
        ThanhToan payment = thanhToanRepository.findByMaGiaoDichForUpdate(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch MoMo"));

        if (amount.compareTo(order.getTongThanhToan()) != 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán MoMo không hợp lệ");
        }

        // Idempotency: nếu chính giao dịch này đã ở trạng thái cuối (DA_THANH_TOAN hoặc CAN_HOAN_TIEN), bỏ qua
        if (payment.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN
                || payment.getTrangThai() == TrangThaiThanhToan.CAN_HOAN_TIEN) {
            log.info("IPN MoMo bỏ qua do giao dịch {} đã ở trạng thái {}", orderId, payment.getTrangThai());
            return;
        }

        payment.setMaGiaoDichMoMo(transId);

        if ("0".equals(resultCode)) {
            payment.setNgayThanhToan(LocalDateTime.now());

            if (order.getTrangThai() == TrangThaiDonHang.DA_HUY) {
                // Trường hợp 1: Đơn hàng đã bị hủy trước đó
                payment.setTrangThai(TrangThaiThanhToan.CAN_HOAN_TIEN);
                thanhToanRepository.save(payment);

                sendCancelledOrderRefundNotification(order, amountStr, orderId, transId);
            } else {
                // Kiểm tra xem đơn hàng này đã có giao dịch nào khác đã DA_THANH_TOAN hay chưa (Thanh toán trùng / Double Charge)
                List<ThanhToan> allPayments = thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(order.getMaDonHang());
                boolean hasOtherPaid = allPayments.stream()
                        .anyMatch(p -> !p.getMaThanhToan().equals(payment.getMaThanhToan())
                                && p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN);

                if (hasOtherPaid) {
                    // Trường hợp 2: Đơn hàng đã được thanh toán bởi một lần thử khác! Đây là lần thanh toán trùng tiền (Double Charge)
                    payment.setTrangThai(TrangThaiThanhToan.CAN_HOAN_TIEN);
                    thanhToanRepository.save(payment);

                    sendDuplicatePaymentRefundNotification(order, amountStr, orderId, transId);
                } else {
                    // Trường hợp 3: Thanh toán thành công hợp lệ đầu tiên
                    payment.setTrangThai(TrangThaiThanhToan.DA_THANH_TOAN);
                    thanhToanRepository.save(payment);
                    donHangService.xacNhanSauThanhToan(order);

                    sendCustomerSuccessNotification(order, amountStr);
                }
            }

        } else {
            // Thanh toán thất bại từ MoMo
            if (payment.getTrangThai() == TrangThaiThanhToan.CHO_THANH_TOAN) {
                payment.setTrangThai(TrangThaiThanhToan.THAT_BAI);
                thanhToanRepository.save(payment);
                log.warn("Thanh toán MoMo thất bại cho mã GD {}: resultCode={}, message={}, transId={}",
                        orderId, resultCode, payload.get("message"), transId);
            }
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refundCheckoutPaymentIfOrderCannotBeCreated(CheckoutRefundRequiredEvent event) {
        if (event.phuongThuc() != PhuongThucTt.VI_DIEN_TU) {
            return;
        }
        ThanhToan payment = new ThanhToan();
        payment.setMaGiaoDich(event.sessionId());
        payment.setMaGiaoDichMoMo(event.providerTransactionId());
        payment.setSoTien(event.amount());
        try {
            String refundId = submitRefund(
                    payment, event.amount(), "RF" + event.sessionId(), "Hoan tien checkout " + event.sessionId());
            checkoutPaymentSessionService.markRefunded(event.sessionId(), refundId);
            thongBaoService.gui(event.customerId(),
                    "Không thể tạo đơn hàng sau khi thanh toán. MoMo đã hoàn tiền thành công.",
                    null);
        } catch (AppException ex) {
            log.error("Không thể tự động hoàn giao dịch MoMo cho checkout {}", event.sessionId(), ex);
            thongBaoService.guiQuanTri("Checkout " + event.sessionId() + " đã thu tiền nhưng chưa tạo được đơn hàng. "
                    + "Cần hoàn tiền thủ công; mã giao dịch MoMo: " + event.providerTransactionId()
                    + ". Nguyên nhân: " + event.reason());
            thongBaoService.gui(event.customerId(),
                    "Thanh toán thành công nhưng không thể tạo đơn do thông tin hàng hóa đã thay đổi. "
                            + "Cửa hàng đang xử lý hoàn tiền cho bạn.",
                    null);
        }
    }

    private void sendCustomerSuccessNotification(DonHang order, String amountStr) {
        executeAfterCommitOrImmediate(() -> {
            try {
                if (order.getKhachHang() != null && order.getKhachHang().getMaKh() != null) {
                    thongBaoService.gui(order.getKhachHang().getMaKh(),
                            "Thanh toán MoMo thành công cho đơn hàng " + order.getMaDonHang()
                                    + " (" + amountStr + " VND). Đơn hàng đang được chờ xác nhận.",
                            ThongBaoService.linkDonHang(order.getMaDonHang()));
                }
            } catch (Exception ex) {
                log.error("Không thể gửi thông báo sau khi xử lý IPN thành công {}", order.getMaDonHang(), ex);
            }
        });
    }

    private void sendCancelledOrderRefundNotification(DonHang order, String amountStr, String orderId, String transId) {
        executeAfterCommitOrImmediate(() -> {
            try {
                thongBaoService.guiQuanTri("Đơn hàng " + order.getMaDonHang()
                        + " đã bị hủy nhưng nhận được thanh toán MoMo thành công (" + amountStr + " VND, mã GD: " + orderId
                        + ", transId: " + transId + "). Cần xử lý hoàn tiền / đối soát cho khách hàng.");
                if (order.getKhachHang() != null && order.getKhachHang().getMaKh() != null) {
                    thongBaoService.gui(order.getKhachHang().getMaKh(),
                            "Đơn hàng " + order.getMaDonHang()
                                    + " đã hủy nhưng MoMo ghi nhận thanh toán thành công. Nhân viên sẽ liên hệ để xử lý hoàn tiền cho bạn.",
                            ThongBaoService.linkDonHang(order.getMaDonHang()));
                }
            } catch (Exception ex) {
                log.error("Không thể gửi thông báo cho đơn đã hủy nhận tiền {}", order.getMaDonHang(), ex);
            }
        });
    }

    private void sendDuplicatePaymentRefundNotification(DonHang order, String amountStr, String orderId, String transId) {
        executeAfterCommitOrImmediate(() -> {
            try {
                thongBaoService.guiQuanTri("CẢNH BÁO THANH TOÁN TRÙNG: Đơn hàng " + order.getMaDonHang()
                        + " nhận được thanh toán MoMo lần 2 (" + amountStr + " VND, mã GD: " + orderId
                        + ", transId: " + transId + "). Giao dịch đã được đánh dấu CẦN HOÀN TIỀN để đối soát và hoàn tiền thừa cho khách hàng.");
                if (order.getKhachHang() != null && order.getKhachHang().getMaKh() != null) {
                    thongBaoService.gui(order.getKhachHang().getMaKh(),
                            "Đơn hàng " + order.getMaDonHang()
                                    + " ghi nhận thêm một giao dịch thanh toán MoMo thành công (" + amountStr + " VND, mã: " + orderId
                                    + "). Do đơn hàng đã được thanh toán trước đó, cửa hàng sẽ liên hệ hỗ trợ hoàn tiền khoản thanh toán thừa này.",
                            ThongBaoService.linkDonHang(order.getMaDonHang()));
                }
            } catch (Exception ex) {
                log.error("Không thể gửi thông báo thanh toán trùng cho đơn {}", order.getMaDonHang(), ex);
            }
        });
    }

    /**
     * 2B.7E: ghi thong bao TRONG cung giao dich xu ly IPN.
     * Loi da phat hien: truoc day thong bao duoc ghi trong afterCommit(); khi do repository.save() tham gia
     * giao dich DA KET THUC nen ban ghi KHONG BAO GIO duoc commit -> moi thong bao MoMo (ke ca canh bao
     * "can hoan tien" gui Admin) bi mat am tham. Ghi trong giao dich: commit/rollback cung trang thai thanh toan.
     */
    private void executeAfterCommitOrImmediate(Runnable task) {
        task.run();
    }

    private Map<?, ?> postToMomo(String uri, Map<String, String> request) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build()
                .post()
                .uri(uri)
                .body(request)
                .retrieve()
                .body(Map.class);
    }

    private ThanhToan findPayment(String maDonHang) {
        List<ThanhToan> payments = thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(maDonHang);
        if (payments.isEmpty()) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch thanh toán");
        }
        // Ưu tiên trả về giao dịch đã thanh toán thành công nếu có
        return payments.stream()
                .filter(p -> p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN)
                .findFirst()
                .or(() -> payments.stream()
                        .filter(p -> p.getTrangThai() == TrangThaiThanhToan.CAN_HOAN_TIEN)
                        .findFirst())
                .orElse(payments.get(0));
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

    private String appendQuery(String url, String name, String value) {
        return url + (url.contains("?") ? "&" : "?")
                + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "=" + java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String signRefund(Map<String, String> request) {
        return hmac("accessKey=" + accessKey + "&amount=" + request.get("amount")
                + "&description=" + request.get("description")
                + "&orderId=" + request.get("orderId")
                + "&partnerCode=" + partnerCode
                + "&requestId=" + request.get("requestId")
                + "&transId=" + request.get("transId"));
    }

    private String signIpn(Map<String, Object> payload) {
        return hmac("accessKey=" + accessKey + "&amount=" + required(payload, "amount")
                + "&extraData=" + value(payload, "extraData") + "&message=" + required(payload, "message")
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
