package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class GioHangService {

    private final GioHangRepository gioHangRepository;
    private final SanPhamRepository sanPhamRepository;

    @Transactional(readOnly = true)
    public GioHangDto get(String maKh) {
        return toDto(gioHang(maKh));
    }

    public GioHangDto add(String maKh, String ma, int soLuong) {
        GioHang g = gioHang(maKh);
        dat(g, ma, g.getItems().getOrDefault(ma, 0) + soLuong);
        return toDto(g);
    }

    public GioHangDto update(String maKh, String ma, int soLuong) {
        GioHang g = gioHang(maKh);
        if (soLuong <= 0) {
            g.getItems().remove(ma);
        } else {
            dat(g, ma, soLuong);
        }
        return toDto(g);
    }

    public GioHangDto remove(String maKh, String ma) {
        GioHang g = gioHang(maKh);
        g.getItems().remove(ma);
        return toDto(g);
    }

    public GioHangDto clear(String maKh) {
        GioHang g = gioHang(maKh);
        g.getItems().clear();
        return toDto(g);
    }

    public GioHangDto sync(String maKh, List<GioHangDto.Request> danhSach) {
        GioHang g = gioHang(maKh);
        for (GioHangDto.Request r : danhSach) {
            try {
                dat(g, r.maSanPham(), g.getItems().getOrDefault(r.maSanPham(), 0) + r.soLuong());
            } catch (AppException e) {
                continue;
            }
        }
        return toDto(g);
    }

    private void dat(GioHang g, String ma, int soLuong) {
        SanPham s = sanPhamRepository.findById(ma)
                .filter(x -> x.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sản phẩm không tồn tại hoặc đã ngừng kinh doanh"));
        if (soLuong < 1 || soLuong > s.getSoLuongTon()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng không hợp lệ (còn " + s.getSoLuongTon() + " sản phẩm)");
        }
        g.getItems().put(ma, soLuong);
    }

    private GioHang gioHang(String maKh) {
        return gioHangRepository.findByMaKh(maKh)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giỏ hàng"));
    }

    private GioHangDto toDto(GioHang g) {
        List<GioHangDto.Item> items = g.getItems().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new GioHangDto.Item(
                        SanPhamMapper.toDto(sanPhamRepository.findById(e.getKey()).orElseThrow()), e.getValue()))
                .toList();
        BigDecimal tong = items.stream()
                .map(i -> i.sanPham().giaBan().multiply(BigDecimal.valueOf(i.soLuong())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GioHangDto(items, tong);
    }
}
