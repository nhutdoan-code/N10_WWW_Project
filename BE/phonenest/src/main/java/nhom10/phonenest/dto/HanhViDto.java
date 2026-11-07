package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotNull;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class HanhViDto {

    private HanhViDto() {}

    public record EventRequest(
            String sessionId,
            @NotNull(message = "Loại hành vi không được để trống")
            LoaiHanhViNguoiDung loaiHanhVi,
            String maSanPham,
            String maPhienBan,
            String tuKhoa,
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
            long addToCartCount,
            long purchaseCount
    ) {}

    public record TopKeywordStats(
            String tuKhoa,
            long count
    ) {}

    public record BehaviorStatsResponse(
            long tongLuot,
            Map<String, Long> theoLoai,
            List<TopProductStats> topSanPhamXem,
            List<TopProductStats> topSanPhamClick,
            List<TopKeywordStats> topTuKhoa,
            List<EventDetailDto> suKienGanDay
    ) {}

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
