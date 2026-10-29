
package nhom10.phonenest.mapper;

import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.HinhAnhSanPham;
import nhom10.phonenest.entity.SanPham;

import java.math.BigDecimal;
import java.util.List;

public class SanPhamMapper {

    private SanPhamMapper() {
        // Không tạo đối tượng Mapper
    }

    public static SanPhamDto toDto(SanPham s) {

        // 1. Lấy danh sách hình ảnh
        List<HinhAnhSanPham> hinhAnhs =
                s.getHinhAnhs() == null
                        ? List.of()
                        : s.getHinhAnhs();

        // 2. Tìm ảnh chính
        String anhChinh = hinhAnhs.stream()
                .filter(HinhAnhSanPham::isAnhChinh)
                .map(HinhAnhSanPham::getDuongDan)
                .findFirst()
                .orElseGet(() ->
                        hinhAnhs.stream()
                                .map(HinhAnhSanPham::getDuongDan)
                                .findFirst()
                                .orElse(null)
                );

        // 3. Chuyển danh sách hình ảnh sang DTO
        List<SanPhamDto.Anh> dsAnh = hinhAnhs.stream()
                .map(a -> new SanPhamDto.Anh(
                        a.getMaHinhAnh(),
                        a.getDuongDan(),
                        a.getGocChup()
                ))
                .toList();

        // 4. Lấy thông số kỹ thuật trực tiếp từ sản phẩm
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

        // 5. Giá bán và số lượng tồn lấy trực tiếp từ bảng SanPham
        BigDecimal giaBan = s.getGiaBan() == null
                ? BigDecimal.ZERO
                : s.getGiaBan();

        int soLuongTon = s.getSoLuongTon() == null
                ? 0
                : s.getSoLuongTon();

        // 6. Trả về DTO
        return new SanPhamDto(
                s.getMaSanPham(),
                s.getLoai() == null ? null : s.getLoai().getMaLoai(),
                s.getLoai() == null ? null : s.getLoai().getTenLoai(),

                // Database hiện tại chưa có cột maThuongHieu
                null,
                null,

                s.getTenMay(),
                giaBan,
                s.getMoTa(),
                thongSo,
                soLuongTon,
                s.getTrangThai(),
                s.getDiemDanhGiaTb(),
                anhChinh,
                dsAnh,

                // Mô hình hiện tại không dùng phiên bản sản phẩm
                List.of()
        );
    }
}
