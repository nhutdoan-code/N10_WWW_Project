
package nhom10.phonenest.service.ai;

/*
 * @description: Các công cụ để AI tìm kiếm và tra cứu sản phẩm PhoneNest
 * @author: LuuVanQuoc
 * @version: 2.0
 */

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.dto.SemanticSearchResultDto;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.service.SanPhamService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PhoneNestProductTools {

    private final SemanticSearchService semanticSearchService;
    private final SanPhamService sanPhamService;

    // =========================================================
    // 1. TÌM KIẾM SẢN PHẨM THEO NGỮ NGHĨA
    //
    // Ví dụ:
    // - Điện thoại pin trâu
    // - Điện thoại chụp ảnh đẹp
    // - Điện thoại chơi game tốt
    // - Điện thoại dưới 10 triệu
    //
    // SemanticSearchService chịu trách nhiệm tìm trong database.
    // =========================================================

    @Tool(description = """
            Tìm kiếm điện thoại trong dữ liệu PhoneNest theo nhu cầu
            tự nhiên của khách hàng.

            Có thể tìm theo:
            - Pin lâu, pin trâu, thời lượng sử dụng
            - Camera đẹp, chụp ảnh tốt, quay video
            - Chơi game, hiệu năng, cấu hình
            - Giá tiền, thương hiệu, tên điện thoại
            - Các nhu cầu kết hợp nhiều tiêu chí.

            Luôn gọi công cụ này khi khách hàng muốn tìm hoặc được
            tư vấn sản phẩm. Không tự nghĩ ra sản phẩm.
            """)
    public List<SemanticSearchResultDto> searchProducts(
            @ToolParam(description = "Nhu cầu tìm kiếm nguyên bản của khách hàng")
            String query,

            @ToolParam(description = "Số lượng kết quả muốn lấy, tối đa 20")
            Integer limit
    ) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        int safeLimit = (limit == null || limit <= 0)
                ? 5
                : Math.min(limit, 20);

        return semanticSearchService.search(
                query.trim(),
                safeLimit
        );
    }

    // =========================================================
    // 2. LẤY CHI TIẾT SẢN PHẨM
    //
    // AI phải dùng mã nội bộ để tra cứu.
    // Mã này không được hiển thị trong câu trả lời khách hàng.
    // =========================================================

    @Tool(description = """
            Lấy thông tin chi tiết chính xác của một điện thoại
            từ database PhoneNest.

            Dùng khi khách hàng muốn:
            - Xem chi tiết sản phẩm
            - Tìm hiểu thông số kỹ thuật
            - Xem camera, pin, chip, màn hình
            - Kiểm tra giá và tình trạng kinh doanh.

            Chỉ truyền mã sản phẩm lấy từ kết quả tìm kiếm
            hoặc dữ liệu hội thoại đã được xác minh.
            """)
    public SanPhamDto getProductDetails(
            @ToolParam(description = "Mã sản phẩm trong database PhoneNest")
            String maSanPham
    ) {
        if (maSanPham == null || maSanPham.isBlank()) {
            return null;
        }

        return sanPhamService.get(
                maSanPham.trim(),
                false
        );
    }

    // =========================================================
    // 3. LẤY CÁC PHIÊN BẢN CỦA SẢN PHẨM
    //
    // Chỉ trả về phiên bản:
    // - Đang kinh doanh
    // - Còn hàng
    // - Có giá hợp lệ
    //
    // Backend vẫn phải kiểm tra lại khi thêm vào giỏ hàng.
    // =========================================================

    @Tool(description = """
            Lấy danh sách phiên bản thực tế của điện thoại trong
            database PhoneNest.

            Trả về màu sắc, RAM, bộ nhớ, giá bán và số lượng tồn.

            Dùng khi khách hàng muốn chọn phiên bản hoặc hỏi
            sản phẩm có những màu sắc, dung lượng nào.

            Không tự tạo phiên bản nếu database không có.
            """)
    public ProductVariantsResult getProductVariants(
            @ToolParam(description = "Mã sản phẩm trong database PhoneNest")
            String maSanPham
    ) {
        if (maSanPham == null || maSanPham.isBlank()) {
            return null;
        }

        SanPhamDto sanPham = sanPhamService.get(
                maSanPham.trim(),
                false
        );

        if (sanPham == null) {
            return null;
        }

        if (sanPham.trangThai() != TrangThaiSanPham.DANG_KINH_DOANH) {
            return new ProductVariantsResult(
                    sanPham.maSanPham(),
                    sanPham.tenMay(),
                    List.of()
            );
        }

        List<SanPhamDto.PhienBan> variants =
                sanPham.phienBans() == null
                        ? List.of()
                        : sanPham.phienBans().stream()
                        .filter(v -> v != null)
                        .filter(v -> v.trangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                        .filter(v -> v.soLuongTon() > 0)
                        .filter(v -> v.giaBan() != null)
                        .filter(v -> v.giaBan().signum() > 0)
                        .toList();

        return new ProductVariantsResult(
                sanPham.maSanPham(),
                sanPham.tenMay(),
                variants
        );
    }

    // =========================================================
    // 4. DTO KẾT QUẢ PHIÊN BẢN
    // =========================================================

    public record ProductVariantsResult(
            String maSanPham,
            String tenMay,
            List<SanPhamDto.PhienBan> phienBans
    ) {
    }
}
