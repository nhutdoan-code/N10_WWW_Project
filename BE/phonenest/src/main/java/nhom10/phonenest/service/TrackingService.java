package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.TrackingDto;
import nhom10.phonenest.entity.HanhViNguoiDung;
import nhom10.phonenest.enums.LoaiHanhVi;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.HanhViNguoiDungRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrackingService {

    private static final Pattern VISITOR_ID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final int PERSONALIZATION_DAYS = 30;
    private static final int PERSONALIZATION_EVENT_LIMIT = 100;
    private static final List<String> PAGE_TYPES = List.of(
            "HOME", "PRODUCT_LIST", "PRODUCT_DETAIL", "CART", "COMPARE", "LOGIN", "REGISTER");

    private final HanhViNguoiDungRepository repository;
    private final SanPhamRepository sanPhamRepository;

    @Value("${app.tracking.retention-days:90}")
    private int retentionDays;

    @Transactional
    public void record(TrackingDto.Event event) {
        validate(event);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        HanhViNguoiDung activity = new HanhViNguoiDung();
        activity.setMaHanhVi(UUID.randomUUID().toString());
        activity.setVisitorId(event.visitorId().toLowerCase(Locale.ROOT));
        activity.setMaTk(customerId(authentication));
        activity.setLoaiHanhVi(event.type());
        activity.setThoiGian(LocalDateTime.now());
        switch (event.type()) {
            case PAGE_VIEW -> activity.setPageType(event.pageType());
            case PRODUCT_VIEW, ADD_TO_CART -> activity.setProductId(event.productId());
            case SEARCH -> activity.setSearchTerm(event.searchTerm().trim());
        }
        repository.save(activity);
    }

    public Preferences preferences(String visitorId) {
        String customerId = customerId(SecurityContextHolder.getContext().getAuthentication());
        if ((visitorId == null || !VISITOR_ID.matcher(visitorId).matches()) && customerId == null) {
            return Preferences.empty();
        }

        List<HanhViNguoiDung> events = repository.findRecent(
                customerId,
                visitorId == null ? "" : visitorId.toLowerCase(Locale.ROOT),
                LocalDateTime.now().minusDays(PERSONALIZATION_DAYS),
                PageRequest.of(0, PERSONALIZATION_EVENT_LIMIT));
        Map<String, Integer> productScores = new LinkedHashMap<>();
        LinkedHashSet<String> searchTerms = new LinkedHashSet<>();
        for (HanhViNguoiDung event : events) {
            if (event.getProductId() != null) {
                int weight = event.getLoaiHanhVi() == LoaiHanhVi.ADD_TO_CART ? 4 : 2;
                productScores.merge(event.getProductId(), weight, Integer::sum);
            } else if (event.getLoaiHanhVi() == LoaiHanhVi.SEARCH
                    && event.getSearchTerm() != null && !event.getSearchTerm().isBlank()) {
                searchTerms.add(event.getSearchTerm());
            }
        }
        List<String> productIds = productScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .limit(10)
                .toList();
        return new Preferences(productIds, new ArrayList<>(searchTerms).stream().limit(5).toList());
    }

    @Scheduled(cron = "${app.tracking.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpired() {
        repository.deleteExpired(LocalDateTime.now().minusDays(retentionDays));
    }

    private void validate(TrackingDto.Event event) {
        if (event.type() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Loại hành vi không hợp lệ");
        }
        switch (event.type()) {
            case PAGE_VIEW -> {
                if (event.pageType() == null || !PAGE_TYPES.contains(event.pageType())
                        || event.productId() != null || event.searchTerm() != null) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Thông tin lượt xem trang không hợp lệ");
                }
            }
            case PRODUCT_VIEW, ADD_TO_CART -> {
                if (event.productId() == null || event.productId().isBlank()
                        || event.searchTerm() != null || event.pageType() != null
                        || !sanPhamRepository.existsById(event.productId())) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm theo dõi không hợp lệ");
                }
            }
            case SEARCH -> {
                if (event.searchTerm() == null || event.searchTerm().isBlank()
                        || event.productId() != null || event.pageType() != null) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Từ khóa tìm kiếm không hợp lệ");
                }
            }
        }
    }

    private String customerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()))
                ? authentication.getName() : null;
    }

    public record Preferences(List<String> productIds, List<String> searchTerms) {
        public static Preferences empty() {
            return new Preferences(List.of(), List.of());
        }
    }
}
