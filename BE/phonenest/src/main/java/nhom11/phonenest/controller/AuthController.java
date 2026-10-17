package nhom11.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom11.phonenest.dto.AuthDto;
import nhom11.phonenest.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    public void forgotPassword(@Valid @RequestBody AuthDto.ForgotPassword request) {
        authService.forgotPassword(request.email());
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
