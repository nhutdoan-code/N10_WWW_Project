package nhom10.phonenest.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.SemanticSearchResultDto;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.SanPhamRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private static final Pattern PATTERN_BUDGET = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(triệu|tr)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of(
            "toi", "minh", "muon", "can", "tim", "mua", "cho", "hoi", "tu", "co", "la", "va",
            "duoi", "tren", "tam", "khoang", "gia", "dien", "thoai", "san", "pham", "may",
            "hang", "nao", "gi", "khong", "voi", "mot", "cai", "chiec", "nhe", "tot"
    );

    private final SanPhamRepository sanPhamRepository;
    private final PhoneNestEmbeddingModel embeddingModel;
    private final ProductSemanticDocumentBuilder documentBuilder;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    private volatile boolean initialized = false;
    private volatile boolean syncTableReady = false;

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        try {
            if (isEmbeddingAvailable()) reindexAll();
            else log.warn("Ollama chưa sẵn sàng. Sẽ thử đồng bộ khi có truy vấn.");
        } catch (Exception e) {
            log.warn("Không thể khởi tạo Vector Store: {}", e.getMessage());
        }
    }

    private synchronized void ensureSyncTable() {
        if (syncTableReady) return;
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_embedding_sync (
                    ma_san_pham VARCHAR(100) PRIMARY KEY,
                    content_hash CHAR(64) NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )""");
        syncTableReady = true;
    }

    private String calculateHash(Document doc) {
        try {
            StringBuilder sb = new StringBuilder(doc.getText());
            new TreeMap<>(doc.getMetadata()).forEach((k, v) -> sb.append("|").append(k).append(":").append(v));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo hash sản phẩm", e);
        }
    }

    private String findStoredHash(String maSanPham) {
        return jdbcTemplate.query("SELECT content_hash FROM product_embedding_sync WHERE ma_san_pham = ?",
                rs -> rs.next() ? rs.getString(1) : null, maSanPham);
    }

    private void saveHash(String maSanPham, String hash) {
        jdbcTemplate.update("""
                INSERT INTO product_embedding_sync (ma_san_pham, content_hash) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash), updated_at = CURRENT_TIMESTAMP""",
                maSanPham, hash);
    }

    private boolean syncDocument(SanPham sp) {
        String id = sp.getMaSanPham();
        Document doc = documentBuilder.build(sp);
        String newHash = calculateHash(doc);
        if (newHash.equals(findStoredHash(id))) return false;

        vectorStore.delete(List.of(id));
        vectorStore.add(List.of(doc));
        saveHash(id, newHash);
        return true;
    }

    public synchronized void removeProduct(String maSanPham) {
        if (maSanPham == null || maSanPham.isBlank()) return;
        try {
            ensureSyncTable();
            vectorStore.delete(List.of(maSanPham));
            jdbcTemplate.update("DELETE FROM product_embedding_sync WHERE ma_san_pham = ?", maSanPham);
            log.info("Đã gỡ sản phẩm {} khỏi Vector Store", maSanPham);
        } catch (Exception e) {
            log.warn("Lỗi khi gỡ sản phẩm {}: {}", maSanPham, e.getMessage());
        }
    }

    public synchronized int reindexAll() {
        if (!isEmbeddingAvailable()) {
            initialized = false;
            return 0;
        }
        ensureSyncTable();
        log.info("Bắt đầu đồng bộ sản phẩm vào MariaDB Vector Store...");

        Set<String> activeIds = new HashSet<>();
        int indexedCount = 0, changedCount = 0;

        for (SanPham sp : sanPhamRepository.findAllWithPhienBans()) {
            if (sp.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH) continue;
            String id = sp.getMaSanPham();
            activeIds.add(id);
            try {
                if (syncDocument(sp)) {
                    changedCount++;
                    log.info("Đã cập nhật embedding sản phẩm {}", id);
                }
                indexedCount++;
            } catch (Exception e) {
                log.warn("Không thể đồng bộ sản phẩm {}: {}", id, e.getMessage());
            }
        }

        List<String> staleIds = jdbcTemplate.queryForList("SELECT ma_san_pham FROM product_embedding_sync", String.class)
                .stream().filter(id -> !activeIds.contains(id)).toList();

        if (!staleIds.isEmpty()) {
            vectorStore.delete(staleIds);
            jdbcTemplate.batchUpdate("DELETE FROM product_embedding_sync WHERE ma_san_pham = ?", staleIds, 100, (ps, id) -> ps.setString(1, id));
        }

        initialized = true;
        log.info("Đồng bộ hoàn tất: {} sản phẩm, {} sản phẩm cần embedding lại.", indexedCount, changedCount);
        return indexedCount;
    }

    public synchronized void indexProduct(SanPham sp) {
        if (sp == null || sp.getMaSanPham() == null) return;
        try {
            ensureSyncTable();
            SanPham product = sanPhamRepository.findByIdWithPhienBans(sp.getMaSanPham()).orElse(null);
            if (product == null || product.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH) {
                removeProduct(sp.getMaSanPham());
                return;
            }
            if (syncDocument(product)) log.info("Đã đồng bộ embedding sản phẩm {}", product.getMaSanPham());
        } catch (Exception e) {
            log.warn("Lỗi cập nhật embedding sản phẩm {}: {}", sp.getMaSanPham(), e.getMessage());
        }
    }

    private String normalize(String input) {
        if (input == null) return "";
        String res = Normalizer.normalize(input.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return res.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private boolean containsPhrase(String source, String phrase) {
        return source != null && phrase != null && !source.isBlank() && !phrase.isBlank()
                && (" " + source + " ").contains(" " + phrase + " ");
    }

    private String resolveBrand(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) return null;

        String bestBrand = null;
        int bestLength = 0;

        for (SanPham sp : sanPhamRepository.findAll()) {
            if (sp.getThuongHieu() == null) continue;
            String brand = sp.getThuongHieu().getTenThuongHieu();
            String normBrand = normalize(brand);
            String normName = normalize(sp.getTenMay());

            if (!normBrand.isBlank() && normBrand.length() > bestLength && containsPhrase(normalizedQuery, normBrand)) {
                bestBrand = brand;
                bestLength = normBrand.length();
            }
            if (!normName.isBlank() && normName.length() > bestLength && containsPhrase(normalizedQuery, normName)) {
                bestBrand = brand;
                bestLength = normName.length();
            }
        }
        return bestBrand;
    }

    private Constraints parseConstraints(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        String brand = resolveBrand(query);
        BigDecimal targetBudget = null, maxBudget = null;

        Matcher matcher = PATTERN_BUDGET.matcher(lower);
        if (matcher.find()) {
            try {
                double val = Double.parseDouble(matcher.group(1).replace(',', '.'));
                BigDecimal amount = BigDecimal.valueOf(Math.round(val * 1_000_000));

                if (List.of("dưới", "duoi", "tối đa", "toi da", "không quá", "khong qua").stream().anyMatch(lower::contains)) {
                    maxBudget = amount;
                } else if (List.of("khoảng", "khoang", "tầm", "tam").stream().anyMatch(lower::contains)) {
                    targetBudget = amount;
                    maxBudget = amount.multiply(BigDecimal.valueOf(1.25));
                } else {
                    targetBudget = amount;
                }
            } catch (Exception e) {
                log.debug("Không thể phân tích ngân sách: {}", e.getMessage());
            }
        }
        return new Constraints(brand, targetBudget, maxBudget);
    }

    private boolean hasAvailableVariant(SanPham sp) {
        return !documentBuilder.getPhienBanConHang(sp).isEmpty();
    }

    private BigDecimal getAvailablePrice(SanPham sp) {
        return documentBuilder.getGiaThapNhat(sp);
    }

    private boolean matchesConstraints(SanPham sp, Constraints c) {
        if (sp.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH || !hasAvailableVariant(sp)) return false;
        if (c.requiredBrand != null) {
            String brand = sp.getThuongHieu() != null ? sp.getThuongHieu().getTenThuongHieu() : "";
            if (!normalize(brand).equals(normalize(c.requiredBrand))) return false;
        }
        return c.maxBudget == null || getAvailablePrice(sp).compareTo(c.maxBudget) <= 0;
    }

    private double evaluateConstraintFit(SanPham sp, Constraints c) {
        double score = 1.0;
        BigDecimal price = getAvailablePrice(sp);

        if (c.targetBudget != null && c.targetBudget.compareTo(BigDecimal.ZERO) > 0) {
            double ratio = price.doubleValue() / c.targetBudget.doubleValue();
            score += (ratio >= 0.8 && ratio <= 1.2) ? 1.0 : (ratio >= 0.6 && ratio <= 1.5) ? 0.5 : -0.5;
        } else if (c.maxBudget != null) {
            score += price.compareTo(c.maxBudget) <= 0 ? 1.0 : -1.0;
        }
        return Math.max(score + (hasAvailableVariant(sp) ? 0.3 : 0.0), 0.0);
    }

    private String explainFit(SanPham sp, double vectorScore) {
        List<String> specs = extractSpecs(sp, true);
        return specs.isEmpty()
                ? "Độ tương đồng ngữ nghĩa: " + Math.round(vectorScore * 100) + "%"
                : "Thông số kỹ thuật: " + String.join(", ", specs);
    }

    private List<String> extractSpecs(SanPham sp, boolean detailed) {
        List<String> list = new ArrayList<>();
        if (sp.getChip() != null && !sp.getChip().isBlank()) list.add("Chip " + sp.getChip());
        if (sp.getTanSoQuet() != null && sp.getTanSoQuet() >= (detailed ? 61 : 120)) list.add("Màn hình " + sp.getTanSoQuet() + "Hz");
        if (sp.getCameraSau() != null && !sp.getCameraSau().isBlank()) list.add((detailed ? "Camera sau " : "Camera ") + sp.getCameraSau());
        if (sp.getPin() != null) list.add("Pin " + sp.getPin() + "mAh");
        if (detailed && sp.getCongSuatSac() != null && sp.getCongSuatSac() > 20) list.add("Sạc nhanh " + sp.getCongSuatSac() + "W");
        return list;
    }

    @Transactional(readOnly = true)
    public List<SemanticSearchResultDto> search(String query, Integer limit) {
        if (query == null || query.isBlank()) return List.of();
        int maxResults = (limit != null && limit > 0) ? Math.min(limit, 20) : 5;
        Constraints constraints = parseConstraints(query);

        if (!isEmbeddingAvailable()) {
            log.warn("Ollama không khả dụng. Chuyển sang KEYWORD_FALLBACK.");
            return fallbackSearch(query, maxResults, constraints);
        }
        if (!initialized) reindexAll();

        List<Document> candidates;
        try {
            candidates = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(maxResults * 4, 15))
                    .similarityThreshold(0.0)
                    .build());
        } catch (Exception e) {
            log.warn("Vector search thất bại: {}", e.getMessage());
            return fallbackSearch(query, maxResults, constraints);
        }

        if (candidates == null || candidates.isEmpty()) return fallbackSearch(query, maxResults, constraints);

        List<ScoredCandidate> scored = new ArrayList<>();
        for (Document doc : candidates) {
            SanPham sp = sanPhamRepository.findById(doc.getId()).orElse(null);
            if (sp == null || !matchesConstraints(sp, constraints)) continue;

            double vectorScore = doc.getScore() != null ? doc.getScore() : 0.5;
            double finalScore = vectorScore * 0.70 + evaluateConstraintFit(sp, constraints) * 0.30;
            scored.add(new ScoredCandidate(sp, finalScore, explainFit(sp, vectorScore)));
        }

        return scored.isEmpty() ? fallbackSearch(query, maxResults, constraints) : toDtoList(scored, maxResults, "VECTOR_SEMANTIC");
    }

    private List<SemanticSearchResultDto> fallbackSearch(String query, int limit, Constraints constraints) {
        log.info("Thực hiện fallback keyword search: '{}'", query);
        Set<String> keywords = Arrays.stream(normalize(query).split("\\s+"))
                .filter(w -> w.length() >= 2 && !STOP_WORDS.contains(w))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (keywords.isEmpty()) return List.of();

        List<ScoredCandidate> scored = sanPhamRepository.findAllWithPhienBans().stream()
                .filter(sp -> matchesConstraints(sp, constraints))
                .map(sp -> {
                    String text = normalize(String.join(" ",
                            Objects.toString(sp.getTenMay(), ""),
                            sp.getThuongHieu() != null ? sp.getThuongHieu().getTenThuongHieu() : "",
                            Objects.toString(sp.getMoTa(), ""),
                            Objects.toString(sp.getChip(), ""),
                            Objects.toString(sp.getCameraSau(), ""),
                            Objects.toString(sp.getHeDieuHanh(), "")
                    ));
                    long matched = keywords.stream().filter(w -> containsPhrase(text, w)).count();
                    return new ScoredCandidate(sp, (double) matched / keywords.size(), "Kết quả tìm kiếm từ dữ liệu PhoneNest");
                })
                .filter(c -> c.finalScore > 0)
                .toList();

        return toDtoList(scored, limit, "KEYWORD_FALLBACK");
    }

    private List<SemanticSearchResultDto> toDtoList(List<ScoredCandidate> list, int limit, String mode) {
        return list.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::finalScore).reversed())
                .limit(limit)
                .map(c -> new SemanticSearchResultDto(
                        SanPhamMapper.toDto(c.product),
                        Math.round(c.finalScore * 1000.0) / 1000.0,
                        c.explanation,
                        extractSpecs(c.product, false),
                        mode
                ))
                .toList();
    }

    public Map<String, Object> getStatus() {
        boolean available = isEmbeddingAvailable();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("provider", "Ollama");
        status.put("model", embeddingModel.getModelName());
        status.put("baseUrl", embeddingModel.getBaseUrl());
        status.put("dimensions", embeddingModel.dimensions());
        status.put("available", available);
        status.put("initialized", initialized);

        try {
            ensureSyncTable();
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_embedding_sync", Long.class);
            status.put("indexedDocuments", count != null ? count : 0);
        } catch (Exception e) {
            status.put("indexedDocuments", 0);
        }

        status.put("mode", available ? "VECTOR_SEMANTIC" : "KEYWORD_FALLBACK");
        if (!available) status.put("reason", "Không thể kết nối Ollama tại " + embeddingModel.getBaseUrl());
        return status;
    }

    public boolean isEmbeddingAvailable() {
        return embeddingModel != null && embeddingModel.isAvailable();
    }

    private record Constraints(String requiredBrand, BigDecimal targetBudget, BigDecimal maxBudget) {}
    private record ScoredCandidate(SanPham product, double finalScore, String explanation) {}
}