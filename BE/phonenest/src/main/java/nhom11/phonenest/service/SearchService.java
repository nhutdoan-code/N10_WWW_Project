package nhom11.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom11.phonenest.dto.SanPhamDto;
import nhom11.phonenest.enums.TrangThaiSanPham;
import nhom11.phonenest.mapper.SanPhamMapper;
import nhom11.phonenest.repository.SanPhamRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private static final String PROMPT = """
            Bạn là bộ máy tìm kiếm của cửa hàng điện thoại PhoneNest.
            Dựa vào danh sách sản phẩm bên dưới (mã | tên | hãng | giá | thông số), hãy chọn các sản phẩm phù hợp nhất với yêu cầu của khách.
            Chỉ trả về mảng JSON chứa mã sản phẩm, xếp từ phù hợp nhất, tối đa 5 mã, ví dụ ["SP001","SP002"].
            Nếu không có sản phẩm nào phù hợp thì trả về [].

            DANH SÁCH SẢN PHẨM:
            {danhSach}
            """;

    private final SanPhamService sanPhamService;
    private final SanPhamRepository sanPhamRepository;
    private final ChatClient chatClient;

    public List<SanPhamDto> nhanh(String q) {
        return sanPhamService.list(q, null, null, null, null, false).stream().limit(5).toList();
    }

    public List<SanPhamDto> ai(String q) {
        try {
            List<String> ma = chatClient.prompt()
                    .system(s -> s.text(PROMPT).param("danhSach", danhSach()))
                    .user(q)
                    .call()
                    .entity(new ParameterizedTypeReference<List<String>>() {
                    });
            return ma.stream()
                    .map(sanPhamRepository::findById).flatMap(Optional::stream)
                    .filter(s -> s.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH)
                    .map(SanPhamMapper::toDto)
                    .toList();
        } catch (Exception e) {
            return nhanh(q);
        }
    }

    private String danhSach() {
        return sanPhamService.list(null, null, null, null, null, false).stream()
                .map(s -> "%s | %s | %s | %s VND | %s".formatted(
                        s.maSanPham(), s.tenMay(), s.tenLoai(), s.giaBan().toBigInteger(), s.thongSoKyThuat()))
                .collect(Collectors.joining("\n"));
    }
}
