package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.CheckoutIdempotency;
import nhom10.phonenest.enums.IdempotencyStatus;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.CheckoutIdempotencyRepository;
import nhom10.phonenest.repository.DonHangRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutIdempotencyService {

    public static final String CODE_IN_PROGRESS = "IDEMPOTENCY_IN_PROGRESS";
    public static final String CODE_PAYLOAD_MISMATCH = "IDEMPOTENCY_PAYLOAD_MISMATCH";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final CheckoutIdempotencyRepository repository;
    private final CheckoutIdempotencyTxHelper txHelper;
    private final DonHangService donHangService;
    private final DonHangRepository donHangRepository;
    private final ObjectMapper objectMapper;

    public DonHangDto executeIdempotentCheckout(
            String userId,
            String idempotencyKey,
            DonHangDto.Checkout request
    ) {
        validateIdempotencyKey(idempotencyKey);
        String normalizedKey = idempotencyKey.trim();
        String fingerprint = computeFingerprint(userId, request);

        Optional<CheckoutIdempotency> existingOpt = repository.findByUserIdAndIdempotencyKey(userId, normalizedKey);
        Long recordId;

        if (existingOpt.isPresent()) {
            CheckoutIdempotency existing = existingOpt.get();

            if (existing.getStatus() == IdempotencyStatus.SUCCESS) {
                if (!existing.getRequestHash().equals(fingerprint)) {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "Idempotency-Key đã được sử dụng với thông tin đặt hàng khác",
                            CODE_PAYLOAD_MISMATCH
                    );
                }
                return resolveCompletedOrder(userId, existing);
            }

            if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                // Đối soát nếu đơn hàng thực tế đã được commit thành công trước đó
                if (existing.getOrderId() != null && donHangRepository.existsById(existing.getOrderId())) {
                    return resolveCompletedOrder(userId, existing);
                }

                // Không tự động thực thi lại checkout khi đang IN_PROGRESS; từ chối mọi retry để tránh tạo đơn trùng
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "Yêu cầu đặt hàng của bạn đang được xử lý hoặc đang chờ đối soát, vui lòng không gửi lại",
                        CODE_IN_PROGRESS
                );
            } else {
                // Status == FAILED (Chỉ cho phép retry khi transaction cũ đã chắc chắn kết thúc và rollback)
                if (!existing.getRequestHash().equals(fingerprint)) {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "Idempotency-Key đã được sử dụng với thông tin đặt hàng khác",
                            CODE_PAYLOAD_MISMATCH
                    );
                }

                if (existing.getOrderId() != null && donHangRepository.existsById(existing.getOrderId())) {
                    return resolveCompletedOrder(userId, existing);
                }

                boolean claimed = txHelper.transitionFromFailedToInProgress(existing.getId(), fingerprint);
                if (!claimed) {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "Yêu cầu đặt hàng của bạn đang được xử lý bởi một tiến trình khác, vui lòng thử lại sau",
                            CODE_IN_PROGRESS
                    );
                }
                recordId = existing.getId();
            }
        } else {
            try {
                CheckoutIdempotency created = txHelper.saveNewInProgress(userId, normalizedKey, fingerprint);
                recordId = created.getId();
            } catch (DataIntegrityViolationException e) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "Yêu cầu đặt hàng của bạn đang được xử lý, vui lòng không gửi lại",
                        CODE_IN_PROGRESS
                );
            }
        }

        // Thực thi tạo đơn và cập nhật SUCCESS nguyên tử trong cùng transaction datHang
        try {
            return donHangService.datHang(userId, request, recordId);
        } catch (Exception e) {
            txHelper.markFailed(recordId);
            throw e;
        }
    }

    public void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Thiếu header Idempotency-Key bắt buộc khi đặt hàng"
            );
        }
        if (!UUID_PATTERN.matcher(key.trim()).matches()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Header Idempotency-Key phải là định dạng UUID hợp lệ"
            );
        }
    }

    public String computeFingerprint(String userId, DonHangDto.Checkout req) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("user:").append(userId != null ? userId.trim() : "").append(";");
            sb.append("pt:").append(req.phuongThucTt() != null ? req.phuongThucTt().name() : "").append(";");
            sb.append("dc:").append(req.maDiaChi() != null ? req.maDiaChi().trim() : "").append(";");
            sb.append("km:").append(req.maKm() != null ? req.maKm().trim().toUpperCase() : "").append(";");
            sb.append("kmVc:").append(req.maKmVanChuyen() != null ? req.maKmVanChuyen().trim().toUpperCase() : "").append(";");
            sb.append("sp:").append(req.maSanPham() != null ? req.maSanPham().trim() : "").append(";");
            sb.append("sl:").append(req.soLuong() != null ? req.soLuong() : "").append(";");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tính toán fingerprint đặt hàng", e);
        }
    }

    private DonHangDto resolveCompletedOrder(String userId, CheckoutIdempotency record) {
        if (record.getResponsePayload() != null && !record.getResponsePayload().isBlank()) {
            try {
                return objectMapper.readValue(record.getResponsePayload(), DonHangDto.class);
            } catch (Exception e) {
                log.warn("Không thể deserialize response payload cho đơn {}, sẽ truy vấn CSDL: {}",
                        record.getOrderId(), e.getMessage());
            }
        }
        if (record.getOrderId() != null) {
            return donHangService.chiTiet(userId, record.getOrderId());
        }
        throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tìm thấy dữ liệu đơn hàng đã tạo");
    }
}
