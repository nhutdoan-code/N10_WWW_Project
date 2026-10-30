package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.LoaiSanPhamDto;
import nhom10.phonenest.dto.TimKiemDto;
import nhom10.phonenest.entity.LoaiSanPham;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.LoaiSanPhamRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimKiemService {

    private static final int GIOI_HAN = 20;

    private final SanPhamRepository sanPhamRepository;
    private final LoaiSanPhamRepository loaiSanPhamRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final DonHangRepository donHangRepository;
    private final TaiKhoanService taiKhoanService;
    private final DonHangMapper donHangMapper;

    public TimKiemDto tim(String q) {
        if (q == null || q.isBlank()) {
            return new TimKiemDto(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        String pattern = "%" + escapeLike(q.trim()) + "%";
        Pageable limit20 = PageRequest.of(0, GIOI_HAN);

        var sanPhams = sanPhamRepository.searchAdmin(pattern, limit20).stream()
                .map(SanPhamMapper::toDto)
                .toList();

        var loais = loaiSanPhamRepository.searchAdmin(pattern, limit20).stream()
                .map(this::toLoaiDto)
                .toList();

        var taiKhoans = taiKhoanRepository.searchAdmin(pattern, limit20).stream()
                .map(taiKhoanService::toDto)
                .toList();

        var donHangs = donHangRepository.searchAdmin(pattern, limit20).stream()
                .map(donHangMapper::toDto)
                .toList();

        return new TimKiemDto(sanPhams, loais, taiKhoans, donHangs);
    }

    private LoaiSanPhamDto toLoaiDto(LoaiSanPham l) {
        return new LoaiSanPhamDto(l.getMaLoai(), l.getTenLoai(), l.getMoTa(), l.getMaLoaiCha());
    }

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
