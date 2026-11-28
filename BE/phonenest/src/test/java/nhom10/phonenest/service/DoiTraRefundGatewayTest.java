package nhom10.phonenest.service;

import nhom10.phonenest.dto.DoiTraDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Hoàn tiền đổi trả: kết hợp điều chỉnh số tiền (hậu mãi V2) với hoàn tự động qua MoMo/VNPay. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DoiTraRefundGatewayTest {
    @Mock YeuCauDoiTraRepository repo;
    @Mock DonHangRepository donRepo;
    @Mock ThongBaoService thongBao;
    @Mock NhatKyHoatDongService nhatKy;
    @Mock KhoService khoService;
    @Mock PhienBanSanPhamRepository phienBanRepo;
    @Mock ThanhToanRepository thanhToanRepo;
    @Mock MomoPaymentService momo;
    @Mock VnPayPaymentService vnPay;
    @InjectMocks DoiTraService service;

    YeuCauDoiTra y;
    ChiTietDoiTra line;

    @BeforeEach
    void setUp() {
        KhachHang kh = new KhachHang();
        kh.setMaKh("KH1");
        kh.setHoTen("Khach");
        DonHang don = new DonHang();
        don.setMaDonHang("DH1");
        don.setKhachHang(kh);
        don.setTamTinh(new BigDecimal("1000000"));
        don.setTongThanhToan(new BigDecimal("1000000"));
        don.setGiamGia(BigDecimal.ZERO);
        PhienBanSanPham pb = new PhienBanSanPham();
        pb.setMaPhienBan("PB1");
        ChiTietDonHang ctdh = new ChiTietDonHang();
        ctdh.setPhienBan(pb);
        ctdh.setTenSanPham("May");
        ctdh.setTenPhienBan("8/128");
        ctdh.setDonGia(new BigDecimal("1000000"));
        ctdh.setSoLuong(1);
        y = new YeuCauDoiTra();
        y.setMaYeuCau("YC1");
        y.setDonHang(don);
        y.setTrangThai(TrangThaiDoiTra.DANG_XU_LY);
        y.setNgayTao(LocalDateTime.now().minusDays(2));
        line = new ChiTietDoiTra();
        line.setYeuCau(y);
        line.setChiTietDonHang(ctdh);
        line.setSoLuong(1);
        line.setLoaiYeuCau(LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN);
        line.setDaNhapKho(true);
        line.setNgayNhapKho(LocalDateTime.now().minusDays(1));
        y.getChiTiet().add(line);
        when(repo.findByIdForUpdate("YC1")).thenReturn(Optional.of(y));
    }

    private void paidBy(PhuongThucTt method) {
        ThanhToan tt = new ThanhToan();
        tt.setPhuongThuc(method);
        tt.setTrangThai(TrangThaiThanhToan.DA_THANH_TOAN);
        tt.setSoTien(new BigDecimal("1000000"));
        when(thanhToanRepo.findByDonHangMaDonHangOrderByNgayTaoDesc("DH1")).thenReturn(List.of(tt));
    }

    private DoiTraDto.XacNhanHoanTienRequest req(String method, String ref, BigDecimal amount, String reason) {
        return new DoiTraDto.XacNhanHoanTienRequest(method, ref, null, amount, reason, null);
    }

    @Test
    void momoRefundSendsAdjustedAmountWithoutManualReference() {
        paidBy(PhuongThucTt.VI_DIEN_TU);
        when(momo.refund(eq("DH1"), any(), eq("YC1"))).thenReturn("MOMO-RF-1");

        DoiTraDto dto = service.ghiNhanHoanTien("YC1", req("MOMO", null, new BigDecimal("800000"), "Trầy xước"), "AD1");

        verify(momo).refund("DH1", new BigDecimal("800000"), "YC1");
        assertEquals("MOMO-RF-1", dto.maGiaoDichHoanTien());
        assertNotNull(dto.ngayHoanTien());
        assertEquals(0, new BigDecimal("800000").compareTo(dto.soTienHoanDuKien()));
    }

    @Test
    void vnPayPendingKeepsRequestOpenAndRetryReusesSentAmount() {
        paidBy(PhuongThucTt.CHUYEN_KHOAN);
        when(vnPay.refundReturn(any(), any(), eq("AD1")))
                .thenReturn(new VnPayPaymentService.ReturnRefundResult("DANG_HOAN_TIEN", null, "Đang xử lý"));

        DoiTraDto first = service.ghiNhanHoanTien("YC1", req("VNPAY", null, new BigDecimal("700000"), "Phí"), "AD1");
        assertNull(first.ngayHoanTien());
        assertEquals("DANG_HOAN_TIEN", first.trangThaiHoanVnPay());
        assertEquals(0, new BigDecimal("700000").compareTo(first.soTienHoanDuKien()));

        // Lần gửi lại không cho chọn hình thức khác và giữ nguyên số tiền đã gửi, kể cả khi body khác.
        assertThrows(AppException.class, () -> service.ghiNhanHoanTien("YC1", req("CHUYEN_KHOAN", "X", null, null), "AD1"));
        when(vnPay.refundReturn(any(), any(), eq("AD1")))
                .thenReturn(new VnPayPaymentService.ReturnRefundResult("DA_HOAN_TIEN", "VNP-RF-1", "OK"));
        DoiTraDto second = service.ghiNhanHoanTien("YC1", req("VNPAY", null, null, null), "AD1");
        verify(vnPay, times(2)).refundReturn(same(y), eq(new BigDecimal("700000")), eq("AD1"));
        assertEquals("VNP-RF-1", second.maGiaoDichHoanTien());
        assertNotNull(second.ngayHoanTien());
    }

    @Test
    void uncollectedCodCannotBeRefundedEvenAfterRestock() {
        paidBy(PhuongThucTt.COD);
        var tt = thanhToanRepo.findByDonHangMaDonHangOrderByNgayTaoDesc("DH1").getFirst();
        tt.setTrangThai(TrangThaiThanhToan.CHO_THANH_TOAN);
        var ex = assertThrows(AppException.class, () -> service.ghiNhanHoanTien("YC1", req("CHUYEN_KHOAN", "CK-1", null, null), "AD1"));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
        assertNull(y.getNgayHoanTien());
        verifyNoInteractions(momo, vnPay, nhatKy);
    }

    @Test
    void missingPaymentCannotBeRefunded() {
        assertThrows(AppException.class, () -> service.ghiNhanHoanTien("YC1", req("CHUYEN_KHOAN", "CK-1", null, null), "AD1"));
        assertNull(y.getNgayHoanTien());
    }

    @Test
    void manualMethodStillRequiresReferenceAndCodOnlyBankTransfer() {
        paidBy(PhuongThucTt.COD);
        assertThrows(AppException.class, () -> service.ghiNhanHoanTien("YC1", req("CHUYEN_KHOAN", " ", null, null), "AD1"));
        assertThrows(AppException.class, () -> service.ghiNhanHoanTien("YC1", req("MOMO", null, null, null), "AD1"));
        verifyNoInteractions(momo, vnPay);

        DoiTraDto dto = service.ghiNhanHoanTien("YC1", req("CHUYEN_KHOAN", "CK-1", null, null), "AD1");
        assertEquals("CK-1", dto.maGiaoDichHoanTien());
        assertEquals(PhuongThucTt.COD, dto.phuongThucTt());
    }
}
