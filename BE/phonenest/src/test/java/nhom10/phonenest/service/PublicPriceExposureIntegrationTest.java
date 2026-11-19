package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.service.ai.ProductSemanticDocumentBuilder;
import nhom10.phonenest.support.ItHttpBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 2B.7D Nhiem vu 1 — gia chua phe duyet KHONG duoc xuat hien o API cong khai, gio hang, tim kiem,
 * bo loc gia, san pham lien quan, wishlist, ngu canh AI. Kiem tra bang HTTP that tren clone.
 *
 * <p>Fixture (chi tren clone): PB010 duoc duyet (gia 3.290.000). PBSP010 (cung san pham SP010) de
 * PENDING_REVIEW va dat gia seed "dau vet" 1.234.000 — neu so nay xuat hien o bat ky dau ra cong khai
 * nao la ro ri.</p>
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
class PublicPriceExposureIntegrationTest extends ItHttpBase {

    private static final String SP = "SP010";
    private static final String PB_DUYET = "PB010";
    private static final String PB_SEED = "PBSP010";
    private static final String DAU_VET = "1234000";

    @Autowired private SanPhamRepository spRepo;
    @Autowired private ProductSemanticDocumentBuilder docBuilder;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("UPDATE sanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham=?", SP);
        jdbc.update("UPDATE phienbansanpham SET ram=4, boNho=128, mauSac='Xanh Navy', giaBan=3290000, giaNiemYet=3690000, "
                + "soLuongTon=25, trangThai='DANG_KINH_DOANH' WHERE maPhienBan=?", PB_DUYET);
        datPending(PB_DUYET);
        duyet(PB_DUYET);
        datPending(PB_SEED);
        jdbc.update("UPDATE phienbansanpham SET giaBan=1234000, giaNiemYet=1299000, soLuongTon=48, trangThai='DANG_KINH_DOANH' WHERE maPhienBan=?", PB_SEED);
        jdbc.update("DELETE FROM chitietgiohang WHERE maGioHang IN (SELECT maGioHang FROM giohang WHERE maKh=?)", KHACH);
    }

    private static void khongCoDauVet(String json, String ngu) {
        assertThat(json).as(ngu + " khong duoc chua gia seed").doesNotContain(DAU_VET).doesNotContain("1299000");
    }

    private static JsonNode bienThe(JsonNode sp, String ma) {
        for (JsonNode v : sp.get("phienBans")) if (ma.equals(v.get("maPhienBan").asText())) return v;
        throw new AssertionError("khong thay " + ma);
    }

    @Test
    @DisplayName("P1 Chi tiet san pham: bien the chua duyet KHONG co truong giaBan/giaNiemYet; gia SP = gia cong bo thap nhat")
    void p1_chiTiet() throws Exception {
        MvcResult r = goi(get("/api/products/" + SP), null, null);
        String body = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        khongCoDauVet(body, "GET /api/products/{ma}");
        JsonNode sp = om.readTree(body);
        JsonNode seed = bienThe(sp, PB_SEED);
        assertThat(seed.has("giaBan")).as("PENDING khong co khoa giaBan").isFalse();
        assertThat(seed.has("giaNiemYet")).isFalse();
        assertThat(seed.get("duocPhepBan").asBoolean()).isFalse();
        JsonNode ok = bienThe(sp, PB_DUYET);
        assertThat(ok.get("giaBan").decimalValue()).isEqualByComparingTo("3290000");
        assertThat(sp.get("giaBan").decimalValue()).as("gia thap nhat chi tu bien the duoc ban").isEqualByComparingTo("3290000");
        for (JsonNode v : sp.get("phienBans")) {
            if (!v.get("duocPhepBan").asBoolean()) {
                assertThat(v.has("giaBan")).as(v.get("maPhienBan").asText()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("P2 Danh sach / tim kiem / tim nhanh / lien quan: khong co gia seed; SP khong co bien the duoc ban -> khong co giaBan (khong phai 0)")
    void p2_danhSach() throws Exception {
        for (String url : List.of("/api/products", "/api/products?q=Redmi", "/api/search?q=Redmi",
                "/api/products/" + SP + "/related", "/api/products?sort=gia_asc")) {
            MvcResult r = goi(get(url), null, null);
            assertThat(r.getResponse().getStatus()).as(url).isEqualTo(200);
            String body = r.getResponse().getContentAsString();
            khongCoDauVet(body, url);
            JsonNode arr = om.readTree(body);
            for (JsonNode sp : arr) {
                boolean coBienTheDuocBan = false;
                for (JsonNode v : sp.get("phienBans")) {
                    if (v.get("duocPhepBan").asBoolean()) coBienTheDuocBan = true;
                    else assertThat(v.has("giaBan")).as(url + " " + v.get("maPhienBan").asText()).isFalse();
                }
                if (!coBienTheDuocBan) {
                    assertThat(sp.has("giaBan")).as(url + " " + sp.get("maSanPham").asText() + " khong co gia cong bo").isFalse();
                } else {
                    assertThat(sp.get("giaBan").decimalValue().signum()).isPositive();
                }
            }
        }
    }

    @Test
    @DisplayName("P3 Bo loc gia khong de lo gia seed bang cach do khoang gia")
    void p3_boLocGia() throws Exception {
        for (String url : List.of("/api/products?min=1200000&max=1300000", "/api/products?max=1500000",
                "/api/products?min=1234000&max=1234000&conHang=true")) {
            JsonNode arr = json(goi(get(url), null, null));
            for (JsonNode sp : arr) {
                assertThat(sp.get("maSanPham").asText()).as(url + " khong duoc tra SP010 nho gia seed").isNotEqualTo(SP);
            }
        }
        // Khoang gia chua gia cong bo 3.290.000 thi SP010 xuat hien
        JsonNode arr = json(goi(get("/api/products?min=3200000&max=3300000"), null, null));
        assertThat(arr.findValuesAsText("maSanPham")).contains(SP);
    }

    @Test
    @DisplayName("P4 Gio hang: dong cua bien the bi thu hoi khong hien gia seed, tong khong tinh dong do")
    void p4_gioHang() throws Exception {
        assertThat(goi(post("/api/customer/cart"), customer, Map.of("maPhienBan", PB_DUYET, "soLuong", 2)).getResponse().getStatus()).isEqualTo(200);
        JsonNode cart = json(goi(get("/api/customer/cart"), customer, null));
        assertThat(cart.toString()).contains("3290000");
        // Admin thu hoi + doi gia noi bo thanh gia dau vet
        JsonNode ct = json(goi(get("/api/admin/variants/" + PB_DUYET + "/approval"), admin, null));
        assertThat(goi(put("/api/admin/variants/" + PB_DUYET + "/approval"), admin, Map.of("trangThaiPheDuyet", "PENDING_REVIEW",
                "lyDo", "Thu hồi để kiểm thử giỏ hàng", "maKiemTra", ct.get("maKiemTra").asText())).getResponse().getStatus()).isEqualTo(200);
        jdbc.update("UPDATE phienbansanpham SET giaBan=1234000 WHERE maPhienBan=?", PB_DUYET);
        MvcResult r = goi(get("/api/customer/cart"), customer, null);
        khongCoDauVet(r.getResponse().getContentAsString(), "GET /api/customer/cart");
        JsonNode c2 = om.readTree(r.getResponse().getContentAsString());
        JsonNode item = c2.get("items").get(0).get("phienBan");
        assertThat(item.has("giaBan")).isFalse();
        assertThat(c2.get("tongTien") != null ? c2.get("tongTien").decimalValue() : c2.get("tong").decimalValue()).isEqualByComparingTo("0");
        goi(delete("/api/customer/cart"), customer, null);
    }

    @Test
    @DisplayName("P5 Wishlist va ket qua tim kiem ngu nghia khong co gia seed")
    void p5_wishlistVaSemantic() throws Exception {
        goi(post("/api/customer/wishlist/" + SP), customer, null);
        MvcResult w = goi(get("/api/customer/wishlist"), customer, null);
        assertThat(w.getResponse().getStatus()).isEqualTo(200);
        khongCoDauVet(w.getResponse().getContentAsString(), "wishlist");
        goi(delete("/api/customer/wishlist/" + SP), customer, null);
        MvcResult s = goi(get("/api/search/semantic?q=Redmi 13C pin trâu giá rẻ"), null, null);
        assertThat(s.getResponse().getStatus()).isEqualTo(200);
        khongCoDauVet(s.getResponse().getContentAsString(), "semantic search");
    }

    @Test
    @DisplayName("P6 Ngu canh AI (tai lieu vector) chi chua gia cong bo; SP chi co bien the chua duyet -> 'dang cap nhat'")
    void p6_taiLieuVector() {
        String doc = tx.execute(st -> {
            SanPham s = spRepo.findByIdWithPhienBans(SP).orElseThrow();
            return docBuilder.build(s).getText();
        });
        assertThat(doc).contains("Giá bán: 3290000").doesNotContain(DAU_VET);
        datPending(PB_DUYET);
        String doc2 = tx.execute(st -> docBuilder.build(spRepo.findByIdWithPhienBans(SP).orElseThrow()).getText());
        assertThat(doc2).contains("Giá bán: đang cập nhật").doesNotContain("3290000").doesNotContain(DAU_VET);
    }

    @Test
    @DisplayName("P7 Chatbot (HTTP that): khong neu gia seed, khong goi y san pham khong co gia cong bo")
    void p7_chatbot() throws Exception {
        datPending(PB_DUYET); // SP010 khong con bien the duoc ban
        List<String> cauHoi = new ArrayList<>(List.of("Redmi 13C giá bao nhiêu?", "Có Redmi 13C không?",
                "Tư vấn điện thoại giá rẻ dưới 2 triệu"));
        for (String q : cauHoi) {
            // ChatBox yeu cau dang nhap (PR #25): goi bang tai khoan khach hang.
            MvcResult r = goi(post("/api/chat"), customer, Map.of("message", q));
            String body = r.getResponse().getContentAsString();
            assertThat(r.getResponse().getStatus()).as(q + " -> " + body).isEqualTo(200);
            khongCoDauVet(body, "chat: " + q);
            assertThat(body).as(q).doesNotContain("3290000").doesNotContain("3.290.000");
            JsonNode res = om.readTree(body);
            if (res.has("products") && res.get("products").isArray()) {
                for (JsonNode p : res.get("products")) {
                    assertThat(p.get("maSanPham").asText()).as("chat khong goi y SP khong co gia cong bo").isNotEqualTo(SP);
                    assertThat(p.has("giaBan")).as("SP chat goi y phai co gia cong bo").isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("P8 Admin van xem gia noi bo; don hang lich su giu nguyen don gia")
    void p8_adminVaLichSu() throws Exception {
        JsonNode sp = json(goi(get("/api/admin/products/" + SP), admin, null));
        assertThat(bienThe(sp, PB_SEED).get("giaBan").decimalValue()).isEqualByComparingTo(DAU_VET);
        List<Map<String, Object>> truoc = jdbc.queryForList("SELECT maChiTiet, donGia FROM chitietdonhang ORDER BY maChiTiet");
        goi(get("/api/products/" + SP), null, null);
        List<Map<String, Object>> sau = jdbc.queryForList("SELECT maChiTiet, donGia FROM chitietdonhang ORDER BY maChiTiet");
        assertThat(sau).isEqualTo(truoc);
        // Gia goc khong bi xoa / ghi de trong database
        BigDecimal g = jdbc.queryForObject("SELECT giaBan FROM phienbansanpham WHERE maPhienBan=?", BigDecimal.class, PB_SEED);
        assertThat(g).isEqualByComparingTo(DAU_VET);
    }
}
