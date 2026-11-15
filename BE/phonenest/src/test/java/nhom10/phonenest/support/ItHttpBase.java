package nhom10.phonenest.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Nen chung cho integration test HTTP 2B.7D tren CLONE DUNG MOT LAN (ItDatabaseGuard).
 * Moi request di qua MockMvc -> security filter -> controller -> service -> MariaDB clone that.
 */
@EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = ItDatabaseGuard.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "phonenest.ai.embedding.provider=ollama",
        "phonenest.ai.embedding.ollama.base-url=http://127.0.0.1:11434"
})
public abstract class ItHttpBase {

    protected static final String KHACH = "TK002";
    protected static final String DIA_CHI = "DC001";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected JwtUtil jwtUtil;
    @Autowired protected JdbcTemplate jdbc;

    protected String admin;
    protected String customer;

    @BeforeEach
    void itBaseSetUp() {
        String db = jdbc.queryForObject("SELECT DATABASE()", String.class);
        ItDatabaseGuard.kiemTra("jdbc:mariadb://127.0.0.1/" + db);
        admin = "Bearer " + jwtUtil.generate("TK001", Role.ADMIN);
        customer = "Bearer " + jwtUtil.generate(KHACH, Role.CUSTOMER);
    }

    protected MvcResult goi(MockHttpServletRequestBuilder rq, String token, Object body) throws Exception {
        if (token != null) rq.header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) rq.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : om.writeValueAsString(body));
        return mvc.perform(rq).andReturn();
    }

    protected JsonNode json(MvcResult r) throws Exception {
        String s = r.getResponse().getContentAsString();
        return s.isEmpty() ? om.nullNode() : om.readTree(s);
    }

    /** Dua bien the ve PENDING sach (chi tren clone). */
    protected void datPending(String ma) {
        jdbc.update("UPDATE phienbansanpham SET trangThaiPheDuyet='PENDING_REVIEW', nguoiPheDuyet=NULL, ngayPheDuyet=NULL, "
                + "lyDoPheDuyet=NULL, giaPheDuyet=NULL, giaNiemYetPheDuyet=NULL, nguonXacMinh=NULL, dauVanTayPheDuyet=NULL, "
                + "lyDoKhoa=NULL, giaBanKhiKhoa=NULL WHERE maPhienBan=?", ma);
    }

    /** Duyet bien the qua API Admin that (gia lap tren clone, nguon ghi ro khong phai gia thi truong). */
    protected void duyet(String ma) throws Exception {
        JsonNode ct = json(goi(get("/api/admin/variants/" + ma + "/approval"), admin, null));
        if ("APPROVED".equals(ct.get("trangThaiPheDuyet").asText()) && ct.get("duocPhepBan").asBoolean()) return;
        if ("APPROVED".equals(ct.get("trangThaiPheDuyet").asText())) {
            datPending(ma);
            ct = json(goi(get("/api/admin/variants/" + ma + "/approval"), admin, null));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trangThaiPheDuyet", "APPROVED");
        m.put("lyDo", "Đối chiếu dữ liệu kiểm thử tích hợp 2B.7D");
        m.put("nguonXacMinh", "Kiểm thử tích hợp trên clone: bảng giá giả lập, không phải giá thị trường");
        m.put("giaPheDuyet", ct.get("giaBan").decimalValue());
        m.put("giaNiemYetPheDuyet", ct.get("giaNiemYet").isNull() ? null : ct.get("giaNiemYet").decimalValue());
        m.put("ramPheDuyet", ct.get("ram").asInt());
        m.put("boNhoPheDuyet", ct.get("boNho").asInt());
        m.put("mauSacPheDuyet", ct.get("mauSac").asText());
        m.put("maKiemTra", ct.get("maKiemTra").asText());
        MvcResult r = goi(put("/api/admin/variants/" + ma + "/approval"), admin, m);
        assertThat(r.getResponse().getStatus()).as("duyet " + ma + ": " + r.getResponse().getContentAsString()).isEqualTo(200);
    }

    @SafeVarargs
    protected final List<Integer> song(Callable<Integer>... viec) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService ex = Executors.newFixedThreadPool(viec.length);
        List<Future<Integer>> fs = new ArrayList<>();
        for (Callable<Integer> v : viec) {
            fs.add(ex.submit(() -> { start.await(); return v.call(); }));
        }
        start.countDown();
        ex.shutdown();
        assertThat(ex.awaitTermination(90, TimeUnit.SECONDS)).isTrue();
        List<Integer> kq = new ArrayList<>();
        for (Future<Integer> f : fs) kq.add(f.get());
        return kq;
    }
}
