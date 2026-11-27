package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.PaymentStatusDto;
import nhom10.phonenest.dto.VnPayIpnResponse;
import nhom10.phonenest.dto.VnPayPaymentDto;
import nhom10.phonenest.dto.VnPayRefundStatusDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.ChiTietDoiTra;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.entity.YeuCauDoiTra;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VnPayPaymentService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DonHangRepository donHangRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final DonHangService donHangService;
    private final ThongBaoService thongBaoService;
    private final CheckoutPaymentSessionService checkoutPaymentSessionService;
    private final NhatKyHoatDongService nhatKyHoatDongService;
    private final YeuCauDoiTraRepository yeuCauDoiTraRepository;

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
    @Value("${vnpay.refund-endpoint:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}")
    private String refundEndpoint;
    @Value("${vnpay.server-ip:127.0.0.1}")
    private String serverIp;
    @Value("${vnpay.connect-timeout-ms:5000}")
    private int connectTimeoutMs;
    @Value("${vnpay.read-timeout-ms:15000}")
    private int readTimeoutMs;

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
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        payment.setNgayTaoGiaoDichVnPay(now);
        payment.setMaGiaoDich(payment.getMaThanhToan());
        payment.setTrangThai(TrangThaiThanhToan.CHO_THANH_TOAN);
        thanhToanRepository.saveAndFlush(payment);

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
        return createCheckoutPayment(sessionId, amount, clientIp, LocalDateTime.now(VIETNAM_ZONE));
    }

    public VnPayPaymentDto createCheckoutPayment(
            String sessionId, BigDecimal amount, String clientIp, LocalDateTime createDate) {
        requireConfigured();
        LocalDateTime now = createDate == null ? LocalDateTime.now(VIETNAM_ZONE) : createDate;
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

    @Transactional(readOnly = true)
    public VnPayRefundStatusDto refundStatus(String maDonHang) {
        ThanhToan payment = findVnPayPayment(maDonHang);
        return refundStatus(payment, "");
    }

    public VnPayRefundStatusDto refundPendingPayment(String maDonHang, String adminId) {
        DonHang order = donHangRepository.findByIdForUpdate(maDonHang)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ThanhToan payment = findVnPayPayment(maDonHang);
        if (payment.getTrangThai() == TrangThaiThanhToan.DA_HOAN_TIEN) {
            return refundStatus(payment, "Giao dịch đã được hoàn tiền.");
        }
        boolean retryExistingRequest = payment.getTrangThai() == TrangThaiThanhToan.DANG_HOAN_TIEN
                && payment.getMaYeuCauHoanVnPay() != null
                && payment.getNgayYeuCauHoanVnPay() != null;
        if (payment.getTrangThai() != TrangThaiThanhToan.CAN_HOAN_TIEN && !retryExistingRequest) {
            throw new AppException(HttpStatus.CONFLICT, "Giao dịch VNPay không ở trạng thái cần hoàn tiền");
        }
        requireRefundConfigured();
        if (payment.getMaGiaoDich() == null || payment.getMaGiaoDichNganHang() == null
                || (payment.getNgayTaoGiaoDichVnPay() == null && payment.getNgayTao() == null)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Thiếu mã giao dịch hoặc thời gian thanh toán VNPay cần để hoàn tiền");
        }
        if (adminId == null || adminId.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không xác định được Admin yêu cầu hoàn tiền");
        }
        if (payment.getSoTien().movePointRight(2).toBigIntegerExact().toString().length() > 12) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền vượt giới hạn API hoàn tiền VNPay");
        }

        String requestId;
        if (retryExistingRequest) {
            requestId = payment.getMaYeuCauHoanVnPay();
        } else {
            String previousStatus = payment.getTrangThai().name();
            requestId = createRefundRequestId();
            payment.setMaYeuCauHoanVnPay(requestId);
            payment.setMaGiaoDichHoanVnPay(null);
            payment.setNgayYeuCauHoanVnPay(LocalDateTime.now(VIETNAM_ZONE));
            payment.setNguoiYeuCauHoanVnPay(adminId.length() > 245 ? adminId.substring(0, 245) : adminId);
            payment.setTrangThai(TrangThaiThanhToan.DANG_HOAN_TIEN);
            thanhToanRepository.saveAndFlush(payment);
            nhatKyHoatDongService.ghi(
                    HanhDongAudit.REFUND_PAYMENT,
                    LoaiDoiTuongAudit.PAYMENT,
                    payment.getMaThanhToan(),
                    Map.of("trangThai", previousStatus),
                    Map.of(
                            "trangThai", payment.getTrangThai().name(),
                            "maGiaoDich", payment.getMaGiaoDich(),
                            "maYeuCauHoanVnPay", requestId,
                            "soTienHoan", payment.getSoTien()));
        }

        Map<String, String> request = createRefundRequest(
                payment,
                requestId,
                payment.getSoTien(),
                "02",
                payment.getNguoiYeuCauHoanVnPay(),
                payment.getNgayYeuCauHoanVnPay(),
                "Hoan tien don hang " + order.getMaDonHang());
        try {
            Map<?, ?> response = postRefundRequest(request);
            return applyRefundResponse(payment, requestId, response);
        } catch (RestClientResponseException ex) {
            log.error("VNPay trả HTTP {} khi hoàn giao dịch {} (requestId={})",
                    ex.getStatusCode(), payment.getMaGiaoDich(), requestId, ex);
            if (ex.getStatusCode().is4xxClientError()) {
                payment.setTrangThai(TrangThaiThanhToan.CAN_HOAN_TIEN);
                thanhToanRepository.save(payment);
                auditRefundStatus(payment, TrangThaiThanhToan.DANG_HOAN_TIEN);
                return refundStatus(payment, "VNPay từ chối yêu cầu hoàn với HTTP "
                        + ex.getStatusCode().value() + ". Kiểm tra cấu hình merchant/API.");
            }
            return refundStatus(payment, "Chưa xác định kết quả yêu cầu hoàn do VNPay trả HTTP "
                    + ex.getStatusCode().value() + ". Giữ trạng thái đang xử lý để tránh gửi trùng.");
        } catch (RuntimeException ex) {
            log.error("Không xác định được kết quả hoàn VNPay cho giao dịch {} (requestId={})",
                    payment.getMaGiaoDich(), requestId, ex);
            return refundStatus(payment,
                    "Chưa nhận được phản hồi VNPay. Giữ yêu cầu đang xử lý; cần đối soát trước khi thử lại.");
        }
    }

    public ReturnRefundResult refundReturn(
            YeuCauDoiTra returnRequest, BigDecimal amount, String adminId) {
        requireRefundConfigured();
        if (returnRequest == null || returnRequest.getDonHang() == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đổi trả");
        }
        if (adminId == null || adminId.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không xác định được Admin yêu cầu hoàn tiền");
        }
        ThanhToan candidate = thanhToanRepository
                .findByDonHangMaDonHangOrderByNgayTaoDesc(returnRequest.getDonHang().getMaDonHang()).stream()
                .filter(p -> p.getPhuongThuc() == PhuongThucTt.CHUYEN_KHOAN
                        && p.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN
                        && p.getMaGiaoDich() != null)
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.CONFLICT,
                        "Không tìm thấy giao dịch VNPay đã thanh toán cho đơn hàng"));
        ThanhToan payment = thanhToanRepository.findByMaGiaoDichForUpdate(candidate.getMaGiaoDich())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch VNPay cần hoàn"));
        if (payment.getTrangThai() != TrangThaiThanhToan.DA_THANH_TOAN
                || payment.getMaGiaoDichNganHang() == null
                || (payment.getNgayTaoGiaoDichVnPay() == null && payment.getNgayTao() == null)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Giao dịch VNPay không còn đủ thông tin hoặc không ở trạng thái đã thanh toán");
        }

        String amountText;
        try {
            if (amount == null || amount.signum() <= 0) throw new ArithmeticException();
            amountText = amount.movePointRight(2).toBigIntegerExact().toString();
        } catch (ArithmeticException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền hoàn VNPay phải là số nguyên VND lớn hơn 0");
        }
        if (amountText.length() > 12) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền vượt giới hạn API hoàn tiền VNPay");
        }

        List<YeuCauDoiTra> returns = yeuCauDoiTraRepository
                .findByDonHangMaDonHangOrderByNgayTaoDesc(returnRequest.getDonHang().getMaDonHang());
        List<YeuCauDoiTra> priorRefunds = returns.stream()
                .filter(other -> !other.getMaYeuCau().equals(returnRequest.getMaYeuCau()))
                .filter(other -> other.getNgayHoanTien() != null
                        || "DANG_HOAN_TIEN".equals(other.getTrangThaiHoanVnPay()))
                .toList();
        boolean missingPriorAmount = priorRefunds.stream()
                .flatMap(other -> other.getChiTiet().stream())
                .anyMatch(item -> item.getSoTienHoan() == null);
        if (missingPriorAmount) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Cần đối soát số tiền của giao dịch hoàn trước đó trước khi gửi yêu cầu VNPay mới");
        }
        BigDecimal reserved = priorRefunds.stream()
                .flatMap(other -> other.getChiTiet().stream())
                .map(ChiTietDoiTra::getSoTienHoan)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reserved.add(amount).compareTo(payment.getSoTien()) > 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Tổng tiền hoàn VNPay vượt số tiền giao dịch sau khi tính các yêu cầu hoàn khác");
        }
        boolean anotherRefundPending = returns.stream()
                .filter(other -> !other.getMaYeuCau().equals(returnRequest.getMaYeuCau()))
                .anyMatch(other -> "DANG_HOAN_TIEN".equals(other.getTrangThaiHoanVnPay()));
        if (anotherRefundPending) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Đang có một yêu cầu hoàn VNPay khác cần được đối soát trước");
        }

        boolean retry = "DANG_HOAN_TIEN".equals(returnRequest.getTrangThaiHoanVnPay())
                && returnRequest.getMaYeuCauHoanVnPay() != null
                && returnRequest.getNgayYeuCauHoanVnPay() != null;
        if (!retry) {
            returnRequest.setMaYeuCauHoanVnPay(createRefundRequestId());
            returnRequest.setNgayYeuCauHoanVnPay(LocalDateTime.now(VIETNAM_ZONE));
            returnRequest.setNguoiYeuCauHoanVnPay(
                    adminId.length() > 245 ? adminId.substring(0, 245) : adminId);
        }
        String requestId = returnRequest.getMaYeuCauHoanVnPay();
        String createBy = returnRequest.getNguoiYeuCauHoanVnPay();
        LocalDateTime requestDate = returnRequest.getNgayYeuCauHoanVnPay();
        String orderInfo = "Hoan tien doi tra " + returnRequest.getMaYeuCau();
        String transactionType = amount.compareTo(payment.getSoTien()) == 0 ? "02" : "03";
        returnRequest.setHinhThucHoanTien("VNPAY");
        returnRequest.setTrangThaiHoanVnPay("DANG_HOAN_TIEN");
        returnRequest.setThongBaoHoanVnPay("Đang gửi yêu cầu hoàn tiền qua VNPay.");
        yeuCauDoiTraRepository.saveAndFlush(returnRequest);

        Map<String, String> request = createRefundRequest(
                payment, requestId, amount, transactionType, createBy, requestDate, orderInfo);
        try {
            Map<?, ?> response = postRefundRequest(request);
            if (!isValidRefundResponse(response)
                    || value(response, "vnp_ResponseId").isBlank()
                    || !tmnCode.equals(value(response, "vnp_TmnCode"))
                    || !payment.getMaGiaoDich().equals(value(response, "vnp_TxnRef"))
                    || !transactionType.equals(value(response, "vnp_TransactionType"))
                    || !amountText.equals(value(response, "vnp_Amount"))
                    || !orderInfo.equals(value(response, "vnp_OrderInfo"))) {
                return new ReturnRefundResult("DANG_HOAN_TIEN", null,
                        "Phản hồi VNPay không hợp lệ hoặc không khớp giao dịch; cần đối soát trước khi thử lại.");
            }
            String responseCode = value(response, "vnp_ResponseCode");
            String transactionStatus = value(response, "vnp_TransactionStatus");
            String refundTransactionId = value(response, "vnp_TransactionNo");
            String message = value(response, "vnp_Message");
            if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
                if (refundTransactionId.isBlank()) {
                    returnRequest.setTrangThaiHoanVnPay("DANG_HOAN_TIEN");
                    returnRequest.setThongBaoHoanVnPay(
                            "VNPay báo yêu cầu thành công nhưng thiếu mã giao dịch hoàn; cần đối soát trước khi thử lại.");
                    yeuCauDoiTraRepository.save(returnRequest);
                    return new ReturnRefundResult("DANG_HOAN_TIEN", null, returnRequest.getThongBaoHoanVnPay());
                }
                returnRequest.setTrangThaiHoanVnPay("DA_HOAN_TIEN");
                returnRequest.setMaGiaoDichHoanTien(refundTransactionId);
                returnRequest.setNgayHoanTien(LocalDateTime.now(VIETNAM_ZONE));
                returnRequest.setThongBaoHoanVnPay(message);
                BigDecimal totalRefunded = reserved.add(amount);
                if (totalRefunded.compareTo(payment.getSoTien()) == 0) {
                    payment.setTrangThai(TrangThaiThanhToan.DA_HOAN_TIEN);
                    thanhToanRepository.save(payment);
                }
                yeuCauDoiTraRepository.save(returnRequest);
                return new ReturnRefundResult("DA_HOAN_TIEN", refundTransactionId, message);
            }
            if ("94".equals(responseCode) || ("00".equals(responseCode)
                    && ("05".equals(transactionStatus) || "06".equals(transactionStatus)))) {
                returnRequest.setTrangThaiHoanVnPay("DANG_HOAN_TIEN");
                returnRequest.setThongBaoHoanVnPay(message.isBlank()
                        ? "VNPay đang xử lý yêu cầu hoàn tiền." : message);
                yeuCauDoiTraRepository.save(returnRequest);
                return new ReturnRefundResult("DANG_HOAN_TIEN", refundTransactionId, returnRequest.getThongBaoHoanVnPay());
            }
            returnRequest.setTrangThaiHoanVnPay("CAN_HOAN_TIEN");
            returnRequest.setThongBaoHoanVnPay("VNPay chưa hoàn tiền (mã "
                    + (responseCode.isBlank() ? "không rõ" : responseCode) + "): "
                    + (message.isBlank() ? "yêu cầu bị từ chối hoặc chưa thành công." : message));
            yeuCauDoiTraRepository.save(returnRequest);
            return new ReturnRefundResult("CAN_HOAN_TIEN", refundTransactionId, returnRequest.getThongBaoHoanVnPay());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                returnRequest.setTrangThaiHoanVnPay("CAN_HOAN_TIEN");
                returnRequest.setThongBaoHoanVnPay("VNPay từ chối yêu cầu hoàn với HTTP "
                        + ex.getStatusCode().value() + ". Kiểm tra cấu hình merchant/API.");
                yeuCauDoiTraRepository.save(returnRequest);
                return new ReturnRefundResult("CAN_HOAN_TIEN", null, returnRequest.getThongBaoHoanVnPay());
            }
            return new ReturnRefundResult("DANG_HOAN_TIEN", null,
                    "Chưa xác định kết quả từ VNPay. Giữ nguyên mã yêu cầu và thử kiểm tra lại.");
        } catch (RuntimeException ex) {
            log.error("Không xác định được kết quả hoàn VNPay cho yêu cầu đổi trả {} (requestId={})",
                    returnRequest.getMaYeuCau(), requestId, ex);
            return new ReturnRefundResult("DANG_HOAN_TIEN", null,
                    "Chưa nhận được phản hồi VNPay. Giữ yêu cầu đang xử lý; cần đối soát trước khi thử lại.");
        }
    }

    public record ReturnRefundResult(String status, String transactionId, String message) {}

    private VnPayRefundStatusDto applyRefundResponse(
            ThanhToan payment, String requestId, Map<?, ?> response) {
        if (!isValidRefundResponse(response)
                || value(response, "vnp_ResponseId").isBlank()
                || !tmnCode.equals(value(response, "vnp_TmnCode"))
                || !payment.getMaGiaoDich().equals(value(response, "vnp_TxnRef"))
                || !"02".equals(value(response, "vnp_TransactionType"))
                || !payment.getSoTien().movePointRight(2).toBigIntegerExact()
                        .toString().equals(value(response, "vnp_Amount"))
                || value(response, "vnp_TransactionNo").isBlank()) {
            log.error("VNPay refund response invalid or mismatched for requestId {}", requestId);
            return refundStatus(payment,
                    "Phản hồi VNPay không hợp lệ hoặc không khớp giao dịch. Yêu cầu giữ ở trạng thái đối soát.");
        }

        String responseCode = value(response, "vnp_ResponseCode");
        String transactionStatus = value(response, "vnp_TransactionStatus");
        String refundTransactionId = value(response, "vnp_TransactionNo");
        String message = value(response, "vnp_Message");
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            payment.setTrangThai(TrangThaiThanhToan.DA_HOAN_TIEN);
            payment.setMaGiaoDichHoanVnPay(refundTransactionId);
            thanhToanRepository.save(payment);
            auditRefundStatus(payment, TrangThaiThanhToan.DANG_HOAN_TIEN);
            return refundStatus(payment, message);
        }
        if ("94".equals(responseCode) || ("00".equals(responseCode)
                && ("05".equals(transactionStatus) || "06".equals(transactionStatus)))) {
            payment.setTrangThai(TrangThaiThanhToan.DANG_HOAN_TIEN);
            payment.setMaGiaoDichHoanVnPay(refundTransactionId);
            thanhToanRepository.save(payment);
            return refundStatus(payment, message.isBlank()
                    ? "VNPay đang xử lý yêu cầu hoàn tiền." : message);
        }

        payment.setTrangThai(TrangThaiThanhToan.CAN_HOAN_TIEN);
        payment.setMaGiaoDichHoanVnPay(refundTransactionId.isBlank() ? null : refundTransactionId);
        thanhToanRepository.save(payment);
        auditRefundStatus(payment, TrangThaiThanhToan.DANG_HOAN_TIEN);
        return refundStatus(payment, "VNPay chưa hoàn tiền (mã "
                + (responseCode.isBlank() ? "không rõ" : responseCode) + "): "
                + (message.isBlank() ? "yêu cầu bị từ chối hoặc chưa thành công." : message));
    }

    private Map<String, String> createRefundRequest(
            ThanhToan payment,
            String requestId,
            BigDecimal refundAmount,
            String transactionType,
            String createBy,
            LocalDateTime requestDate,
            String orderInfo) {
        String amount = refundAmount.movePointRight(2).toBigIntegerExact().toString();
        Map<String, String> request = new LinkedHashMap<>();
        request.put("vnp_RequestId", requestId);
        request.put("vnp_Version", "2.1.0");
        request.put("vnp_Command", "refund");
        request.put("vnp_TmnCode", tmnCode);
        request.put("vnp_TransactionType", transactionType);
        request.put("vnp_TxnRef", payment.getMaGiaoDich());
        request.put("vnp_Amount", amount);
        request.put("vnp_OrderInfo", orderInfo);
        request.put("vnp_TransactionNo", payment.getMaGiaoDichNganHang());
        LocalDateTime transactionDate = payment.getNgayTaoGiaoDichVnPay() == null
                ? payment.getNgayTao() : payment.getNgayTaoGiaoDichVnPay();
        request.put("vnp_TransactionDate", transactionDate.format(VNPAY_DATE));
        request.put("vnp_CreateBy", createBy);
        request.put("vnp_CreateDate", requestDate.format(VNPAY_DATE));
        request.put("vnp_IpAddr", normalizeIp(serverIp));
        request.put("vnp_SecureHash", hmacSha512(String.join("|",
                requestId,
                "2.1.0",
                "refund",
                tmnCode,
                transactionType,
                payment.getMaGiaoDich(),
                amount,
                payment.getMaGiaoDichNganHang(),
                transactionDate.format(VNPAY_DATE),
                request.get("vnp_CreateBy"),
                request.get("vnp_CreateDate"),
                request.get("vnp_IpAddr"),
                request.get("vnp_OrderInfo"))));
        return request;
    }

    private Map<?, ?> postRefundRequest(Map<String, String> request) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        Map<?, ?> response = RestClient.builder()
                .requestFactory(requestFactory)
                .build()
                .post()
                .uri(refundEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new IllegalStateException("VNPay refund endpoint returned an empty response");
        }
        return response;
    }

    private boolean isValidRefundResponse(Map<?, ?> response) {
        String signature = value(response, "vnp_SecureHash");
        if (signature.isBlank() || !"refund".equals(value(response, "vnp_Command"))) return false;
        String responseData = String.join("|",
                value(response, "vnp_ResponseId"),
                value(response, "vnp_Command"),
                value(response, "vnp_ResponseCode"),
                value(response, "vnp_Message"),
                value(response, "vnp_TmnCode"),
                value(response, "vnp_TxnRef"),
                value(response, "vnp_Amount"),
                value(response, "vnp_BankCode"),
                value(response, "vnp_PayDate"),
                value(response, "vnp_TransactionNo"),
                value(response, "vnp_TransactionType"),
                value(response, "vnp_TransactionStatus"),
                value(response, "vnp_OrderInfo"));
        if (response.containsKey("vnp_PromotionCode") || response.containsKey("vnp_PromotionAmount")) {
            responseData += "|" + value(response, "vnp_PromotionCode")
                    + "|" + value(response, "vnp_PromotionAmount");
        }
        return MessageDigest.isEqual(
                hmacSha512(responseData).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    private ThanhToan findVnPayPayment(String maDonHang) {
        return thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(maDonHang).stream()
                .filter(p -> p.getPhuongThuc() == PhuongThucTt.CHUYEN_KHOAN)
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch VNPay của đơn hàng"));
    }

    private VnPayRefundStatusDto refundStatus(ThanhToan payment, String message) {
        return new VnPayRefundStatusDto(
                payment.getTrangThai().name(),
                payment.getMaGiaoDich(),
                payment.getMaYeuCauHoanVnPay(),
                payment.getMaGiaoDichHoanVnPay(),
                message);
    }

    private void auditRefundStatus(ThanhToan payment, TrangThaiThanhToan previousStatus) {
        nhatKyHoatDongService.ghi(
                HanhDongAudit.REFUND_PAYMENT,
                LoaiDoiTuongAudit.PAYMENT,
                payment.getMaThanhToan(),
                Map.of("trangThai", previousStatus.name()),
                Map.of(
                        "trangThai", payment.getTrangThai().name(),
                        "maGiaoDich", payment.getMaGiaoDich(),
                        "maYeuCauHoanVnPay", payment.getMaYeuCauHoanVnPay(),
                        "maGiaoDichHoanVnPay", payment.getMaGiaoDichHoanVnPay() == null
                                ? "" : payment.getMaGiaoDichHoanVnPay()));
    }

    private String createRefundRequestId() {
        byte[] random = new byte[15];
        SECURE_RANDOM.nextBytes(random);
        StringBuilder id = new StringBuilder("RF");
        for (byte value : random) id.append(String.format("%02x", value & 0xff));
        return id.toString();
    }

    private String value(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null ? "" : value.toString();
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

    private void requireRefundConfigured() {
        requireConfigured();
        if (refundEndpoint == null || refundEndpoint.isBlank()
                || serverIp == null || serverIp.isBlank()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "API hoàn tiền VNPay chưa được cấu hình trên máy chủ");
        }
    }

    private VnPayIpnResponse response(String code, String message) {
        return new VnPayIpnResponse(code, message);
    }
}
