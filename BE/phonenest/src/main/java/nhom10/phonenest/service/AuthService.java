package nhom11.phonenest.service;

import nhom11.phonenest.dto.AuthDto;
import nhom11.phonenest.entity.GioHang;
import nhom11.phonenest.entity.KhachHang;
import nhom11.phonenest.entity.QuanTriVien;
import nhom11.phonenest.entity.TaiKhoan;
import nhom11.phonenest.entity.Wishlist;
import nhom11.phonenest.enums.Role;
import nhom11.phonenest.exception.AppException;
import nhom11.phonenest.repository.GioHangRepository;
import nhom11.phonenest.repository.KhachHangRepository;
import nhom11.phonenest.repository.QuanTriVienRepository;
import nhom11.phonenest.repository.TaiKhoanRepository;
import nhom11.phonenest.repository.WishlistRepository;
import nhom11.phonenest.util.HangThanhVien;
import nhom11.phonenest.util.IdGenerator;
import nhom11.phonenest.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int DIEM_GIOI_THIEU = 50;

    private final TaiKhoanRepository taiKhoanRepository;
    private final KhachHangRepository khachHangRepository;
    private final QuanTriVienRepository quanTriVienRepository;
    private final GioHangRepository gioHangRepository;
    private final WishlistRepository wishlistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public void register(AuthDto.Register r) {
        if (taiKhoanRepository.existsByEmail(r.email().trim().toLowerCase())) {
            throw new AppException(HttpStatus.CONFLICT, "Email đã được sử dụng");
        }
        if (taiKhoanRepository.existsBySoDienThoai(r.soDienThoai())) {
            throw new AppException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
        }
        KhachHang nguoiGioiThieu = null;
        if (r.maGioiThieu() != null && !r.maGioiThieu().isBlank()) {
            nguoiGioiThieu = khachHangRepository.findById(r.maGioiThieu().trim())
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Mã giới thiệu không tồn tại"));
        }
        String ma = IdGenerator.next("TK");

        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk(ma);
        tk.setEmail(r.email().trim().toLowerCase());
        tk.setMatKhau(passwordEncoder.encode(r.matKhau()));
        tk.setSoDienThoai(r.soDienThoai());
        tk.setNgayTao(LocalDate.now());
        tk.setTrangThaiHoatDong(true);
        tk.setLoaiDangNhap("LOCAL");
        taiKhoanRepository.save(tk);

        KhachHang kh = new KhachHang();
        kh.setMaKh(ma);
        kh.setHoTen(r.hoTen().trim());
        kh.setNgaySinh(r.ngaySinh());
        if (nguoiGioiThieu != null) {
            kh.setMaGioiThieu(nguoiGioiThieu.getMaKh());
            kh.setDiemTichLuy(DIEM_GIOI_THIEU);
            nguoiGioiThieu.setDiemTichLuy(nguoiGioiThieu.getDiemTichLuy() + DIEM_GIOI_THIEU);
            nguoiGioiThieu.setHangThanhVien(HangThanhVien.of(nguoiGioiThieu.getDiemTichLuy()));
        }
        kh.setHangThanhVien(HangThanhVien.of(kh.getDiemTichLuy()));
        khachHangRepository.save(kh);

        GioHang gh = new GioHang();
        gh.setMaGioHang(IdGenerator.next("GH"));
        gh.setMaKh(ma);
        gh.setNgayTao(LocalDate.now());
        gioHangRepository.save(gh);

        Wishlist wl = new Wishlist();
        wl.setMaDanhSach(IdGenerator.next("WL"));
        wl.setMaKh(ma);
        wishlistRepository.save(wl);

        emailService.gui(tk.getEmail(), "Chào mừng bạn đến với PhoneNest",
                "Xin chào " + kh.getHoTen() + ", tài khoản của bạn đã được tạo thành công. Mã giới thiệu của bạn: " + ma);
    }

    @Transactional(readOnly = true)
    public AuthDto.Token login(AuthDto.Login l) {
        TaiKhoan tk = taiKhoanRepository.findByEmailOrSoDienThoai(l.taiKhoan().trim(), l.taiKhoan().trim())
                .filter(t -> passwordEncoder.matches(l.matKhau(), t.getMatKhau()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Mật khẩu hoặc tài khoản không chính xác"));
        if (!tk.isTrangThaiHoatDong()) {
            throw new AppException(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khóa");
        }
        var admin = quanTriVienRepository.findById(tk.getMaTk());
        Role role = admin.isPresent() ? Role.ADMIN : Role.CUSTOMER;
        String hoTen = admin.map(QuanTriVien::getHoTen)
                .orElseGet(() -> khachHangRepository.findById(tk.getMaTk()).map(KhachHang::getHoTen).orElse(""));
        return new AuthDto.Token(jwtUtil.generate(tk.getMaTk(), role), tk.getMaTk(), hoTen, tk.getEmail(), role);
    }

    @Transactional(readOnly = true)
    public void forgotPassword(String email) {
        taiKhoanRepository.findByEmail(email.trim().toLowerCase()).ifPresent(tk -> {
            String token = jwtUtil.generateReset(tk.getMaTk(), vanTay(tk));
            emailService.gui(tk.getEmail(), "Đặt lại mật khẩu PhoneNest",
                    "Nhấp vào liên kết sau để đặt lại mật khẩu (hiệu lực 15 phút): "
                            + frontendUrl + "/reset-password?token=" + token);
        });
    }

    @Transactional
    public void resetPassword(AuthDto.ResetPassword r) {
        Claims claims = docTokenReset(r.token());
        TaiKhoan tk = taiKhoanRepository.findById(claims.getSubject())
                .filter(t -> "reset".equals(claims.get("type", String.class)))
                .filter(t -> vanTay(t).equals(claims.get("pv", String.class)))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Liên kết đã hết hạn hoặc đã được sử dụng"));
        tk.setMatKhau(passwordEncoder.encode(r.matKhau()));
    }

    private Claims docTokenReset(String token) {
        try {
            return jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Liên kết đã hết hạn hoặc không hợp lệ");
        }
    }

    private String vanTay(TaiKhoan tk) {
        String h = tk.getMatKhau();
        return h.substring(h.length() - 12);
    }
}
