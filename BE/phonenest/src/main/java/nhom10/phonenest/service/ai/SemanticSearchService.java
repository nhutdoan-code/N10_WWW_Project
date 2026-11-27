package nhom10.phonenest.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.dto.SemanticSearchResultDto;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.service.PheDuyetBanHangPolicy;
import nhom10.phonenest.repository.SanPhamRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * <p>
 * Luu tru vector (QuocLuu): neu co bean {@code phoneNestMariaDbVectorStore} + JdbcTemplate (migration 21),
 * embedding duoc luu trong bang {@code phonenest_vector_store} va chi tinh lai khi noi dung san pham thay doi
 * (dau van tay SHA-256 trong {@code product_embedding_sync}). Neu khong co / loi CSDL (chua chay migration 21,
 * unit test), tu dong dung vector store trong bo nho {@link PhoneNestVectorStore} nhu truoc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {
    private static final Pattern PATTERN_BUDGET =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(triệu|tr)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_CAMERA_MP =
            Pattern.compile("(\\d+)\\s*mp", Pattern.CASE_INSENSITIVE);
    private final SanPhamRepository sanPhamRepository;
    private final PhoneNestEmbeddingModel embeddingModel;
    private final ProductSemanticDocumentBuilder documentBuilder;

    private VectorStore vectorStore;
    private volatile boolean initialized = false;
    // Cache số tài liệu, tránh COUNT(*) mỗi lần chat.
    private volatile int indexedDocumentCount = 0;

    /** Vector store luu trong MariaDB (tuy chon). null -> dung vector store trong bo nho. */
    private VectorStore persistentStore;
    private JdbcTemplate jdbcTemplate;
    private volatile boolean persistentFailed = false;

    @Autowired(required = false)
    void setPersistentStore(@Qualifier("phoneNestMariaDbVectorStore") VectorStore store, JdbcTemplate jdbcTemplate) {
        this.persistentStore = store;
        this.jdbcTemplate = jdbcTemplate;
    }

    private boolean dungPersistent() {
        return persistentStore != null && jdbcTemplate != null && !persistentFailed;
    }

    private void chuyenSangBoNho(Exception e) {
        if (!persistentFailed) {
            persistentFailed = true;
            initialized = false;
            indexedDocumentCount = 0;
            log.warn("Không dùng được vector store MariaDB (đã chạy migration 21 chưa?): {}. Chuyển sang vector store trong bộ nhớ.",
                    e.getMessage());
        }
    }

    private static boolean laLoiCsdl(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof org.springframework.dao.DataAccessException || current instanceof java.sql.SQLException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
    /**
     * 2B.7D: reindexAll() duoc goi noi bo (self-invocation) nen @Transactional khong co hieu luc ->
     * moi san pham loi "failed to lazily initialize ... phienBans" va vector store luon rong.
     * Dung TransactionTemplate de nap du lieu trong mot giao dich chi doc.
     */
    private TransactionTemplate docTx;

    @Autowired(required = false)
    void setTransactionManager(PlatformTransactionManager tm) {
        TransactionTemplate t = new TransactionTemplate(tm);
        t.setReadOnly(true);
        this.docTx = t;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        if (!embeddingModel.isAvailable()) {
            log.warn("Ollama chưa sẵn sàng. Hệ thống tạm dùng fallback và sẽ thử lập chỉ mục lại sau.");
            return;
        }
        try {
            reindexAll();
        } catch (Exception e) {
            log.warn("Chưa thể khởi tạo Vector Store lúc khởi động: {}", e.getMessage());
        }
    }

    /**
     * Thử lập chỉ mục lại khi Ollama sẵn sàng.
     * Không thực hiện công việc nặng trong request chat của khách hàng.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retryIndexing() {
        if (initialized || !embeddingModel.isAvailable()) {
            return;
        }
        try {
            log.info("Đang thử khởi tạo lại Semantic Vector Store...");
            reindexAll();
        } catch (Exception e) {
            log.warn("Thử lập chỉ mục lại thất bại: {}", e.getMessage());
        }
    }

    private synchronized VectorStore getVectorStore() {
        if (dungPersistent()) {
            return persistentStore;
        }
        if (vectorStore == null) {
            vectorStore = new PhoneNestVectorStore(embeddingModel);
        }
        return vectorStore;
    }

//    /** So tai lieu dang duoc lap chi muc (cho trang thai va kiem tra store rong). */
//    private long soTaiLieu() {
//        if (dungPersistent()) {
//            try {
//                Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_embedding_sync", Long.class);
//                return n == null ? 0 : n;
//            } catch (Exception e) {
//                chuyenSangBoNho(e);
//            }
//        }
//        return vectorStore instanceof PhoneNestVectorStore mem ? mem.size() : 0;
//    }
    // [CHỈNH SỬA] Không truy vấn COUNT(*) trong mỗi request tìm kiếm.
    private int soTaiLieu() {
        return indexedDocumentCount;
    }

    public synchronized int reindexAll() {

        if (dungPersistent()) {
            try {
                return dongBoPersistent();
            } catch (Exception e) {
                if (laLoiCsdl(e)) {
                    chuyenSangBoNho(e);
                } else {
                    initialized = false;
                    log.error("Không thể đồng bộ MariaDB Vector Store. " + "Giữ nguyên chế độ persistent và chờ thử lại.", e);
                    throw new IllegalStateException("Đồng bộ Semantic Vector Store thất bại.", e);
                }
            }
        }
        log.info("Bắt đầu tái lập chỉ mục Vector Store trong bộ nhớ bằng model {}...", embeddingModel.getModelName());
        VectorStore newStore = new PhoneNestVectorStore(embeddingModel);
        List<Document> docs = docTx != null
                ? docTx.execute(st -> taoTaiLieu())
                : taoTaiLieu();

        if (!docs.isEmpty()) {
            newStore.add(docs);
        }
        this.vectorStore = newStore;
        this.indexedDocumentCount = docs.size();
        this.initialized = true;
        log.info("Tái lập chỉ mục Vector Store hoàn tất: {} sản phẩm.", docs.size());
        return docs.size();
    }

    /**
     * Dong bo bang phonenest_vector_store voi san pham dang kinh doanh: chi embedding lai san pham co noi dung
     * thay doi, go san pham khong con kinh doanh.
     */
    private int dongBoPersistent() {
        log.info("Bắt đầu đồng bộ sản phẩm vào MariaDB Vector Store bằng model {}...",
                embeddingModel.getModelName());

        List<Document> docs = docTx != null
                ? docTx.execute(st -> taoTaiLieu())
                : taoTaiLieu();

        Set<String> activeIds = new HashSet<>();
        int changed = 0;
        boolean allSynced = true;

        for (Document doc : docs) {
            activeIds.add(doc.getId());

            try {
                if (dongBoTaiLieu(doc)) {
                    changed++;
                }
            } catch (RuntimeException e) {
                if (laLoiCsdl(e)) {
                    throw e;
                }
                allSynced = false;
                log.warn("Không thể đồng bộ embedding sản phẩm {}: {}", doc.getId(), e.getMessage()
                );
            }
        }

        // Xóa sản phẩm không còn kinh doanh khỏi vector store.
        List<String> stale = jdbcTemplate.queryForList(
                        "SELECT ma_san_pham FROM product_embedding_sync",
                        String.class
                )
                .stream()
                .filter(id -> !activeIds.contains(id))
                .toList();

        if (!stale.isEmpty()) {
            persistentStore.delete(stale);

            jdbcTemplate.batchUpdate(
                    "DELETE FROM product_embedding_sync WHERE ma_san_pham = ?",
                    stale,
                    100,
                    (ps, id) -> ps.setString(1, id)
            );
        }
        // Chỉ COUNT một lần sau khi đồng bộ, không COUNT mỗi lần khách tìm kiếm.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_embedding_sync",
                Integer.class
        );
        indexedDocumentCount = count != null ? count : 0;
        // Chỉ READY khi tất cả tài liệu đã đồng bộ thành công.
        initialized = allSynced;
        if (allSynced) {
            log.info("Đồng bộ MariaDB Vector Store hoàn tất: {} tài liệu, {} embedding mới/cập nhật, {} tài liệu đã gỡ.", indexedDocumentCount, changed, stale.size()
            );
        } else {
            log.warn("Đồng bộ chưa hoàn tất. Đã có {} tài liệu trong Vector Store. Hệ thống sẽ thử lại ở lần lập chỉ mục tiếp theo.",
                   indexedDocumentCount);
        }
        return indexedDocumentCount;
    }

    private void capNhatSoTaiLieu() {
        if (dungPersistent()) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM product_embedding_sync",
                        Integer.class
                );
                indexedDocumentCount = count != null ? count : 0;
                return;
            } catch (Exception e) {
                chuyenSangBoNho(e);
            }
        }
        VectorStore store = vectorStore;
        if (store instanceof PhoneNestVectorStore memoryStore) {
            indexedDocumentCount = memoryStore.size();
        }
    }

    /** true neu tai lieu thay doi va da embedding lai. */
    private boolean dongBoTaiLieu(Document doc) {
        String hash = bamNoiDung(doc);
        String cu = jdbcTemplate.query("SELECT content_hash FROM product_embedding_sync WHERE ma_san_pham = ?",
                rs -> rs.next() ? rs.getString(1) : null, doc.getId());
        if (hash.equals(cu)) return false;
        persistentStore.delete(List.of(doc.getId()));
        persistentStore.add(List.of(doc));
        jdbcTemplate.update("""
                INSERT INTO product_embedding_sync (ma_san_pham, content_hash) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash), updated_at = CURRENT_TIMESTAMP""",
                doc.getId(), hash);
        return true;
    }

    private static String bamNoiDung(Document doc) {
        try {
            StringBuilder sb = new StringBuilder(Objects.toString(doc.getText(), ""));
            new TreeMap<>(doc.getMetadata()).forEach((k, v) -> sb.append('|').append(k).append(':').append(v));
            byte[] h = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo dấu vân tay tài liệu sản phẩm", e);
        }
    }

    private void xoaPersistent(String maSanPham) {
        persistentStore.delete(List.of(maSanPham));
        jdbcTemplate.update("DELETE FROM product_embedding_sync WHERE ma_san_pham = ?", maSanPham);
    }

    public synchronized void indexProduct(SanPham sp) {
        if (sp == null) return;
        if (dungPersistent()) {
            try {
                if (sp.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH) {
                    if (dongBoTaiLieu(documentBuilder.build(sp))) {
                        log.info("Đã cập nhật embedding MariaDB cho sản phẩm {}", sp.getMaSanPham());
                    }
                } else {
                    xoaPersistent(sp.getMaSanPham());
                }
                capNhatSoTaiLieu();
                return;
            } catch (RuntimeException e) {
                if (!laLoiCsdl(e)) {
                    initialized = false;
                    log.warn("Lỗi khi cập nhật vector store cho sản phẩm {}: {}", sp.getMaSanPham(), e.getMessage());
                    return;
                }
                chuyenSangBoNho(e);
            }
        }
        VectorStore store = getVectorStore();
        try {
            store.delete(List.of(sp.getMaSanPham()));
            if (sp.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH) {
                Document doc = documentBuilder.build(sp);
                store.add(List.of(doc));
                log.info("Đã cập nhật vector store cho sản phẩm {}", sp.getMaSanPham());
            }
            capNhatSoTaiLieu();
        } catch (Exception e) {
            initialized = false;
            log.warn("Lỗi khi cập nhật vector store cho sản phẩm {}: {}", sp.getMaSanPham(), e.getMessage()
            );
        }
    }

    private List<Document> taoTaiLieu() {
        List<SanPham> activeProducts = sanPhamRepository.findAllWithPhienBans()
                .stream()
                .filter(s -> s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                .toList();
        List<Document> docs = new ArrayList<>();
        for (SanPham sp : activeProducts) {
            try {
                docs.add(documentBuilder.build(sp));
            } catch (Exception e) {
                log.error("Không thể tạo tài liệu embedding cho sản phẩm {}", sp.getMaSanPham(), e);
                throw new IllegalStateException("Lập chỉ mục thất bại tại sản phẩm " + sp.getMaSanPham(), e
                );
            }
        }
        return docs;
    }
    /** Tai lap tai lieu vector cua mot san pham trong giao dich chi doc rieng (dung sau khi giao dich khac da commit). */
    public void indexProductById(String maSanPham) {
        if (maSanPham == null || maSanPham.isBlank()) {
            return;
        }
        SanPham sp;
        if (docTx != null) {
            sp = docTx.execute(st -> sanPhamRepository.findByIdWithPhienBans(maSanPham).orElse(null));
        } else {
            sp = sanPhamRepository.findByIdWithPhienBans(maSanPham).orElse(null);
        }
        if (sp != null) {
            // Thực hiện cập nhật vector sau khi transaction đọc kết thúc.
            indexProduct(sp);
        }
    }

    public synchronized void removeProduct(String maSanPham) {
        if (maSanPham == null) return;
        if (dungPersistent()) {
            try {
                xoaPersistent(maSanPham);
                log.info("Đã gỡ sản phẩm {} khỏi vector store MariaDB", maSanPham);
                capNhatSoTaiLieu();
                return;
            } catch (RuntimeException e) {
                chuyenSangBoNho(e);
            }
        }
        try {
            getVectorStore().delete(List.of(maSanPham));
            capNhatSoTaiLieu();
            log.info("Đã gỡ sản phẩm {} khỏi vector store", maSanPham);
        } catch (Exception e) {
            initialized = false;
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
        if (!initialized) {
            return fallbackSearch(query, maxResults);
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
            if (dungPersistent() && laLoiCsdl(e)) {
                chuyenSangBoNho(e);
            }
            log.warn("Vector similarity search thất bại ({}), chuyển sang fallback: {}", e.getClass().getSimpleName(), e.getMessage());
            return fallbackSearch(query, maxResults);
        }

        if (rawCandidates.isEmpty()) {
            return fallbackSearch(query, maxResults);
        }

        // [CHỈNH SỬA] Lấy toàn bộ sản phẩm bằng một truy vấn thay vì findById từng lần.
        List<String> candidateIds = rawCandidates.stream()
                .map(Document::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, SanPham> productMap = sanPhamRepository.findAllById(candidateIds)
                .stream()
                .collect(Collectors.toMap(
                        SanPham::getMaSanPham,
                        sp -> sp,
                        (first, second) -> first
                ));

// Rank and filter candidates
        List<ScoredCandidate> scored = new ArrayList<>();

        for (Document doc : rawCandidates) {
            SanPham sp = productMap.get(doc.getId());
            if (sp == null) {
                continue;
            }
            if (sp.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH || sp.getSoLuongTon() <= 0) continue;

            // 2B.7D: chi dung gia DUOC CONG BO; san pham chua co gia cong bo khong qua duoc bo loc ngan sach.
            BigDecimal giaCB = PheDuyetBanHangPolicy.giaCongBo(sp);
            if ((constraints.maxBudget != null || constraints.minBudget != null) && giaCB == null) continue;

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
                    int cmp = giaCB.compareTo(constraints.maxBudget);
                    if (constraints.maxInclusive ? cmp > 0 : cmp >= 0) {
                        continue; // Exceeds strict budget ceiling
                    }
                } else {
                    BigDecimal ceiling = constraints.maxBudget.multiply(BigDecimal.valueOf(1.15));
                    if (giaCB.compareTo(ceiling) > 0) {
                        continue; // Exceeds fuzzy budget ceiling
                    }
                }
            }

            if (constraints.minBudget != null) {
                int cmp = giaCB.compareTo(constraints.minBudget);
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
        status.put("indexedDocuments", indexedDocumentCount);
        status.put("storage", dungPersistent() ? "MARIADB" : "IN_MEMORY");
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
        BigDecimal price = PheDuyetBanHangPolicy.giaCongBo(sp);
        if (price == null) {
            return score; // khong co gia cong bo: khong cham diem theo ngan sach, khong suy doan gia
        }

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
            if (mp >= 108) {
                score += 1.5;
            } else if (mp >= 50) {
                score += 1.0;
            } else if (mp >= 48) {
                score += 0.8;
            } else if (mp > 0) {
                score += 0.4;
            }
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
        if (cameraSau == null || cameraSau.isBlank()) {
            return 0;
        }
        Matcher m = PATTERN_CAMERA_MP.matcher(cameraSau);
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

        List<SanPham> active = sanPhamRepository
                .findAllWithPhienBansCoHang(TrangThaiSanPham.DANG_KINH_DOANH)
                .stream()
                .filter(s -> s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH && s.getSoLuongTon() > 0)
                .filter(s -> {
                    // Check required brand
                    if (sc.requiredBrand != null) {
                        String brand = s.getThuongHieu() != null ? s.getThuongHieu().getTenThuongHieu().toLowerCase() : "";
                        if (!brand.contains(sc.requiredBrand.toLowerCase())) return false;
                    }
                    // Check budget constraints (2B.7D: chi gia cong bo)
                    BigDecimal giaCB = PheDuyetBanHangPolicy.giaCongBo(s);
                    if ((sc.maxBudget != null || sc.minBudget != null) && giaCB == null) return false;
                    if (sc.maxBudget != null) {
                        int cmp = giaCB.compareTo(sc.maxBudget);
                        if (sc.maxInclusive ? cmp > 0 : cmp >= 0) return false;
                    }
                    if (sc.minBudget != null) {
                        int cmp = giaCB.compareTo(sc.minBudget);
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
