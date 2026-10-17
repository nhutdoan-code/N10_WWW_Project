package nhom11.phonenest.service;

import nhom11.phonenest.dto.TimKiemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimKiemService {

    private static final int GIOI_HAN = 20;

    private final SanPhamService sanPhamService;
    private final LoaiSanPhamService loaiSanPhamService;
    private final TaiKhoanService taiKhoanService;
    private final DonHangService donHangService;

    public TimKiemDto tim(String q) {
        String k = q.trim().toLowerCase();
        return new TimKiemDto(
                sanPhamService.list(q, null, null, null, null, true).stream().limit(GIOI_HAN).toList(),
                loaiSanPhamService.list().stream().filter(l -> chua(l.tenLoai(), k)).limit(GIOI_HAN).toList(),
                taiKhoanService.list().stream()
                        .filter(t -> chua(t.hoTen(), k) || chua(t.email(), k) || chua(t.soDienThoai(), k) || chua(t.maTk(), k))
                        .limit(GIOI_HAN).toList(),
                donHangService.tatCa().stream()
                        .filter(d -> chua(d.maDonHang(), k) || chua(d.tenKhach(), k) || chua(d.maKh(), k))
                        .limit(GIOI_HAN).toList());
    }

    private boolean chua(String giaTri, String k) {
        return giaTri != null && giaTri.toLowerCase().contains(k);
    }
}
