package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.AuthDto;
import nhom10.phonenest.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody AuthDto.Register request) {
        authService.register(request);
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @RequestBody AuthDto.ForgotPassword request) {
        authService.forgotPassword(request.email());
        return "Nếu email tồn tại, mã xác minh sẽ được gửi đến email đó.";
    }

    @PostMapping("/verify-reset-otp")
    public AuthDto.ResetToken verifyResetOtp(@Valid @RequestBody AuthDto.VerifyResetOtp request) {
        return new AuthDto.ResetToken(authService.verifyResetOtp(request));
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody AuthDto.ResetPassword request) {
        authService.resetPassword(request);
    }

    @PostMapping("/login")
    public AuthDto.Token login(@Valid @RequestBody AuthDto.Login request) {
        return authService.login(request);
    }
}