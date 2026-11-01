package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.ThuongHieuDto;
import nhom10.phonenest.entity.ThuongHieu;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.ThuongHieuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ThuongHieuService {

    private final ThuongHieuRepository thuongHieuRepository;
    private final SanPhamRepository sanPhamRepository;

    @Transactional(readOnly = true)
    public List<ThuongHieuDto> list() {
        return thuongHieuRepository.findAll().stream()
                .filter(ThuongHieu::isTrangThaiHoatDong)
                .sorted(Comparator.comparing(ThuongHieu::getTenThuongHieu, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ThuongHieuDto> listAll() {
        return thuongHieuRepository.findAll().stream()
                .sorted(Comparator.comparing(ThuongHieu::getTenThuongHieu, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getImpact(String ma) {
        ThuongHieu th = tim(ma);
        long soSanPham = sanPhamRepository.countByThuongHieuMaThuongHieu(ma);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("maThuongHieu", th.getMaThuongHieu());
        res.put("tenThuongHieu", th.getTenThuongHieu());
        res.put("trangThaiHoatDong", th.isTrangThaiHoatDong());
        res.put("soSanPhamLienQuan", soSanPham);
        res.put("productCount", soSanPham);
        res.put("coSanPhamLienQuan", soSanPham > 0);
        return res;
    }

    public ThuongHieuDto archive(String ma) {
        ThuongHieu th = tim(ma);
        if (!th.isTrangThaiHoatDong()) {
            return toDto(th);
        }
        th.setTrangThaiHoatDong(false);
        ThuongHieu saved = thuongHieuRepository.save(th);
        return toDto(saved);
    }

    public ThuongHieuDto restore(String ma) {
        ThuongHieu th = tim(ma);
        if (th.isTrangThaiHoatDong()) {
            return toDto(th);
        }
        th.setTrangThaiHoatDong(true);
        ThuongHieu saved = thuongHieuRepository.save(th);
        return toDto(saved);
    }

    private ThuongHieu tim(String ma) {
        return thuongHieuRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy thương hiệu"));
    }

    private ThuongHieuDto toDto(ThuongHieu t) {
        return new ThuongHieuDto(t.getMaThuongHieu(), t.getTenThuongHieu(), t.getLogo(), t.isTrangThaiHoatDong());
    }
}
