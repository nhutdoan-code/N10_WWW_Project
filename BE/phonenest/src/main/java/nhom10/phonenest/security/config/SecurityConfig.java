
package nhom10.phonenest.security.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJson(response, HttpStatus.UNAUTHORIZED,
                                        "Vui lòng đăng nhập để tiếp tục"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeJson(response, HttpStatus.FORBIDDEN,
                                        "Bạn không có quyền thực hiện thao tác này")))
                .authorizeHttpRequests(auth -> auth
                        // Các endpoint công khai.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()

                        //Khách chưa đăng nhập được xem dữ liệu công khai.
                        .requestMatchers(HttpMethod.GET,
                                "/api/products", "/api/products/**",
                                "/api/categories", "/api/categories/**",
                                "/api/brands", "/api/brands/**",
                                "/api/search", "/api/search/**",
                                "/api/locations", "/api/locations/**"
                        ).permitAll()

                        // Chat công khai.
                        .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()

                        // Chỉ ADMIN được truy cập API quản trị.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Chỉ CUSTOMER được truy cập API khách hàng.
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/notifications/**").hasRole("CUSTOMER")

                        // Các API còn lại phải đăng nhập.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    //  Mã hóa mật khẩu bằng BCrypt.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    // Trả lỗi dưới dạng JSON.
    private static void writeJson(
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
