package nhom10.phonenest.mapper;

import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.HinhAnhSanPham;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public final class SanPhamMapper {

    private SanPhamMapper() {
    }

    public static SanPhamDto toDto(SanPham s) {

        List<HinhAnhSanPham> anh = s.getHinhAnhs();
        String chinh = anh.stream()
                .filter(HinhAnhSanPham::isAnhChinh)
                .map(HinhAnhSanPham::getDuongDan)
                .findFirst()
                .orElse(anh.isEmpty() ? null : anh.get(0).getDuongDan());

        // Chỉ variant đang kinh doanh mới được tính vào giá/tồn hiển thị.
        List<PhienBanSanPham> active = s.getPhienBans().stream()
                .filter(x -> x.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                .toList();

        PhienBanSanPham p = active.stream()
                .filter(x -> x.getGiaBan() != null)
                .min(Comparator.comparing(PhienBanSanPham::getGiaBan))
                .orElse(null);

        BigDecimal gia = p == null ? BigDecimal.ZERO : p.getGiaBan();
        int ton = active.stream().mapToInt(PhienBanSanPham::getSoLuongTon).sum();

        SanPhamDto.ThongSo ts = new SanPhamDto.ThongSo(
                s.getKichThuocManHinh(), s.getLoaiManHinh(), s.getTanSoQuet(), s.getChip(),
                p == null ? null : p.getRam(), p == null ? null : p.getBoNho(),
                s.getCameraSau(), null, s.getPin(), s.getCongSuatSac(), s.getHeDieuHanh(),
                s.isNfc(), s.isHo5G(), s.getTrongLuong());

        List<SanPhamDto.PhienBan> pbs = s.getPhienBans().stream()
                .map(x -> new SanPhamDto.PhienBan(
                        x.getMaPhienBan(), x.getSku(), x.getRam(), x.getBoNho(), x.getMauSac(),
                        x.getGiaBan(), x.getGiaNiemYet(), x.getSoLuongTon(), x.getTrangThai()))
                .toList();

        return new SanPhamDto(
                s.getMaSanPham(), s.getLoai().getMaLoai(), s.getLoai().getTenLoai(),
                s.getThuongHieu() == null ? null : s.getThuongHieu().getMaThuongHieu(),
                s.getThuongHieu() == null ? null : s.getThuongHieu().getTenThuongHieu(),
                s.getTenMay(), gia, s.getMoTa(), ts, ton, s.getTrangThai(), s.getDiemDanhGiaTb(),
                chinh, anh.stream().map(HinhAnhSanPham::getDuongDan).toList(), pbs);
    }
}
