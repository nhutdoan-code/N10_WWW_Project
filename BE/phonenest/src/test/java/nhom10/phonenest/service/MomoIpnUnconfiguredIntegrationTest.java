package nhom10.phonenest.service;

import nhom10.phonenest.support.ItHttpBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 2B.7E: may chu chua cau hinh MoMo -> IPN tra 503 ro rang, khong ghi gi (truoc day 500). */
@EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
@TestPropertySource(properties = {"momo.partner-code=", "momo.access-key=", "momo.secret-key="})
class MomoIpnUnconfiguredIntegrationTest extends ItHttpBase {

    @Test
    @DisplayName("IPN khi chua cau hinh MoMo -> 503, bang thanh toan khong doi")
    void ipnChuaCauHinh() throws Exception {
        String truoc = jdbc.queryForObject("SELECT MD5(IFNULL(GROUP_CONCAT(CONCAT_WS('|',maThanhToan,trangThai,IFNULL(maGiaoDichMoMo,'')) ORDER BY maThanhToan),'')) FROM thanhtoan", String.class);
        Map<String, Object> p = new LinkedHashMap<>();
        for (String k : new String[]{"partnerCode", "orderId", "requestId", "amount", "orderInfo", "orderType", "transId",
                "resultCode", "message", "payType", "responseTime", "signature"}) p.put(k, "x");
        p.put("amount", "1000");
        p.put("resultCode", "0");
        MvcResult r = goi(post("/api/payment/momo/ipn"), null, p);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(503);
        String sau = jdbc.queryForObject("SELECT MD5(IFNULL(GROUP_CONCAT(CONCAT_WS('|',maThanhToan,trangThai,IFNULL(maGiaoDichMoMo,'')) ORDER BY maThanhToan),'')) FROM thanhtoan", String.class);
        assertThat(sau).isEqualTo(truoc);
    }
}
