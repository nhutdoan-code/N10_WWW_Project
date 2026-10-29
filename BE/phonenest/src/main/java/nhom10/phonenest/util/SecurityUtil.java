package nhom10.phonenest.util;

import nhom10.phonenest.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * Identity of the current caller, taken from the JWT subject stored in the SecurityContext.
     * Never use a customer/account id supplied by the client for "my data" endpoints.
     */
    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để tiếp tục");
        }
        return auth.getName();
    }
}
