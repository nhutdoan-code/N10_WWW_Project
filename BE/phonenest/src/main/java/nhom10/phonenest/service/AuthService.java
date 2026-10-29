
package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.AuthDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.HangThanhVien;
import nhom10.phonenest.util.IdGenerator;
import nhom10.phonenest.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int DIEM_GIOI_THIEU = 50;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TaiKhoanRepository taiKhoanRepository;
    private final KhachHangRepository khachHangRepository;
    private final QuanTriVienRepository quanTriVienRepository;
    private final WishlistRepository wishlistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final PasswordResetOtpRepository passwordResetOtpRepository;

    @Value("${app.jwt.secret}")
    private String otpPepper;

    // ==================== ĐĂNG KÝ ====================

    @Transactional
    public void register(AuthDto.Register r) {
        String email = r.email().trim().toLowerCase();
        String soDienThoai = r.soDienThoai().trim();

        if (taiKhoanRepository.existsByEmail(email)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Email đã được sử dụng"
            );
        }

        if (taiKhoanRepository.existsBySoDienThoai(soDienThoai)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Số điện thoại đã được sử dụng"
            );
        }

        // Kiểm tra mã giới thiệu
        KhachHang nguoiGioiThieu = null;

        if (r.maGioiThieu() != null && !r.maGioiThieu().isBlank()) {
            nguoiGioiThieu = khachHangRepository
                    .findById(r.maGioiThieu().trim())
                    .orElseThrow(() -> new AppException(
                            HttpStatus.BAD_REQUEST,
                            "Mã giới thiệu không tồn tại"
                    ));
        }

        // Tạo tài khoản
        String ma = IdGenerator.next("TK");

        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk(ma);
        tk.setEmail(email);
        tk.setMatKhau(passwordEncoder.encode(r.matKhau()));
        tk.setSoDienThoai(soDienThoai);
        tk.setNgayTao(LocalDate.now());
        tk.setTrangThaiHoatDong(true);
        tk.setLoaiDangNhap("LOCAL");

        taiKhoanRepository.save(tk);

        // Tạo thông tin khách hàng
        KhachHang kh = new KhachHang();
        kh.setMaKh(ma);
        kh.setHoTen(r.hoTen().trim());
        kh.setNgaySinh(r.ngaySinh());

        if (nguoiGioiThieu != null) {
            kh.setMaGioiThieu(nguoiGioiThieu.getMaKh());
            kh.setDiemTichLuy(DIEM_GIOI_THIEU);

            int diemMoi = nguoiGioiThieu.getDiemTichLuy()
                    + DIEM_GIOI_THIEU;

            nguoiGioiThieu.setDiemTichLuy(diemMoi);
            nguoiGioiThieu.setHangThanhVien(
                    HangThanhVien.of(diemMoi)
            );
        }

        kh.setHangThanhVien(
                HangThanhVien.of(kh.getDiemTichLuy())
        );

        khachHangRepository.save(kh);

        // Tạm thời không tạo giỏ hàng tại đây.
        // Lý do: mapping giỏ hàng hiện đang truy vấn bảng
        // PhienBanSanPham, nhưng bảng này không tồn tại trong DB.
        // Cần sửa GioHang và ChiTietGioHang theo schema thực tế
        // trước khi khôi phục chức năng tạo giỏ hàng.

        // Tạo danh sách yêu thích
        Wishlist wl = new Wishlist();
        wl.setMaDanhSach(IdGenerator.next("WL"));
        wl.setMaKh(ma);

        wishlistRepository.save(wl);

        // Gửi email chào mừng
        try {
            emailService.gui(
                    tk.getEmail(),
                    "Chào mừng bạn đến với PhoneNest",
                    "Xin chào " + kh.getHoTen()
                            + ", tài khoản của bạn đã được tạo thành công."
                            + " Mã giới thiệu của bạn: " + ma
            );
        } catch (Exception e) {
            // Ghi nhận lỗi để kiểm tra cấu hình email khi cần.
            // Không ném lỗi trở lại để tránh rollback giao dịch.
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                    .error("Tạo tài khoản thành công nhưng gửi email chào mừng thất bại.");
        }
    }

    // ==================== ĐĂNG NHẬP ====================

    @Transactional(readOnly = true)
    public AuthDto.Token login(AuthDto.Login l) {
        String taiKhoan = l.taiKhoan().trim();

        TaiKhoan tk = taiKhoanRepository
                .findByEmailOrSoDienThoai(taiKhoan, taiKhoan)
                .filter(t -> passwordEncoder.matches(
                        l.matKhau(),
                        t.getMatKhau()
                ))
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        "Mật khẩu hoặc tài khoản không chính xác"
                ));

        if (!tk.isTrangThaiHoatDong()) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản của bạn đã bị khóa"
            );
        }

        var admin = quanTriVienRepository.findById(tk.getMaTk());

        Role role;
        String hoTen;

        if (admin.isPresent()) {
            role = Role.ADMIN;
            hoTen = admin.get().getHoTen();
        } else {
            KhachHang kh = khachHangRepository
                    .findById(tk.getMaTk())
                    .orElseThrow(() -> new AppException(
                            HttpStatus.FORBIDDEN,
                            "Tài khoản chưa được cấp quyền truy cập"
                    ));

            role = Role.CUSTOMER;
            hoTen = kh.getHoTen();
        }

        return new AuthDto.Token(
                jwtUtil.generate(tk.getMaTk(), role),
                tk.getMaTk(),
                hoTen,
                tk.getEmail(),
                role
        );
    }

    // ==================== QUÊN MẬT KHẨU ====================

    @Transactional
    public void forgotPassword(String email) {
        taiKhoanRepository
                .findByEmail(email.trim().toLowerCase())
                .filter(TaiKhoan::isTrangThaiHoatDong)
                .ifPresent(tk -> {
                    passwordResetOtpRepository
                            .invalidateActiveByMaTk(tk.getMaTk());

                    String otp = String.format(
                            "%06d",
                            SECURE_RANDOM.nextInt(1_000_000)
                    );

                    LocalDateTime now = LocalDateTime.now();

                    PasswordResetOtp record = new PasswordResetOtp();
                    record.setMaTk(tk.getMaTk());
                    record.setOtpHash(
                            hashSecret("otp:" + tk.getMaTk() + ":" + otp)
                    );
                    record.setCreatedAt(now);
                    record.setOtpExpiresAt(now.plusMinutes(5));
                    record.setAttempts(0);
                    record.setOtpVerified(false);
                    record.setUsed(false);

                    passwordResetOtpRepository.save(record);

                    emailService.gui(
                            tk.getEmail(),
                            "Mã xác minh đặt lại mật khẩu PhoneNest",
                            "Mã OTP của bạn là: " + otp
                                    + "\nMã có hiệu lực trong 5 phút."
                                    + "\nKhông chia sẻ mã này với bất kỳ ai."
                    );
                });
    }

    // ==================== XÁC MINH OTP ====================

    @Transactional(noRollbackFor = AppException.class)
    public String verifyResetOtp(AuthDto.VerifyResetOtp request) {
        String email = request.email().trim().toLowerCase();

        TaiKhoan tk = taiKhoanRepository
                .findByEmail(email)
                .filter(TaiKhoan::isTrangThaiHoatDong)
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Mã OTP không hợp lệ hoặc đã hết hạn"
                ));

        PasswordResetOtp record = passwordResetOtpRepository
                .findTopByMaTkAndUsedFalseOrderByCreatedAtDesc(tk.getMaTk())
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Mã OTP không hợp lệ hoặc đã hết hạn"
                ));

        if (record.isOtpVerified()
                || !LocalDateTime.now().isBefore(record.getOtpExpiresAt())) {
            record.setUsed(true);
            passwordResetOtpRepository.save(record);

            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP không hợp lệ hoặc đã hết hạn"
            );
        }

        if (record.getAttempts() >= MAX_OTP_ATTEMPTS) {
            record.setUsed(true);
            passwordResetOtpRepository.save(record);

            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Bạn đã nhập sai quá số lần cho phép"
            );
        }

        String submittedHash = hashSecret(
                "otp:" + tk.getMaTk() + ":" + request.otp()
        );

        boolean matched = MessageDigest.isEqual(
                submittedHash.getBytes(StandardCharsets.UTF_8),
                record.getOtpHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!matched) {
            int attempts = record.getAttempts() + 1;
            record.setAttempts(attempts);

            if (attempts >= MAX_OTP_ATTEMPTS) {
                record.setUsed(true);
            }

            passwordResetOtpRepository.save(record);

            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP không chính xác"
            );
        }

        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);

        String resetToken = HexFormat.of().formatHex(bytes);

        record.setOtpVerified(true);
        record.setResetTokenHash(hashSecret("reset:" + resetToken));
        record.setResetExpiresAt(LocalDateTime.now().plusMinutes(5));

        passwordResetOtpRepository.save(record);

        return resetToken;
    }

    // ==================== ĐẶT LẠI MẬT KHẨU ====================

    @Transactional
    public void resetPassword(AuthDto.ResetPassword request) {
        String tokenHash = hashSecret("reset:" + request.resetToken());

        PasswordResetOtp record = passwordResetOtpRepository
                .findByResetTokenHashAndUsedFalse(tokenHash)
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Yêu cầu đổi mật khẩu không hợp lệ hoặc đã hết hạn"
                ));

        if (!record.isOtpVerified()
                || record.getResetExpiresAt() == null
                || !LocalDateTime.now().isBefore(record.getResetExpiresAt())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Yêu cầu đổi mật khẩu không hợp lệ hoặc đã hết hạn"
            );
        }

        TaiKhoan tk = taiKhoanRepository
                .findById(record.getMaTk())
                .filter(TaiKhoan::isTrangThaiHoatDong)
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Tài khoản không hợp lệ hoặc đã bị khóa"
                ));

        tk.setMatKhau(passwordEncoder.encode(request.matKhau()));
        taiKhoanRepository.save(tk);

        record.setUsed(true);
        passwordResetOtpRepository.save(record);

        passwordResetOtpRepository.invalidateActiveByMaTk(tk.getMaTk());
    }

    // ==================== BĂM OTP VÀ RESET TOKEN ====================

    private String hashSecret(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    otpPepper.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            return HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Không thể xử lý mã xác minh",
                    e
            );
        }
    }
}
