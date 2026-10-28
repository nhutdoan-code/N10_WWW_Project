package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.ThuongHieuDto;
import nhom10.phonenest.entity.ThuongHieu;
import nhom10.phonenest.repository.ThuongHieuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThuongHieuService {

    private final ThuongHieuRepository thuongHieuRepository;

    public List<ThuongHieuDto> list() {
        return thuongHieuRepository.findAll().stream()
                .filter(ThuongHieu::isTrangThaiHoatDong)
                .sorted(Comparator.comparing(ThuongHieu::getTenThuongHieu, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    private ThuongHieuDto toDto(ThuongHieu t) {
        return new ThuongHieuDto(t.getMaThuongHieu(), t.getTenThuongHieu(), t.getLogo());
    }
}
