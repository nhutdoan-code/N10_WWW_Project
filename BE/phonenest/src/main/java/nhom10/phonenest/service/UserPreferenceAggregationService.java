package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.HanhViDto;
import nhom10.phonenest.entity.ChiTietGioHang;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.HanhViNguoiDung;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.HanhViNguoiDungRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferenceAggregationService {

    private final HanhViNguoiDungRepository hanhViRepository;
    private final SanPhamRepository sanPhamRepository;
    private final GioHangRepository gioHangRepository;

    public HanhViDto.UserPreferenceDto aggregatePreferences(String maKh) {
        if (maKh == null || maKh.isBlank()) {
            return new HanhViDto.UserPreferenceDto(
                    null, List.of(), List.of(), null, null, List.of(), List.of(), 0, "Khách vãng lai"
            );
        }

        long totalInteractions = hanhViRepository.countByMaKh(maKh);

        // 1. Get recent interactions
        List<HanhViNguoiDung> recentEvents = hanhViRepository.findByMaKhOrderByThoiGianDesc(maKh, PageRequest.of(0, 100));

        // 2. Weights: PURCHASE=5, ADD_TO_CART=3, PRODUCT_VIEW=1, PRODUCT_CLICK=1, RECOMMENDATION_CLICK=2
        Map<String, Integer> brandScores = new HashMap<>();
        Map<String, Integer> categoryScores = new HashMap<>();
        Map<String, String> productNames = new HashMap<>();
        List<BigDecimal> prices = new ArrayList<>();
        Set<String> interactedProductIds = new LinkedHashSet<>();

        // Batch fetch products to avoid N+1 queries
        Set<String> productIdsToFetch = recentEvents.stream()
                .map(HanhViNguoiDung::getMaSanPham)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, SanPham> productCache = sanPhamRepository.findAllById(productIdsToFetch).stream()
                .collect(Collectors.toMap(SanPham::getMaSanPham, p -> p, (a, b) -> a));

        for (HanhViNguoiDung event : recentEvents) {
            String pId = event.getMaSanPham();
            if (pId != null && !pId.isBlank()) {
                interactedProductIds.add(pId);
                int weight = getWeight(event.getLoaiHanhVi());

                SanPham p = productCache.get(pId);
                if (p != null) {
                    productNames.putIfAbsent(pId, p.getTenMay());
                    if (p.getThuongHieu() != null) {
                        brandScores.merge(p.getThuongHieu().getTenThuongHieu(), weight, Integer::sum);
                    }
                    if (p.getLoai() != null) {
                        categoryScores.merge(p.getLoai().getTenLoai(), weight, Integer::sum);
                    }
                    BigDecimal price = p.getGiaBan();
                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        prices.add(price);
                    }
                }
            }
        }

        // 3. Check current cart items
        Optional<GioHang> gioHangOpt = gioHangRepository.findByKhachHangMaKh(maKh);
        if (gioHangOpt.isPresent()) {
            for (ChiTietGioHang item : gioHangOpt.get().getChiTiet()) {
                if (item.getPhienBan() != null && item.getPhienBan().getSanPham() != null) {
                    SanPham p = item.getPhienBan().getSanPham();
                    String pId = p.getMaSanPham();
                    interactedProductIds.add(pId);
                    productNames.putIfAbsent(pId, p.getTenMay());
                    if (p.getThuongHieu() != null) {
                        brandScores.merge(p.getThuongHieu().getTenThuongHieu(), 3, Integer::sum);
                    }
                    if (p.getLoai() != null) {
                        categoryScores.merge(p.getLoai().getTenLoai(), 3, Integer::sum);
                    }
                    BigDecimal price = item.getPhienBan().getGiaBan();
                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        prices.add(price);
                    }
                }
            }
        }

        // Top brands
        List<String> topBrands = brandScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(4)
                .map(Map.Entry::getKey)
                .toList();

        // Top categories
        List<String> topCategories = categoryScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        // Price range
        BigDecimal minPrice = prices.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal maxPrice = prices.stream().max(BigDecimal::compareTo).orElse(null);

        // Recent search keywords
        List<String> recentKeywords = hanhViRepository.findRecentKeywordsByMaKh(maKh, PageRequest.of(0, 5));

        // Interacted product names
        List<String> topProductList = interactedProductIds.stream()
                .limit(5)
                .map(id -> productNames.getOrDefault(id, id))
                .toList();

        // Summary for AI Prompt
        String aiSummary = buildSummaryText(topBrands, topCategories, minPrice, maxPrice, recentKeywords, topProductList);

        return new HanhViDto.UserPreferenceDto(
                maKh,
                topBrands,
                topCategories,
                minPrice,
                maxPrice,
                recentKeywords,
                topProductList,
                totalInteractions,
                aiSummary
        );
    }

    public String buildAiPromptContext(String maKh) {
        if (maKh == null || maKh.isBlank()) {
            return "";
        }
        try {
            HanhViDto.UserPreferenceDto pref = aggregatePreferences(maKh);
            if (pref.tongLuotTuongTac() == 0 && pref.topThuongHieu().isEmpty() && pref.tuKhoaGanDay().isEmpty()) {
                return "";
            }
            return """
                    SỞ THÍCH VÀ HÀNH VI KHÁCH HÀNG HIỆN TẠI:
                    %s
                    Hãy ưu tiên tư vấn các sản phẩm thuộc thương hiệu hoặc khoảng giá này khi phù hợp với câu hỏi của khách hàng.
                    """.formatted(pref.tomTatAi()).trim();
        } catch (Exception e) {
            log.warn("Không thể tổng hợp ngữ cảnh sở thích cho khách hàng {}: {}", maKh, e.getMessage());
            return "";
        }
    }

    private int getWeight(LoaiHanhViNguoiDung loai) {
        if (loai == null) return 1;
        return switch (loai) {
            case PURCHASE -> 5;
            case ADD_TO_CART -> 3;
            case RECOMMENDATION_CLICK -> 2;
            case PRODUCT_VIEW, PRODUCT_CLICK, SEARCH, REMOVE_FROM_CART -> 1;
        };
    }

    private String buildSummaryText(
            List<String> brands,
            List<String> categories,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> keywords,
            List<String> products
    ) {
        StringBuilder sb = new StringBuilder();
        if (!brands.isEmpty()) {
            sb.append("- Thương hiệu quan tâm: ").append(String.join(", ", brands)).append("\n");
        }
        if (!categories.isEmpty()) {
            sb.append("- Danh mục xem nhiều: ").append(String.join(", ", categories)).append("\n");
        }
        if (minPrice != null && maxPrice != null) {
            sb.append("- Tầm giá quan tâm: ").append(formatMoney(minPrice)).append(" - ").append(formatMoney(maxPrice)).append("\n");
        }
        if (!keywords.isEmpty()) {
            sb.append("- Từ khóa tìm kiếm gần đây: ").append(String.join(", ", keywords)).append("\n");
        }
        if (!products.isEmpty()) {
            sb.append("- Sản phẩm vừa tương tác: ").append(String.join("; ", products)).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getInstance(Locale.of("vi", "VN")).format(amount) + " đ";
    }
}
