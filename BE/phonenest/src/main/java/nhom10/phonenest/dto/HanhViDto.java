package nhom10.phonenest.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HanhViDto {

    private HanhViDto() {}

    public record EventRequest(
            @JsonAlias({"visitorId", "sessionId"})
            String sessionId,

            @NotNull(message = "Loại hành vi không được để trống")
            @JsonAlias({"type", "eventType", "loaiHanhVi"})
            LoaiHanhViNguoiDung loaiHanhVi,

            @JsonAlias({"productId", "maSanPham"})
            String maSanPham,

            String maPhienBan,

            @JsonAlias({"searchTerm", "tuKhoa"})
            String tuKhoa,

            @JsonAlias({"pageType", "nguonSuKien"})
            String nguonSuKien,

            String metadata
    ) {}

    public record EventResponse(
            String maHanhVi,
            boolean success,
            String message
    ) {}

    public record EventDetailDto(
            String maHanhVi,
            String maKh,
            String sessionId,
            String maSanPham,
            String tenSanPham,
            String maPhienBan,
            LoaiHanhViNguoiDung loaiHanhVi,
            String tuKhoa,
            String nguonSuKien,
            String metadata,
            LocalDateTime thoiGian
    ) {}

    public record TopProductStats(
            String maSanPham,
            String tenSanPham,
            long viewCount,
            long clickCount,
            long addToCartCount
    ) {}

    public record TopKeywordStats(
            String tuKhoa,
            long count
    ) {}

    public record BehaviorStatsResponse(
            long tongLuot,
            Map<String, Long> theoLoai,
            List<TopProductStats> sanPhamTuongTac,
            List<TopKeywordStats> topTuKhoa,
            List<EventDetailDto> suKienGanDay
    ) {
        public BehaviorStatsResponse(
                long tongLuot,
                Map<String, Long> theoLoai,
                List<TopProductStats> topSanPhamXem,
                List<TopProductStats> topSanPhamClick,
                List<TopKeywordStats> topTuKhoa,
                List<EventDetailDto> suKienGanDay
        ) {
            this(tongLuot, theoLoai, mergeProductLists(topSanPhamXem, topSanPhamClick), topTuKhoa, suKienGanDay);
        }

        private static List<TopProductStats> mergeProductLists(
                List<TopProductStats> viewed,
                List<TopProductStats> clicked
        ) {
            Map<String, TopProductStats> products = new LinkedHashMap<>();
            mergeProductList(products, viewed);
            mergeProductList(products, clicked);
            return List.copyOf(products.values());
        }

        private static void mergeProductList(Map<String, TopProductStats> products, List<TopProductStats> group) {
            if (group == null) return;
            for (TopProductStats product : group) {
                if (product == null) continue;
                products.merge(product.maSanPham(), product, (left, right) -> new TopProductStats(
                        left.maSanPham(),
                        left.tenSanPham() != null ? left.tenSanPham() : right.tenSanPham(),
                        left.viewCount() + right.viewCount(),
                        left.clickCount() + right.clickCount(),
                        left.addToCartCount() + right.addToCartCount()
                ));
            }
        }
    }

    public record UserPreferenceDto(
            String maKh,
            List<String> topThuongHieu,
            List<String> topDanhMuc,
            BigDecimal giaThapNhat,
            BigDecimal giaCaoNhat,
            List<String> tuKhoaGanDay,
            List<String> sanPhamQuanTam,
            long tongLuotTuongTac,
            String tomTatAi
    ) {}
}
