package nhom10.phonenest.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestInfoUtilTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("shouldReturnNullsWhenNoRequestContext")
    void shouldReturnNullsWhenNoRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        RequestInfoUtil.ThongTinYeuCau info = RequestInfoUtil.hienTai();

        assertThat(info.ipAddress()).isNull();
        assertThat(info.requestMethod()).isNull();
        assertThat(info.requestPath()).isNull();
    }

    @Test
    @DisplayName("shouldExtractRemoteAddrWhenNoForwardedHeader")
    void shouldExtractRemoteAddrWhenNoForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/products");
        request.setRemoteAddr("192.168.1.100");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInfoUtil.ThongTinYeuCau info = RequestInfoUtil.hienTai();

        assertThat(info.ipAddress()).isEqualTo("192.168.1.100");
        assertThat(info.requestMethod()).isEqualTo("GET");
        assertThat(info.requestPath()).isEqualTo("/api/admin/products");
    }

    @Test
    @DisplayName("shouldExtractFirstAddressFromXForwardedFor")
    void shouldExtractFirstAddressFromXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/categories");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInfoUtil.ThongTinYeuCau info = RequestInfoUtil.hienTai();

        assertThat(info.ipAddress()).isEqualTo("203.0.113.195");
        assertThat(info.requestMethod()).isEqualTo("POST");
        assertThat(info.requestPath()).isEqualTo("/api/admin/categories");
    }

    @Test
    @DisplayName("shouldExtractPathWithoutQueryString")
    void shouldExtractPathWithoutQueryString() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/audit-logs");
        request.setQueryString("page=0&size=20&keyword=test");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInfoUtil.ThongTinYeuCau info = RequestInfoUtil.hienTai();

        assertThat(info.requestPath()).isEqualTo("/api/admin/audit-logs");
        assertThat(info.requestPath()).doesNotContain("?");
        assertThat(info.requestPath()).doesNotContain("page=");
    }

    @Test
    @DisplayName("shouldTruncateLongMetadataSafely")
    void shouldTruncateLongMetadataSafely() {
        MockHttpServletRequest request = new MockHttpServletRequest("CUSTOM_VERY_LONG_METHOD_NAME", "/" + "a".repeat(300));
        request.setRemoteAddr("1".repeat(60));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInfoUtil.ThongTinYeuCau info = RequestInfoUtil.hienTai();

        assertThat(info.ipAddress()).hasSizeLessThanOrEqualTo(45);
        assertThat(info.requestMethod()).hasSizeLessThanOrEqualTo(10);
        assertThat(info.requestPath()).hasSizeLessThanOrEqualTo(255);
    }
}
