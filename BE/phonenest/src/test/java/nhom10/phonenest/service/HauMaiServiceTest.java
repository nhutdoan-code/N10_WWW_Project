package nhom10.phonenest.service;

import nhom10.phonenest.dto.HauMaiDto;
import nhom10.phonenest.entity.ChiTietDonHang;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.enums.LoaiHauMai;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiHauMai;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.YeuCauHauMaiRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HauMaiServiceTest {
    @Mock private YeuCauHauMaiRepository yeuCauRepo;
    @Mock private DonHangRepository donHangRepo;
    @Mock private ThongBaoService thongBaoService;
    @InjectMocks private HauMaiService service;

    private DonHang donHang(String maKh) {
        KhachHang kh = new KhachHang();
        kh.setMaKh(maKh);
        kh.setHoTen("Khach Hang");
        DonHang don = new DonHang();
        don.setMaDonHang("DH1");
        don.setKhachHang(kh);
        don.setTrangThai(TrangThaiDonHang.DA_GIAO);
        don.setNgayGiao(LocalDateTime.now().minusDays(4));
        return don;
    }

    @Test
    void khongXemDuocDonCuaKhachKhac() {
        when(donHangRepo.findByIdForUpdate("DH1")).thenReturn(Optional.of(donHang("KH1")));
        HauMaiDto.Request request = new HauMaiDto.Request("DH1", LoaiHauMai.KHIEU_NAI,
                null, "Giao sai dia chi", null);
        AppException error = assertThrows(AppException.class, () -> service.tao("KH2", request));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verifyNoInteractions(yeuCauRepo, thongBaoService);
    }

    @Test
    void khongChoKhieuNaiKhiDonMoiChoXacNhan() {
        DonHang don = donHang("KH1");
        don.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);
        when(donHangRepo.findByIdForUpdate("DH1")).thenReturn(Optional.of(don));
        AppException error = assertThrows(AppException.class, () -> service.tao("KH1",
                new HauMaiDto.Request("DH1", LoaiHauMai.KHIEU_NAI,
                        null, "Yeu cau xu ly", null)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(yeuCauRepo, thongBaoService);
    }

    @Test
    void tuChoiBaoHanhHetHan() {
        DonHang don = donHang("KH1");
        don.setNgayGiao(LocalDateTime.now().minusMonths(13));
        PhienBanSanPham phienBan = new PhienBanSanPham();
        phienBan.setMaPhienBan("PB1");
        ChiTietDonHang dong = new ChiTietDonHang();
        dong.setMaChiTiet("CT1");
        dong.setPhienBan(phienBan);
        dong.setBaoHanhThang(12);
        don.setChiTiet(List.of(dong));
        when(donHangRepo.findByIdForUpdate("DH1")).thenReturn(Optional.of(don));

        AppException error = assertThrows(AppException.class, () -> service.tao("KH1",
                new HauMaiDto.Request("DH1", LoaiHauMai.BAO_HANH, "PB1", "May khong bat", null)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(yeuCauRepo, thongBaoService);
    }

    @Test
    void taoKhieuNaiCoLichSuBanDau() {
        when(donHangRepo.findByIdForUpdate("DH1")).thenReturn(Optional.of(donHang("KH1")));
        HauMaiDto dto = service.tao("KH1", new HauMaiDto.Request(
                "DH1", LoaiHauMai.KHIEU_NAI, null, "Giao hang cham", null));
        assertEquals(TrangThaiHauMai.CHO_TIEP_NHAN, dto.trangThai());
        assertEquals(1, dto.lichSu().size());
        assertEquals("DH1", dto.maDonHang());
        verify(yeuCauRepo).save(any());
        verify(thongBaoService).guiQuanTri(anyString());
    }

    @Test
    void trangThaiKhongDuocNhayHoanTatNgay() {
        assertFalse(TrangThaiHauMai.CHO_TIEP_NHAN.coTheChuyenSang(TrangThaiHauMai.HOAN_TAT));
        assertTrue(TrangThaiHauMai.CHO_TIEP_NHAN.coTheChuyenSang(TrangThaiHauMai.DANG_XU_LY));
        assertTrue(TrangThaiHauMai.DANG_XU_LY.coTheChuyenSang(TrangThaiHauMai.HOAN_TAT));
        assertFalse(TrangThaiHauMai.HOAN_TAT.coTheChuyenSang(TrangThaiHauMai.DANG_XU_LY));
    }
}
