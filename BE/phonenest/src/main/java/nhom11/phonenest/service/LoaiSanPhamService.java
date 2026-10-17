package nhom11.phonenest.service;

import nhom11.phonenest.dto.LoaiSanPhamDto;
import nhom11.phonenest.entity.LoaiSanPham;
import nhom11.phonenest.exception.AppException;
import nhom11.phonenest.repository.LoaiSanPhamRepository;
import nhom11.phonenest.repository.SanPhamRepository;
import nhom11.phonenest.util.IdGenerator;
import java.util.List;
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

    @Transactional(readOnly = true)
    public List<LoaiSanPhamDto> list() {
        return loaiSanPhamRepository.findAll().stream().map(this::toDto).toList();
    }

    public LoaiSanPhamDto save(String ma, LoaiSanPhamDto d) {
        LoaiSanPham l = ma == null ? new LoaiSanPham() : find(ma);
        String cha = d.maLoaiCha() == null || d.maLoaiCha().isBlank() ? null : d.maLoaiCha();
        kiemTraCha(ma, cha);
        if (ma == null) {
            l.setMaLoai(IdGenerator.next("L"));
        }
        l.setTenLoai(d.tenLoai().trim());
        l.setMoTa(d.moTa());
        l.setMaLoaiCha(cha);
        return toDto(loaiSanPhamRepository.save(l));
    }

    public void delete(String ma) {
        if (sanPhamRepository.existsByLoaiMaLoai(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Loại sản phẩm đang có sản phẩm, không thể xóa");
        }
        if (loaiSanPhamRepository.existsByMaLoaiCha(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Loại sản phẩm đang có danh mục con, không thể xóa");
        }
        loaiSanPhamRepository.delete(find(ma));
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
}
