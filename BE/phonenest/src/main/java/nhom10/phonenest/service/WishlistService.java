package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.Wishlist;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.WishlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final SanPhamRepository sanPhamRepository;

    @Transactional(readOnly = true)
    public List<SanPhamDto> list(String maKh) {
        return danhSach(maKh).getSanPhams().stream().sorted()
                .map(sanPhamRepository::findById).flatMap(Optional::stream)
                .map(SanPhamMapper::toDto).toList();
    }

    public List<SanPhamDto> add(String maKh, String ma) {
        if (!sanPhamRepository.existsById(ma)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
        danhSach(maKh).getSanPhams().add(ma);
        return list(maKh);
    }

    public List<SanPhamDto> remove(String maKh, String ma) {
        danhSach(maKh).getSanPhams().remove(ma);
        return list(maKh);
    }

    private Wishlist danhSach(String maKh) {
        return wishlistRepository.findByMaKh(maKh)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy danh sách yêu thích"));
    }
}
