package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.HanhViDto;
import nhom10.phonenest.entity.HanhViNguoiDung;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.HanhViNguoiDungRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HanhViNguoiDungService {

    private final HanhViNguoiDungRepository hanhViRepository;
    private final SanPhamRepository sanPhamRepository;
    private final PhienBanSanPhamRepository phienBanRepository;
    private final UserPreferenceAggregationService userPreferenceService;

    // Rate Limiter configuration
    private static final int MAX_ANON_IP_EVENTS_PER_MINUTE = 60;
    private static final int MAX_ANON_SESSION_EVENTS_PER_MINUTE = 60;
    private static final int MAX_AUTH_USER_EVENTS_PER_MINUTE = 120;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;
    private static final int MAX_CACHE_ENTRIES = 5_000;

    private final Map<String, Deque<Long>> rateLimitTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Long> recentViewLock = new ConcurrentHashMap<>();

    private boolean checkAndRecordRateLimit(String key, int maxAllowed, long now) {
        Deque<Long> timestamps = rateLimitTimestamps.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > RATE_LIMIT_WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAllowed) {
                return true;
            }
            timestamps.addLast(now);
        }
        return false;
    }

    private boolean isRateLimited(String maKh, String sessionId, String clientIp) {
        long now = System.currentTimeMillis();

        // 1. Authenticated user rate limiting
        if (maKh != null && !maKh.isBlank()) {
            if (checkAndRecordRateLimit("USER:" + maKh, MAX_AUTH_USER_EVENTS_PER_MINUTE, now)) {
                return true;
            }
        } else {
            // 2. Anonymous user rate limiting
            // Rate limit by IP address across all session IDs to prevent rotating-session bypass
            if (clientIp != null && !clientIp.isBlank() && !"UNKNOWN".equalsIgnoreCase(clientIp)) {
                if (checkAndRecordRateLimit("IP:" + clientIp, MAX_ANON_IP_EVENTS_PER_MINUTE, now)) {
                    return true;
                }
            }
            // Rate limit by session ID
            if (sessionId != null && !sessionId.isBlank()) {
                if (checkAndRecordRateLimit("SESS:" + sessionId, MAX_ANON_SESSION_EVENTS_PER_MINUTE, now)) {
                    return true;
                }
            }
        }

        // Clean up cache to prevent unbounded memory growth
        if (rateLimitTimestamps.size() > MAX_CACHE_ENTRIES) {
            rateLimitTimestamps.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || (now - e.getValue().peekLast() > RATE_LIMIT_WINDOW_MS * 2);
                }
            });
        }
        if (recentViewLock.size() > MAX_CACHE_ENTRIES) {
            recentViewLock.entrySet().removeIf(e -> (now - e.getValue() > 120_000L));
        }

        return false;
    }

    public HanhViDto.EventResponse ghiNhan(HanhViDto.EventRequest req, String maKh) {
        return ghiNhan(req, maKh, "UNKNOWN");
    }

    public HanhViDto.EventResponse ghiNhan(HanhViDto.EventRequest req, String maKh, String clientIp) {
        if (req == null || req.loaiHanhVi() == null) {
            return new HanhViDto.EventResponse(null, false, "Loại hành vi không hợp lệ");
        }

        // 0. Security Guard: Client must not spoof PURCHASE events
        if (req.loaiHanhVi() == LoaiHanhViNguoiDung.PURCHASE) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Hành vi PURCHASE chỉ được ghi nhận từ hệ thống xử lý giao dịch");
        }

        // 1. Session ID fallback
        String sessionId = req.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "ANON_" + UUID.randomUUID();
        }

        // 2. Rate limiting check (authenticated vs anonymous)
        if (isRateLimited(maKh, sessionId, clientIp)) {
            return new HanhViDto.EventResponse("RATE_LIMITED", false, "Tần suất gửi sự kiện vượt giới hạn cho phép");
        }

        // 3. Foreign Key & Domain Validation:
        // Do NOT silently convert invalid maSanPham or maPhienBan to null!
        final String maSp = (req.maSanPham() != null && !req.maSanPham().isBlank()) ? req.maSanPham().trim() : null;
        final String maPb = (req.maPhienBan() != null && !req.maPhienBan().isBlank()) ? req.maPhienBan().trim() : null;

        // Check if event strictly requires maSanPham
        boolean requiresProduct = (
                req.loaiHanhVi() == LoaiHanhViNguoiDung.PRODUCT_VIEW
                || req.loaiHanhVi() == LoaiHanhViNguoiDung.PRODUCT_CLICK
                || req.loaiHanhVi() == LoaiHanhViNguoiDung.ADD_TO_CART
                || req.loaiHanhVi() == LoaiHanhViNguoiDung.REMOVE_FROM_CART
        );

        if (requiresProduct && maSp == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Hành vi " + req.loaiHanhVi() + " bắt buộc phải có mã sản phẩm (maSanPham)");
        }

        if (maSp != null) {
            if (!sanPhamRepository.existsById(maSp)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm không tồn tại: " + maSp);
            }
        }

        if (maPb != null) {
            PhienBanSanPham pb = phienBanRepository.findById(maPb)
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Phiên bản không tồn tại: " + maPb));
            if (maSp != null && (pb.getSanPham() == null || !maSp.equals(pb.getSanPham().getMaSanPham()))) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Phiên bản " + maPb + " không thuộc sản phẩm " + maSp);
            }
        }

        // 4. Throttling and race condition prevention for PRODUCT_VIEW
        if (req.loaiHanhVi() == LoaiHanhViNguoiDung.PRODUCT_VIEW && maSp != null) {
            String lockKey = (maKh != null && !maKh.isBlank() ? ("U:" + maKh) : ("S:" + sessionId)) + ":" + maSp;
            long now = System.currentTimeMillis();
            Long prev = recentViewLock.get(lockKey);
            if (prev != null && (now - prev < 120_000L)) {
                return new HanhViDto.EventResponse("THROTTLED", true, "Đã ghi nhận lượt xem gần đây");
            }

            LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
            if (hanhViRepository.existsRecentView(maKh, sessionId, maSp, LoaiHanhViNguoiDung.PRODUCT_VIEW, threshold)) {
                recentViewLock.put(lockKey, now);
                return new HanhViDto.EventResponse("THROTTLED", true, "Đã ghi nhận lượt xem gần đây");
            }

            // Atomic in-memory check to prevent concurrent race conditions
            if (recentViewLock.putIfAbsent(lockKey, now) != null) {
                Long existing = recentViewLock.get(lockKey);
                if (existing != null && (now - existing < 120_000L)) {
                    return new HanhViDto.EventResponse("THROTTLED", true, "Đã ghi nhận lượt xem gần đây");
                }
                recentViewLock.put(lockKey, now);
            }
        }

        // 5. Normalize fields
        String tuKhoa = req.tuKhoa();
        if (tuKhoa != null) {
            tuKhoa = tuKhoa.trim();
            if (tuKhoa.length() > 255) {
                tuKhoa = tuKhoa.substring(0, 255);
            }
        }

        String metadata = req.metadata();
        if (metadata != null && metadata.length() > 1000) {
            metadata = metadata.substring(0, 1000);
        }

        String nguonSuKien = req.nguonSuKien();
        if (nguonSuKien != null && nguonSuKien.length() > 100) {
            nguonSuKien = nguonSuKien.substring(0, 100);
        }

        // 6. Create and save entity
        HanhViNguoiDung entity = HanhViNguoiDung.builder()
                .maHanhVi(IdGenerator.next("HV"))
                .maKh(maKh != null && !maKh.isBlank() ? maKh : null)
                .sessionId(sessionId)
                .maSanPham(maSp)
                .maPhienBan(maPb)
                .loaiHanhVi(req.loaiHanhVi())
                .tuKhoa(tuKhoa)
                .metadata(metadata)
                .nguonSuKien(nguonSuKien)
                .thoiGian(LocalDateTime.now())
                .build();

        hanhViRepository.save(entity);
        return new HanhViDto.EventResponse(entity.getMaHanhVi(), true, "Ghi nhận thành công");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ghiNhanPurchase(String maKh, String maSanPham, String maPhienBan, String maDonHang, BigDecimal gia) {
        try {
            // Idempotency: Skip if already recorded for this order + product
            if (hanhViRepository.existsBySessionIdAndMaSanPhamAndLoaiHanhVi(
                    "BACKEND_ORDER_" + maDonHang, maSanPham, LoaiHanhViNguoiDung.PURCHASE)) {
                log.info("PURCHASE event cho đơn hàng {} và sản phẩm {} đã tồn tại, bỏ qua ghi trùng", maDonHang, maSanPham);
                return;
            }

            HanhViNguoiDung entity = HanhViNguoiDung.builder()
                    .maHanhVi(IdGenerator.next("HV"))
                    .maKh(maKh)
                    .sessionId("BACKEND_ORDER_" + maDonHang)
                    .maSanPham(maSanPham)
                    .maPhienBan(maPhienBan)
                    .loaiHanhVi(LoaiHanhViNguoiDung.PURCHASE)
                    .metadata("Đơn hàng: " + maDonHang + (gia != null ? " | Đơn giá: " + gia : ""))
                    .nguonSuKien("CHECKOUT_BACKEND")
                    .thoiGian(LocalDateTime.now())
                    .build();

            hanhViRepository.save(entity);
        } catch (Exception e) {
            // Non-blocking: ensure tracking failure never impacts checkout
            log.warn("Không thể ghi nhận hành vi PURCHASE cho đơn hàng {}: {}", maDonHang, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public HanhViDto.BehaviorStatsResponse layThongKe(LocalDate tuNgay, LocalDate denNgay, LoaiHanhViNguoiDung loai, String maKh) {
        LocalDateTime start = tuNgay != null ? tuNgay.atStartOfDay() : null;
        LocalDateTime end = denNgay != null ? denNgay.atTime(23, 59, 59) : null;

        // 1. Group by event type
        List<Object[]> grouped = hanhViRepository.countGroupedByLoaiHanhVi(start, end, maKh);
        Map<String, Long> theoLoai = new LinkedHashMap<>();
        long tongLuot = 0;
        for (Object[] row : grouped) {
            if (row[0] != null && row[1] != null) {
                String loaiName = row[0].toString();
                long count = ((Number) row[1]).longValue();
                theoLoai.put(loaiName, count);
                tongLuot += count;
            }
        }

        // 2. Top viewed products
        List<HanhViDto.TopProductStats> topView = layTopSanPhamTheoLoai(LoaiHanhViNguoiDung.PRODUCT_VIEW, start, end);

        // 3. Top clicked products
        List<HanhViDto.TopProductStats> topClick = layTopSanPhamTheoLoai(LoaiHanhViNguoiDung.PRODUCT_CLICK, start, end);

        // 4. Top keywords
        List<Object[]> keywordRows = hanhViRepository.findTopKeywords(start, end, PageRequest.of(0, 10));
        List<HanhViDto.TopKeywordStats> topTuKhoa = keywordRows.stream()
                .map(r -> new HanhViDto.TopKeywordStats(r[0] != null ? r[0].toString() : "", ((Number) r[1]).longValue()))
                .toList();

        // 5. Recent events (Batch fetch product names to avoid N+1)
        List<HanhViNguoiDung> recent = hanhViRepository.findAllByOrderByThoiGianDesc(PageRequest.of(0, 20));
        Set<String> recentProductIds = recent.stream()
                .map(HanhViNguoiDung::getMaSanPham)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> sanPhamNames = sanPhamRepository.findAllById(recentProductIds).stream()
                .collect(Collectors.toMap(SanPham::getMaSanPham, SanPham::getTenMay, (a, b) -> a));

        List<HanhViDto.EventDetailDto> suKienGanDay = recent.stream().map(h -> {
            String pName = h.getMaSanPham() != null ? sanPhamNames.get(h.getMaSanPham()) : null;
            return new HanhViDto.EventDetailDto(
                    h.getMaHanhVi(),
                    h.getMaKh(),
                    h.getSessionId(),
                    h.getMaSanPham(),
                    pName,
                    h.getMaPhienBan(),
                    h.getLoaiHanhVi(),
                    h.getTuKhoa(),
                    h.getNguonSuKien(),
                    h.getMetadata(),
                    h.getThoiGian()
            );
        }).toList();

        return new HanhViDto.BehaviorStatsResponse(
                tongLuot,
                theoLoai,
                topView,
                topClick,
                topTuKhoa,
                suKienGanDay
        );
    }

    private List<HanhViDto.TopProductStats> layTopSanPhamTheoLoai(LoaiHanhViNguoiDung loai, LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = hanhViRepository.findTopProductsByLoai(loai, start, end, PageRequest.of(0, 10));
        Set<String> pIds = rows.stream()
                .map(r -> r[0] != null ? r[0].toString() : "")
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> pNames = sanPhamRepository.findAllById(pIds).stream()
                .collect(Collectors.toMap(SanPham::getMaSanPham, SanPham::getTenMay, (a, b) -> a));

        List<HanhViDto.TopProductStats> list = new ArrayList<>();
        for (Object[] r : rows) {
            String pId = r[0] != null ? r[0].toString() : "";
            long count = ((Number) r[1]).longValue();
            String pName = pNames.getOrDefault(pId, pId);
            list.add(new HanhViDto.TopProductStats(
                    pId,
                    pName,
                    loai == LoaiHanhViNguoiDung.PRODUCT_VIEW ? count : 0,
                    loai == LoaiHanhViNguoiDung.PRODUCT_CLICK ? count : 0,
                    0,
                    0
            ));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public HanhViDto.UserPreferenceDto laySoThichNguoiDung(String maKh) {
        return userPreferenceService.aggregatePreferences(maKh);
    }

    @Transactional
    public int xoaHanhViCu(int soNgayLuuTru) {
        if (soNgayLuuTru < 7 || soNgayLuuTru > 3650) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số ngày lưu trữ không hợp lệ. Phải từ 7 đến 3650 ngày để đảm bảo an toàn dữ liệu.");
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(soNgayLuuTru);
        int deleted = hanhViRepository.deleteByThoiGianBefore(threshold);
        log.info("Dọn dẹp {} bản ghi hành vi người dùng cũ hơn {} ngày (trước {})", deleted, soNgayLuuTru, threshold);
        return deleted;
    }
}
