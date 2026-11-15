package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.enums.LyDoKhoaBan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.exception.AppException;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test quy trinh phe duyet ban hang 2B.7B tren CLONE DUNG MOT LAN.
 * Chi chay khi PHONENEST_IT_DB_URL tro vao database hop le (xem ItDatabaseGuard).
 * Database clone phai da ap dung Migration 15 + 16.
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
class ApprovalWorkflowIntegrationTest {

    private static final String SP_REDMI = "SP010";
    private static final String PB = "PB010";            // Redmi 13C 4/128 Xanh Navy (co gia niem yet)
    private static final String PB_KHAC = "PBSP010";     // Redmi 13C 4/128 Den
    private static final String SP_FLIP5 = "SP500476";
    private static final String PB_FLIP5 = "PB500476";   // BLOCKED gia seed 1.99M
    private static final String KHACH = "TK002";
    private static final String DIA_CHI = "DC001";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PhienBanSanPhamRepository pbRepo;
    @Autowired private DonHangService donHangService;
    @Autowired private GioHangService gioHangService;
    @Autowired private ThanhToanService thanhToanService;

    private String admin;
    private String customer;

    @BeforeEach
    void setUp() {
        String db = jdbc.queryForObject("SELECT DATABASE()", String.class);
        ItDatabaseGuard.kiemTra("jdbc:mariadb://127.0.0.1/" + db);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='phienbansanpham' AND COLUMN_NAME='dauVanTayPheDuyet'", Integer.class))
                .as("Clone phai da ap dung Migration 16").isEqualTo(1);
        admin = "Bearer " + jwtUtil.generate("TK001", Role.ADMIN);
        customer = "Bearer " + jwtUtil.generate(KHACH, Role.CUSTOMER);
        // Dat lai fixture (chi tren clone): Redmi 13C ve du lieu goc, PENDING_REVIEW
        jdbc.update("UPDATE phienbansanpham SET ram=4, boNho=128, mauSac='Xanh Navy', giaBan=3290000, giaNiemYet=3690000, "
                + "soLuongTon=25, trangThai='DANG_KINH_DOANH' WHERE maPhienBan=?", PB);
        datPending(PB);
        datPending(PB_KHAC);
        jdbc.update("UPDATE phienbansanpham SET soLuongTon=48 WHERE maPhienBan=?", PB_KHAC);
        jdbc.update("UPDATE phienbansanpham SET giaBan=1990000, giaNiemYet=2490000, trangThaiPheDuyet='BLOCKED', "
                + "lyDoKhoa='GIA_SEED_CHUA_XAC_MINH', giaBanKhiKhoa=1990000, giaPheDuyet=NULL, giaNiemYetPheDuyet=NULL, "
                + "dauVanTayPheDuyet=NULL, nguonXacMinh=NULL, nguoiPheDuyet='SYSTEM:M15', lyDoPheDuyet='Khoa tu dong: gia seed' "
                + "WHERE maPhienBan=?", PB_FLIP5);
        jdbc.update("DELETE FROM chitietgiohang WHERE maGioHang IN (SELECT maGioHang FROM giohang WHERE maKh=?)", KHACH);
    }

    // =========================================================================== 1-2 BLOCKED

    @Test
    @DisplayName("TC01 BLOCKED -> PENDING -> APPROVED khong co can cu: bi tu choi o moi buoc")
    void tc01_blockedBypassRefused() throws Exception {
        approval(PB_FLIP5, Map.of("trangThaiPheDuyet", "PENDING_REVIEW", "lyDo", "Chuyen sang cho duyet de mo ban"))
                .andExpect(status().isConflict());
        approval(PB_FLIP5, xacNhan(PB_FLIP5, "APPROVED")).andExpect(status().isConflict());
        unblock(PB_FLIP5, Map.of("lyDo", "x")).andExpect(status().isBadRequest());
        assertThat(pb(PB_FLIP5).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.BLOCKED);
    }

    @Test
    @DisplayName("TC02 BLOCKED gia seed: doi gia khong tu mo khoa; unblock can gia moi + can cu; ket qua chi la PENDING")
    void tc02_unblockOnlyThroughProcess() throws Exception {
        // a) Gia van la gia luc khoa -> tu choi du co can cu
        unblock(PB_FLIP5, xacNhanUnblock(PB_FLIP5)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("giá seed")));
        // b) Admin sua gia qua form san pham -> van BLOCKED
        suaBienThe(SP_FLIP5, PB_FLIP5, n -> n.put("giaBan", 22990000).put("giaNiemYet", 25990000));
        assertThat(pb(PB_FLIP5).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.BLOCKED);
        // c) Unblock du lieu xac nhan sai (gia cu) -> 409
        Map<String, Object> sai = new LinkedHashMap<>(xacNhanUnblock(PB_FLIP5));
        sai.put("giaBanHienTai", 1990000);
        unblock(PB_FLIP5, sai).andExpect(status().isConflict());
        // d) Unblock hop le -> PENDING_REVIEW (khong phai APPROVED), van khong ban duoc
        unblock(PB_FLIP5, xacNhanUnblock(PB_FLIP5)).andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThaiPheDuyet").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.duocPhepBan").value(false));
        // e) Phai duyet tiep voi can cu day du moi APPROVED
        approval(PB_FLIP5, xacNhan(PB_FLIP5, "APPROVED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.duocPhepBan").value(true));
        assertThat(auditCount("UNBLOCK_VARIANT", PB_FLIP5)).isGreaterThanOrEqualTo(1);
    }

    // =========================================================================== 3-8 thay doi du lieu

    @Test
    @DisplayName("TC03-07 Doi gia ban / gia niem yet / RAM / ROM / mau -> mat APPROVED + audit INVALIDATE")
    void tc03_07_dataChangeInvalidatesApproval() throws Exception {
        Map<String, java.util.function.Consumer<ObjectNode>> thayDoi = new LinkedHashMap<>();
        thayDoi.put("TC03 gia ban", n -> n.put("giaBan", 3291000));
        thayDoi.put("TC04 gia niem yet", n -> n.put("giaNiemYet", 3790000));
        thayDoi.put("TC05 RAM", n -> n.put("ram", 6));
        thayDoi.put("TC06 ROM", n -> n.put("boNho", 64));
        thayDoi.put("TC07 mau", n -> n.put("mauSac", "Xanh Navy Kiem Thu"));
        for (var e : thayDoi.entrySet()) {
            setUp();
            duyetHopLe(PB);
            assertThat(PheDuyetBanHangPolicy.duocPhepBan(pb(PB))).as(e.getKey() + " truoc").isTrue();
            long auditTruoc = auditCount("INVALIDATE_VARIANT_APPROVAL", PB);
            suaBienThe(SP_REDMI, PB, e.getValue());
            PhienBanSanPham sau = pb(PB);
            assertThat(sau.getTrangThaiPheDuyet()).as(e.getKey()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
            assertThat(sau.getDauVanTayPheDuyet()).as(e.getKey()).isNull();
            assertThat(sau.getLyDoPheDuyet()).as(e.getKey()).startsWith("Tự động hủy phê duyệt");
            assertThat(auditCount("INVALIDATE_VARIANT_APPROVAL", PB)).as(e.getKey() + " audit").isEqualTo(auditTruoc + 1);
            assertThatThrownBy(() -> gioHangService.add(KHACH, PB, 1)).as(e.getKey() + " mua")
                    .isInstanceOf(AppException.class);
        }
    }

    @Test
    @DisplayName("TC08 Chi doi ton kho (form san pham va API nhap kho) -> giu APPROVED")
    void tc08_stockOnlyKeepsApproval() throws Exception {
        duyetHopLe(PB);
        // 2B.7D: form san pham KHONG con doi ton kho bien the da co (gia tri gui len bi bo qua)
        suaBienThe(SP_REDMI, PB, n -> n.put("soLuongTon", 30));
        assertThat(pb(PB).getSoLuongTon()).isEqualTo(25);
        mvc.perform(post("/api/admin/inventory/" + PB + "/import").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"soLuong\":5,\"ghiChu\":\"IT 2B.7B\"}"))
                .andExpect(status().isOk());
        PhienBanSanPham p = pb(PB);
        assertThat(p.getSoLuongTon()).isEqualTo(30);
        assertThat(p.getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.APPROVED);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isTrue();
    }

    // =========================================================================== 9-12 validation

    @Test
    @DisplayName("TC09-12 Thieu giaPheDuyet / thieu nguon / can cu yeu / qua dai -> 400 (khong 500)")
    void tc09_12_validation() throws Exception {
        Map<String, Object> ok = xacNhan(PB, "APPROVED");
        Map<String, Object> m;

        m = new LinkedHashMap<>(ok); m.remove("giaPheDuyet");
        approval(PB, m).andExpect(status().isBadRequest());                                   // TC09
        m = new LinkedHashMap<>(ok); m.remove("nguonXacMinh");
        approval(PB, m).andExpect(status().isBadRequest());                                   // TC10
        m = new LinkedHashMap<>(ok); m.remove("ramPheDuyet");
        approval(PB, m).andExpect(status().isBadRequest());
        for (String yeu : new String[]{"1234567890", "aaaaaaaaaa", "https://cellphones.com.vn/redmi-13c.html", "ngắn"}) {
            m = new LinkedHashMap<>(ok); m.put("lyDo", yeu);
            approval(PB, m).andExpect(status().isBadRequest());                               // TC11 ly do
            m = new LinkedHashMap<>(ok); m.put("nguonXacMinh", yeu);
            approval(PB, m).andExpect(status().isBadRequest());                               // TC11 nguon
        }
        m = new LinkedHashMap<>(ok); m.put("lyDo", "Lý do rất dài ".repeat(30));
        approval(PB, m).andExpect(status().isBadRequest());                                   // TC12 > 255
        m = new LinkedHashMap<>(ok); m.put("nguonXacMinh", "Nguồn xác minh ".repeat(40));
        approval(PB, m).andExpect(status().isBadRequest());                                   // TC12 > 500
        mvc.perform(put("/api/admin/variants/" + PB + "/approval").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trangThaiPheDuyet\":\"BLOCKED\",\"lyDo\":\"Khoa ban de kiem tra du lieu\",\"lyDoKhoa\":\"KHONG_TON_TAI\"}"))
                .andExpect(status().isBadRequest());                                          // enum sai
        approval(PB, Map.of("trangThaiPheDuyet", "BLOCKED", "lyDo", "Khoa ban ma khong chon ly do"))
                .andExpect(status().isBadRequest());                                          // thieu lyDoKhoa
        assertThat(pb(PB).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
    }

    // =========================================================================== 13-14 quyen

    @Test
    @DisplayName("TC13-14 CUSTOMER -> 403, khong dang nhap -> 401; CUSTOMER khong doc duoc audit/chi tiet phe duyet")
    void tc13_14_authorization() throws Exception {
        String body = om.writeValueAsString(xacNhan(PB, "APPROVED"));
        mvc.perform(put("/api/admin/variants/" + PB + "/approval").header(HttpHeaders.AUTHORIZATION, customer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/variants/" + PB + "/approval")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/variants/" + PB_FLIP5 + "/unblock").header(HttpHeaders.AUTHORIZATION, customer)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/variants/" + PB + "/approval").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
        // API cong khai khong lo du lieu noi bo
        duyetHopLe(PB);
        String pub = mvc.perform(get("/api/products/" + SP_REDMI)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(pub).doesNotContain("nguonXacMinh").doesNotContain("nguoiPheDuyet")
                .doesNotContain("lyDoPheDuyet").doesNotContain("dauVanTay").contains("\"duocPhepBan\":true");
        assertThat(pb(PB).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.APPROVED);
    }

    // =========================================================================== 15-17 mua hang

    @Test
    @DisplayName("TC15-17 PENDING/BLOCKED khong mua duoc qua gio/mua ngay/preview/dat hang; APPROVED hop le mua duoc")
    void tc15_17_purchase() throws Exception {
        for (String ma : new String[]{PB, PB_FLIP5}) {           // PB dang PENDING, Flip5 BLOCKED
            assertThatThrownBy(() -> gioHangService.add(KHACH, ma, 1)).isInstanceOf(AppException.class);
            assertThatThrownBy(() -> thanhToanService.xemTruoc(KHACH, DIA_CHI, ma, 1)).isInstanceOf(AppException.class);
            assertThatThrownBy(() -> donHangService.datHang(KHACH,
                    new DonHangDto.Checkout(PhuongThucTt.COD, DIA_CHI, null, null, ma, 1))).isInstanceOf(AppException.class);
            mvc.perform(post("/api/customer/orders").header(HttpHeaders.AUTHORIZATION, customer)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phuongThucTt\":\"COD\",\"maDiaChi\":\"" + DIA_CHI + "\",\"maSanPham\":\"" + ma + "\",\"soLuong\":1}"))
                    .andExpect(status().isBadRequest());
        }
        duyetHopLe(PB);
        int ton = pb(PB).getSoLuongTon();
        mvc.perform(post("/api/customer/orders").header(HttpHeaders.AUTHORIZATION, customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phuongThucTt\":\"COD\",\"maDiaChi\":\"" + DIA_CHI + "\",\"maSanPham\":\"" + PB + "\",\"soLuong\":1}"))
                .andExpect(status().isOk());
        assertThat(pb(PB).getSoLuongTon()).isEqualTo(ton - 1);
    }

    @Test
    @DisplayName("Phong ve chieu sau: sua gia bang SQL truc tiep (bo qua service) -> dau van tay lech -> khong ban duoc")
    void directSqlChangeIsFailClosed() throws Exception {
        duyetHopLe(PB);
        jdbc.update("UPDATE phienbansanpham SET giaBan = giaBan - 1000000 WHERE maPhienBan=?", PB);
        PhienBanSanPham p = pb(PB);
        assertThat(p.getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.APPROVED);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isFalse();
        assertThatThrownBy(() -> gioHangService.add(KHACH, PB, 1)).isInstanceOf(AppException.class)
                .hasMessageContaining("xác minh lại");
    }

    @Test
    @DisplayName("Phe duyet cu (M15 v1, APPROVED khong dau van tay) -> khong ban duoc")
    void legacyApprovalWithoutFingerprintIsNotSellable() {
        jdbc.update("UPDATE phienbansanpham SET trangThaiPheDuyet='APPROVED', dauVanTayPheDuyet=NULL WHERE maPhienBan=?", PB);
        assertThatThrownBy(() -> gioHangService.add(KHACH, PB, 1)).isInstanceOf(AppException.class);
    }

    // =========================================================================== 18 du lieu cu / dong thoi

    @Test
    @DisplayName("TC18 Du lieu thay doi trong luc phe duyet -> 409, khong duyet du lieu cu")
    void tc18_staleApprovalRejected() throws Exception {
        Map<String, Object> daXem = xacNhan(PB, "APPROVED");          // Admin xem du lieu luc t0
        suaBienThe(SP_REDMI, PB, n -> n.put("giaBan", 3190000));       // Admin khac doi gia
        approval(PB, daXem).andExpect(status().isConflict());
        assertThat(pb(PB).getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
    }

    @Test
    @DisplayName("TC18b Dong thoi: phe duyet va sua gia cung luc -> khong bao gio APPROVED voi du lieu khong khop")
    void tc18b_concurrentApproveAndEdit() throws Exception {
        for (int vong = 0; vong < 5; vong++) {
            setUp();
            Map<String, Object> daXem = xacNhan(PB, "APPROVED");
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService ex = Executors.newFixedThreadPool(2);
            int giaMoi = 3200000 + vong * 1000;
            ex.submit(() -> { start.await(); approval(PB, daXem); return null; });
            ex.submit(() -> { start.await(); suaBienThe(SP_REDMI, PB, n -> n.put("giaBan", giaMoi)); return null; });
            start.countDown();
            ex.shutdown();
            assertThat(ex.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            PhienBanSanPham p = pb(PB);
            // 2B.7C: khoa dong san pham + bien the va ma kiem tra -> KHONG con trang thai
            // "APPROVED nhung dau van tay lech". Hoac gia moi da luu (va khong APPROVED),
            // hoac form sua bi 409 (gia giu nguyen, APPROVED con hieu luc).
            if (p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.APPROVED) {
                assertThat(PheDuyetBanHangPolicy.pheDuyetConHieuLuc(p)).as("vong " + vong).isTrue();
                assertThat(p.getGiaBan()).as("vong " + vong).isEqualByComparingTo(new BigDecimal("3290000"));
            } else {
                assertThat(p.getGiaBan()).as("vong " + vong).isEqualByComparingTo(BigDecimal.valueOf(giaMoi));
                assertThatThrownBy(() -> gioHangService.add(KHACH, PB, 1)).as("vong " + vong).isInstanceOf(AppException.class);
            }
        }
    }

    @Test
    @DisplayName("Dong thoi: dat hang va khoa ban cung luc -> khoa khong bi ghi de, ton kho khop so don thanh cong")
    void concurrentOrderAndBlock() throws Exception {
        duyetHopLe(PB);
        int ton = pb(PB).getSoLuongTon();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService ex = Executors.newFixedThreadPool(4);
        AtomicInteger thanhCong = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            ex.submit(() -> {
                start.await();
                try {
                    donHangService.datHang(KHACH, new DonHangDto.Checkout(PhuongThucTt.COD, DIA_CHI, null, null, PB, 1));
                    thanhCong.incrementAndGet();
                } catch (Exception ignored) {
                    // bi chan sau khi khoa: hop le
                }
                return null;
            });
        }
        ex.submit(() -> {
            start.await();
            approval(PB, Map.of("trangThaiPheDuyet", "BLOCKED", "lyDo", "Khoa ban khan cap trong kiem thu dong thoi",
                    "lyDoKhoa", LyDoKhoaBan.QUYET_DINH_QUAN_TRI.name())).andExpect(status().isOk());
            return null;
        });
        start.countDown();
        ex.shutdown();
        assertThat(ex.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        PhienBanSanPham p = pb(PB);
        assertThat(p.getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.BLOCKED);
        assertThat(p.getSoLuongTon()).isEqualTo(ton - thanhCong.get());
    }

    // =========================================================================== 21 bien the moi

    @Test
    @DisplayName("TC21 Tao bien the moi qua form san pham -> mac dinh PENDING_REVIEW, khong ban duoc")
    void tc21_newVariantDefaultsPending() throws Exception {
        JsonNode sp = layAdmin(SP_REDMI);
        ObjectNode moi = om.createObjectNode();
        moi.put("ram", 6).put("boNho", 128).put("mauSac", "Kiểm Thử " + System.nanoTime() % 100000)
                .put("giaBan", 3590000).put("giaNiemYet", 3990000).put("soLuongTon", 0);
        ((ArrayNode) sp.get("phienBans")).add(moi);
        mvc.perform(put("/api/admin/products/" + SP_REDMI).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(sp))).andExpect(status().isOk());
        String ma = jdbc.queryForObject("SELECT maPhienBan FROM phienbansanpham WHERE maSanPham=? AND mauSac=?",
                String.class, SP_REDMI, moi.get("mauSac").asText());
        PhienBanSanPham p = pb(ma);
        assertThat(p.getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isFalse();
        // don dep fixture tren clone: bien the chua phat sinh giao dich
        jdbc.update("DELETE FROM phienbansanpham WHERE maPhienBan=?", ma);
    }

    // =========================================================================== audit

    @Test
    @DisplayName("Audit: phe duyet ghi nguoi, thoi diem, gia, gia niem yet, nguon, dau van tay; thu hoi va khoa cung duoc ghi")
    void auditRecordsFullApprovalData() throws Exception {
        LocalDateTime t0 = LocalDateTime.now().minusSeconds(1);
        duyetHopLe(PB);
        Map<String, Object> row = jdbc.queryForMap("SELECT maTk, hanhDong, loaiDoiTuong, duLieuSau, thoiGian FROM nhatkyhoatdong "
                + "WHERE hanhDong='APPROVE_VARIANT' AND maDoiTuong=? ORDER BY thoiGian DESC LIMIT 1", PB);
        assertThat(row.get("maTk")).isEqualTo("TK001");
        assertThat(row.get("loaiDoiTuong")).isEqualTo("VARIANT");
        assertThat(((java.sql.Timestamp) row.get("thoiGian")).toLocalDateTime()).isAfter(t0);
        assertThat((String) row.get("duLieuSau")).contains("APPROVED", "giaPheDuyet", "giaNiemYetPheDuyet",
                "nguonXacMinh", "dauVanTayPheDuyet");
        approval(PB, Map.of("trangThaiPheDuyet", "PENDING_REVIEW", "lyDo", "Thu hoi de doi chieu lai nguon gia"))
                .andExpect(status().isOk());
        approval(PB, Map.of("trangThaiPheDuyet", "BLOCKED", "lyDo", "Khoa ban do nghi sai cau hinh phan phoi",
                "lyDoKhoa", "SAI_CAU_HINH")).andExpect(status().isOk());
        assertThat(auditCount("REVOKE_VARIANT_APPROVAL", PB)).isGreaterThanOrEqualTo(1);
        assertThat(auditCount("BLOCK_VARIANT", PB)).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/admin/variants/" + PB + "/approval").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lyDoKhoa").value("SAI_CAU_HINH"));
        // 2B.7C: moi ban ghi lich su co du boi canh (gia, cau hinh) du cac truong do khong doi
        mvc.perform(get("/api/admin/variants/" + PB + "/approval-history").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hanhDong").value("BLOCK_VARIANT"))
                .andExpect(jsonPath("$[0].sau.boiCanh.ram").value(4))
                .andExpect(jsonPath("$[0].sau.boiCanh.boNho").value(128))
                .andExpect(jsonPath("$[0].sau.boiCanh.mauSac").value("Xanh Navy"))
                .andExpect(jsonPath("$[0].sau.boiCanh.giaBan").value(3290000.0))
                .andExpect(jsonPath("$[1].hanhDong").value("REVOKE_VARIANT_APPROVAL"))
                .andExpect(jsonPath("$[1].sau.boiCanh.giaNiemYet").value(3690000.0));
        mvc.perform(get("/api/admin/variants/" + PB + "/approval-history").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
    }

    // =========================================================================== helpers

    private void datPending(String ma) {
        jdbc.update("UPDATE phienbansanpham SET trangThaiPheDuyet='PENDING_REVIEW', nguoiPheDuyet=NULL, ngayPheDuyet=NULL, "
                + "lyDoPheDuyet=NULL, giaPheDuyet=NULL, giaNiemYetPheDuyet=NULL, nguonXacMinh=NULL, dauVanTayPheDuyet=NULL, "
                + "lyDoKhoa=NULL, giaBanKhiKhoa=NULL WHERE maPhienBan=?", ma);
    }

    private PhienBanSanPham pb(String ma) {
        return pbRepo.findById(ma).orElseThrow();
    }

    private Map<String, Object> xacNhan(String ma, String trangThai) {
        PhienBanSanPham p = pb(ma);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trangThaiPheDuyet", trangThai);
        m.put("lyDo", "Đã đối chiếu giá và cấu hình với nguồn phân phối");
        m.put("nguonXacMinh", "Kiểm thử tích hợp trên clone: bảng giá giả lập, không phải giá thị trường");
        m.put("giaPheDuyet", p.getGiaBan());
        m.put("giaNiemYetPheDuyet", p.getGiaNiemYet());
        m.put("ramPheDuyet", p.getRam());
        m.put("boNhoPheDuyet", p.getBoNho());
        m.put("mauSacPheDuyet", p.getMauSac());
        return m;
    }

    private Map<String, Object> xacNhanUnblock(String ma) {
        PhienBanSanPham p = pb(ma);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lyDo", "Giá seed đã được thay bằng giá đã xác minh");
        m.put("nguonXacMinh", "Kiểm thử tích hợp trên clone: căn cứ giả lập cho quy trình mở khóa");
        m.put("giaBanHienTai", p.getGiaBan());
        m.put("giaNiemYetHienTai", p.getGiaNiemYet());
        m.put("ramHienTai", p.getRam());
        m.put("boNhoHienTai", p.getBoNho());
        m.put("mauSacHienTai", p.getMauSac());
        return m;
    }

    private void duyetHopLe(String ma) throws Exception {
        approval(ma, xacNhan(ma, "APPROVED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThaiPheDuyet").value("APPROVED"))
                .andExpect(jsonPath("$.duocPhepBan").value(true));
    }

    private ResultActions approval(String ma, Map<String, ?> body) throws Exception {
        return mvc.perform(put("/api/admin/variants/" + ma + "/approval").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    private ResultActions unblock(String ma, Map<String, ?> body) throws Exception {
        return mvc.perform(put("/api/admin/variants/" + ma + "/unblock").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    private JsonNode layAdmin(String sp) throws Exception {
        String json = mvc.perform(get("/api/admin/products/" + sp).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(json);
    }

    private void suaBienThe(String sp, String ma, java.util.function.Consumer<ObjectNode> sua) throws Exception {
        JsonNode body = layAdmin(sp);
        for (JsonNode v : body.get("phienBans")) {
            if (ma.equals(v.get("maPhienBan").asText())) {
                sua.accept((ObjectNode) v);
            }
        }
        mvc.perform(put("/api/admin/products/" + sp).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))).andExpect(status().isOk());
    }

    private long auditCount(String hanhDong, String ma) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM nhatkyhoatdong WHERE hanhDong=? AND maDoiTuong=?",
                Long.class, hanhDong, ma);
        return n == null ? 0 : n;
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> debug(String sql) {
        return jdbc.queryForList(sql);
    }
}
