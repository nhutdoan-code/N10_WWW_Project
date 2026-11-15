package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nhom10.phonenest.support.ItHttpBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 2B.7D Nhiem vu 2 — hai Admin sua cung san pham: thong tin chung (ten, mo ta), thong so, hinh anh.
 * Form cu -> 409, du lieu cua Admin kia khong bi ghi de. Co ca kich ban chay song song that.
 * Chung minh "kiem tra ma + ghi" an toan: ca hai buoc nam trong CUNG giao dich sau khi khoa dong
 * san pham (SELECT ... FOR UPDATE) — khi chay song song, dung mot request thang, request con lai 409.
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
class ProductLostUpdateIntegrationTest extends ItHttpBase {

    private static final String SP = "SP010";
    private static final int VONG = 6;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hinhanhsanpham WHERE maSanPham=? AND duongDan LIKE '/uploads/it-2b7d-%'", SP);
        jdbc.update("UPDATE sanpham SET tenMay='Redmi 13C', moTa='Mo ta goc IT 2B.7D', chip='MediaTek Helio G85', "
                + "trangThai='DANG_KINH_DOANH' WHERE maSanPham=?", SP);
    }

    private JsonNode form() throws Exception {
        return json(goi(get("/api/admin/products/" + SP), admin, null));
    }

    private int luu(JsonNode f) throws Exception {
        return goi(put("/api/admin/products/" + SP), admin, f).getResponse().getStatus();
    }

    private JsonNode sua(JsonNode f, Consumer<ObjectNode> c) {
        JsonNode copy = f.deepCopy();
        c.accept((ObjectNode) copy);
        return copy;
    }

    private String cot(String c) {
        return jdbc.queryForObject("SELECT " + c + " FROM sanpham WHERE maSanPham=?", String.class, SP);
    }

    private String anhHash() {
        return jdbc.queryForObject("SELECT MD5(IFNULL(GROUP_CONCAT(CONCAT_WS('|', maHinhAnh, duongDan, anhChinh, IFNULL(mauSac,''), IFNULL(maPhienBan,'')) ORDER BY maHinhAnh), '')) "
                + "FROM hinhanhsanpham WHERE maSanPham=?", String.class, SP);
    }

    private String hangSanPham() {
        return jdbc.queryForObject("SELECT MD5(CONCAT_WS('|', tenMay, moTa, chip, pin, kichThuocManHinh, trangThai)) FROM sanpham WHERE maSanPham=?", String.class, SP);
    }

    private static ObjectNode anhMoi(JsonNode f, String duongDan) {
        ObjectNode a = ((ObjectNode) f).objectNode();
        a.put("duongDan", duongDan);
        return a;
    }

    @Test
    @DisplayName("L1 Form cu sau khi Admin khac doi MO TA -> 409, mo ta moi giu nguyen, ten khong doi")
    void l1_moTa() throws Exception {
        JsonNode a = form();
        assertThat(luu(sua(form(), n -> n.put("moTa", "Mo ta cua Admin B")))).isEqualTo(200);
        MvcResult r = goi(put("/api/admin/products/" + SP), admin, sua(a, n -> n.put("tenMay", "Redmi 13C (A sua)")));
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(r.getResponse().getContentAsString()).contains("STALE_PRODUCT");
        assertThat(cot("moTa")).isEqualTo("Mo ta cua Admin B");
        assertThat(cot("tenMay")).isEqualTo("Redmi 13C");
    }

    @Test
    @DisplayName("L2 Form cu sau khi Admin khac doi THONG SO -> 409, thong so moi giu nguyen")
    void l2_thongSo() throws Exception {
        JsonNode a = form();
        assertThat(luu(sua(form(), n -> ((ObjectNode) n.get("thongSo")).put("chip", "Chip Admin B")))).isEqualTo(200);
        assertThat(luu(sua(a, n -> n.put("moTa", "A chi sua mo ta")))).isEqualTo(409);
        assertThat(cot("chip")).isEqualTo("Chip Admin B");
        assertThat(cot("moTa")).isEqualTo("Mo ta goc IT 2B.7D");
    }

    @Test
    @DisplayName("L3 Form cu sau khi Admin khac THEM / XOA anh -> 409, tap anh cua Admin kia giu nguyen")
    void l3_anh() throws Exception {
        JsonNode a = form();
        JsonNode b = form();
        ((ArrayNode) b.get("hinhAnhs")).add(anhMoi(b, "/uploads/it-2b7d-b.webp"));
        assertThat(luu(b)).isEqualTo(200);
        String sauB = anhHash();
        // A (mo truoc khi B them anh) luu -> neu khong co ma kiem tra, anh cua B se bi XOA (reconcile theo payload)
        assertThat(luu(sua(a, n -> n.put("moTa", "A sua mo ta")))).isEqualTo(409);
        assertThat(anhHash()).isEqualTo(sauB);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hinhanhsanpham WHERE duongDan='/uploads/it-2b7d-b.webp'", Integer.class)).isEqualTo(1);
        // C mo form (co anh B), B xoa anh, C luu -> 409, khong tao lai anh da xoa
        JsonNode c = form();
        JsonNode b2 = form();
        ArrayNode ds = (ArrayNode) b2.get("hinhAnhs");
        for (int i = ds.size() - 1; i >= 0; i--) if (ds.get(i).get("duongDan").asText().startsWith("/uploads/it-2b7d-")) ds.remove(i);
        assertThat(luu(b2)).isEqualTo(200);
        assertThat(luu(c)).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hinhanhsanpham WHERE duongDan LIKE '/uploads/it-2b7d-%'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("L4 Giao dich that bai (409) khong de lai thay doi nao: san pham, anh, bien the")
    void l4_khongGhiMotPhan() throws Exception {
        JsonNode a = form();
        assertThat(luu(sua(form(), n -> n.put("moTa", "B")))).isEqualTo(200);
        String sp = hangSanPham();
        String anh = anhHash();
        String pb = jdbc.queryForObject("SELECT MD5(GROUP_CONCAT(CONCAT_WS('|', maPhienBan, giaBan, soLuongTon, trangThai, trangThaiPheDuyet) ORDER BY maPhienBan)) FROM phienbansanpham WHERE maSanPham=?", String.class, SP);
        JsonNode xau = sua(a, n -> {
            n.put("tenMay", "A doi ten");
            ((ObjectNode) n.get("thongSo")).put("chip", "A doi chip");
            ((ArrayNode) n.get("hinhAnhs")).add(anhMoi(n, "/uploads/it-2b7d-a.webp"));
        });
        assertThat(luu(xau)).isEqualTo(409);
        assertThat(hangSanPham()).isEqualTo(sp);
        assertThat(anhHash()).isEqualTo(anh);
        assertThat(jdbc.queryForObject("SELECT MD5(GROUP_CONCAT(CONCAT_WS('|', maPhienBan, giaBan, soLuongTon, trangThai, trangThaiPheDuyet) ORDER BY maPhienBan)) FROM phienbansanpham WHERE maSanPham=?", String.class, SP)).isEqualTo(pb);
    }

    @Test
    @DisplayName("L5 Hai Admin luu SONG SONG tu cung mot form (mo ta vs thong so vs anh), nhieu vong: dung mot thang, khong tron")
    void l5_songSong() throws Exception {
        for (int vong = 0; vong < VONG; vong++) {
            setUp();
            JsonNode f = form();
            JsonNode a = sua(f, n -> n.put("moTa", "A vong"));
            JsonNode b = sua(f, n -> ((ObjectNode) n.get("thongSo")).put("chip", "B vong"));
            JsonNode c = sua(f, n -> ((ArrayNode) n.get("hinhAnhs")).add(anhMoi(n, "/uploads/it-2b7d-c.webp")));
            List<Integer> kq = song(() -> luu(a), () -> luu(b), () -> luu(c));
            assertThat(kq.stream().filter(x -> x == 200).count()).as("vong " + vong + " " + kq).isEqualTo(1);
            assertThat(kq).as("vong " + vong).containsOnly(200, 409);
            boolean aThang = "A vong".equals(cot("moTa"));
            boolean bThang = "B vong".equals(cot("chip"));
            boolean cThang = jdbc.queryForObject("SELECT COUNT(*) FROM hinhanhsanpham WHERE duongDan='/uploads/it-2b7d-c.webp'", Integer.class) == 1;
            assertThat((aThang ? 1 : 0) + (bThang ? 1 : 0) + (cThang ? 1 : 0)).as("dung mot thay doi duoc luu, vong " + vong).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("L6 Sua bien the va sua thong tin chung tach biet: thong bao 409 neu ro phan bi doi")
    void l6_phanBiet() throws Exception {
        JsonNode a = form();
        // Admin khac doi gia mot bien the
        JsonNode b = form();
        for (JsonNode v : b.get("phienBans")) {
            if ("PB010".equals(v.get("maPhienBan").asText())) ((ObjectNode) v).put("giaBan", v.get("giaBan").asLong() + 1000);
        }
        assertThat(luu(b)).isEqualTo(200);
        MvcResult r = goi(put("/api/admin/products/" + SP), admin, sua(a, n -> n.put("moTa", "x")));
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(r.getResponse().getContentAsString()).contains("STALE_VARIANT").contains("PB010");
        jdbc.update("UPDATE phienbansanpham SET giaBan=3290000 WHERE maPhienBan='PB010'");
    }
}
