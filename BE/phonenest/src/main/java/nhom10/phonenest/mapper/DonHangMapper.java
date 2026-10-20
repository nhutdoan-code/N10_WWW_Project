package nhom10.phonenest.mapper;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.repository.HuyDonHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DonHangMapper {

    private final SanPhamRepository sanPhamRepository;
    private final KhachHangRepository khachHangRepository;
    private final YeuCauDoiTraRepository yeuCauDoiTraRepository;
    private final HuyDonHangRepository huyDonHangRepository;

    public DonHangDto toDto(DonHang d) {
        List<DonHangDto.Dong> dong = d.getChiTiet().stream().map(c -> {
            var sp = sanPhamRepository.findById(c.getMaSanPham()).map(SanPhamMapper::toDto).orElse(null);
            return new DonHangDto.Dong(c.getMaSanPham(), sp == null ? "" : sp.tenMay(),
                    sp == null ? null : sp.anhChinh(), c.getSoLuong(), c.getDonGia());
        }).toList();
        BigDecimal tamTinh = dong.stream().map(x -> x.donGia().multiply(BigDecimal.valueOf(x.soLuong())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tong = tamTinh.subtract(d.getGiamGia()).add(d.getPhiVanChuyen()).subtract(d.getGiamPhiVanChuyen());
        return new DonHangDto(d.getMaDonHang(), d.getMaKh(),
                d.getKhuyenMai() == null ? null : d.getKhuyenMai().getMaKm(), d.getMaKmVanChuyen(),
                d.getNgayMua(), d.getPhuongThucTt(), d.getTrangThai(),
                tamTinh, d.getGiamGia(), d.getPhiVanChuyen(), d.getGiamPhiVanChuyen(), tong,
                d.getDiaChiGiaoHang(), d.getNgayCapNhat(),
                khachHangRepository.findById(d.getMaKh()).map(KhachHang::getHoTen).orElse(""),
                yeuCauDoiTraRepository.findByMaDonHang(d.getMaDonHang()).map(y -> y.getTrangThai().name()).orElse(null),
                huyDonHangRepository.findById(d.getMaDonHang())
                        .map(h -> h.getGhiChu() == null || h.getGhiChu().isBlank() ? h.getLyDo() : h.getLyDo() + ": " + h.getGhiChu())
                        .orElse(null),
                dong);
    }
}
