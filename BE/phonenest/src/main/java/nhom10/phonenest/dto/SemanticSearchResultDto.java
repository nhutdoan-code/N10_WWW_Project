package nhom10.phonenest.dto;

import java.util.List;

public record SemanticSearchResultDto(
        SanPhamDto sanPham,
        double diemTuongDong,
        String lyDoPhuHop,
        List<String> dacDiemNoiBat,
        String retrievalMode
) {
    public SemanticSearchResultDto(SanPhamDto sanPham, double diemTuongDong, String lyDoPhuHop, List<String> dacDiemNoiBat) {
        this(sanPham, diemTuongDong, lyDoPhuHop, dacDiemNoiBat, "VECTOR_SEMANTIC");
    }
}
