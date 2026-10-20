package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.PhiVanChuyenDto;
import nhom10.phonenest.entity.PhiVanChuyen;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.PhiVanChuyenRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PhiVanChuyenService {

    private static final String KHAC = "Khác";

    private final PhiVanChuyenRepository phiVanChuyenRepository;

    @Transactional(readOnly = true)
    public List<PhiVanChuyenDto> list() {
        return phiVanChuyenRepository.findAll().stream().map(this::toDto).toList();
    }

    public PhiVanChuyenDto tao(PhiVanChuyenDto d) {
        if (phiVanChuyenRepository.existsByKhuVucIgnoreCase(d.khuVuc().trim())) {
            throw new AppException(HttpStatus.CONFLICT, "Khu vực này đã có bảng phí");
        }
        PhiVanChuyen p = new PhiVanChuyen();
        p.setMaPhi(IdGenerator.next("PVC"));
        return luu(p, d);
    }

    public PhiVanChuyenDto sua(String ma, PhiVanChuyenDto d) {
        PhiVanChuyen p = tim(ma);
        if (KHAC.equals(p.getKhuVuc()) && !KHAC.equals(d.khuVuc().trim())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không đổi tên khu vực mặc định \"Khác\"");
        }
        return luu(p, d);
    }

    public void xoa(String ma) {
        PhiVanChuyen p = tim(ma);
        if (KHAC.equals(p.getKhuVuc())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể xóa khu vực mặc định \"Khác\"");
        }
        phiVanChuyenRepository.delete(p);
    }

    @Transactional(readOnly = true)
    public PhiVanChuyen theoDiaChi(String tinhThanh) {
        List<PhiVanChuyen> all = phiVanChuyenRepository.findAll();
        String key = chuanHoa(tinhThanh);
        return all.stream().filter(p -> !key.isEmpty() && chuanHoa(p.getKhuVuc()).equals(key)).findFirst()
                .or(() -> all.stream().filter(p -> KHAC.equals(p.getKhuVuc())).findFirst())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Chưa cấu hình phí vận chuyển cho khu vực này"));
    }

    public BigDecimal tinhPhi(PhiVanChuyen p, BigDecimal tamTinh) {
        boolean mienPhi = p.getMienPhiTu() != null && tamTinh.compareTo(p.getMienPhiTu()) >= 0;
        return mienPhi ? BigDecimal.ZERO : p.getPhiCoBan();
    }

    private String chuanHoa(String s) {
        if (s == null) {
            return "";
        }
        String bo = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("[^a-z0-9]", "");
        return bo.replaceFirst("^(thanhpho|tinh|tp)", "");
    }

    private PhiVanChuyenDto luu(PhiVanChuyen p, PhiVanChuyenDto d) {
        p.setKhuVuc(d.khuVuc().trim());
        p.setPhiCoBan(d.phiCoBan());
        p.setMienPhiTu(d.mienPhiTu());
        p.setThoiGianGiao(d.thoiGianGiao());
        return toDto(phiVanChuyenRepository.save(p));
    }

    private PhiVanChuyen tim(String ma) {
        return phiVanChuyenRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy bảng phí vận chuyển"));
    }

    private PhiVanChuyenDto toDto(PhiVanChuyen p) {
        return new PhiVanChuyenDto(p.getMaPhi(), p.getKhuVuc(), p.getPhiCoBan(), p.getMienPhiTu(), p.getThoiGianGiao());
    }
}
