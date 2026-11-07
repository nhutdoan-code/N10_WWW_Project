package nhom10.phonenest.service;

import nhom10.phonenest.dto.LoaiSanPhamDto;
import nhom10.phonenest.entity.LoaiSanPham;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.LoaiSanPhamRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.util.IdGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoaiSanPhamService {

    private final LoaiSanPhamRepository loaiSanPhamRepository;
    private final SanPhamRepository sanPhamRepository;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    @Transactional(readOnly = true)
    public List<LoaiSanPhamDto> list() {
        return loaiSanPhamRepository.findAll().stream().map(this::toDto).toList();
    }

    public LoaiSanPhamDto save(String ma, LoaiSanPhamDto d) {
        LoaiSanPham l = ma == null ? new LoaiSanPham() : find(ma);
        Map<String, Object> duLieuTruoc = ma == null ? null : snapshot(l);
        String cha = d.maLoaiCha() == null || d.maLoaiCha().isBlank() ? null : d.maLoaiCha();
        kiemTraCha(ma, cha);
        if (ma == null) {
            l.setMaLoai(IdGenerator.next("L"));
        }
        l.setTenLoai(d.tenLoai().trim());
        l.setMoTa(d.moTa());
        l.setMaLoaiCha(cha);
        LoaiSanPham daLuu = loaiSanPhamRepository.save(l);
        Map<String, Object> duLieuSau = snapshot(daLuu);
        if (ma == null) {
            nhatKyHoatDongService.ghi(HanhDongAudit.CREATE_CATEGORY, LoaiDoiTuongAudit.CATEGORY,
                    daLuu.getMaLoai(), null, duLieuSau);
        } else {
            nhatKyHoatDongService.ghi(HanhDongAudit.UPDATE_CATEGORY, LoaiDoiTuongAudit.CATEGORY,
                    daLuu.getMaLoai(), duLieuTruoc, duLieuSau);
        }
        return toDto(daLuu);
    }

    public void delete(String ma) {
        if (sanPhamRepository.existsByLoaiMaLoai(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Loại sản phẩm đang có sản phẩm, không thể xóa");
        }
        if (loaiSanPhamRepository.existsByMaLoaiCha(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Loại sản phẩm đang có danh mục con, không thể xóa");
        }
        LoaiSanPham l = find(ma);
        Map<String, Object> duLieuTruoc = snapshot(l);
        loaiSanPhamRepository.delete(l);
        nhatKyHoatDongService.ghi(HanhDongAudit.DELETE_CATEGORY, LoaiDoiTuongAudit.CATEGORY,
                ma, duLieuTruoc, null);
    }

    private void kiemTraCha(String ma, String cha) {
        String hienTai = cha;
        while (hienTai != null) {
            if (hienTai.equals(ma)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Danh mục cha không hợp lệ");
            }
            hienTai = loaiSanPhamRepository.findById(hienTai)
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Danh mục cha không tồn tại"))
                    .getMaLoaiCha();
        }
    }

    private LoaiSanPham find(String ma) {
        return loaiSanPhamRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy loại sản phẩm"));
    }

    private LoaiSanPhamDto toDto(LoaiSanPham l) {
        return new LoaiSanPhamDto(l.getMaLoai(), l.getTenLoai(), l.getMoTa(), l.getMaLoaiCha());
    }

    private Map<String, Object> snapshot(LoaiSanPham l) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("tenLoai", l.getTenLoai());
        duLieu.put("moTa", l.getMoTa());
        duLieu.put("maLoaiCha", l.getMaLoaiCha());
        return duLieu;
    }
}
