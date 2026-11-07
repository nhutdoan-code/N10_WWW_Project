package nhom10.phonenest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDiffUtilTest {

    private AuditDiffUtil auditDiffUtil;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditDiffUtil = new AuditDiffUtil(objectMapper);
    }

    @Test
    @DisplayName("shouldCreateDiffWhenBeforeIsNullAndAfterIsPresent")
    void shouldCreateDiffWhenBeforeIsNullAndAfterIsPresent() {
        Map<String, Object> after = Map.of("tenMay", "iPhone 15", "giaBan", 25000000);

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(null, after);

        assertThat(diff.coThayDoi()).isTrue();
        assertThat(diff.duLieuTruoc()).isNull();
        assertThat(diff.duLieuSau()).contains("\"tenMay\":\"iPhone 15\"");
        assertThat(diff.duLieuSau()).contains("\"giaBan\":25000000");
    }

    @Test
    @DisplayName("shouldDeleteDiffWhenBeforeIsPresentAndAfterIsNull")
    void shouldDeleteDiffWhenBeforeIsPresentAndAfterIsNull() {
        Map<String, Object> before = Map.of("tenMay", "iPhone 15", "giaBan", 25000000);

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(before, null);

        assertThat(diff.coThayDoi()).isTrue();
        assertThat(diff.duLieuTruoc()).contains("\"tenMay\":\"iPhone 15\"");
        assertThat(diff.duLieuSau()).isNull();
    }

    @Test
    @DisplayName("shouldReturnNoOpWhenBothBeforeAndAfterAreNullOrEmpty")
    void shouldReturnNoOpWhenBothBeforeAndAfterAreNullOrEmpty() {
        AuditDiffUtil.KetQuaDiff diff1 = auditDiffUtil.tinhDiff(null, null);
        AuditDiffUtil.KetQuaDiff diff2 = auditDiffUtil.tinhDiff(Collections.emptyMap(), Collections.emptyMap());

        assertThat(diff1.coThayDoi()).isFalse();
        assertThat(diff1.duLieuTruoc()).isNull();
        assertThat(diff1.duLieuSau()).isNull();

        assertThat(diff2.coThayDoi()).isFalse();
        assertThat(diff2.duLieuTruoc()).isNull();
        assertThat(diff2.duLieuSau()).isNull();
    }

    @Test
    @DisplayName("shouldReturnNoOpWhenNoFieldsChanged")
    void shouldReturnNoOpWhenNoFieldsChanged() {
        Map<String, Object> before = Map.of("tenMay", "Galaxy S24", "giaBan", 20000000);
        Map<String, Object> after = Map.of("tenMay", "Galaxy S24", "giaBan", 20000000);

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(before, after);

        assertThat(diff.coThayDoi()).isFalse();
        assertThat(diff.duLieuTruoc()).isNull();
        assertThat(diff.duLieuSau()).isNull();
    }

    @Test
    @DisplayName("shouldDetectScalarChangesOnlyForChangedFields")
    void shouldDetectScalarChangesOnlyForChangedFields() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("tenMay", "iPhone 15");
        before.put("giaBan", 25000000);
        before.put("trangThai", "DANG_KINH_DOANH");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("tenMay", "iPhone 15"); // unchanged
        after.put("giaBan", 26000000);     // changed
        after.put("trangThai", "NGUNG_KINH_DOANH"); // changed

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(before, after);

        assertThat(diff.coThayDoi()).isTrue();
        assertThat(diff.duLieuTruoc()).doesNotContain("iPhone 15");
        assertThat(diff.duLieuSau()).doesNotContain("iPhone 15");
        assertThat(diff.duLieuTruoc()).contains("\"giaBan\":25000000");
        assertThat(diff.duLieuSau()).contains("\"giaBan\":26000000");
        assertThat(diff.duLieuTruoc()).contains("\"trangThai\":\"DANG_KINH_DOANH\"");
        assertThat(diff.duLieuSau()).contains("\"trangThai\":\"NGUNG_KINH_DOANH\"");
    }

    @Test
    @DisplayName("shouldHandleNullToValueAndValueToNull")
    void shouldHandleNullToValueAndValueToNull() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("moTa", null);
        before.put("ghiChu", "Cũ");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("moTa", "Mới");
        after.put("ghiChu", null);

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(before, after);

        assertThat(diff.coThayDoi()).isTrue();
        assertThat(diff.duLieuTruoc()).contains("\"moTa\":null");
        assertThat(diff.duLieuSau()).contains("\"moTa\":\"Mới\"");
        assertThat(diff.duLieuTruoc()).contains("\"ghiChu\":\"Cũ\"");
        assertThat(diff.duLieuSau()).contains("\"ghiChu\":null");
    }

    @Test
    @DisplayName("shouldTreatEqualBigDecimalsWithDifferentScaleAsEqual")
    void shouldTreatEqualBigDecimalsWithDifferentScaleAsEqual() {
        Map<String, Object> before = Map.of("giaBan", new BigDecimal("12.50"));
        Map<String, Object> after = Map.of("giaBan", new BigDecimal("12.5"));

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(before, after);

        assertThat(diff.coThayDoi()).isFalse();
    }

    @Test
    @DisplayName("shouldTreatNumericEqualPrimitivesAsEqual")
    void shouldTreatNumericEqualPrimitivesAsEqual() {
        Map<String, Object> before = Map.of("soLuong", 10);
        Map<String, Object> after = Map.of("soLuong", 10L);

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(before, after);

        assertThat(diff.coThayDoi()).isFalse();
    }

    @Test
    @DisplayName("shouldDetectNestedMapChanges")
    void shouldDetectNestedMapChanges() {
        Map<String, Object> thongSoTruoc = Map.of("ram", 8, "rom", 128);
        Map<String, Object> thongSoSau = Map.of("ram", 8, "rom", 256);

        Map<String, Object> before = Map.of("thongSo", thongSoTruoc);
        Map<String, Object> after = Map.of("thongSo", thongSoSau);

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(before, after);

        assertThat(diff.coThayDoi()).isTrue();
        assertThat(diff.duLieuTruoc()).contains("\"rom\":128");
        assertThat(diff.duLieuSau()).contains("\"rom\":256");
    }

    @Test
    @DisplayName("shouldDetectNestedListChanges")
    void shouldDetectNestedListChanges() {
        Map<String, Object> before = Map.of("tags", List.of("apple", "pro"));
        Map<String, Object> after = Map.of("tags", List.of("apple", "pro", "max"));

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(before, after);

        assertThat(diff.coThayDoi()).isTrue();
        assertThat(diff.duLieuSau()).contains("max");
    }

    @Test
    @DisplayName("shouldRemoveSensitiveKeysRecursively")
    void shouldRemoveSensitiveKeysRecursively() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("apiKey", "SECRET_KEY_123");
        nested.put("safeField", "PUBLIC_DATA");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("password", "PlainPassword123");
        profile.put("matKhau", "Hash123456");
        profile.put("token", "jwt.token.here");
        profile.put("accessToken", "access.token.here");
        profile.put("refreshToken", "refresh.token.here");
        profile.put("authorization", "Bearer xyz");
        profile.put("jwt", "header.payload.sig");
        profile.put("secret", "top-secret");
        profile.put("nested", nested);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("authorization", "Bearer inner");
        item.put("itemCode", "IT001");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("email", "admin@phonenest.com");
        source.put("profile", profile);
        source.put("items", List.of(item));

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(null, source);

        assertThat(diff.coThayDoi()).isTrue();
        String json = diff.duLieuSau();

        // Sensitive keys must be completely removed
        assertThat(json).doesNotContain("PlainPassword123");
        assertThat(json).doesNotContain("Hash123456");
        assertThat(json).doesNotContain("jwt.token.here");
        assertThat(json).doesNotContain("access.token.here");
        assertThat(json).doesNotContain("refresh.token.here");
        assertThat(json).doesNotContain("Bearer xyz");
        assertThat(json).doesNotContain("Bearer inner");
        assertThat(json).doesNotContain("SECRET_KEY_123");
        assertThat(json).doesNotContain("top-secret");
        assertThat(json).doesNotContain("\"password\"");
        assertThat(json).doesNotContain("\"matKhau\"");
        assertThat(json).doesNotContain("\"token\"");
        assertThat(json).doesNotContain("\"apiKey\"");

        // Safe fields must remain
        assertThat(json).contains("\"email\":\"admin@phonenest.com\"");
        assertThat(json).contains("\"safeField\":\"PUBLIC_DATA\"");
        assertThat(json).contains("\"itemCode\":\"IT001\"");
    }

    @Test
    @DisplayName("shouldPreservePayloadBelowLimit")
    void shouldPreservePayloadBelowLimit() {
        Map<String, Object> data = Map.of("content", "A".repeat(500));

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(null, data);

        assertThat(diff.coThayDoi()).isTrue();
        assertThat(diff.duLieuSau()).doesNotContain("\"_truncated\":true");
        assertThat(diff.duLieuSau()).contains("A".repeat(500));
    }

    @Test
    @DisplayName("shouldTruncatePayloadAboveLimitWithoutSplittingJsonOrSurrogates")
    void shouldTruncatePayloadAboveLimitWithoutSplittingJsonOrSurrogates() throws Exception {
        // String with surrogate pair (emoji) repeated to exceed 20,000 limit
        String emoji = "\uD83D\uDE00"; // 😀 (surrogate pair)
        String large = emoji.repeat(15000); // 30,000 chars

        Map<String, Object> data = Map.of("largeField", large);

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(null, data);

        assertThat(diff.coThayDoi()).isTrue();
        String json = diff.duLieuSau();

        assertThat(json.length()).isLessThanOrEqualTo(AuditDiffUtil.GIOI_HAN_PAYLOAD);
        assertThat(json).contains("\"_truncated\":true");
        assertThat(json).contains("\"_originalLength\":");
        assertThat(json).contains("\"_preview\":");

        // Verify JSON is completely valid and parseable by Jackson
        var tree = objectMapper.readTree(json);
        assertThat(tree.get("_truncated").asBoolean()).isTrue();
        assertThat(tree.get("_originalLength").asInt()).isGreaterThan(20000);
        assertThat(tree.get("_preview").asText()).isNotEmpty();
    }
}
