package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import nhom10.phonenest.support.ItHttpBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 2B.7D Nhiem vu 6 — doi chieu tien don hang: gia tai thoi diem dat, so luong, voucher, dieu kien,
 * muc giam, phi van chuyen, tong; du lieu luu o donhang / chitietdonhang / thanhtoan / sudungkhuyenmai.
 * Khach KHONG the tu dat gia, muc giam hay tong tien trong request. HTTP that tren clone.
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderTotalIntegrationTest extends ItHttpBase {

    private static final String PB = "PB010";

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("UPDATE sanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham='SP010'");
        jdbc.update("UPDATE phienbansanpham SET ram=4, boNho=128, mauSac='Xanh Navy', giaBan=3290000, giaNiemYet=3690000, "
                + "soLuongTon=GREATEST(soLuongTon, 20), trangThai='DANG_KINH_DOANH' WHERE maPhienBan=?", PB);
        duyet(PB);
    }

    private MvcResult dat(Map<String, Object> body) throws Exception {
        return goi(post("/api/customer/orders").header("Idempotency-Key", UUID.randomUUID().toString()), customer, body);
    }

    private static Map<String, Object> don(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phuongThucTt", "COD");
        m.put("maDiaChi", DIA_CHI);
        m.put("maSanPham", PB);
        m.put("soLuong", 1);
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    @Order(1)
    @DisplayName("O1 NEST10 + truong gia gia mao trong request: backend tu tinh, luu nhat quan o 4 bang, khop preview")
    void o1_tinhTienVaChongGiaMao() throws Exception {
        Integer daDung = jdbc.queryForObject("SELECT COUNT(*) FROM sudungkhuyenmai WHERE maKh=? AND maKm='NEST10'", Integer.class, KHACH);
        assertThat(daDung).as("clone moi: khach chua dung NEST10").isZero();
        BigDecimal gia = jdbc.queryForObject("SELECT giaBan FROM phienbansanpham WHERE maPhienBan=?", BigDecimal.class, PB);
        int sl = 2;

        JsonNode pv = json(goi(get("/api/customer/checkout/preview").param("maDiaChi", DIA_CHI).param("maSanPham", PB)
                .param("soLuong", String.valueOf(sl)), customer, null));
        BigDecimal tamTinhPv = pv.get("tamTinh").decimalValue();
        BigDecimal phiPv = pv.get("phiVanChuyen").decimalValue();
        BigDecimal giamPv = null;
        for (JsonNode v : pv.get("vouchers")) if ("NEST10".equals(v.get("maKm").asText())) giamPv = v.get("soTienGiam").decimalValue();

        MvcResult r = dat(don("soLuong", sl, "maKm", "NEST10",
                "tongThanhToan", 1, "giamGia", 99999999, "donGia", 1, "giaBan", 1, "phiVanChuyen", 0, "tamTinh", 1));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isIn(200, 201);
        String ma = json(r).get("maDonHang").asText();

        Map<String, Object> d = jdbc.queryForMap("SELECT tamTinh, giamGia, phiVanChuyen, giamPhiVanChuyen, tongThanhToan, maKm FROM donhang WHERE maDonHang=?", ma);
        BigDecimal tamTinh = gia.multiply(BigDecimal.valueOf(sl));
        BigDecimal giam = tamTinh.multiply(BigDecimal.TEN).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).min(new BigDecimal("2000000"));
        BigDecimal phi = (BigDecimal) d.get("phiVanChuyen");
        BigDecimal giamPhi = (BigDecimal) d.get("giamPhiVanChuyen");
        assertThat((BigDecimal) d.get("tamTinh")).isEqualByComparingTo(tamTinh);
        assertThat((BigDecimal) d.get("giamGia")).isEqualByComparingTo(giam);
        assertThat(d.get("maKm")).isEqualTo("NEST10");
        assertThat((BigDecimal) d.get("tongThanhToan")).isEqualByComparingTo(tamTinh.subtract(giam).add(phi).subtract(giamPhi));
        // Khop voi preview Frontend dang hien thi
        assertThat(tamTinhPv).isEqualByComparingTo(tamTinh);
        assertThat(phiPv).isEqualByComparingTo(phi);
        assertThat(giamPv).isEqualByComparingTo(giam);
        // Chi tiet don: don gia = gia bien the luc dat, khong phai gia khach gui
        Map<String, Object> ct = jdbc.queryForMap("SELECT donGia, soLuong FROM chitietdonhang WHERE maDonHang=?", ma);
        assertThat((BigDecimal) ct.get("donGia")).isEqualByComparingTo(gia);
        assertThat(((Number) ct.get("soLuong")).intValue()).isEqualTo(sl);
        // Thanh toan = tong don
        assertThat(jdbc.queryForObject("SELECT soTien FROM thanhtoan WHERE maDonHang=?", BigDecimal.class, ma))
                .isEqualByComparingTo((BigDecimal) d.get("tongThanhToan"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sudungkhuyenmai WHERE maDonHang=? AND maKm='NEST10'", Integer.class, ma)).isEqualTo(1);
        // Phan hoi API cung khop
        assertThat(json(r).get("tongTien").decimalValue()).isEqualByComparingTo((BigDecimal) d.get("tongThanhToan"));
    }

    @Test
    @Order(2)
    @DisplayName("O2 Dung NEST10 lan 2 (gioi han 1/khach) -> 400, khong tao don")
    void o2_gioiHanMoiKhach() throws Exception {
        long truoc = jdbc.queryForObject("SELECT COUNT(*) FROM donhang WHERE maKh=?", Long.class, KHACH);
        MvcResult r = dat(don("maKm", "NEST10"));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM donhang WHERE maKh=?", Long.class, KHACH)).isEqualTo(truoc);
    }

    @Test
    @Order(3)
    @DisplayName("O3 Voucher het han / sai loai / khong ton tai / chua du don toi thieu / trung ma -> 400, khong tao don")
    void o3_voucherKhongHopLe() throws Exception {
        long truoc = jdbc.queryForObject("SELECT COUNT(*) FROM donhang", Long.class);
        for (Map<String, Object> body : new Map[]{
                don("maKm", "HETHAN20"),
                don("maKm", "FREESHIP"),
                don("maKm", "KHONG_TON_TAI_2B7D"),
                don("maKm", "SALE10TR"),
                don("maKmVanChuyen", "NEST10"),
                don("maKm", "SHIP50", "maKmVanChuyen", "SHIP50")}) {
            MvcResult r = dat(body);
            assertThat(r.getResponse().getStatus()).as(body + " -> " + r.getResponse().getContentAsString()).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM donhang", Long.class)).isEqualTo(truoc);
    }

    @Test
    @Order(4)
    @DisplayName("O4 So luong khong hop le / vuot ton -> 400")
    void o4_soLuong() throws Exception {
        int ton = jdbc.queryForObject("SELECT soLuongTon FROM phienbansanpham WHERE maPhienBan=?", Integer.class, PB);
        for (int sl : new int[]{0, -1, ton + 1}) {
            MvcResult r = dat(don("soLuong", sl));
            assertThat(r.getResponse().getStatus()).as("soLuong=" + sl + " -> " + r.getResponse().getContentAsString()).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("SELECT soLuongTon FROM phienbansanpham WHERE maPhienBan=?", Integer.class, PB)).isEqualTo(ton);
    }
}
