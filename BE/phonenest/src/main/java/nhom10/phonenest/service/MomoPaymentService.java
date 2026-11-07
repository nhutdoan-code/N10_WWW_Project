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
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

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
    private final ThongBaoService thongBaoService;

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
            response = RestClient.create(endpoint).post().body(request).retrieve().body(Map.class);
        } catch (RuntimeException ex) {
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

    public PaymentStatusDto status(String maKh, String maDonHang) {
        DonHang order = donHangRepository.findById(maDonHang)
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ThanhToan payment = findPayment(order.getMaDonHang());
        return new PaymentStatusDto(payment.getTrangThai().name(), payment.getMaGiaoDich());
    }

    public void processIpn(Map<String, Object> payload) {
        if (payload == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Payload MoMo không được để trống");
        }

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
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch MoMo"));

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

        String resultCode = required(payload, "resultCode");
        String transId = required(payload, "transId");
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

    private void sendCustomerSuccessNotification(DonHang order, String amountStr) {
        executeAfterCommitOrImmediate(() -> {
            try {
                if (order.getKhachHang() != null && order.getKhachHang().getMaKh() != null) {
                    thongBaoService.gui(order.getKhachHang().getMaKh(),
                            "Thanh toán MoMo thành công cho đơn hàng " + order.getMaDonHang()
                                    + " (" + amountStr + " VND). Đơn hàng đang được chờ xác nhận.");
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
                                    + " đã hủy nhưng MoMo ghi nhận thanh toán thành công. Nhân viên sẽ liên hệ để xử lý hoàn tiền cho bạn.");
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
                                    + "). Do đơn hàng đã được thanh toán trước đó, cửa hàng sẽ liên hệ hỗ trợ hoàn tiền khoản thanh toán thừa này.");
                }
            } catch (Exception ex) {
                log.error("Không thể gửi thông báo thanh toán trùng cho đơn {}", order.getMaDonHang(), ex);
            }
        });
    }

    private void executeAfterCommitOrImmediate(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
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
