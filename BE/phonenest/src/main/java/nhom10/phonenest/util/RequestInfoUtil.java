package nhom10.phonenest.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Lay thong tin HTTP hien tai cho NhatKyHoatDong.
 *
 * <p>Khong nem loi khi chay ngoai HTTP context (batch, scheduler, manual runner):
 * khi do ca ba gia tri deu la null.
 */
public final class RequestInfoUtil {

    private static final int MAX_IP = 45;
    private static final int MAX_METHOD = 10;
    private static final int MAX_PATH = 255;

    private RequestInfoUtil() {
    }

    public static ThongTinYeuCau hienTai() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return ThongTinYeuCau.KHONG_CO;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return new ThongTinYeuCau(
                layIp(request),
                cat(request.getMethod(), MAX_METHOD),
                cat(layPath(request), MAX_PATH));
    }

    /**
     * getRequestURI() cua servlet da bo query string; van cat them mot lan nua
     * de dam bao khong bao gio luu ?token=... / ?q=... trong audit log.
     */
    private static String layPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return null;
        }
        int dauHoi = uri.indexOf('?');
        return dauHoi >= 0 ? uri.substring(0, dauHoi) : uri;
    }

    /**
     * Uu tien IP dau tien cua X-Forwarded-For, sau do moi den remoteAddr.
     * Khong parse proxy chain phuc tap.
     */
    private static String layIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = null;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int dauPhay = forwardedFor.indexOf(',');
            ip = (dauPhay >= 0 ? forwardedFor.substring(0, dauPhay) : forwardedFor).trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return cat(ip, MAX_IP);
    }

    private static String cat(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public record ThongTinYeuCau(String ipAddress, String requestMethod, String requestPath) {

        public static final ThongTinYeuCau KHONG_CO = new ThongTinYeuCau(null, null, null);
    }
}
