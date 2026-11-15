package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nhom10.phonenest.dto.AdminApprovalDto;
import nhom10.phonenest.entity.NhatKyHoatDong;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.NhatKyHoatDongRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tach quyen 4 mat (2B.7C): nguoi mo khoa khong duoc tu phe duyet khi bat co cau hinh. */
class PheDuyetTachQuyenTest {

    private PhienBanSanPhamRepository pbRepo;
    private NhatKyHoatDongRepository nhatKyRepo;
    private PheDuyetBanHangService service;
    private PhienBanSanPham p;

    @BeforeEach
    void setUp() {
        pbRepo = mock(PhienBanSanPhamRepository.class);
        nhatKyRepo = mock(NhatKyHoatDongRepository.class);
        service = new PheDuyetBanHangService(pbRepo, mock(NhatKyHoatDongService.class), nhatKyRepo, new ObjectMapper());
        ReflectionTestUtils.setField(service, "tachQuyenMoKhoaVaDuyet", true);
        SanPham sp = new SanPham();
        sp.setMaSanPham("SP_T");
        sp.setTenMay("May Test");
        sp.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
        p = new PhienBanSanPham();
        p.setMaPhienBan("PB_T");
        p.setSanPham(sp);
        p.setRam(8);
        p.setBoNho(256);
        p.setMauSac("Đen");
        p.setGiaBan(new BigDecimal("10990000"));
        p.setGiaNiemYet(new BigDecimal("11990000"));
        p.setTrangThaiPheDuyet(TrangThaiPheDuyet.PENDING_REVIEW);
        when(pbRepo.findByIdForUpdate("PB_T")).thenReturn(Optional.of(p));
        NhatKyHoatDong moKhoa = new NhatKyHoatDong();
        moKhoa.setMaTk("TK_A");
        moKhoa.setHanhDong(HanhDongAudit.UNBLOCK_VARIANT);
        when(nhatKyRepo.findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongInOrderByThoiGianDescMaNhatKyDesc(
                any(), eq("PB_T"), any())).thenReturn(Optional.of(moKhoa));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void dangNhap(String maTk) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                maTk, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private AdminApprovalDto duyet() {
        return new AdminApprovalDto(TrangThaiPheDuyet.APPROVED, "Đã đối chiếu giá với nhà phân phối",
                "Bảng giá nhà phân phối ngày 14/11 (kiểm thử đơn vị)", p.getGiaBan(), p.getGiaNiemYet(),
                p.getRam(), p.getBoNho(), p.getMauSac(), null);
    }

    @Test
    void nguoiMoKhoaKhongDuocTuDuyet() {
        dangNhap("TK_A");
        assertThatThrownBy(() -> service.capNhatTrangThai("PB_T", duyet()))
                .isInstanceOf(AppException.class).hasMessageContaining("Tách quyền");
        assertThat(p.getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
    }

    @Test
    void adminKhacDuocDuyet() {
        dangNhap("TK_B");
        var kq = service.capNhatTrangThai("PB_T", duyet());
        assertThat(kq.trangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.APPROVED);
        assertThat(kq.duocPhepBan()).isTrue();
        assertThat(p.getNguoiPheDuyet()).isEqualTo("TK_B");
    }

    @Test
    void maKiemTraCuBiTuChoi() {
        dangNhap("TK_B");
        AdminApprovalDto d = duyet();
        AdminApprovalDto cu = new AdminApprovalDto(d.trangThaiPheDuyet(), d.lyDo(), d.nguonXacMinh(), d.giaPheDuyet(),
                d.giaNiemYetPheDuyet(), d.ramPheDuyet(), d.boNhoPheDuyet(), d.mauSacPheDuyet(), null, "0".repeat(32));
        assertThatThrownBy(() -> service.capNhatTrangThai("PB_T", cu))
                .isInstanceOf(AppException.class).hasMessageContaining("Tải lại");
        assertThat(p.getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.PENDING_REVIEW);
    }

    @Test
    void maKiemTraKhongPhuThuocTonKho() {
        String truoc = PheDuyetBanHangPolicy.maKiemTraPheDuyet(p);
        String suaTruoc = PheDuyetBanHangPolicy.maKiemTraSua(p);
        p.setSoLuongTon(p.getSoLuongTon() + 3);
        assertThat(PheDuyetBanHangPolicy.maKiemTraPheDuyet(p)).isEqualTo(truoc);
        // 2B.7D: form san pham khong con sua ton kho -> ma kiem tra sua cung khong phu thuoc ton kho
        assertThat(PheDuyetBanHangPolicy.maKiemTraSua(p)).isEqualTo(suaTruoc);
        p.setGiaBan(p.getGiaBan().add(BigDecimal.ONE));
        assertThat(PheDuyetBanHangPolicy.maKiemTraPheDuyet(p)).isNotEqualTo(truoc);
    }
}
