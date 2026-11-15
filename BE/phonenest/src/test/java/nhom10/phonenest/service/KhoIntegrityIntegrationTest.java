package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nhom10.phonenest.support.ItHttpBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 2B.7D Nhiem vu 3 — ton kho chi doi qua nghiep vu Kho (co ly do, nguoi thuc hien, lich su BienDongKho):
 * form san pham khong doi ton cua bien the da co; ton ban dau cua bien the moi duoc ghi so kho;
 * dieu chinh kiem ke tu choi khi ton da doi; nhap kho / dieu chinh / dat hang / huy don song song
 * khong lam lech so kho (chuoi tonTruoc -> tonSau lien tuc, khop ton hien tai, khong am).
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
class KhoIntegrityIntegrationTest extends ItHttpBase {

    private static final String SP = "SP010";
    private static final String PB = "PB010";

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("UPDATE sanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham=?", SP);
        jdbc.update("UPDATE phienbansanpham SET ram=4, boNho=128, mauSac='Xanh Navy', giaBan=3290000, giaNiemYet=3690000, "
                + "trangThai='DANG_KINH_DOANH' WHERE maPhienBan=?", PB);
        duyet(PB);
    }

    private int ton() {
        return jdbc.queryForObject("SELECT soLuongTon FROM phienbansanpham WHERE maPhienBan=?", Integer.class, PB);
    }

    private long soDongKho() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM biendongkho WHERE maPhienBan=?", Long.class, PB);
    }

    /** Chuoi so kho cua PB tu moc: moi dong tonTruoc = tonSau dong truoc; dong cuoi = ton hien tai. */
    private void kiemTraChuoiSoKho(String tuMa, int tonDau) {
        List<Map<String, Object>> ds = jdbc.queryForList("SELECT maBienDong, loaiBienDong, tonTruoc, tonSau FROM biendongkho "
                + "WHERE maPhienBan=? AND maBienDong > ? ORDER BY maBienDong", PB, tuMa);
        int truoc = tonDau;
        for (Map<String, Object> d : ds) {
            assertThat(((Number) d.get("tonTruoc")).intValue()).as("chuoi so kho tai " + d).isEqualTo(truoc);
            truoc = ((Number) d.get("tonSau")).intValue();
            assertThat(truoc).as("ton khong am").isGreaterThanOrEqualTo(0);
        }
        assertThat(ton()).as("ton hien tai = tonSau cuoi so kho").isEqualTo(truoc);
    }

    private String mocSoKho() {
        String m = jdbc.queryForObject("SELECT MAX(maBienDong) FROM biendongkho", String.class);
        return m == null ? "" : m;
    }

    @Test
    @DisplayName("K1 Form san pham KHONG doi ton kho bien the da co, khong tao so kho")
    void k1_formKhongDoiTon() throws Exception {
        int t0 = ton();
        long d0 = soDongKho();
        JsonNode f = json(goi(get("/api/admin/products/" + SP), admin, null));
        for (JsonNode v : f.get("phienBans")) if (PB.equals(v.get("maPhienBan").asText())) ((ObjectNode) v).put("soLuongTon", t0 + 500);
        assertThat(goi(put("/api/admin/products/" + SP), admin, f).getResponse().getStatus()).isEqualTo(200);
        assertThat(ton()).isEqualTo(t0);
        assertThat(soDongKho()).isEqualTo(d0);
    }

    @Test
    @DisplayName("K2 Bien the moi tao qua form: ton ban dau ghi so kho NHAP_KHO (nguoi thuc hien, ly do)")
    void k2_tonBanDau() throws Exception {
        JsonNode f = json(goi(get("/api/admin/products/" + SP), admin, null));
        String mau = "IT Kho " + (System.nanoTime() % 1_000_000);
        ObjectNode moi = om.createObjectNode();
        moi.put("ram", 6).put("boNho", 128).put("mauSac", mau).put("giaBan", 3590000).put("giaNiemYet", 3990000).put("soLuongTon", 5);
        ((ArrayNode) f.get("phienBans")).add(moi);
        assertThat(goi(put("/api/admin/products/" + SP), admin, f).getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> pb = jdbc.queryForMap("SELECT maPhienBan, soLuongTon, trangThaiPheDuyet FROM phienbansanpham WHERE maSanPham=? AND mauSac=?", SP, mau);
        assertThat(((Number) pb.get("soLuongTon")).intValue()).isEqualTo(5);
        assertThat(pb.get("trangThaiPheDuyet")).isEqualTo("PENDING_REVIEW");
        Map<String, Object> bd = jdbc.queryForMap("SELECT loaiBienDong, soLuong, tonTruoc, tonSau, nguoiThucHien, ghiChu FROM biendongkho WHERE maPhienBan=?", pb.get("maPhienBan"));
        assertThat(bd.get("loaiBienDong")).isEqualTo("NHAP_KHO");
        assertThat(((Number) bd.get("tonTruoc")).intValue()).isZero();
        assertThat(((Number) bd.get("tonSau")).intValue()).isEqualTo(5);
        assertThat(bd.get("nguoiThucHien")).isEqualTo("TK001");
        assertThat((String) bd.get("ghiChu")).contains("Tồn kho ban đầu");
    }

    @Test
    @DisplayName("K3 Dieu chinh kiem ke: ton da doi -> 409; thieu so ton da xem -> 400; am -> 400; hop le -> ghi so kho")
    void k3_dieuChinh() throws Exception {
        int t0 = ton();
        MvcResult cu = goi(put("/api/admin/inventory/" + PB + "/adjust"), admin,
                Map.of("soLuongThucTe", t0 + 3, "lyDo", "Kiểm kê IT 2B.7D", "soLuongHienTai", t0 - 1));
        assertThat(cu.getResponse().getStatus()).isEqualTo(409);
        assertThat(cu.getResponse().getContentAsString()).contains("STALE_STOCK");
        assertThat(goi(put("/api/admin/inventory/" + PB + "/adjust"), admin,
                Map.of("soLuongThucTe", t0 + 3, "lyDo", "Kiểm kê IT 2B.7D")).getResponse().getStatus()).isEqualTo(400);
        assertThat(goi(put("/api/admin/inventory/" + PB + "/adjust"), admin,
                Map.of("soLuongThucTe", -1, "lyDo", "Kiểm kê IT 2B.7D", "soLuongHienTai", t0)).getResponse().getStatus()).isEqualTo(400);
        assertThat(ton()).isEqualTo(t0);
        String moc = mocSoKho();
        assertThat(goi(put("/api/admin/inventory/" + PB + "/adjust"), admin,
                Map.of("soLuongThucTe", t0 + 3, "lyDo", "Kiểm kê IT 2B.7D", "soLuongHienTai", t0)).getResponse().getStatus()).isEqualTo(200);
        assertThat(ton()).isEqualTo(t0 + 3);
        kiemTraChuoiSoKho(moc, t0);
    }

    @Test
    @DisplayName("K4 Nhap kho + dieu chinh + dat hang SONG SONG: so kho lien tuc, khop ton, khong am, khong mat cap nhat")
    void k4_songSong() throws Exception {
        jdbc.update("DELETE FROM chitietgiohang WHERE maGioHang IN (SELECT maGioHang FROM giohang WHERE maKh=?)", KHACH);
        for (int vong = 0; vong < 3; vong++) {
            int t0 = ton();
            String moc = mocSoKho();
            List<Callable<Integer>> viec = new ArrayList<>();
            List<String> loiDon = java.util.Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < 5; i++) {
                viec.add(() -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("phuongThucTt", "COD");
                    b.put("maDiaChi", DIA_CHI);
                    b.put("maSanPham", PB);
                    b.put("soLuong", 1);
                    MvcResult rr = goi(post("/api/customer/orders").header("Idempotency-Key", UUID.randomUUID().toString()), customer, b);
                    if (rr.getResponse().getStatus() >= 300) loiDon.add(rr.getResponse().getStatus() + " " + rr.getResponse().getContentAsString());
                    return rr.getResponse().getStatus();
                });
            }
            for (int i = 0; i < 3; i++) {
                viec.add(() -> goi(post("/api/admin/inventory/" + PB + "/import"), admin,
                        Map.of("soLuong", 2, "ghiChu", "IT 2B.7D nhap song song")).getResponse().getStatus());
            }
            final int daXem = t0;
            viec.add(() -> goi(put("/api/admin/inventory/" + PB + "/adjust"), admin,
                    Map.of("soLuongThucTe", daXem + 50, "lyDo", "Kiểm kê song song IT 2B.7D", "soLuongHienTai", daXem)).getResponse().getStatus());
            @SuppressWarnings("unchecked")
            List<Integer> kq = song(viec.toArray(Callable[]::new));
            int donOk = (int) kq.subList(0, 5).stream().filter(s -> s == 200 || s == 201).count();
            int nhapOk = (int) kq.subList(5, 8).stream().filter(s -> s == 200).count();
            int dcSt = kq.get(8);
            assertThat(kq).as("vong " + vong + " " + kq + " " + loiDon).allMatch(s -> s == 200 || s == 201 || s == 409);
            assertThat(donOk).as("phai co don dat thanh cong de kiem tra that su, vong " + vong + " " + loiDon).isPositive();
            kiemTraChuoiSoKho(moc, t0);
            if (dcSt != 200) {
                assertThat(ton()).as("vong " + vong).isEqualTo(t0 + 2 * nhapOk - donOk);
            }
        }
    }

    @Test
    @DisplayName("K5 Huy don hoan ton kho va ghi so kho HOAN_HUY_DON")
    void k5_huyDon() throws Exception {
        int t0 = ton();
        String moc = mocSoKho();
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("phuongThucTt", "COD");
        b.put("maDiaChi", DIA_CHI);
        b.put("maSanPham", PB);
        b.put("soLuong", 2);
        MvcResult r = goi(post("/api/customer/orders").header("Idempotency-Key", UUID.randomUUID().toString()), customer, b);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isIn(200, 201);
        String ma = json(r).get("maDonHang").asText();
        assertThat(ton()).isEqualTo(t0 - 2);
        MvcResult h = goi(put("/api/customer/orders/" + ma + "/cancel"), customer, Map.of("lyDo", "Muốn thay đổi địa chỉ giao hàng"));
        assertThat(h.getResponse().getStatus()).as(h.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(ton()).isEqualTo(t0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM biendongkho WHERE maDonHang=? AND loaiBienDong='HOAN_HUY_DON'", Integer.class, ma)).isEqualTo(1);
        kiemTraChuoiSoKho(moc, t0);
    }
}
