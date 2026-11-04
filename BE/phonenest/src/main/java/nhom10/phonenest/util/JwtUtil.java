
package nhom10.phonenest.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import nhom10.phonenest.enums.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // Thời hạn reset mật khẩu: 15 phút
    private static final long RESET_EXPIRATION_MS = 15 * 60 * 1000L;
    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {

        // Secret phải đủ dài cho thuật toán HMAC được sử dụng.
        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationMs = expirationMs;
    }
    // Tạo JWT dùng cho đăng nhập
    public String generate(String maTk, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(maTk)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }
    // Tạo token đặt lại mật khẩu
    public String generateReset(String maTk, String vanTay) {
        Date now = new Date();
        return Jwts.builder()
                .subject(maTk)
                .claim("type", "reset")
                .claim("pv", vanTay)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + RESET_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }
    // Kiểm tra chữ ký và thời hạn token, sau đó lấy thông tin bên trong.
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
