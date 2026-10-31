package nhom10.phonenest.service;

import nhom10.phonenest.dto.TaiKhoanDto;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.QuanTriVien;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.entity.TaiKhoan;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepresentativeBusinessIntegrationTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("KhoService Audit Integration")
    class KhoServiceTest {

        @Mock
        private SanPhamRepository spRepo;

        @Mock
        private PhienBanSanPhamRepository pbRepo;

        @Mock
        private NhatKyHoatDongService nhatKyHoatDongService;

        @InjectMocks
        private KhoService khoService;

        @Test
        @DisplayName("shouldTriggerStockImportAuditWhenNhapKhoSucceeds")
        void shouldTriggerStockImportAuditWhenNhapKhoSucceeds() {
            PhienBanSanPham pb = new PhienBanSanPham();
            pb.setMaPhienBan("PB001");
            pb.setSoLuongTon(10);
            SanPham sp = new SanPham();
            sp.setMaSanPham("SP001");
            nhom10.phonenest.entity.LoaiSanPham loai = new nhom10.phonenest.entity.LoaiSanPham();
            loai.setMaLoai("L01");
            loai.setTenLoai("Smartphone");
            sp.setLoai(loai);
            pb.setSanPham(sp);

            when(pbRepo.findById("PB001")).thenReturn(Optional.of(pb));

            khoService.nhapKho("PB001", 5);

            assertThat(pb.getSoLuongTon()).isEqualTo(15);
            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.STOCK_IMPORT),
                    eq(LoaiDoiTuongAudit.INVENTORY),
                    eq("PB001"),
                    argThat(map -> Integer.valueOf(10).equals(map.get("soLuongTon"))),
                    argThat(map -> Integer.valueOf(15).equals(map.get("soLuongTon")))
            );
        }

        @Test
        @DisplayName("shouldTriggerStockAdjustmentAuditWhenDatTonSucceeds")
        void shouldTriggerStockAdjustmentAuditWhenDatTonSucceeds() {
            PhienBanSanPham pb = new PhienBanSanPham();
            pb.setMaPhienBan("PB001");
            pb.setSoLuongTon(15);
            SanPham sp = new SanPham();
            sp.setMaSanPham("SP001");
            nhom10.phonenest.entity.LoaiSanPham loai = new nhom10.phonenest.entity.LoaiSanPham();
            loai.setMaLoai("L01");
            loai.setTenLoai("Smartphone");
            sp.setLoai(loai);
            pb.setSanPham(sp);

            when(pbRepo.findById("PB001")).thenReturn(Optional.of(pb));

            khoService.datTon("PB001", 10);

            assertThat(pb.getSoLuongTon()).isEqualTo(10);
            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.STOCK_ADJUSTMENT),
                    eq(LoaiDoiTuongAudit.INVENTORY),
                    eq("PB001"),
                    argThat(map -> Integer.valueOf(15).equals(map.get("soLuongTon"))),
                    argThat(map -> Integer.valueOf(10).equals(map.get("soLuongTon")))
            );
        }

        @Test
        @DisplayName("shouldNotAuditWhenValidationFailsOnNhapKho")
        void shouldNotAuditWhenValidationFailsOnNhapKho() {
            assertThatThrownBy(() -> khoService.nhapKho("PB001", 0))
                    .isInstanceOf(AppException.class);

            verify(nhatKyHoatDongService, never()).ghi(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("TaiKhoanService Audit Integration")
    class TaiKhoanServiceTest {

        @Mock
        private TaiKhoanRepository taiKhoanRepository;

        @Mock
        private KhachHangRepository khachHangRepository;

        @Mock
        private QuanTriVienRepository quanTriVienRepository;

        @Mock
        private DonHangRepository donHangRepository;

        @Mock
        private NhatKyHoatDongService nhatKyHoatDongService;

        @InjectMocks
        private TaiKhoanService taiKhoanService;

        @Test
        @DisplayName("shouldTriggerLockAccountAuditWhenLockingActiveAccount")
        void shouldTriggerLockAccountAuditWhenLockingActiveAccount() {
            TaiKhoan tk = new TaiKhoan();
            tk.setMaTk("TK002");
            tk.setTrangThaiHoatDong(true);

            when(taiKhoanRepository.findById("TK002")).thenReturn(Optional.of(tk));
            when(quanTriVienRepository.existsById("TK002")).thenReturn(false);

            TaiKhoanDto dto = taiKhoanService.doiTrangThai("TK002");

            assertThat(tk.isTrangThaiHoatDong()).isFalse();
            assertThat(dto.trangThaiHoatDong()).isFalse();

            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.LOCK_ACCOUNT),
                    eq(LoaiDoiTuongAudit.ACCOUNT),
                    eq("TK002"),
                    argThat(m -> Boolean.TRUE.equals(m.get("trangThai"))),
                    argThat(m -> Boolean.FALSE.equals(m.get("trangThai")))
            );
        }

        @Test
        @DisplayName("shouldTriggerUnlockAccountAuditWhenUnlockingLockedAccount")
        void shouldTriggerUnlockAccountAuditWhenUnlockingLockedAccount() {
            TaiKhoan tk = new TaiKhoan();
            tk.setMaTk("TK002");
            tk.setTrangThaiHoatDong(false);

            when(taiKhoanRepository.findById("TK002")).thenReturn(Optional.of(tk));
            when(quanTriVienRepository.existsById("TK002")).thenReturn(false);

            TaiKhoanDto dto = taiKhoanService.doiTrangThai("TK002");

            assertThat(tk.isTrangThaiHoatDong()).isTrue();
            assertThat(dto.trangThaiHoatDong()).isTrue();

            verify(nhatKyHoatDongService, times(1)).ghi(
                    eq(HanhDongAudit.UNLOCK_ACCOUNT),
                    eq(LoaiDoiTuongAudit.ACCOUNT),
                    eq("TK002"),
                    argThat(m -> Boolean.FALSE.equals(m.get("trangThai"))),
                    argThat(m -> Boolean.TRUE.equals(m.get("trangThai")))
            );
        }
    }

    @Nested
    @DisplayName("Product Architectural Special Rule")
    class ProductArchitecturalRuleTest {

        @Test
        @DisplayName("shouldVerifySanPhamServiceUsesUpdateProductAndNeverStockAdjustment")
        void shouldVerifySanPhamServiceUsesUpdateProductAndNeverStockAdjustment() throws IOException {
            Path servicePath = Path.of("src/main/java/nhom10/phonenest/service/SanPhamService.java");
            if (!Files.exists(servicePath)) {
                servicePath = Path.of("BE/phonenest/src/main/java/nhom10/phonenest/service/SanPhamService.java");
            }
            assertThat(servicePath).exists();

            String source = Files.readString(servicePath);

            // SanPhamService must audit UPDATE_PRODUCT, CREATE_PRODUCT, DELETE_PRODUCT
            assertThat(source).contains("HanhDongAudit.UPDATE_PRODUCT");
            assertThat(source).contains("HanhDongAudit.CREATE_PRODUCT");
            assertThat(source).contains("HanhDongAudit.DELETE_PRODUCT");

            // SanPhamService must NOT audit STOCK_ADJUSTMENT (which belongs exclusively to KhoService)
            assertThat(source)
                    .as("SanPhamService MUST NOT audit STOCK_ADJUSTMENT (Phase 1 architectural invariant)")
                    .doesNotContain("HanhDongAudit.STOCK_ADJUSTMENT");
        }
    }
}
