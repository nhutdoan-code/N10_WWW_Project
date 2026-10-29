
package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.SanPhamRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class KhoService {

    public static final int NGUONG_SAP_HET = 10;

    private final SanPhamRepository spRepo;

    // Danh sách sản phẩm theo tình trạng tồn kho
    public List<SanPhamDto> list(String loc) {

        String filter = loc == null ? "" : loc;

        return spRepo.findAll()
                .stream()
                .filter(s -> khop(s, filter))
                .sorted(Comparator.comparingInt(
                        s -> s.getSoLuongTon() == null
                                ? 0
                                : s.getSoLuongTon()
                ))
                .map(SanPhamMapper::toDto)
                .toList();
    }

    private boolean khop(SanPham s, String loc) {

        int ton = s.getSoLuongTon() == null
                ? 0
                : s.getSoLuongTon();

        return switch (loc) {
            case "het" -> ton == 0;
            case "sap-het" -> ton > 0 && ton <= NGUONG_SAP_HET;
            default -> true;
        };
    }

    // Nhập thêm hàng theo mã sản phẩm
    public SanPhamDto nhapKho(String ma, int sl) {

        if (sl <= 0) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng nhập phải lớn hơn 0"
            );
        }

        SanPham s = resolve(ma);

        int ton = s.getSoLuongTon() == null
                ? 0
                : s.getSoLuongTon();

        s.setSoLuongTon(ton + sl);

        s = spRepo.save(s);

        return SanPhamMapper.toDto(s);
    }

    // Đặt lại tồn kho theo mã sản phẩm
    public SanPhamDto datTon(String ma, int sl) {

        if (sl < 0) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Tồn kho không được âm"
            );
        }

        SanPham s = resolve(ma);
        s.setSoLuongTon(sl);

        if (sl == 0
                && s.getTrangThai()
                == nhom10.phonenest.enums.TrangThaiSanPham.CON_HANG) {
            s.setTrangThai(
                    nhom10.phonenest.enums.TrangThaiSanPham.HET_HANG
            );
        }

        s = spRepo.save(s);

        return SanPhamMapper.toDto(s);
    }

    private SanPham resolve(String ma) {

        return spRepo.findById(ma)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));
    }
}
