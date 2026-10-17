package nhom11.phonenest.dto;

import java.util.List;

public record TimKiemDto(
        List<SanPhamDto> sanPhams,
        List<LoaiSanPhamDto> loais,
        List<TaiKhoanDto> taiKhoans,
        List<DonHangDto> donHangs) {
}
