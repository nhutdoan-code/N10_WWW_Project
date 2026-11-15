package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.support.ItDatabaseGuard;
import nhom10.phonenest.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Giai doan 2B.7C — cap nhat dong thoi giua Admin, don hang va kho, tren CLONE DUNG MOT LAN.
 * Muc tieu: khong lost update, khong duyet du lieu cu, khong ghi de quyet dinh quan tri,
 * khong ban bien the khong hop le, khong sai ton kho.
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
class ApprovalConcurrencyIntegrationTest {

    private static final String SP = "SP010";
    private static final String PB = "PB010";
    private static final int VONG = 6;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PhienBanSanPhamRepository pbRepo;
    @Autowired private PheDuyetBanHangService pheDuyetBanHangService;

    private String admin;

    @BeforeEach
    void setUp() {
        String db = jdbc.queryForObject("SELECT DATABASE()", String.class);
        ItDatabaseGuard.kiemTra("jdbc:mariadb://127.0.0.1/" + db);
        admin = "Bearer " + jwtUtil.generate("TK001", Role.ADMIN);
        jdbc.update("UPDATE sanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham=?", SP);
        jdbc.update("UPDATE phienbansanpham SET ram=4, boNho=128, mauSac='Xanh Navy', giaBan=3290000, giaNiemYet=3690000, "
                + "soLuongTon=25, trangThai='DANG_KINH_DOANH', trangThaiPheDuyet='PENDING_REVIEW', nguoiPheDuyet=NULL, "
                + "ngayPheDuyet=NULL, lyDoPheDuyet=NULL, giaPheDuyet=NULL, giaNiemYetPheDuyet=NULL, nguonXacMinh=NULL, "
                + "dauVanTayPheDuyet=NULL, lyDoKhoa=NULL, giaBanKhiKhoa=NULL WHERE maPhienBan=?", PB);
        jdbc.update("UPDATE phienbansanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham=? AND trangThaiPheDuyet<>'BLOCKED'", SP);
        Object target = AopTestUtils.getTargetObject(pheDuyetBanHangService);
        ReflectionTestUtils.setField(target, "tachQuyenMoKhoaVaDuyet", false);
    }

    // ------------------------------------------------------------------ 1. A duyet trong khi B doi gia

    @Test
    @DisplayName("C1 Admin A duyet (ma kiem tra cu) sau khi Admin B doi gia -> 409 STALE_APPROVAL, khong APPROVED")
    void c1_approveWithStaleTokenAfterPriceChange() throws Exception {
        JsonNode chiTiet = chiTiet(PB);                                   // A mo hop thoai
        suaBienThe(n -> n.put("giaBan", 3190000));                         // B doi gia
        Map<String, Object> body = xacNhanTu(chiTiet);                     // A gui du lieu da xem
        MvcResult r = approval(PB, body);
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(r.getResponse().getContentAsString()).contains("STALE_APPROVAL");
        assertThat(pb(PB).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
    }

    @Test
    @DisplayName("C1b Duyet va doi gia DONG THOI (nhieu vong): khong bao gio APPROVED voi du lieu khong khop")
    void c1b_concurrentApproveAndPriceChange() throws Exception {
        for (int vong = 0; vong < VONG; vong++) {
            setUp();
            Map<String, Object> body = xacNhanTu(chiTiet(PB));
            int giaMoi = 3100000 + vong * 1000;
            List<Integer> kq = song(() -> approval(PB, body).getResponse().getStatus(),
                    () -> suaBienTheStatus(n -> n.put("giaBan", giaMoi)));
            kiemTraBatBien(vong);
            PhienBanSanPham p = pb(PB);
            if (p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.APPROVED) {
                assertThat(p.getGiaBan()).isEqualByComparingTo("3290000");
                assertThat(kq.get(1)).as("form sua phai bi 409").isEqualTo(409);
            } else {
                assertThat(p.getGiaBan()).isEqualByComparingTo(BigDecimal.valueOf(giaMoi));
            }
        }
    }

    // ------------------------------------------------------------------ 2. A doi RAM/ROM/mau trong khi B duyet

    @Test
    @DisplayName("C2 Doi RAM/ROM/mau DONG THOI voi phe duyet: phe duyet khong ap len cau hinh moi")
    void c2_concurrentConfigChangeAndApprove() throws Exception {
        List<Consumer<ObjectNode>> thayDoi = List.of(
                n -> n.put("ram", 6), n -> n.put("boNho", 256), n -> n.put("mauSac", "Xanh Navy Đậm"));
        for (int vong = 0; vong < VONG; vong++) {
            setUp();
            Map<String, Object> body = xacNhanTu(chiTiet(PB));
            Consumer<ObjectNode> doi = thayDoi.get(vong % thayDoi.size());
            song(() -> approval(PB, body).getResponse().getStatus(), () -> suaBienTheStatus(doi));
            kiemTraBatBien(vong);
            PhienBanSanPham p = pb(PB);
            boolean cauHinhGoc = p.getRam() == 4 && p.getBoNho() == 128 && "Xanh Navy".equals(p.getMauSac());
            if (p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.APPROVED) {
                assertThat(cauHinhGoc).as("APPROVED chi voi cau hinh da xac nhan, vong " + vong).isTrue();
            }
        }
    }

    // ------------------------------------------------------------------ 4. duyet trong khi ton kho thay doi

    @Test
    @DisplayName("C4 Duyet trong khi nhap kho dong thoi: phe duyet thanh cong, ton kho dung tuyet doi")
    void c4_approveWhileStockChanges() throws Exception {
        Map<String, Object> body = xacNhanTu(chiTiet(PB));   // ma kiem tra lay TRUOC khi kho doi
        int truoc = pb(PB).getSoLuongTon();
        int soLanNhap = 8;
        List<Callable<Integer>> viec = new ArrayList<>();
        for (int i = 0; i < soLanNhap; i++) {
            viec.add(() -> mvc.perform(post("/api/admin/inventory/" + PB + "/import")
                    .header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"soLuong\":2,\"ghiChu\":\"IT 2B.7C nhap kho dong thoi\"}")).andReturn().getResponse().getStatus());
        }
        viec.add(() -> approval(PB, body).getResponse().getStatus());
        List<Integer> kq = song(viec.toArray(Callable[]::new));
        assertThat(kq.subList(0, soLanNhap)).containsOnly(200);
        assertThat(kq.get(soLanNhap)).as("ton kho khong thuoc ma kiem tra phe duyet").isEqualTo(200);
        PhienBanSanPham p = pb(PB);
        assertThat(p.getSoLuongTon()).isEqualTo(truoc + 2 * soLanNhap);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isTrue();
    }

    // ------------------------------------------------------------------ 5. hai Admin cung duyet

    @Test
    @DisplayName("C5 Hai Admin duyet cung bien the dong thoi: dung mot thanh cong, mot 409, mot ban ghi audit")
    void c5_twoAdminsApproveSimultaneously() throws Exception {
        for (int vong = 0; vong < VONG; vong++) {
            setUp();
            long auditTruoc = audit("APPROVE_VARIANT");
            Map<String, Object> body = xacNhanTu(chiTiet(PB));
            List<Integer> kq = song(() -> approval(PB, body).getResponse().getStatus(),
                    () -> approval(PB, body).getResponse().getStatus());
            assertThat(kq).as("vong " + vong).containsExactlyInAnyOrder(200, 409);
            assertThat(audit("APPROVE_VARIANT")).isEqualTo(auditTruoc + 1);
            kiemTraBatBien(vong);
        }
    }

    // ------------------------------------------------------------------ 6. form cu ghi de

    @Test
    @DisplayName("C6a Form mo truoc khi bien the duoc DUYET -> luu bi 409, phe duyet giu nguyen")
    void c6a_staleFormAfterApproval() throws Exception {
        JsonNode form = layForm();
        approval(PB, xacNhanTu(chiTiet(PB)));
        assertThat(pb(PB).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.APPROVED);
        sua(form, n -> n.put("giaBan", 3000000));
        assertThat(luu(form)).isEqualTo(409);
        PhienBanSanPham p = pb(PB);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isTrue();
        assertThat(p.getGiaBan()).isEqualByComparingTo("3290000");
    }

    @Test
    @DisplayName("C6b Form mo truoc khi KHOA BAN -> luu (ca khi chi doi mo ta) bi 409, BLOCKED giu nguyen")
    void c6b_staleFormAfterBlock() throws Exception {
        JsonNode form = layForm();
        Map<String, Object> khoa = new LinkedHashMap<>();
        khoa.put("trangThaiPheDuyet", "BLOCKED");
        khoa.put("lyDoKhoa", "QUYET_DINH_QUAN_TRI");
        khoa.put("lyDo", "Tạm khóa để kiểm tra lại nguồn giá");
        assertThat(approval(PB, khoa).getResponse().getStatus()).isEqualTo(200);
        ((ObjectNode) form).put("moTa", "Mô tả sửa từ form cũ");
        assertThat(luu(form)).isEqualTo(409);
        assertThat(pb(PB).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.BLOCKED);
    }

    @Test
    @DisplayName("C6c Form mo truoc khi TON KHO doi: form khong con sua ton kho -> luu duoc, ton kho KHONG bi ghi de (2B.7D)")
    void c6c_staleFormDoesNotOverwriteStock() throws Exception {
        JsonNode form = layForm();
        mvc.perform(post("/api/admin/inventory/" + PB + "/import").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"soLuong\":7,\"ghiChu\":\"IT 2B.7C\"}"));
        assertThat(luu(form)).isEqualTo(200);
        assertThat(pb(PB).getSoLuongTon()).isEqualTo(32);
    }

    @Test
    @DisplayName("C6d Form mo truoc khi san pham bi NGUNG KINH DOANH -> 409, khong tu khoi phuc kinh doanh")
    void c6d_staleFormAfterArchive() throws Exception {
        JsonNode form = layForm();
        mvc.perform(put("/api/admin/products/" + SP + "/archive").header(HttpHeaders.AUTHORIZATION, admin));
        try {
            assertThat(luu(form)).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT trangThai FROM sanpham WHERE maSanPham=?", String.class, SP))
                    .isEqualTo("NGUNG_KINH_DOANH");
        } finally {
            mvc.perform(put("/api/admin/products/" + SP + "/restore").header(HttpHeaders.AUTHORIZATION, admin));
        }
    }

    @Test
    @DisplayName("C6e Thieu ma kiem tra -> 409; ma moi -> 200")
    void c6e_missingTokenRejected() throws Exception {
        JsonNode form = layForm();
        ((ObjectNode) form).remove("maKiemTraSua");
        assertThat(luu(form)).isEqualTo(409);
        JsonNode form2 = layForm();
        for (JsonNode v : form2.get("phienBans")) ((ObjectNode) v).remove("maKiemTraSua");
        assertThat(luu(form2)).isEqualTo(409);
        assertThat(luu(layForm())).isEqualTo(200);
    }

    @Test
    @DisplayName("C6f Hai Admin luu cung form dong thoi voi gia khac nhau -> dung mot thanh cong")
    void c6f_twoAdminsSaveSameForm() throws Exception {
        for (int vong = 0; vong < VONG; vong++) {
            setUp();
            JsonNode a = layForm();
            JsonNode b = a.deepCopy();
            sua(a, n -> n.put("giaBan", 3111000));
            sua(b, n -> n.put("giaBan", 3222000));
            List<Integer> kq = song(() -> luu(a), () -> luu(b));
            assertThat(kq).as("vong " + vong).containsExactlyInAnyOrder(200, 409);
            BigDecimal gia = pb(PB).getGiaBan();
            assertThat(gia).isIn(new BigDecimal("3111000.00"), new BigDecimal("3222000.00"));
        }
    }

    // ------------------------------------------------------------------ tach quyen (thiet ke, mac dinh tat)

    @Test
    @DisplayName("Tach quyen BAT: Admin vua mo khoa khong duoc tu duyet (403); TAT: duoc duyet")
    void separationOfDutiesFlag() throws Exception {
        Object target = AopTestUtils.getTargetObject(pheDuyetBanHangService);
        try {
            datBlockedVaMoKhoa();
            ReflectionTestUtils.setField(target, "tachQuyenMoKhoaVaDuyet", true);
            MvcResult r = approval(PB, xacNhanTu(chiTiet(PB)));
            assertThat(r.getResponse().getStatus()).isEqualTo(403);
            assertThat(r.getResponse().getContentAsString()).contains("SEPARATION_OF_DUTIES");
            assertThat(pb(PB).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
            ReflectionTestUtils.setField(target, "tachQuyenMoKhoaVaDuyet", false);
            assertThat(approval(PB, xacNhanTu(chiTiet(PB))).getResponse().getStatus()).isEqualTo(200);
        } finally {
            ReflectionTestUtils.setField(target, "tachQuyenMoKhoaVaDuyet", false);
        }
    }

    // ------------------------------------------------------------------ tien ich

    /** Bat bien: APPROVED => phe duyet con hieu luc (khong con trang thai gay hieu nham). */
    private void kiemTraBatBien(int vong) {
        PhienBanSanPham p = pb(PB);
        if (p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.APPROVED) {
            assertThat(PheDuyetBanHangPolicy.pheDuyetConHieuLuc(p)).as("APPROVED phai con hieu luc, vong " + vong).isTrue();
        } else {
            assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isFalse();
        }
    }

    private void datBlockedVaMoKhoa() throws Exception {
        Map<String, Object> khoa = new LinkedHashMap<>();
        khoa.put("trangThaiPheDuyet", "BLOCKED");
        khoa.put("lyDoKhoa", "SAI_CAU_HINH");
        khoa.put("lyDo", "Khóa để kiểm tra cấu hình trên clone");
        assertThat(approval(PB, khoa).getResponse().getStatus()).isEqualTo(200);
        PhienBanSanPham p = pb(PB);
        Map<String, Object> mo = new LinkedHashMap<>();
        mo.put("lyDo", "Đã kiểm tra lại cấu hình với nhà phân phối");
        mo.put("nguonXacMinh", "Kiểm thử tích hợp trên clone: căn cứ giả lập cho mở khóa");
        mo.put("giaBanHienTai", p.getGiaBan());
        mo.put("giaNiemYetHienTai", p.getGiaNiemYet());
        mo.put("ramHienTai", p.getRam());
        mo.put("boNhoHienTai", p.getBoNho());
        mo.put("mauSacHienTai", p.getMauSac());
        mo.put("maKiemTra", chiTiet(PB).get("maKiemTra").asText());
        int st = mvc.perform(put("/api/admin/variants/" + PB + "/unblock").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(mo))).andReturn().getResponse().getStatus();
        assertThat(st).isEqualTo(200);
    }

    @SafeVarargs
    private List<Integer> song(Callable<Integer>... viec) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService ex = Executors.newFixedThreadPool(viec.length);
        List<Future<Integer>> fs = new ArrayList<>();
        for (Callable<Integer> v : viec) {
            fs.add(ex.submit(() -> { start.await(); return v.call(); }));
        }
        start.countDown();
        ex.shutdown();
        assertThat(ex.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        List<Integer> kq = new ArrayList<>();
        for (Future<Integer> f : fs) kq.add(f.get());
        return kq;
    }

    private JsonNode chiTiet(String ma) throws Exception {
        return om.readTree(mvc.perform(get("/api/admin/variants/" + ma + "/approval").header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString());
    }

    /** Body phe duyet dung du lieu Admin DA XEM trong hop thoai (kem ma kiem tra). */
    private Map<String, Object> xacNhanTu(JsonNode ct) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trangThaiPheDuyet", "APPROVED");
        m.put("lyDo", "Đã đối chiếu giá và cấu hình với nguồn phân phối");
        m.put("nguonXacMinh", "Kiểm thử tích hợp trên clone: bảng giá giả lập, không phải giá thị trường");
        m.put("giaPheDuyet", ct.get("giaBan").decimalValue());
        m.put("giaNiemYetPheDuyet", ct.get("giaNiemYet").isNull() ? null : ct.get("giaNiemYet").decimalValue());
        m.put("ramPheDuyet", ct.get("ram").asInt());
        m.put("boNhoPheDuyet", ct.get("boNho").asInt());
        m.put("mauSacPheDuyet", ct.get("mauSac").asText());
        m.put("maKiemTra", ct.get("maKiemTra").asText());
        return m;
    }

    private MvcResult approval(String ma, Map<String, ?> body) throws Exception {
        return mvc.perform(put("/api/admin/variants/" + ma + "/approval").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))).andReturn();
    }

    private JsonNode layForm() throws Exception {
        return om.readTree(mvc.perform(get("/api/admin/products/" + SP).header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString());
    }

    private void sua(JsonNode form, Consumer<ObjectNode> f) {
        for (JsonNode v : form.get("phienBans")) {
            if (PB.equals(v.get("maPhienBan").asText())) f.accept((ObjectNode) v);
        }
    }

    private int luu(JsonNode form) throws Exception {
        return mvc.perform(put("/api/admin/products/" + SP).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(form))).andReturn().getResponse().getStatus();
    }

    private void suaBienThe(Consumer<ObjectNode> f) throws Exception {
        JsonNode form = layForm();
        sua(form, f);
        assertThat(luu(form)).isEqualTo(200);
    }

    private int suaBienTheStatus(Consumer<ObjectNode> f) throws Exception {
        JsonNode form = layForm();
        sua(form, f);
        return luu(form);
    }

    private PhienBanSanPham pb(String ma) {
        return pbRepo.findById(ma).orElseThrow();
    }

    private long audit(String hanhDong) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM nhatkyhoatdong WHERE hanhDong=? AND maDoiTuong=?",
                Long.class, hanhDong, PB);
        return n == null ? 0 : n;
    }
}
