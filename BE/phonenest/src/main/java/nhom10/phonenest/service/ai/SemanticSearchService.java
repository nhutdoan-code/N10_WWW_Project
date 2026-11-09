package nhom10.phonenest.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.dto.SemanticSearchResultDto;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.SanPhamRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Semantic Product Search Service powered by real pretrained neural vector embeddings.
 * <p>
 * Pipeline:
 * Query -> Ollama EmbeddingModel -> Dense 1024-d Vector -> VectorStore Cosine Similarity ->
 * Structured Constraints Filter -> MariaDB Anti-Hallucination Check -> Factual Highlights.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private static final Pattern PATTERN_BUDGET = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(triệu|tr)\\b", Pattern.CASE_INSENSITIVE);

    private final SanPhamRepository sanPhamRepository;
    private final PhoneNestEmbeddingModel embeddingModel;
    private final ProductSemanticDocumentBuilder documentBuilder;

    private VectorStore vectorStore;
    private boolean initialized = false;

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        try {
            if (embeddingModel.isAvailable()) {
                reindexAll();
            } else {
                log.warn("Ollama embedding server chưa sẵn sàng lúc khởi động. Sẽ thử lập chỉ mục lại khi có truy vấn.");
            }
        } catch (Exception e) {
            log.warn("Chưa thể khởi tạo Vector Store lúc khởi động: {}", e.getMessage());
        }
    }

    private synchronized VectorStore getVectorStore() {
        if (vectorStore == null) {
            vectorStore = new PhoneNestVectorStore(embeddingModel);
        }
        return vectorStore;
    }

    @Transactional(readOnly = true)
    public synchronized int reindexAll() {
        log.info("Bắt đầu tái lập chỉ mục Vector Store cho sản phẩm PhoneNest bằng model {}...", embeddingModel.getModelName());
        VectorStore newStore = new PhoneNestVectorStore(embeddingModel);

        List<SanPham> activeProducts = sanPhamRepository.findAll().stream()
                .filter(s -> s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                .toList();

        List<Document> docs = new ArrayList<>();
        for (SanPham sp : activeProducts) {
            try {
                Document doc = documentBuilder.build(sp);
                docs.add(doc);
            } catch (Exception e) {
                log.warn("Bỏ qua sản phẩm {} khi lập chỉ mục: {}", sp.getMaSanPham(), e.getMessage());
            }
        }

        if (!docs.isEmpty()) {
            newStore.add(docs);
        }

        this.vectorStore = newStore;
        this.initialized = true;
        log.info("Tái lập chỉ mục Vector Store hoàn tất: {} sản phẩm được đánh chỉ mục bằng pretrained embedding.", docs.size());
        return docs.size();
    }

    public synchronized void indexProduct(SanPham sp) {
        if (sp == null) return;
        VectorStore store = getVectorStore();
        try {
            store.delete(List.of(sp.getMaSanPham()));
            if (sp.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH) {
                Document doc = documentBuilder.build(sp);
                store.add(List.of(doc));
                log.info("Đã cập nhật vector store cho sản phẩm {}", sp.getMaSanPham());
            }
        } catch (Exception e) {
            log.warn("Lỗi khi cập nhật vector store cho sản phẩm {}: {}", sp.getMaSanPham(), e.getMessage());
        }
    }

    public synchronized void removeProduct(String maSanPham) {
        if (maSanPham == null) return;
        try {
            getVectorStore().delete(List.of(maSanPham));
            log.info("Đã gỡ sản phẩm {} khỏi vector store", maSanPham);
        } catch (Exception e) {
            log.warn("Lỗi khi gỡ sản phẩm {} khỏi vector store: {}", maSanPham, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<SemanticSearchResultDto> search(String query, Integer limit) {
        int maxResults = (limit != null && limit > 0) ? Math.min(limit, 20) : 5;
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Check if embedding model is available
        if (!embeddingModel.isAvailable()) {
            log.warn("Ollama embedding model '{}' không khả dụng tại {}. Kích hoạt KEYWORD_FALLBACK.",
                    embeddingModel.getModelName(), embeddingModel.getBaseUrl());
            return fallbackSearch(query, maxResults);
        }

        // Ensure store is populated
        if (!initialized || ((PhoneNestVectorStore) getVectorStore()).size() == 0) {
            reindexAll();
        }

        StructuredConstraints constraints = parseConstraints(query);

        List<Document> rawCandidates;
        try {
            SearchRequest req = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(maxResults * 4, 15))
                    .similarityThreshold(0.0) // retrieve candidates and rank mathematically
                    .build();
            rawCandidates = getVectorStore().similaritySearch(req);
        } catch (Exception e) {
            log.warn("Vector similarity search thất bại ({}), chuyển sang fallback: {}", e.getClass().getSimpleName(), e.getMessage());
            return fallbackSearch(query, maxResults);
        }

        if (rawCandidates.isEmpty()) {
            return fallbackSearch(query, maxResults);
        }

        // Rank and filter candidates
        List<ScoredCandidate> scored = new ArrayList<>();
        for (Document doc : rawCandidates) {
            Optional<SanPham> opt = sanPhamRepository.findById(doc.getId());
            if (opt.isEmpty()) continue; // Anti-hallucination check: product must exist in MariaDB

            SanPham sp = opt.get();
            if (sp.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH || sp.getSoLuongTon() <= 0) continue;

            double vectorScore = doc.getScore() != null ? doc.getScore() : 0.5;
            double fitScore = evaluateConstraintFit(sp, constraints);

            // Hard pruning for strict explicit brand mismatch if requested in query
            if (constraints.requiredBrand != null) {
                String brand = sp.getThuongHieu() != null ? sp.getThuongHieu().getTenThuongHieu().toLowerCase() : "";
                if (!brand.contains(constraints.requiredBrand.toLowerCase())) {
                    continue; // Skip products not belonging to requested brand
                }
            }

            // Hard pruning for strict upper bound budget if clearly specified
            if (constraints.maxBudget != null) {
                if (constraints.strictMax) {
                    int cmp = sp.getGiaBan().compareTo(constraints.maxBudget);
                    if (constraints.maxInclusive ? cmp > 0 : cmp >= 0) {
                        continue; // Exceeds strict budget ceiling
                    }
                } else {
                    BigDecimal ceiling = constraints.maxBudget.multiply(BigDecimal.valueOf(1.15));
                    if (sp.getGiaBan().compareTo(ceiling) > 0) {
                        continue; // Exceeds fuzzy budget ceiling
                    }
                }
            }

            if (constraints.minBudget != null) {
                int cmp = sp.getGiaBan().compareTo(constraints.minBudget);
                if (constraints.minInclusive ? cmp < 0 : cmp <= 0) {
                    continue; // Below budget floor
                }
            }

            double finalScore = (vectorScore * 0.70) + (fitScore * 0.30);
            scored.add(new ScoredCandidate(sp, finalScore, vectorScore, explainFit(sp, vectorScore)));
        }

        if (scored.isEmpty()) {
            return fallbackSearch(query, maxResults);
        }

        // Sort by final score descending
        scored.sort(Comparator.comparingDouble(ScoredCandidate::finalScore).reversed());

        return scored.stream()
                .limit(maxResults)
                .map(c -> new SemanticSearchResultDto(
                        SanPhamMapper.toDto(c.product),
                        Math.round(c.finalScore * 1000.0) / 1000.0,
                        c.explanation,
                        extractKeyHighlights(c.product),
                        "VECTOR_SEMANTIC"
                ))
                .toList();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("provider", "Ollama");
        status.put("model", embeddingModel.getModelName());
        status.put("baseUrl", embeddingModel.getBaseUrl());
        status.put("dimensions", embeddingModel.dimensions());
        boolean available = embeddingModel.isAvailable();
        status.put("available", available);
        status.put("indexedDocuments", vectorStore != null ? ((PhoneNestVectorStore) vectorStore).size() : 0);
        status.put("mode", available ? "VECTOR_SEMANTIC" : "KEYWORD_FALLBACK");
        if (!available) {
            status.put("reason", "Không thể kết nối đến Ollama tại " + embeddingModel.getBaseUrl() + ". Đảm bảo dịch vụ 'ollama serve' đang chạy.");
        }
        return status;
    }

    public boolean isEmbeddingAvailable() {
        return embeddingModel != null && embeddingModel.isAvailable();
    }

    private StructuredConstraints parseConstraints(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        StructuredConstraints sc = new StructuredConstraints();

        // 1. Explicit unconstrained budget check
        if (lower.contains("giá bao nhiêu cũng được") || lower.contains("bao nhiêu tiền cũng được")
                || lower.contains("giá nào cũng được") || lower.contains("không quan trọng giá")
                || lower.contains("không giới hạn giá") || lower.contains("thoải mái giá")
                || lower.contains("bất kỳ giá nào")) {
            sc.unlimitedBudget = true;
        }

        // 2. Deterministic Brand detection
        if (lower.contains("iphone") || lower.contains("apple")) {
            sc.requiredBrand = "Apple";
        } else if (lower.contains("samsung") || lower.contains("galaxy")) {
            sc.requiredBrand = "Samsung";
        } else if (lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco")) {
            sc.requiredBrand = "Xiaomi";
        } else if (lower.contains("oppo")) {
            sc.requiredBrand = "OPPO";
        } else if (lower.contains("vivo")) {
            sc.requiredBrand = "Vivo";
        }

        // 3. Deterministic Budget extraction (only if budget is not explicitly unlimited)
        if (!sc.unlimitedBudget) {
            Matcher m = PATTERN_BUDGET.matcher(lower);
            if (m.find()) {
                try {
                    double val = Double.parseDouble(m.group(1).replace(',', '.'));
                    BigDecimal amount = BigDecimal.valueOf((long) (val * 1_000_000));
                    if (lower.contains("dưới") || lower.contains("nhỏ hơn") || lower.contains("thấp hơn") || lower.contains("ít hơn")) {
                        sc.maxBudget = amount;
                        sc.strictMax = true;
                        sc.maxInclusive = false;
                    } else if (lower.contains("tối đa") || lower.contains("không quá") || lower.contains("không vượt quá")) {
                        sc.maxBudget = amount;
                        sc.strictMax = true;
                        sc.maxInclusive = true;
                    } else if (lower.contains("trên") || lower.contains("lớn hơn") || lower.contains("cao hơn") || lower.contains("hơn")) {
                        sc.minBudget = amount;
                        sc.minInclusive = false;
                    } else if (lower.contains("từ") || lower.contains("tối thiểu") || lower.contains("ít nhất") || lower.contains("trở lên")) {
                        sc.minBudget = amount;
                        sc.minInclusive = true;
                    } else if (lower.contains("khoảng") || lower.contains("tầm") || lower.contains("quanh")) {
                        sc.targetBudget = amount;
                        sc.minBudget = amount.multiply(BigDecimal.valueOf(0.8));
                        sc.maxBudget = amount.multiply(BigDecimal.valueOf(1.2));
                    } else {
                        sc.targetBudget = amount;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 4. Soft preferences
        sc.hasCamera = lower.contains("chụp") || lower.contains("ảnh") || lower.contains("camera") || lower.contains("selfie") || lower.contains("quay video");
        sc.hasGaming = lower.contains("game") || lower.contains("gaming") || lower.contains("cấu hình") || lower.contains("hiệu năng") || lower.contains("chip");
        sc.hasBattery = lower.contains("pin") || lower.contains("pin trâu") || lower.contains("pin khủng") || lower.contains("pin lâu");
        sc.hasValue = lower.contains("giá tốt") || lower.contains("giá rẻ") || lower.contains("ngon bổ rẻ") || lower.contains("tiết kiệm") || lower.contains("hợp lý");

        return sc;
    }

    private double evaluateConstraintFit(SanPham sp, StructuredConstraints sc) {
        double score = 1.0;
        BigDecimal price = sp.getGiaBan();

        // Budget evaluation
        if (sc.targetBudget != null && sc.targetBudget.compareTo(BigDecimal.ZERO) > 0) {
            double ratio = price.doubleValue() / sc.targetBudget.doubleValue();
            if (ratio >= 0.8 && ratio <= 1.2) {
                score += 1.0;
            } else if (ratio >= 0.6 && ratio <= 1.5) {
                score += 0.5;
            } else {
                score -= 0.5;
            }
        } else if (sc.maxBudget != null) {
            if (price.compareTo(sc.maxBudget) <= 0) {
                score += 1.0;
            } else {
                score -= 1.0;
            }
        }

        // Stock availability boost
        if (sp.getSoLuongTon() > 0) {
            score += 0.3;
        }

        // Feature-specific boosts based on user soft preferences
        if (sc.hasCamera) {
            int mp = extractMainCameraMp(sp.getCameraSau());
            if (mp >= 108) score += 1.5;
            else if (mp >= 50) score += 1.0;
            else if (mp >= 48) score += 0.8;
            else if (mp > 0) score += 0.4;
        }

        if (sc.hasGaming) {
            String chip = sp.getChip() != null ? sp.getChip().toLowerCase(Locale.ROOT) : "";
            if (chip.contains("snapdragon 8") || chip.contains("a17") || chip.contains("a18")
                    || chip.contains("dimensity 9") || chip.contains("dimensity 8300")) {
                score += 1.5;
            } else if (chip.contains("snapdragon") || chip.contains("dimensity") || chip.contains("exynos 1480") || chip.contains("apple")) {
                score += 0.8;
            }
            if (sp.getTanSoQuet() != null && sp.getTanSoQuet() >= 120) {
                score += 0.5;
            }
        }

        if (sc.hasBattery) {
            if (sp.getPin() != null) {
                if (sp.getPin() >= 5500) score += 1.5;
                else if (sp.getPin() >= 5000) score += 1.0;
                else if (sp.getPin() >= 4500) score += 0.5;
            }
        }

        if (sc.hasValue) {
            if (sp.getDiemDanhGiaTb() != null && sp.getDiemDanhGiaTb().compareTo(BigDecimal.valueOf(4.0)) >= 0) {
                score += 0.8;
            }
            if (price != null && price.compareTo(BigDecimal.valueOf(15_000_000)) <= 0) {
                score += 0.5;
            }
        }

        return Math.max(score, 0.0);
    }

    private int extractMainCameraMp(String cameraSau) {
        if (cameraSau == null || cameraSau.isBlank()) return 0;
        Matcher m = Pattern.compile("(\\d+)\\s*mp", Pattern.CASE_INSENSITIVE).matcher(cameraSau);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /**
     * Explains the match using ONLY factual retrieved specifications.
     * NO artificial/fake justifications.
     */
    private String explainFit(SanPham sp, double vectorScore) {
        List<String> specs = new ArrayList<>();
        if (sp.getChip() != null && !sp.getChip().isBlank()) {
            specs.add("Chip " + sp.getChip());
        }
        if (sp.getTanSoQuet() != null && sp.getTanSoQuet() > 60) {
            specs.add("Màn hình " + sp.getTanSoQuet() + "Hz");
        }
        if (sp.getCameraSau() != null && !sp.getCameraSau().isBlank()) {
            specs.add("Camera sau " + sp.getCameraSau());
        }
        if (sp.getPin() != null) {
            specs.add("Pin " + sp.getPin() + "mAh");
        }
        if (sp.getCongSuatSac() != null && sp.getCongSuatSac() > 20) {
            specs.add("Sạc nhanh " + sp.getCongSuatSac() + "W");
        }
        if (specs.isEmpty()) {
            return "Độ tương đồng ngữ nghĩa: " + Math.round(vectorScore * 100) + "%";
        }
        return "Thông số kỹ thuật: " + String.join(", ", specs);
    }

    private List<String> extractKeyHighlights(SanPham sp) {
        List<String> hl = new ArrayList<>();
        if (sp.getChip() != null && !sp.getChip().isBlank()) hl.add("Chip " + sp.getChip());
        if (sp.getCameraSau() != null && !sp.getCameraSau().isBlank()) hl.add("Camera " + sp.getCameraSau());
        if (sp.getPin() != null) hl.add("Pin " + sp.getPin() + "mAh");
        if (sp.getTanSoQuet() != null && sp.getTanSoQuet() >= 120) hl.add("Màn hình " + sp.getTanSoQuet() + "Hz");
        return hl;
    }

    private List<SemanticSearchResultDto> fallbackSearch(String query, int limit) {
        log.info("Thực hiện fallback search cho query: '{}'", query);
        StructuredConstraints sc = parseConstraints(query);

        List<SanPham> active = sanPhamRepository.findAll().stream()
                .filter(s -> s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH && s.getSoLuongTon() > 0)
                .filter(s -> {
                    // Check required brand
                    if (sc.requiredBrand != null) {
                        String brand = s.getThuongHieu() != null ? s.getThuongHieu().getTenThuongHieu().toLowerCase() : "";
                        if (!brand.contains(sc.requiredBrand.toLowerCase())) return false;
                    }
                    // Check budget constraints
                    if (sc.maxBudget != null) {
                        int cmp = s.getGiaBan().compareTo(sc.maxBudget);
                        if (sc.maxInclusive ? cmp > 0 : cmp >= 0) return false;
                    }
                    if (sc.minBudget != null) {
                        int cmp = s.getGiaBan().compareTo(sc.minBudget);
                        if (sc.minInclusive ? cmp < 0 : cmp <= 0) return false;
                    }
                    return true;
                })
                .toList();

        if (active.isEmpty()) {
            return List.of();
        }

        // Rank active products by how well they match the query intent
        List<ScoredCandidate> scored = new ArrayList<>();
        for (SanPham sp : active) {
            double fitScore = evaluateConstraintFit(sp, sc);

            // Text match boost if model name or brand is mentioned in query
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            if (sp.getTenMay() != null && lowerQuery.contains(sp.getTenMay().toLowerCase())) {
                fitScore += 2.0;
            }

            scored.add(new ScoredCandidate(sp, fitScore, 0.5, explainFit(sp, 0.5)));
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::finalScore).reversed());

        return scored.stream()
                .limit(limit)
                .map(c -> new SemanticSearchResultDto(
                        SanPhamMapper.toDto(c.product),
                        Math.round(c.finalScore * 1000.0) / 1000.0,
                        c.explanation,
                        extractKeyHighlights(c.product),
                        "KEYWORD_FALLBACK"
                ))
                .toList();
    }

    private static class StructuredConstraints {
        String requiredBrand;
        BigDecimal targetBudget;
        BigDecimal minBudget;
        BigDecimal maxBudget;
        boolean strictMax = false;
        boolean maxInclusive = true;
        boolean minInclusive = true;
        boolean unlimitedBudget = false;
        boolean hasCamera = false;
        boolean hasGaming = false;
        boolean hasBattery = false;
        boolean hasValue = false;
    }

    private record ScoredCandidate(SanPham product, double finalScore, double vectorScore, String explanation) {}
}
