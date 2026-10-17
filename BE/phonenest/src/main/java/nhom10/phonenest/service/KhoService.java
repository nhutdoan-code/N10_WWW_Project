package nhom10.phonenest.service;

import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.SanPhamRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class KhoService {

    public static final int NGUONG_SAP_HET = 10;

    private final SanPhamRepository sanPhamRepository;

    @Transactional(readOnly = true)
    public List<SanPhamDto> list(String loc) {
        return sanPhamRepository.findAll().stream()
                .filter(s -> switch (loc == null ? "" : loc) {
                    case "het" -> s.getSoLuongTon() == 0;
                    case "sap-het" -> s.getSoLuongTon() > 0 && s.getSoLuongTon() <= NGUONG_SAP_HET;
                    default -> true;
                })
                .sorted((a, b) -> Integer.compare(a.getSoLuongTon(), b.getSoLuongTon()))
                .map(SanPhamMapper::toDto)
                .toList();
    }

    public SanPhamDto nhapKho(String ma, int soLuong) {
        if (soLuong <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng nhập phải lớn hơn 0");
        }
        SanPham s = tim(ma);
        return capNhat(s, s.getSoLuongTon() + soLuong);
    }

    public SanPhamDto datTon(String ma, int soLuong) {
        return capNhat(tim(ma), soLuong);
    }

    private SanPhamDto capNhat(SanPham s, int ton) {
        s.setSoLuongTon(ton);
        if (s.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH) {
            s.setTrangThai(ton > 0 ? TrangThaiSanPham.CON_HANG : TrangThaiSanPham.HET_HANG);
        }
        return SanPhamMapper.toDto(s);
    }

    private SanPham tim(String ma) {
        return sanPhamRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
    }
}
