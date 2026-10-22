package nhom10.phonenest.mapper;

import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.HinhAnhSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.entity.ThongSoKyThuat;

import java.util.List;

public final class SanPhamMapper {

    private SanPhamMapper() {
    }

    private static SanPhamDto.ThongSo thongSo(ThongSoKyThuat t) {
        return new SanPhamDto.ThongSo(t.getKichThuocManHinh(), t.getLoaiManHinh(), t.getTanSoQuet(), t.getChip(),
                t.getRam(), t.getBoNho(), t.getCameraSau(), t.getCameraTruoc(), t.getPin(), t.getCongSuatSac(),
                t.getHeDieuHanh(), t.isNfc(), t.isHo5G(), t.getTrongLuong());
    }

    public static SanPhamDto toDto(SanPham s) {
        List<HinhAnhSanPham> anh = s.getHinhAnhs();
        String chinh = anh.stream().filter(HinhAnhSanPham::isAnhChinh).map(HinhAnhSanPham::getDuongDan)
                .findFirst()
                .orElse(anh.isEmpty() ? null : anh.get(0).getDuongDan());
        return new SanPhamDto(s.getMaSanPham(), s.getLoai().getMaLoai(), s.getLoai().getTenLoai(),
                s.getTenMay(), s.getGiaBan(), s.getMoTa(), thongSo(s.getThongSo()), s.getSoLuongTon(),
                s.getTrangThai(), s.getDiemDanhGiaTb(), chinh,
                anh.stream().map(HinhAnhSanPham::getDuongDan).toList());
    }
}
