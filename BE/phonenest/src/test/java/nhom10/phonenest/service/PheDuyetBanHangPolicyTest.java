package nhom10.phonenest.service;

import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.support.TestVariants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PheDuyetBanHangPolicyTest {

    private static PhienBanSanPham bienThe() {
        SanPham sp = new SanPham();
        sp.setMaSanPham("SP_T");
        sp.setTenMay("May Test");
        sp.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
        PhienBanSanPham p = new PhienBanSanPham();
        p.setMaPhienBan("PB_T");
        p.setSanPham(sp);
        p.setRam(8);
        p.setBoNho(256);
        p.setMauSac("Đen");
        p.setGiaBan(new BigDecimal("10990000"));
        p.setGiaNiemYet(new BigDecimal("11990000"));
        p.setSoLuongTon(5);
        return p;
    }

    @Test
    void bienTheMoiMacDinhPendingVaKhongDuocBan() {
        PhienBanSanPham p = new PhienBanSanPham();
        assertThat(p.getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isFalse();
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(null)).isFalse();
    }

    @Test
    void approvedThieuDauVanTayKhongDuocBan() {
        PhienBanSanPham p = bienThe();
        p.setTrangThaiPheDuyet(TrangThaiPheDuyet.APPROVED);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isFalse();
        assertThatThrownBy(() -> PheDuyetBanHangPolicy.kiemTraDuocBan(p, null))
                .isInstanceOf(AppException.class).hasMessageContaining("xác minh lại");
    }

    @Test
    void moiThayDoiDuLieuBanLamDauVanTayLech() {
        java.util.List<Consumer<PhienBanSanPham>> thayDoi = java.util.List.of(
                p -> p.setGiaBan(p.getGiaBan().add(BigDecimal.ONE)),
                p -> p.setGiaNiemYet(new BigDecimal("12990000")),
                p -> p.setGiaNiemYet(null),
                p -> p.setRam(12),
                p -> p.setBoNho(512),
                p -> p.setMauSac("Trắng"));
        for (Consumer<PhienBanSanPham> c : thayDoi) {
            PhienBanSanPham p = TestVariants.daDuyet(bienThe());
            assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isTrue();
            c.accept(p);
            assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isFalse();
        }
    }

    @Test
    void doiTonKhoKhongLamMatHieuLuc() {
        PhienBanSanPham p = TestVariants.daDuyet(bienThe());
        p.setSoLuongTon(0);
        p.setSoLuongTon(99);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isTrue();
    }

    @Test
    void sanPhamNgungKinhDoanhKhongDuocBan() {
        PhienBanSanPham p = TestVariants.daDuyet(bienThe());
        p.getSanPham().setTrangThai(TrangThaiSanPham.NGUNG_KINH_DOANH);
        assertThat(PheDuyetBanHangPolicy.duocPhepBan(p)).isFalse();
    }

    @Test
    void moTaThayDoiPhatHienDu5Truong() {
        BigDecimal g = new BigDecimal("100.00");
        assertThat(PheDuyetBanHangPolicy.moTaThayDoi(g, g, 8, 128, "Đen", new BigDecimal("100"), g, 8, 128, " Đen ")).isNull();
        assertThat(PheDuyetBanHangPolicy.moTaThayDoi(g, g, 8, 128, "Đen", g.add(BigDecimal.ONE), g, 8, 128, "Đen")).contains("giá bán");
        assertThat(PheDuyetBanHangPolicy.moTaThayDoi(g, g, 8, 128, "Đen", g, null, 8, 128, "Đen")).contains("giá niêm yết");
        assertThat(PheDuyetBanHangPolicy.moTaThayDoi(g, g, 8, 128, "Đen", g, g, 12, 128, "Đen")).contains("RAM");
        assertThat(PheDuyetBanHangPolicy.moTaThayDoi(g, g, 8, 128, "Đen", g, g, 8, 256, "Đen")).contains("ROM");
        assertThat(PheDuyetBanHangPolicy.moTaThayDoi(g, g, 8, 128, "Đen", g, g, 8, 128, "Xám")).contains("màu");
    }

    @Test
    void kiemTraVanBan() {
        for (String xau : new String[]{null, " ", "ngắn", "1234567890", "aaaaaaaaaa",
                "https://cellphones.com.vn/x", "https://a.vn/x https://b.vn/y", "abc abc abc"}) {
            assertThatThrownBy(() -> PheDuyetBanHangPolicy.kiemTraVanBan(xau, "Lý do", 10, 255))
                    .as(String.valueOf(xau)).isInstanceOf(AppException.class);
        }
        assertThatThrownBy(() -> PheDuyetBanHangPolicy.kiemTraVanBan("Lý do ".repeat(60), "Lý do", 10, 255))
                .isInstanceOf(AppException.class);
        assertThat(PheDuyetBanHangPolicy.kiemTraVanBan(
                "Giá niêm yết TGDĐ 14/11/2026 https://www.thegioididong.com/dtdd/x", "Nguồn", 10, 500))
                .startsWith("Giá niêm yết");
    }
}
