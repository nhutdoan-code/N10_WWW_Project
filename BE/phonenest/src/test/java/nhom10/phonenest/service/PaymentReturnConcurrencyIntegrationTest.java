package nhom10.phonenest.service;

import nhom10.phonenest.support.ItHttpBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 2B.7E Nhiem vu 1 — thanh toan MoMo (IPN gia lap, KHONG goi cong MoMo that), COD, huy don, doi tra,
 * hoan ton, voucher: request dong thoi, callback lap, callback sai thu tu, retry. HTTP that tren clone.
 * Khoa MoMo duoi day la gia tri GIA cho test, khong phai khoa that.
 */
@EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
@TestPropertySource(properties = {
        "momo.partner-code=PN_TEST_PARTNER",
        "momo.access-key=PN_TEST_ACCESS",
        "momo.secret-key=PN_TEST_SECRET_NOT_REAL"
})
class PaymentReturnConcurrencyIntegrationTest extends ItHttpBase {

    private static final String PB = "PB010";
    private static final int VONG = 5;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("UPDATE sanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham='SP010'");
        jdbc.update("UPDATE phienbansanpham SET ram=4, boNho=128, mauSac='Xanh Navy', giaBan=3290000, giaNiemYet=3690000, "
                + "soLuongTon=GREATEST(soLuongTon, 200), trangThai='DANG_KINH_DOANH' WHERE maPhienBan=?", PB);
        duyet(PB);
    }

    // ------------------------------------------------------------------ tien ich

    private String datDon(String pt, String maKm) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("phuongThucTt", pt);
        b.put("maDiaChi", DIA_CHI);
        b.put("maSanPham", PB);
        b.put("soLuong", 1);
        if (maKm != null) b.put("maKm", maKm);
        MvcResult r = goi(post("/api/customer/orders").header("Idempotency-Key", UUID.randomUUID().toString()), customer, b);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isIn(200, 201);
        return json(r).get("maDonHang").asText();
    }

    /** Gia lap buoc create() da gui yeu cau toi MoMo: gan maGiaoDich = maThanhToan (chi tren clone). */
    private String giaoDichMoMo(String maDon) {
        jdbc.update("UPDATE thanhtoan SET maGiaoDich=maThanhToan WHERE maDonHang=? AND maGiaoDich IS NULL", maDon);
        return jdbc.queryForObject("SELECT maGiaoDich FROM thanhtoan WHERE maDonHang=? ORDER BY ngayTao DESC LIMIT 1", String.class, maDon);
    }

    private static String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("PN_TEST_SECRET_NOT_REAL".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder sb = new StringBuilder();
        for (byte x : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private Map<String, Object> ipn(String orderId, String amount, String resultCode, String transId) throws Exception {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("partnerCode", "PN_TEST_PARTNER");
        p.put("orderId", orderId);
        p.put("requestId", orderId);
        p.put("amount", amount);
        p.put("orderInfo", "Thanh toan don hang IT");
        p.put("orderType", "momo_wallet");
        p.put("transId", transId);
        p.put("resultCode", resultCode);
        p.put("message", "0".equals(resultCode) ? "Successful." : "Transaction denied.");
        p.put("payType", "qr");
        p.put("responseTime", String.valueOf(System.currentTimeMillis()));
        p.put("extraData", "");
        p.put("signature", hmac("accessKey=PN_TEST_ACCESS&amount=" + amount + "&extraData=&message=" + p.get("message")
                + "&orderId=" + orderId + "&orderInfo=" + p.get("orderInfo") + "&orderType=momo_wallet&partnerCode=PN_TEST_PARTNER"
                + "&payType=qr&requestId=" + orderId + "&responseTime=" + p.get("responseTime") + "&resultCode=" + resultCode
                + "&transId=" + transId));
        return p;
    }

    private int guiIpn(Map<String, Object> p) throws Exception {
        return goi(post("/api/payment/momo/ipn"), null, p).getResponse().getStatus();
    }

    private String tongTien(String maDon) {
        return jdbc.queryForObject("SELECT tongThanhToan FROM donhang WHERE maDonHang=?", BigDecimal.class, maDon).toBigInteger().toString();
    }

    private String trangThaiTt(String maGd) {
        return jdbc.queryForObject("SELECT trangThai FROM thanhtoan WHERE maGiaoDich=?", String.class, maGd);
    }

    private int thongBao(String maDon, String tuKhoa) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM thongbao WHERE noiDung LIKE ? AND noiDung LIKE ?", Integer.class,
                "%" + maDon + "%", "%" + tuKhoa + "%");
    }

    private int soDongKho(String maDon, String loai) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM biendongkho WHERE maDonHang=? AND loaiBienDong=?", Integer.class, maDon, loai);
    }

    private int ton() {
        return jdbc.queryForObject("SELECT soLuongTon FROM phienbansanpham WHERE maPhienBan=?", Integer.class, PB);
    }

    // ================================================================== MoMo IPN

    @Test
    @DisplayName("M1 IPN thanh cong va that bai den DONG THOI (nhieu vong): giao dich cuoi luon DA_THANH_TOAN, 1 thong bao")
    void m1_ipnThanhCongVaThatBaiDongThoi() throws Exception {
        for (int v = 0; v < VONG; v++) {
            String don = datDon("VI_DIEN_TU", null);
            String gd = giaoDichMoMo(don);
            String tien = tongTien(don);
            Map<String, Object> ok = ipn(gd, tien, "0", "T-OK-" + v);
            Map<String, Object> fail = ipn(gd, tien, "1006", "T-FAIL-" + v);
            List<Integer> kq = song(() -> guiIpn(fail), () -> guiIpn(ok), () -> guiIpn(fail));
            assertThat(kq).as("vong " + v).allMatch(s -> s == 200 || s == 204 || s == 409);
            // Neu IPN that bai bi 409 (xung dot) thi MoMo se gui lai: mo phong retry
            for (int i = 0; i < 2 && !"DA_THANH_TOAN".equals(trangThaiTt(gd)); i++) guiIpn(ok);
            assertThat(trangThaiTt(gd)).as("vong " + v + " " + kq).isEqualTo("DA_THANH_TOAN");
            assertThat(thongBao(don, "thành công")).as("vong " + v).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thanhtoan WHERE maDonHang=?", Integer.class, don)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("M2 IPN thanh cong lap 5 lan dong thoi: 1 ban ghi thanh toan, 1 thong bao, khong CAN_HOAN_TIEN gia")
    void m2_ipnLap() throws Exception {
        String don = datDon("VI_DIEN_TU", null);
        String gd = giaoDichMoMo(don);
        Map<String, Object> ok = ipn(gd, tongTien(don), "0", "T-DUP");
        List<Callable<Integer>> v = new ArrayList<>();
        for (int i = 0; i < 5; i++) v.add(() -> guiIpn(ok));
        @SuppressWarnings("unchecked")
        List<Integer> kq = song(v.toArray(Callable[]::new));
        assertThat(kq).allMatch(s -> s == 200 || s == 204 || s == 409);
        assertThat(trangThaiTt(gd)).isEqualTo("DA_THANH_TOAN");
        assertThat(thongBao(don, "thành công")).isEqualTo(1);
        assertThat(thongBao(don, "TRÙNG")).isZero();
    }

    @Test
    @DisplayName("M3 Sai thu tu tuan tu: that bai -> thanh cong = DA_THANH_TOAN; thanh cong -> that bai = van DA_THANH_TOAN")
    void m3_saiThuTu() throws Exception {
        String d1 = datDon("VI_DIEN_TU", null);
        String g1 = giaoDichMoMo(d1);
        assertThat(guiIpn(ipn(g1, tongTien(d1), "1006", "A1"))).isIn(200, 204);
        assertThat(trangThaiTt(g1)).isEqualTo("THAT_BAI");
        assertThat(guiIpn(ipn(g1, tongTien(d1), "0", "A2"))).isIn(200, 204);
        assertThat(trangThaiTt(g1)).isEqualTo("DA_THANH_TOAN");
        String d2 = datDon("VI_DIEN_TU", null);
        String g2 = giaoDichMoMo(d2);
        assertThat(guiIpn(ipn(g2, tongTien(d2), "0", "B1"))).isIn(200, 204);
        assertThat(guiIpn(ipn(g2, tongTien(d2), "1006", "B2"))).isIn(200, 204);
        assertThat(trangThaiTt(g2)).isEqualTo("DA_THANH_TOAN");
    }

    @Test
    @DisplayName("M4 IPN gia mao: sai chu ky / sai so tien / ma giao dich khong ton tai -> tu choi, khong doi trang thai")
    void m4_ipnKhongHopLe() throws Exception {
        String don = datDon("VI_DIEN_TU", null);
        String gd = giaoDichMoMo(don);
        Map<String, Object> sai = ipn(gd, tongTien(don), "0", "X1");
        sai.put("signature", "00" + ((String) sai.get("signature")).substring(2));
        assertThat(guiIpn(sai)).isEqualTo(400);
        assertThat(guiIpn(ipn(gd, "1000", "0", "X2"))).isEqualTo(400);
        assertThat(guiIpn(ipn("TT_KHONG_TON_TAI_IT", "1000", "0", "X3"))).isEqualTo(404);
        assertThat(trangThaiTt(gd)).isEqualTo("CHO_THANH_TOAN");
    }

    @Test
    @DisplayName("M5 Huy don roi IPN thanh cong -> CAN_HOAN_TIEN; da thanh toan roi huy -> CAN_HOAN_TIEN (khong de sot hoan tien)")
    void m5_huyVaThanhToan() throws Exception {
        String d1 = datDon("VI_DIEN_TU", null);
        String g1 = giaoDichMoMo(d1);
        assertThat(goi(put("/api/customer/orders/" + d1 + "/cancel"), customer, Map.of("lyDo", "Muốn thay đổi địa chỉ giao hàng"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(guiIpn(ipn(g1, tongTien(d1), "0", "C1"))).isIn(200, 204);
        assertThat(trangThaiTt(g1)).isEqualTo("CAN_HOAN_TIEN");

        String d2 = datDon("VI_DIEN_TU", null);
        String g2 = giaoDichMoMo(d2);
        assertThat(guiIpn(ipn(g2, tongTien(d2), "0", "C2"))).isIn(200, 204);
        assertThat(trangThaiTt(g2)).isEqualTo("DA_THANH_TOAN");
        assertThat(goi(put("/api/customer/orders/" + d2 + "/cancel"), customer, Map.of("lyDo", "Muốn thay đổi địa chỉ giao hàng"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(trangThaiTt(g2)).as("don da thanh toan bi huy phai cho hoan tien").isEqualTo("CAN_HOAN_TIEN");
        // IPN lap lai sau do khong doi trang thai
        assertThat(guiIpn(ipn(g2, tongTien(d2), "0", "C2"))).isIn(200, 204);
        assertThat(trangThaiTt(g2)).isEqualTo("CAN_HOAN_TIEN");
    }

    // ================================================================== Huy don, hoan ton, voucher

    @Test
    @DisplayName("H1 Khach huy 5 lan + Admin huy dong thoi (COD, co voucher): 1 lan huy, hoan ton 1 lan, hoan luot voucher 1 lan")
    void h1_huyDongThoi() throws Exception {
        jdbc.update("DELETE FROM sudungkhuyenmai WHERE maKh=? AND maKm='NEST10'", KHACH);
        int tonTruoc = ton();
        String don = datDon("COD", "NEST10");
        int daDung0 = jdbc.queryForObject("SELECT daDung FROM khuyenmai WHERE maKm='NEST10'", Integer.class);
        assertThat(ton()).isEqualTo(tonTruoc - 1);
        List<Callable<Integer>> v = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            v.add(() -> goi(put("/api/customer/orders/" + don + "/cancel"), customer, Map.of("lyDo", "Muốn thay đổi địa chỉ giao hàng"))
                    .getResponse().getStatus());
        }
        v.add(() -> goi(put("/api/admin/orders/" + don + "/status").param("trangThai", "DA_HUY"), admin, null).getResponse().getStatus());
        @SuppressWarnings("unchecked")
        List<Integer> kq = song(v.toArray(Callable[]::new));
        assertThat(kq.stream().filter(s -> s == 200).count()).as(kq.toString()).isEqualTo(1);
        assertThat(ton()).isEqualTo(tonTruoc);
        assertThat(soDongKho(don, "HOAN_HUY_DON")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT daDung FROM khuyenmai WHERE maKm='NEST10'", Integer.class)).isEqualTo(daDung0 - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sudungkhuyenmai WHERE maDonHang=?", Integer.class, don)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM huydonhang WHERE maDonHang=?", Integer.class, don)).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("V1 Voucher con 2 luot, 6 don dat dong thoi: dung 2 don dung duoc voucher, daDung khong vuot gioi han")
    void v1_voucherGioiHan() throws Exception {
        jdbc.update("UPDATE khuyenmai SET soLuong=daDung+2, gioiHanMoiKhach=100, trangThaiHoatDong=1, ngayKetThuc='2026-12-31' WHERE maKm='SHIP50'");
        int daDung0 = jdbc.queryForObject("SELECT daDung FROM khuyenmai WHERE maKm='SHIP50'", Integer.class);
        List<Callable<Integer>> v = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            v.add(() -> {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("phuongThucTt", "COD");
                b.put("maDiaChi", "DC002");
                b.put("maSanPham", PB);
                b.put("soLuong", 1);
                b.put("maKmVanChuyen", "SHIP50");
                return goi(post("/api/customer/orders").header("Idempotency-Key", UUID.randomUUID().toString()), customer, b)
                        .getResponse().getStatus();
            });
        }
        @SuppressWarnings("unchecked")
        List<Integer> kq = song(v.toArray(Callable[]::new));
        int daDung = jdbc.queryForObject("SELECT daDung FROM khuyenmai WHERE maKm='SHIP50'", Integer.class);
        assertThat(daDung).as(kq.toString()).isEqualTo(daDung0 + 2);
        assertThat(kq.stream().filter(s -> s == 200 || s == 201).count()).as(kq.toString()).isEqualTo(2);
        jdbc.update("UPDATE khuyenmai SET soLuong=NULL, gioiHanMoiKhach=1 WHERE maKm='SHIP50'");
    }

    // ================================================================== Doi tra / hoan tien

    private String donDaGiao() throws Exception {
        String don = datDon("COD", null);
        for (String tt : List.of("DA_XAC_NHAN", "DANG_CHUAN_BI", "DANG_GIAO")) {
            MvcResult r = goi(put("/api/admin/orders/" + don + "/status").param("trangThai", tt), admin, null);
            assertThat(r.getResponse().getStatus()).as(tt + " " + r.getResponse().getContentAsString()).isEqualTo(200);
        }
        assertThat(goi(put("/api/admin/orders/" + don + "/mark-delivered"), admin, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(goi(put("/api/customer/orders/" + don + "/confirm-received"), customer, null).getResponse().getStatus()).isEqualTo(200);
        return don;
    }

    private int yeuCauDoiTra(String don) throws Exception {
        return goi(post("/api/customer/returns"), customer, Map.of("maDonHang", don, "lyDo", "Máy lỗi màn hình khi nhận hàng"))
                .getResponse().getStatus();
    }

    @Test
    @DisplayName("D1 Khong tao duoc nhieu yeu cau doi tra cho cung mot don (tuan tu va dong thoi)")
    void d1_mot_yeuCau() throws Exception {
        String don = donDaGiao();
        assertThat(yeuCauDoiTra(don)).isIn(200, 201);
        assertThat(yeuCauDoiTra(don)).as("yeu cau thu hai").isIn(400, 409);
        String don2 = donDaGiao();
        List<Integer> kq = song(() -> yeuCauDoiTra(don2), () -> yeuCauDoiTra(don2), () -> yeuCauDoiTra(don2));
        assertThat(kq.stream().filter(s -> s == 200 || s == 201).count()).as(kq.toString()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM yeucaudoitra WHERE maDonHang=?", Integer.class, don2)).isEqualTo(1);
    }

    @Test
    @DisplayName("D2 Hai Admin hoan tat hoan tien dong thoi: 1 lan thanh cong, so tien hoan tinh 1 lan, 1 ban ghi audit")
    void d2_hoanTatDongThoi() throws Exception {
        String don = donDaGiao();
        assertThat(yeuCauDoiTra(don)).isIn(200, 201);
        String yc = jdbc.queryForObject("SELECT maYeuCau FROM yeucaudoitra WHERE maDonHang=?", String.class, don);
        assertThat(goi(put("/api/admin/returns/" + yc + "/status").param("trangThai", "DA_DUYET"), admin, null).getResponse().getStatus()).isEqualTo(200);
        List<Integer> kq = song(
                () -> goi(put("/api/admin/returns/" + yc + "/status").param("trangThai", "HOAN_TAT"), admin, null).getResponse().getStatus(),
                () -> goi(put("/api/admin/returns/" + yc + "/status").param("trangThai", "HOAN_TAT"), admin, null).getResponse().getStatus(),
                () -> goi(put("/api/admin/returns/" + yc + "/status").param("trangThai", "HOAN_TAT"), admin, null).getResponse().getStatus());
        assertThat(kq.stream().filter(s -> s == 200).count()).as(kq.toString()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM nhatkyhoatdong WHERE hanhDong='CHANGE_RETURN_STATUS' AND maDoiTuong=? AND duLieuSau LIKE '%HOAN_TAT%'",
                Integer.class, yc)).isEqualTo(1);
        BigDecimal hoan = jdbc.queryForObject("SELECT SUM(soTienHoan) FROM chitietdoitra WHERE maYeuCau=?", BigDecimal.class, yc);
        BigDecimal tam = jdbc.queryForObject("SELECT tamTinh - IFNULL(giamGia,0) FROM donhang WHERE maDonHang=?", BigDecimal.class, don);
        assertThat(hoan).isEqualByComparingTo(tam);
    }
}
