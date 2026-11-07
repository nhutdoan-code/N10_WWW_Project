package nhom10.phonenest.service;

import nhom10.phonenest.dto.AuthDto;
import nhom10.phonenest.entity.PasswordResetOtp;
import nhom10.phonenest.entity.TaiKhoan;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthOtpServiceTest {

    @Mock
    private TaiKhoanRepository taiKhoanRepository;
    @Mock
    private KhachHangRepository khachHangRepository;
    @Mock
    private QuanTriVienRepository quanTriVienRepository;
    @Mock
    private WishlistRepository wishlistRepository;
    @Mock
    private GioHangRepository gioHangRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                taiKhoanRepository,
                khachHangRepository,
                quanTriVienRepository,
                wishlistRepository,
                gioHangRepository,
                passwordEncoder,
                jwtUtil,
                emailService,
                passwordResetOtpRepository
        );
        ReflectionTestUtils.setField(authService, "otpPepper", "test-secret-pepper-for-otp-12345678");
    }

    @Test
    @DisplayName("forgotPassword sends OTP email and saves hashed OTP for active user")
    void forgotPassword_Success() {
        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk("TK002");
        tk.setEmail("an@phonenest.com");
        tk.setTrangThaiHoatDong(true);

        when(taiKhoanRepository.findByEmail("an@phonenest.com")).thenReturn(Optional.of(tk));

        authService.forgotPassword("an@phonenest.com");

        verify(passwordResetOtpRepository).invalidateActiveByMaTk("TK002");
        ArgumentCaptor<PasswordResetOtp> otpCaptor = ArgumentCaptor.forClass(PasswordResetOtp.class);
        verify(passwordResetOtpRepository).save(otpCaptor.capture());

        PasswordResetOtp savedOtp = otpCaptor.getValue();
        assertEquals("TK002", savedOtp.getMaTk());
        assertNotNull(savedOtp.getOtpHash());
        assertFalse(savedOtp.isOtpVerified());
        assertFalse(savedOtp.isUsed());
        assertEquals(0, savedOtp.getAttempts());
        assertTrue(savedOtp.getOtpExpiresAt().isAfter(LocalDateTime.now()));

        verify(emailService).gui(eq("an@phonenest.com"), contains("Mã xác minh"), anyString());
    }

    @Test
    @DisplayName("forgotPassword silently ignores non-existent email to prevent enumeration")
    void forgotPassword_NonExistentEmail() {
        when(taiKhoanRepository.findByEmail("unknown@phonenest.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword("unknown@phonenest.com"));
        verify(passwordResetOtpRepository, never()).save(any());
        verify(emailService, never()).gui(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("forgotPassword enforces rate limiting cooldown across repeated requests")
    void forgotPassword_RateLimiting_CooldownEnforced() {
        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk("TK002");
        tk.setEmail("spam@phonenest.com");
        tk.setTrangThaiHoatDong(true);

        when(taiKhoanRepository.findByEmail("spam@phonenest.com")).thenReturn(Optional.of(tk));

        // Lần đầu thành công
        authService.forgotPassword("spam@phonenest.com");

        // Lần 2 gửi ngay lập tức -> ném TOO_MANY_REQUESTS
        AppException ex = assertThrows(AppException.class, () ->
                authService.forgotPassword("spam@phonenest.com"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertTrue(ex.getMessage().contains("Vui lòng đợi"));
    }

    @Test
    @DisplayName("forgotPassword also rate-limits non-existent emails to prevent user enumeration")
    void forgotPassword_NonExistentEmail_RateLimited() {
        when(taiKhoanRepository.findByEmail("ghost@phonenest.com")).thenReturn(Optional.empty());

        // Lần 1 không báo lỗi (im lặng)
        assertDoesNotThrow(() -> authService.forgotPassword("ghost@phonenest.com"));

        // Lần 2 gọi ngay lập tức -> ném TOO_MANY_REQUESTS như tài khoản thật
        AppException ex = assertThrows(AppException.class, () ->
                authService.forgotPassword("ghost@phonenest.com"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    }

    @Test
    @DisplayName("forgotPassword throws exception when email service fails, preventing false success")
    void forgotPassword_EmailServiceFailure_PropagatesError() {
        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk("TK002");
        tk.setEmail("an@phonenest.com");
        tk.setTrangThaiHoatDong(true);

        when(taiKhoanRepository.findByEmail("an@phonenest.com")).thenReturn(Optional.of(tk));
        doThrow(new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Lỗi kết nối SMTP"))
                .when(emailService).gui(anyString(), anyString(), anyString());

        AppException ex = assertThrows(AppException.class, () ->
                authService.forgotPassword("an@phonenest.com"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    @DisplayName("verifyResetOtp fails when OTP record is expired")
    void verifyResetOtp_Expired() {
        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk("TK002");
        tk.setEmail("an@phonenest.com");
        tk.setTrangThaiHoatDong(true);

        PasswordResetOtp record = new PasswordResetOtp();
        record.setMaTk("TK002");
        record.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        record.setOtpExpiresAt(LocalDateTime.now().minusMinutes(5));
        record.setOtpVerified(false);
        record.setUsed(false);

        when(taiKhoanRepository.findByEmail("an@phonenest.com")).thenReturn(Optional.of(tk));
        when(passwordResetOtpRepository.findTopByMaTkAndUsedFalseOrderByCreatedAtDesc("TK002"))
                .thenReturn(Optional.of(record));

        AppException ex = assertThrows(AppException.class, () ->
                authService.verifyResetOtp(new AuthDto.VerifyResetOtp("an@phonenest.com", "123456")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(record.isUsed());
    }

    @Test
    @DisplayName("verifyResetOtp locks out after exceeding maximum attempts")
    void verifyResetOtp_LockoutAfterMaxAttempts() {
        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk("TK002");
        tk.setEmail("an@phonenest.com");
        tk.setTrangThaiHoatDong(true);

        PasswordResetOtp record = new PasswordResetOtp();
        record.setMaTk("TK002");
        record.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        record.setOtpExpiresAt(LocalDateTime.now().plusMinutes(4));
        record.setAttempts(5);
        record.setOtpVerified(false);
        record.setUsed(false);

        when(taiKhoanRepository.findByEmail("an@phonenest.com")).thenReturn(Optional.of(tk));
        when(passwordResetOtpRepository.findTopByMaTkAndUsedFalseOrderByCreatedAtDesc("TK002"))
                .thenReturn(Optional.of(record));

        AppException ex = assertThrows(AppException.class, () ->
                authService.verifyResetOtp(new AuthDto.VerifyResetOtp("an@phonenest.com", "123456")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("quá số lần cho phép"));
        assertTrue(record.isUsed());
    }

    @Test
    @DisplayName("resetPassword successfully updates password and invalidates tokens")
    void resetPassword_Success() {
        PasswordResetOtp record = new PasswordResetOtp();
        record.setMaTk("TK002");
        record.setOtpVerified(true);
        record.setResetExpiresAt(LocalDateTime.now().plusMinutes(3));
        record.setUsed(false);

        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk("TK002");
        tk.setEmail("an@phonenest.com");
        tk.setMatKhau("oldHashedPassword");
        tk.setTrangThaiHoatDong(true);

        when(passwordResetOtpRepository.findByResetTokenHashAndUsedFalse(anyString()))
                .thenReturn(Optional.of(record));
        when(taiKhoanRepository.findById("TK002")).thenReturn(Optional.of(tk));
        when(passwordEncoder.encode("newPassword123")).thenReturn("newHashedPassword");

        authService.resetPassword(new AuthDto.ResetPassword("validTokenString", "newPassword123"));

        assertEquals("newHashedPassword", tk.getMatKhau());
        assertTrue(record.isUsed());
        verify(taiKhoanRepository).save(tk);
        verify(passwordResetOtpRepository).save(record);
        verify(passwordResetOtpRepository).invalidateActiveByMaTk("TK002");
    }
}
