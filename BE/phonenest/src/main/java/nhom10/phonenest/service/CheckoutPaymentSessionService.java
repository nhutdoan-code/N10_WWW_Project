package nhom10.phonenest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.CheckoutDto;
import nhom10.phonenest.dto.CheckoutPaymentPayload;
import nhom10.phonenest.dto.CheckoutPaymentStatusDto;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.PhienThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.TrangThaiPhienThanhToan;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.PhienThanhToanRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckoutPaymentSessionService {

    private final PhienThanhToanRepository sessionRepository;
    private final KhachHangRepository khachHangRepository;
    private final ThanhToanService thanhToanService;
    private final ThanhToanDaChonService thanhToanDaChonService;
    private final CheckoutIdempotencyService checkoutIdempotencyService;
    private final CheckoutPaymentOrderFinalizer orderFinalizer;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    public PhienThanhToan prepare(
            String customerId,
            String idempotencyKey,
            DonHangDto.Checkout checkout,
            List<String> selectedVariants) {
        checkoutIdempotencyService.validateIdempotencyKey(idempotencyKey);
        if (checkout.phuongThucTt() != PhuongThucTt.VI_DIEN_TU
                && checkout.phuongThucTt() != PhuongThucTt.CHUYEN_KHOAN) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Phiên thanh toán chỉ áp dụng cho MoMo hoặc VNPay");
        }

        CheckoutDto preview = selectedVariants == null
                ? thanhToanService.xemTruoc(
                        customerId, checkout.maDiaChi(), checkout.maSanPham(), checkout.soLuong(), checkout.phuongThucTt())
                : thanhToanDaChonService.xemTruoc(
                        customerId, checkout.maDiaChi(), selectedVariants, checkout.phuongThucTt());
        validateVoucher(checkout.maKm(), preview, "DON_HANG");
        validateVoucher(checkout.maKmVanChuyen(), preview, "VAN_CHUYEN");

        List<CheckoutPaymentPayload.CheckoutLine> lines = preview.items().stream()
                .map(item -> new CheckoutPaymentPayload.CheckoutLine(item.maSanPham(), item.soLuong()))
                .sorted(java.util.Comparator.comparing(CheckoutPaymentPayload.CheckoutLine::maPhienBan))
                .toList();
        if (lines.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không có sản phẩm để thanh toán");
        }
        CheckoutPaymentPayload payload = new CheckoutPaymentPayload(checkout, lines);
        String checkoutJson = serialize(payload);
        String requestHash = sha256(checkoutJson);
        String normalizedKey = idempotencyKey.trim();

        Optional<PhienThanhToan> existing =
                sessionRepository.findByKhachHangMaKhAndIdempotencyKey(customerId, normalizedKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash);
        }

        BigDecimal orderDiscount = selectedVoucherDiscount(checkout.maKm(), preview, "DON_HANG");
        BigDecimal shippingDiscount = selectedVoucherDiscount(checkout.maKmVanChuyen(), preview, "VAN_CHUYEN");
        BigDecimal amount = preview.tamTinh()
                .subtract(orderDiscount)
                .add(preview.phiVanChuyen())
                .subtract(shippingDiscount)
                .max(BigDecimal.ZERO);

        KhachHang customer = khachHangRepository.findById(customerId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"));
        LocalDateTime now = LocalDateTime.now();
        PhienThanhToan session = new PhienThanhToan();
        session.setMaPhienThanhToan(IdGenerator.next("PC"));
        session.setKhachHang(customer);
        session.setIdempotencyKey(normalizedKey);
        session.setRequestHash(requestHash);
        session.setPhuongThuc(checkout.phuongThucTt());
        session.setSoTien(amount);
        session.setDuLieuCheckout(checkoutJson);
        session.setTrangThai(TrangThaiPhienThanhToan.CHO_THANH_TOAN);
        session.setNgayTao(now);
        session.setNgayCapNhat(now);
        try {
            return sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            return sessionRepository.findByKhachHangMaKhAndIdempotencyKey(customerId, normalizedKey)
                    .map(found -> resolveExisting(found, requestHash))
                    .orElseThrow(() -> e);
        }
    }

    private PhienThanhToan resolveExisting(PhienThanhToan session, String requestHash) {
        if (!session.getRequestHash().equals(requestHash)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Idempotency-Key đã được sử dụng với thông tin thanh toán khác",
                    CheckoutIdempotencyService.CODE_PAYLOAD_MISMATCH);
        }
        if (session.getTrangThai() != TrangThaiPhienThanhToan.CHO_THANH_TOAN
                && session.getTrangThai() != TrangThaiPhienThanhToan.DA_TAO_DON) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Phiên thanh toán đã kết thúc. Vui lòng bắt đầu thanh toán lại.",
                    "PAYMENT_SESSION_TERMINAL");
        }
        return session;
    }

    private void validateVoucher(String code, CheckoutDto preview, String expectedType) {
        if (code == null || code.isBlank()) {
            return;
        }
        CheckoutDto.Voucher voucher = preview.vouchers().stream()
                .filter(item -> item.maKm().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "Mã khuyến mãi " + code + " không còn áp dụng được"));
        if (!voucher.loai().equals(expectedType) || !voucher.apDungDuoc()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    voucher.lyDo() != null ? voucher.lyDo() : "Mã khuyến mãi " + code + " không hợp lệ");
        }
    }

    private BigDecimal selectedVoucherDiscount(String code, CheckoutDto preview, String expectedType) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        return preview.vouchers().stream()
                .filter(item -> item.maKm().equalsIgnoreCase(code.trim())
                        && item.loai().equals(expectedType))
                .map(CheckoutDto.Voucher::soTienGiam)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private String serialize(CheckoutPaymentPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể lưu thông tin phiên thanh toán");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }

    @Transactional
    public PhienThanhToan savePayUrl(String sessionId, String payUrl) {
        PhienThanhToan session = sessionRepository.findByMaPhienThanhToanForUpdate(sessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));
        if (session.getTrangThai() == TrangThaiPhienThanhToan.CHO_THANH_TOAN) {
            session.setPayUrl(payUrl);
            session.setNgayCapNhat(LocalDateTime.now());
            return sessionRepository.save(session);
        }
        if (session.getTrangThai() != TrangThaiPhienThanhToan.DA_TAO_DON) {
            throw new AppException(HttpStatus.CONFLICT, "Phiên thanh toán đã kết thúc");
        }
        return session;
    }

    @Transactional
    public LocalDateTime getOrCreateVnPayCreateDate(String sessionId) {
        PhienThanhToan session = sessionRepository.findByMaPhienThanhToanForUpdate(sessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));
        if (session.getTrangThai() != TrangThaiPhienThanhToan.CHO_THANH_TOAN) {
            throw new AppException(HttpStatus.CONFLICT, "Phiên thanh toán đã kết thúc");
        }
        if (session.getNgayTaoGiaoDichVnPay() == null) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            session.setNgayTaoGiaoDichVnPay(now);
            session.setNgayCapNhat(now);
            sessionRepository.save(session);
        }
        return session.getNgayTaoGiaoDichVnPay();
    }

    @Transactional
    public void markProviderCreationFailed(String sessionId) {
        sessionRepository.findByMaPhienThanhToanForUpdate(sessionId).ifPresent(session -> {
            if (session.getTrangThai() == TrangThaiPhienThanhToan.CHO_THANH_TOAN) {
                session.setTrangThai(TrangThaiPhienThanhToan.THAT_BAI);
                session.setNgayCapNhat(LocalDateTime.now());
                sessionRepository.save(session);
            }
        });
    }

    @Transactional(readOnly = true)
    public CheckoutPaymentStatusDto status(String customerId, String sessionId) {
        PhienThanhToan session = sessionRepository.findById(sessionId)
                .filter(item -> item.getKhachHang().getMaKh().equals(customerId))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));
        return new CheckoutPaymentStatusDto(session.getTrangThai().name(), session.getMaDonHang());
    }

    @Transactional(readOnly = true)
    public boolean isSession(String sessionId) {
        return sessionRepository.existsById(sessionId);
    }

    @Transactional
    public Optional<CheckoutPaymentCallbackResult> processCallback(
            String sessionId,
            PhuongThucTt paymentMethod,
            BigDecimal paidAmount,
            boolean paymentSucceeded,
            String providerTransactionId) {
        if (sessionId == null || !sessionId.startsWith("PC")) {
            return Optional.empty();
        }
        if (sessionRepository.findById(sessionId).isEmpty()) {
            return Optional.empty();
        }
        PhienThanhToan session = sessionRepository.findByMaPhienThanhToanForUpdate(sessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));
        if (session.getPhuongThuc() != paymentMethod) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Phương thức thanh toán phiên không hợp lệ");
        }
        if (paidAmount.compareTo(session.getSoTien()) != 0) {
            return Optional.of(new CheckoutPaymentCallbackResult(
                    CheckoutPaymentCallbackResult.Status.AMOUNT_MISMATCH, session.getMaDonHang()));
        }
        if (session.getTrangThai() == TrangThaiPhienThanhToan.DA_TAO_DON
                || session.getTrangThai() == TrangThaiPhienThanhToan.DA_HOAN_TIEN) {
            return Optional.of(new CheckoutPaymentCallbackResult(
                    CheckoutPaymentCallbackResult.Status.ALREADY_HANDLED, session.getMaDonHang()));
        }
        if (session.getTrangThai() == TrangThaiPhienThanhToan.CAN_HOAN_TIEN) {
            if (paymentSucceeded) {
                publishRefundRequired(session, providerTransactionId,
                        session.getLyDoHoanTien() != null ? session.getLyDoHoanTien() : "Không thể tạo đơn hàng sau thanh toán");
            }
            return Optional.of(new CheckoutPaymentCallbackResult(
                    CheckoutPaymentCallbackResult.Status.REFUND_REQUIRED, session.getMaDonHang()));
        }
        if (!paymentSucceeded) {
            if (session.getTrangThai() == TrangThaiPhienThanhToan.CHO_THANH_TOAN) {
                session.setTrangThai(TrangThaiPhienThanhToan.THAT_BAI);
                session.setNgayCapNhat(LocalDateTime.now());
                sessionRepository.save(session);
            }
            return Optional.of(new CheckoutPaymentCallbackResult(
                    CheckoutPaymentCallbackResult.Status.PAYMENT_FAILED, session.getMaDonHang()));
        }

        session.setMaGiaoDichNhaCungCap(providerTransactionId);
        try {
            DonHangDto order = orderFinalizer.finalizePaidCheckout(
                    session.getKhachHang().getMaKh(),
                    session.getMaPhienThanhToan(),
                    session.getDuLieuCheckout(),
                    session.getSoTien(),
                    providerTransactionId,
                    paymentMethod,
                    session.getNgayTaoGiaoDichVnPay());
            session.setMaDonHang(order.maDonHang());
            session.setTrangThai(TrangThaiPhienThanhToan.DA_TAO_DON);
            session.setNgayCapNhat(LocalDateTime.now());
            sessionRepository.save(session);
            return Optional.of(new CheckoutPaymentCallbackResult(
                    CheckoutPaymentCallbackResult.Status.ORDER_CREATED, order.maDonHang()));
        } catch (AppException ex) {
            session.setTrangThai(TrangThaiPhienThanhToan.CAN_HOAN_TIEN);
            session.setLyDoHoanTien(ex.getMessage());
            session.setNgayCapNhat(LocalDateTime.now());
            sessionRepository.save(session);
            publishRefundRequired(session, providerTransactionId, ex.getMessage());
            return Optional.of(new CheckoutPaymentCallbackResult(
                    CheckoutPaymentCallbackResult.Status.REFUND_REQUIRED, null));
        }
    }

    private void publishRefundRequired(
            PhienThanhToan session, String providerTransactionId, String reason) {
        eventPublisher.publishEvent(new CheckoutRefundRequiredEvent(
                session.getMaPhienThanhToan(),
                session.getKhachHang().getMaKh(),
                session.getPhuongThuc(),
                session.getSoTien(),
                providerTransactionId,
                reason));
    }

    @Transactional
    public void markRefunded(String sessionId, String refundTransactionId) {
        PhienThanhToan session = sessionRepository.findByMaPhienThanhToanForUpdate(sessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));
        completeRefund(session, refundTransactionId);
    }

    @Transactional
    public CheckoutPaymentStatusDto recordManualRefund(String sessionId, String refundTransactionId) {
        if (refundTransactionId == null || refundTransactionId.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập mã giao dịch hoàn tiền");
        }
        PhienThanhToan session = sessionRepository.findByMaPhienThanhToanForUpdate(sessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));
        if (session.getTrangThai() != TrangThaiPhienThanhToan.CAN_HOAN_TIEN) {
            throw new AppException(HttpStatus.CONFLICT, "Phiên thanh toán không còn chờ hoàn tiền");
        }
        String previousStatus = session.getTrangThai().name();
        completeRefund(session, refundTransactionId.trim());
        nhatKyHoatDongService.ghi(
                HanhDongAudit.REFUND_PAYMENT,
                LoaiDoiTuongAudit.PAYMENT,
                sessionId,
                Map.of("trangThai", previousStatus),
                Map.of(
                        "trangThai", session.getTrangThai().name(),
                        "maGiaoDichHoanTien", session.getMaGiaoDichHoanTien()));
        return new CheckoutPaymentStatusDto(session.getTrangThai().name(), session.getMaDonHang());
    }

    private void completeRefund(PhienThanhToan session, String refundTransactionId) {
        if (session.getTrangThai() != TrangThaiPhienThanhToan.CAN_HOAN_TIEN) {
            throw new AppException(HttpStatus.CONFLICT, "Phiên thanh toán không còn chờ hoàn tiền");
        }
        session.setTrangThai(TrangThaiPhienThanhToan.DA_HOAN_TIEN);
        session.setMaGiaoDichHoanTien(refundTransactionId);
        session.setNgayCapNhat(LocalDateTime.now());
        sessionRepository.save(session);
    }
}
