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
import nhom10.phonenest.repository.KhachHangRepository;
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
    private final KhachHangRepository khachHangRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(header.substring(7));
                String maTk = claims.getSubject();
                String tokenRole = claims.get("role", String.class);

                // Token reset mật khẩu không có role nên không xác thực đăng nhập.
                if (maTk != null && tokenRole != null) {
                    TaiKhoan tk = taiKhoanRepository.findById(maTk).orElse(null);
                    if (tk != null && tk.isTrangThaiHoatDong()) {
                        // [CHỈNH SỬA] Xác định role thực tế từ CSDL.
                        Role dbRole = null;
                        if (quanTriVienRepository.existsById(maTk)) {
                            dbRole = Role.ADMIN;
                        } else if (khachHangRepository.existsById(maTk)) {
                            dbRole = Role.CUSTOMER;
                        }
                        // Chỉ xác thực nếu role trong JWT khớp với CSDL.
                        if (dbRole != null && dbRole.name().equals(tokenRole)) {
                            var authority = new SimpleGrantedAuthority(
                                    "ROLE_" + dbRole.name()
                            );
                            var authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            maTk, null, List.of(authority)
                                    );
                            SecurityContextHolder.getContext()
                                    .setAuthentication(authentication);
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
