package nhom10.phonenest.service;

import nhom10.phonenest.dto.TaiKhoanDto;
import nhom10.phonenest.dto.TimKiemDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.LoaiSanPham;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.entity.TaiKhoan;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.LoaiSanPhamRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimKiemServiceTest {

    @Mock
    private SanPhamRepository sanPhamRepository;

    @Mock
    private LoaiSanPhamRepository loaiSanPhamRepository;

    @Mock
    private TaiKhoanRepository taiKhoanRepository;

    @Mock
    private DonHangRepository donHangRepository;

    @Mock
    private TaiKhoanService taiKhoanService;

    @Mock
    private DonHangMapper donHangMapper;

    @InjectMocks
    private TimKiemService timKiemService;

    @Test
    @DisplayName("Blank or null search query must immediately return empty DTO without database calls")
    void shouldReturnEmptyForBlankOrNullQuery() {
        TimKiemDto nullRes = timKiemService.tim(null);
        assertThat(nullRes.sanPhams()).isEmpty();
        assertThat(nullRes.loais()).isEmpty();
        assertThat(nullRes.taiKhoans()).isEmpty();
        assertThat(nullRes.donHangs()).isEmpty();

        TimKiemDto blankRes = timKiemService.tim("   ");
        assertThat(blankRes.sanPhams()).isEmpty();
        assertThat(blankRes.loais()).isEmpty();
        assertThat(blankRes.taiKhoans()).isEmpty();
        assertThat(blankRes.donHangs()).isEmpty();

        verifyNoInteractions(sanPhamRepository, loaiSanPhamRepository, taiKhoanRepository, donHangRepository);
    }

    @Test
    @DisplayName("Search query must escape LIKE wildcards and enforce limit 20 at DB level")
    void shouldEscapeLikeWildcardsAndLimit20() {
        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(sanPhamRepository.searchAdmin(any(), any())).thenReturn(Collections.emptyList());
        when(loaiSanPhamRepository.searchAdmin(any(), any())).thenReturn(Collections.emptyList());
        when(taiKhoanRepository.searchAdmin(any(), any())).thenReturn(Collections.emptyList());
        when(donHangRepository.searchAdmin(any(), any())).thenReturn(Collections.emptyList());

        timKiemService.tim("iPhone%_\\100");

        verify(sanPhamRepository).searchAdmin(patternCaptor.capture(), pageableCaptor.capture());
        verify(loaiSanPhamRepository).searchAdmin(any(), any());
        verify(taiKhoanRepository).searchAdmin(any(), any());
        verify(donHangRepository).searchAdmin(any(), any());

        // Escaped pattern check: % -> \%, _ -> \_, \ -> \\
        String capturedPattern = patternCaptor.getValue();
        assertThat(capturedPattern).isEqualTo("%iPhone\\%\\_\\\\100%");

        // Limit 20 check
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getPageNumber()).isEqualTo(0);
    }

    @Test
    @DisplayName("Search must correctly map returned entity results into DTOs")
    void shouldMapEntitiesToDtos() {
        LoaiSanPham l = new LoaiSanPham();
        l.setMaLoai("L10");
        l.setTenLoai("iPhone");

        SanPham sp = new SanPham();
        sp.setMaSanPham("SP001");
        sp.setTenMay("iPhone 15 Pro");
        sp.setLoai(l);
        sp.setHinhAnhs(Collections.emptyList());
        sp.setPhienBans(Collections.emptyList());

        TaiKhoan tk = new TaiKhoan();
        tk.setMaTk("TK002");
        tk.setEmail("an@phonenest.com");

        DonHang dh = new DonHang();
        dh.setMaDonHang("DH001");

        when(sanPhamRepository.searchAdmin(any(), any())).thenReturn(List.of(sp));
        when(loaiSanPhamRepository.searchAdmin(any(), any())).thenReturn(List.of(l));
        when(taiKhoanRepository.searchAdmin(any(), any())).thenReturn(List.of(tk));
        when(donHangRepository.searchAdmin(any(), any())).thenReturn(List.of(dh));

        when(taiKhoanService.toDto(tk)).thenReturn(new TaiKhoanDto(
                "TK002", "Nguyen Van An", "an@phonenest.com", "0900000002", Role.CUSTOMER, java.time.LocalDate.now(), true
        ));

        TimKiemDto res = timKiemService.tim("an");

        assertThat(res.sanPhams()).hasSize(1);
        assertThat(res.sanPhams().get(0).tenMay()).isEqualTo("iPhone 15 Pro");

        assertThat(res.loais()).hasSize(1);
        assertThat(res.loais().get(0).tenLoai()).isEqualTo("iPhone");

        assertThat(res.taiKhoans()).hasSize(1);
        assertThat(res.taiKhoans().get(0).email()).isEqualTo("an@phonenest.com");

        assertThat(res.donHangs()).hasSize(1);
        verify(donHangMapper).toDto(dh);
    }
}
