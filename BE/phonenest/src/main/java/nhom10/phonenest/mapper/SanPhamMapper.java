package nhom11.phonenest.mapper;

import nhom11.phonenest.dto.SanPhamDto;
import nhom11.phonenest.entity.HinhAnhSanPham;
import nhom11.phonenest.entity.SanPham;

import java.util.List;

public final class SanPhamMapper {

    private SanPhamMapper() {
    }

    public static SanPhamDto toDto(SanPham s) {
        List<HinhAnhSanPham> anh = s.getHinhAnhs();
        String chinh = anh.stream().filter(HinhAnhSanPham::isAnhChinh).map(HinhAnhSanPham::getDuongDan)
                .findFirst()
                .orElse(anh.isEmpty() ? null : anh.get(0).getDuongDan());
        return new SanPhamDto(s.getMaSanPham(), s.getLoai().getMaLoai(), s.getLoai().getTenLoai(),
                s.getTenMay(), s.getGiaBan(), s.getMoTa(), s.getThongSoKyThuat(), s.getSoLuongTon(),
                s.getTrangThai(), s.getDiemDanhGiaTb(), chinh,
                anh.stream().map(HinhAnhSanPham::getDuongDan).toList());
    }
}
