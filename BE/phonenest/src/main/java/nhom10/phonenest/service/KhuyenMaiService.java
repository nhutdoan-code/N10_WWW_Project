package nhom10.phonenest.service;

import nhom10.phonenest.dto.KhuyenMaiDto;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class KhuyenMaiService {

    private final KhuyenMaiRepository khuyenMaiRepository;

    @Transactional(readOnly = true)
    public List<KhuyenMaiDto> list() {
        return khuyenMaiRepository.findAll().stream().map(this::toDto).toList();
    }

    public KhuyenMaiDto tao(KhuyenMaiDto d) {
        String ma = d.maKm().trim().toUpperCase();
        if (khuyenMaiRepository.existsById(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Mã khuyến mãi đã tồn tại");
        }
        return luu(new KhuyenMai(), ma, d);
    }

    public KhuyenMaiDto sua(String ma, KhuyenMaiDto d) {
        return luu(tim(ma), ma, d);
    }

    public void xoa(String ma) {
        khuyenMaiRepository.delete(tim(ma));
    }

    private KhuyenMaiDto luu(KhuyenMai k, String ma, KhuyenMaiDto d) {
        if (d.ngayKetThuc().isBefore(d.ngayBatDau())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }
        k.setMaKm(ma);
        k.setTenKm(d.tenKm().trim());
        k.setPhanTramGiam(d.phanTramGiam());
        k.setSoTienGiamToiDa(d.soTienGiamToiDa());
        k.setNgayBatDau(d.ngayBatDau());
        k.setNgayKetThuc(d.ngayKetThuc());
        k.setDieuKienApDung(d.dieuKienApDung());
        return toDto(khuyenMaiRepository.save(k));
    }

    private KhuyenMai tim(String ma) {
        return khuyenMaiRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy khuyến mãi"));
    }

    private KhuyenMaiDto toDto(KhuyenMai k) {
        return new KhuyenMaiDto(k.getMaKm(), k.getTenKm(), k.getPhanTramGiam(), k.getSoTienGiamToiDa(),
                k.getNgayBatDau(), k.getNgayKetThuc(), k.getDieuKienApDung());
    }
}
