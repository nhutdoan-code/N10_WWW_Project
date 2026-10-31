package nhom10.phonenest.mapper;

import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.HinhAnhSanPham;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;

import java.math.BigDecimal;
import java.util.List;

public class SanPhamMapper {

    private SanPhamMapper() {}

    public static SanPhamDto toDto(SanPham s) {

        // Lấy hình ảnh
        List<HinhAnhSanPham> hinhAnhs =
                s.getHinhAnhs() == null
                        ? List.of()
                        : s.getHinhAnhs();

        // Tìm ảnh chính
        String anhChinh = hinhAnhs.stream()
                .filter(HinhAnhSanPham::isAnhChinh)
                .map(HinhAnhSanPham::getDuongDan)
                .findFirst()
                .orElseGet(() -> hinhAnhs.stream()
                        .map(HinhAnhSanPham::getDuongDan)
                        .findFirst()
                        .orElse(null));

        // Chuyển hình ảnh sang DTO
        List<SanPhamDto.Anh> dsAnh = hinhAnhs.stream()
                .map(a -> new SanPhamDto.Anh(
                        a.getMaHinhAnh(),
                        a.getDuongDan(),
                        a.getGocChup()
                ))
                .toList();

        // Lấy thông số kỹ thuật chung
        SanPhamDto.ThongSo thongSo = new SanPhamDto.ThongSo(
                s.getKichThuocManHinh(),
                s.getLoaiManHinh(),
                s.getTanSoQuet(),
                s.getChip(),
                s.getRam(),
                s.getBoNho(),
                s.getCameraSau(),
                s.getCameraTruoc(),
                s.getPin(),
                s.getCongSuatSac(),
                s.getHeDieuHanh(),
                s.isNfc(),
                s.isHo5G(),
                s.getTrongLuong()
        );

        // Lấy danh sách phiên bản
        List<PhienBanSanPham> dsPhienBan =
                s.getPhienBans() == null
                        ? List.of()
                        : s.getPhienBans();

        List<SanPhamDto.PhienBan> phienBans = dsPhienBan.stream()
                .map(p -> new SanPhamDto.PhienBan(
                        p.getMaPhienBan(),
                        p.getSku(),
                        p.getRam(),
                        p.getBoNho(),
                        p.getMauSac(),
                        p.getGiaBan(),
                        p.getGiaNiemYet(),
                        p.getSoLuongTon(),
                        p.getTrangThai()
                ))
                .toList();

        // Giá sản phẩm là giá thấp nhất trong các phiên bản còn kinh doanh
        BigDecimal giaBan = dsPhienBan.stream()
                .filter(p -> p.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH)
                .map(PhienBanSanPham::getGiaBan)
                .filter(g -> g != null)
                .min(BigDecimal::compareTo)
                .orElse(s.getGiaBan() == null ? BigDecimal.ZERO : s.getGiaBan());

        // Tổng tồn kho của các phiên bản còn kinh doanh
        int soLuongTon = dsPhienBan.isEmpty()
                ? (s.getSoLuongTon() == null ? 0 : s.getSoLuongTon())
                : dsPhienBan.stream()
                .filter(p -> p.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH)
                .mapToInt(PhienBanSanPham::getSoLuongTon)
                .sum();

        // Trạng thái tổng hợp của sản phẩm
        TrangThaiSanPham trangThai = s.getTrangThai();

        if (trangThai != TrangThaiSanPham.NGUNG_KINH_DOANH
                && !dsPhienBan.isEmpty()) {

            boolean coPhienBanConHang = dsPhienBan.stream()
                    .anyMatch(p ->
                            p.getTrangThai() == TrangThaiSanPham.CON_HANG
                                    && p.getSoLuongTon() > 0
                    );

            boolean tatCaNgung = dsPhienBan.stream()
                    .allMatch(p ->
                            p.getTrangThai() == TrangThaiSanPham.NGUNG_KINH_DOANH
                    );

            trangThai = tatCaNgung
                    ? TrangThaiSanPham.NGUNG_KINH_DOANH
                    : coPhienBanConHang
                    ? TrangThaiSanPham.CON_HANG
                    : TrangThaiSanPham.HET_HANG;
        }

        return new SanPhamDto(
                s.getMaSanPham(),
                s.getLoai() == null ? null : s.getLoai().getMaLoai(),
                s.getLoai() == null ? null : s.getLoai().getTenLoai(),
                null,
                null,
                s.getTenMay(),
                giaBan,
                s.getMoTa(),
                thongSo,
                soLuongTon,
                trangThai,
                s.getDiemDanhGiaTb(),
                anhChinh,
                dsAnh,
                phienBans
        );
    }
}