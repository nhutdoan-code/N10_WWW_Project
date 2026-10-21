package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.PhiVanChuyenDto;
import nhom10.phonenest.entity.PhiVanChuyen;
import nhom10.phonenest.entity.TinhThanh;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.PhiVanChuyenRepository;
import nhom10.phonenest.repository.TinhThanhRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PhiVanChuyenService {

    private static final String MAC_DINH = "Khu vực khác (mặc định)";

    private final PhiVanChuyenRepository phiVanChuyenRepository;
    private final TinhThanhRepository tinhThanhRepository;

    @Transactional(readOnly = true)
    public List<PhiVanChuyenDto> list() {
        return phiVanChuyenRepository.findAll().stream().map(this::toDto).toList();
    }

    public PhiVanChuyenDto tao(PhiVanChuyenDto d) {
        if (d.maTinh() == null || d.maTinh().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn tỉnh / thành phố");
        }
        if (!tinhThanhRepository.existsById(d.maTinh())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Tỉnh / thành phố không hợp lệ");
        }
        if (phiVanChuyenRepository.existsByMaTinh(d.maTinh())) {
            throw new AppException(HttpStatus.CONFLICT, "Tỉnh / thành phố này đã có bảng phí");
        }
        PhiVanChuyen p = new PhiVanChuyen();
        p.setMaPhi(IdGenerator.next("PVC"));
        p.setMaTinh(d.maTinh());
        return luu(p, d);
    }

    public PhiVanChuyenDto sua(String ma, PhiVanChuyenDto d) {
        return luu(tim(ma), d);
    }

    public void xoa(String ma) {
        PhiVanChuyen p = tim(ma);
        if (p.getMaTinh() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể xóa mức phí mặc định");
        }
        phiVanChuyenRepository.delete(p);
    }

    @Transactional(readOnly = true)
    public PhiVanChuyen theoDiaChi(String maTinh) {
        return phiVanChuyenRepository.findByMaTinh(maTinh)
                .or(phiVanChuyenRepository::findByMaTinhIsNull)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Chưa cấu hình phí vận chuyển cho khu vực này"));
    }

    public BigDecimal tinhPhi(PhiVanChuyen p, BigDecimal tamTinh) {
        boolean mienPhi = p.getMienPhiTu() != null && tamTinh.compareTo(p.getMienPhiTu()) >= 0;
        return mienPhi ? BigDecimal.ZERO : p.getPhiCoBan();
    }

    private PhiVanChuyenDto luu(PhiVanChuyen p, PhiVanChuyenDto d) {
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
        String ten = p.getMaTinh() == null ? MAC_DINH
                : tinhThanhRepository.findById(p.getMaTinh()).map(TinhThanh::getTenTinh).orElse(p.getMaTinh());
        return new PhiVanChuyenDto(p.getMaPhi(), p.getMaTinh(), ten, p.getPhiCoBan(), p.getMienPhiTu(), p.getThoiGianGiao());
    }
}
