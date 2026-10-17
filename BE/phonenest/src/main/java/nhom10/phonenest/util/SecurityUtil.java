package nhom11.phonenest.util;

import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
