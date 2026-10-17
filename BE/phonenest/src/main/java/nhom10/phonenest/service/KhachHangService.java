package nhom11.phonenest.service;

import nhom11.phonenest.dto.HoSoDto;
import nhom11.phonenest.entity.KhachHang;
import nhom11.phonenest.entity.TaiKhoan;
import nhom11.phonenest.exception.AppException;
import nhom11.phonenest.repository.KhachHangRepository;
import nhom11.phonenest.repository.TaiKhoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class KhachHangService {

    private final TaiKhoanRepository taiKhoanRepository;
    private final KhachHangRepository khachHangRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public HoSoDto.Profile get(String maKh) {
        return toDto(taiKhoan(maKh), khachHang(maKh));
    }

    public HoSoDto.Profile update(String maKh, HoSoDto.Update u) {
        TaiKhoan tk = taiKhoan(maKh);
        KhachHang kh = khachHang(maKh);
        String sdt = u.soDienThoai();
        if (sdt != null && !sdt.isBlank() && !sdt.equals(tk.getSoDienThoai())) {
            if (taiKhoanRepository.existsBySoDienThoai(sdt)) {
                throw new AppException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
            }
            tk.setSoDienThoai(sdt);
        }
        kh.setHoTen(u.hoTen().trim());
        kh.setNgaySinh(u.ngaySinh());
        kh.setAnhDaiDien(u.anhDaiDien());
        return toDto(tk, kh);
    }

    public void doiMatKhau(String maKh, HoSoDto.Password p) {
        TaiKhoan tk = taiKhoan(maKh);
        if (!passwordEncoder.matches(p.matKhauCu(), tk.getMatKhau())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }
        tk.setMatKhau(passwordEncoder.encode(p.matKhauMoi()));
    }

    private TaiKhoan taiKhoan(String ma) {
        return taiKhoanRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
    }

    private KhachHang khachHang(String ma) {
        return khachHangRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"));
    }

    private HoSoDto.Profile toDto(TaiKhoan tk, KhachHang kh) {
        return new HoSoDto.Profile(kh.getMaKh(), kh.getHoTen(), tk.getEmail(), tk.getSoDienThoai(), kh.getNgaySinh(),
                kh.getAnhDaiDien(), kh.getDiemTichLuy(), kh.getHangThanhVien(), kh.getMaGioiThieu());
    }
}
