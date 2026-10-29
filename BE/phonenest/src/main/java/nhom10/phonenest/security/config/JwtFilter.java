package nhom10.phonenest.security.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.entity.TaiKhoan;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.repository.QuanTriVienRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import nhom10.phonenest.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TaiKhoanRepository taiKhoanRepository;
    private final QuanTriVienRepository quanTriVienRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(header.substring(7));
                String maTk = claims.getSubject();
                String tokenRole = claims.get("role", String.class);
                // Reset-password tokens carry no role claim, so they can never authenticate as a bearer.
                if (maTk != null && tokenRole != null) {
                    // Re-check against the database so locked accounts and role changes take effect
                    // immediately instead of waiting for the token to expire.
                    TaiKhoan tk = taiKhoanRepository.findById(maTk).orElse(null);
                    if (tk != null && tk.isTrangThaiHoatDong()) {
                        Role dbRole = quanTriVienRepository.existsById(maTk) ? Role.ADMIN : Role.CUSTOMER;
                        if (dbRole.name().equals(tokenRole)) {
                            var authority = new SimpleGrantedAuthority("ROLE_" + dbRole.name());
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(maTk, null, List.of(authority)));
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Invalid/expired/malformed token -> no authentication. Protected routes return 401.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
