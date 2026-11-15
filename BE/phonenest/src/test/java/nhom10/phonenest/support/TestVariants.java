package nhom10.phonenest.support;

import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.service.PheDuyetBanHangPolicy;

import java.time.LocalDateTime;

/** Fixture cho unit test: dat bien the vao trang thai APPROVED hop le (co dau van tay). */
public final class TestVariants {

    private TestVariants() {
    }

    /** Goi SAU khi da gan sanPham, gia, RAM/ROM/mau cho bien the. */
    public static PhienBanSanPham daDuyet(PhienBanSanPham p) {
        p.setTrangThaiPheDuyet(TrangThaiPheDuyet.APPROVED);
        p.setNguoiPheDuyet("TK001");
        p.setNgayPheDuyet(LocalDateTime.now());
        p.setLyDoPheDuyet("Fixture unit test: da xac minh");
        p.setNguonXacMinh("Fixture unit test: nguon xac minh gia");
        p.setGiaPheDuyet(p.getGiaBan());
        p.setGiaNiemYetPheDuyet(p.getGiaNiemYet());
        p.setDauVanTayPheDuyet(PheDuyetBanHangPolicy.dauVanTay(p));
        return p;
    }
}
