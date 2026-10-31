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
                s.getCameraSau(), mp(s.getCameraTruoc()), s.getPin(), s.getCongSuatSac(), s.getHeDieuHanh(),
                s.isNfc(), s.isHo5G(), s.getTrongLuong());

        List<SanPhamDto.PhienBan> pbs = s.getPhienBans().stream()
                .map(x -> new SanPhamDto.PhienBan(
                        x.getMaPhienBan(), x.getSku(), x.getRam(), x.getBoNho(), x.getMauSac(),
                        x.getGiaBan(), x.getGiaNiemYet(), x.getSoLuongTon(), x.getTrangThai()))
                .toList();

        List<SanPhamDto.Anh> anhs = anh.stream()
                .map(x -> new SanPhamDto.Anh(
                        x.getMaHinhAnh(), x.getDuongDan(), x.getGocChup(), x.getMauSac(),
                        x.getPhienBan() == null ? null : x.getPhienBan().getMaPhienBan()))
                .toList();

        return new SanPhamDto(
                s.getMaSanPham(), s.getLoai().getMaLoai(), s.getLoai().getTenLoai(),
                s.getThuongHieu() == null ? null : s.getThuongHieu().getMaThuongHieu(),
                s.getThuongHieu() == null ? null : s.getThuongHieu().getTenThuongHieu(),
                s.getTenMay(), gia, s.getMoTa(), ts, ton, s.getTrangThai(), s.getDiemDanhGiaTb(),
                chinh, anhs, pbs);
    }
    /**
     * Chọn ảnh đại diện theo màu: ưu tiên ảnh gán cho màu đó,
     * nếu màu chưa có ảnh riêng thì dùng ảnh chính của sản phẩm.
     */
    /**
     * Ảnh đại diện của một phiên bản: ảnh riêng phiên bản > ảnh theo màu > ảnh chung > ảnh chính.
     */
    public static String anhTheoMau(SanPham s, PhienBanSanPham pb) {
        List<HinhAnhSanPham> ds = s.getHinhAnhs();
        if (ds == null || ds.isEmpty()) {
            return null;
        }

        String chinh = ds.stream()
                .filter(HinhAnhSanPham::isAnhChinh)
                .map(HinhAnhSanPham::getDuongDan)
                .findFirst()
                .orElse(ds.get(0).getDuongDan());

        List<HinhAnhSanPham> ung = List.of();

        if (pb != null) {
            String ma = pb.getMaPhienBan();
            ung = ds.stream()
                    .filter(a -> a.getPhienBan() != null && ma.equals(a.getPhienBan().getMaPhienBan()))
                    .toList();

            if (ung.isEmpty() && pb.getMauSac() != null && !pb.getMauSac().isBlank()) {
                String m = pb.getMauSac().trim();
                ung = ds.stream()
                        .filter(a -> a.getPhienBan() == null
                                && a.getMauSac() != null
                                && a.getMauSac().trim().equalsIgnoreCase(m))
                        .toList();
            }
        }

        if (ung.isEmpty()) {
            ung = ds.stream()
                    .filter(a -> a.getPhienBan() == null
                            && (a.getMauSac() == null || a.getMauSac().isBlank()))
                    .toList();
        }

        if (ung.isEmpty()) {
            return chinh;
        }

        return ung.stream()
                .map(HinhAnhSanPham::getDuongDan)
                .filter(chinh::equals)
                .findFirst()
                .orElse(ung.get(0).getDuongDan());
    }
    /**
     * DB lưu camera trước dạng chuỗi (ví dụ "12 MP" hoặc "12"); DTO trả về số MP
     * để FE hiển thị thống nhất.
     */
    private static Integer mp(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        StringBuilder so = new StringBuilder();

        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                so.append(c);
            } else if (so.length() > 0) {
                break;
            }
        }

        return so.length() == 0 ? null : Integer.valueOf(so.toString());
    }
}
