package nhom10.phonenest.service;

import nhom10.phonenest.dto.ChatDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.repository.KhachHangRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Them gio hang qua chat: nhan dien san pham, mau, RAM / bo nho tu cau noi tu nhien. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatCartServiceTest {

    private static final String MA_KH = "KH001";

    @Mock
    private SanPhamService sanPhamService;
    @Mock
    private GioHangService gioHangService;
    @Mock
    private KhachHangRepository khachHangRepository;

    @InjectMocks
    private ChatCartService chatCartService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                MA_KH, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        KhachHang kh = new KhachHang();
        kh.setMaKh(MA_KH);
        when(khachHangRepository.findById(MA_KH)).thenReturn(Optional.of(kh));
        when(sanPhamService.list(nullable(String.class), nullable(String.class), nullable(BigDecimal.class),
                nullable(BigDecimal.class), nullable(String.class), eq(false)))
                .thenReturn(List.of(galaxyS25(), galaxyS25Plus()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cauDayDuMauVaBoNhoKhongGhiChuBoNho_themDungPhienBan() {
        ChatDto.Response res = chatCartService.themVaoGio(
                "Thêm sản phẩm Samsung Galaxy S25 màu xanh da trời phiên bản 512GB vào giỏ hàng giúp tôi",
                MA_KH, List.of());

        assertThat(res.mode()).isEqualTo("CART_ADDED");
        verify(gioHangService).add(MA_KH, "PB-S25-512-XANH-DA-TROI", 1);
    }

    @Test
    void chiTenMay_hoiChonPhienBan_traLoiMauVaBoNho_themDungPhienBan() {
        ChatDto.Response hoi = chatCartService.themVaoGio(
                "thêm sản phẩm Samsung Galaxy S25 vào giỏ hàng", MA_KH, List.of());
        assertThat(hoi.mode()).isEqualTo("CART_SELECT_VARIANT");
        verifyNoInteractions(gioHangService);

        List<Message> history = new ArrayList<>();
        history.add(new UserMessage("thêm sản phẩm Samsung Galaxy S25 vào giỏ hàng"));
        history.add(new AssistantMessage(hoi.reply()));
        String traLoi = "màu xanh da trời 512GB";

        assertThat(chatCartService.laTiepTucChonPhienBan(traLoi, history)).isTrue();
        ChatDto.Response res = chatCartService.themVaoGio(traLoi, MA_KH, history);

        assertThat(res.mode()).isEqualTo("CART_ADDED");
        verify(gioHangService).add(MA_KH, "PB-S25-512-XANH-DA-TROI", 1);
    }

    @Test
    void khongLayMayCuTrongLichSuKhiTroLyKhongHoiChonPhienBan() {
        List<Message> history = List.of(
                new UserMessage("tư vấn Samsung Galaxy S25"),
                new AssistantMessage("Samsung Galaxy S25 là lựa chọn tốt trong tầm giá."));

        ChatDto.Response res = chatCartService.themVaoGio(
                "thêm Galaxy Z99 màu đen vào giỏ hàng", MA_KH, history);

        assertThat(res.mode()).isEqualTo("CART_NOT_FOUND");
        verifyNoInteractions(gioHangService);
    }

    @Test
    void mauChungChungVanHoiLaiKhiConNhieuPhienBan() {
        ChatDto.Response res = chatCartService.themVaoGio(
                "thêm Samsung Galaxy S25 màu xanh 512GB vào giỏ hàng", MA_KH, List.of());

        assertThat(res.mode()).isEqualTo("CART_SELECT_VARIANT");
        verifyNoInteractions(gioHangService);
    }

    @Test
    void phanBietRamVaBoNho() {
        assertThat(chatCartService.layDungLuong("12gb ram 512gb"))
                .isEqualTo(new ChatCartService.DungLuong(12, 512));
        assertThat(chatCartService.layDungLuong("ram 8gb bo nho 256 gb"))
                .isEqualTo(new ChatCartService.DungLuong(8, 256));
        assertThat(chatCartService.layDungLuong("phien ban 1tb"))
                .isEqualTo(new ChatCartService.DungLuong(null, 1024));
        assertThat(chatCartService.layDungLuong("mau xanh"))
                .isEqualTo(new ChatCartService.DungLuong(null, null));
    }

    private static SanPhamDto galaxyS25() {
        List<SanPhamDto.PhienBan> pbs = new ArrayList<>();
        for (int boNho : new int[]{256, 512}) {
            for (String mau : new String[]{"Xám bạc", "Xanh da trời", "Xanh lá", "Xanh navy"}) {
                String ma = "PB-S25-" + boNho + "-" + mau.toUpperCase()
                        .replace("Á", "A").replace("Ạ", "A").replace("Ờ", "O").replace(' ', '-');
                pbs.add(phienBan(ma, boNho, mau));
            }
        }
        return sanPham("SP500376", "Samsung Galaxy S25", pbs);
    }

    private static SanPhamDto galaxyS25Plus() {
        return sanPham("SP500391", "Samsung Galaxy S25+",
                List.of(phienBan("PB-S25P-512-XANH-DA-TROI", 512, "Xanh da trời")));
    }

    private static SanPhamDto.PhienBan phienBan(String ma, int boNho, String mau) {
        return new SanPhamDto.PhienBan(ma, ma, 12, boNho, mau, BigDecimal.valueOf(20_090_000),
                BigDecimal.valueOf(26_010_000), 10, TrangThaiSanPham.DANG_KINH_DOANH,
                TrangThaiPheDuyet.APPROVED, true);
    }

    private static SanPhamDto sanPham(String ma, String ten, List<SanPhamDto.PhienBan> pbs) {
        return new SanPhamDto(ma, "L21", "Galaxy S", "TH02", "Samsung", ten, BigDecimal.valueOf(17_490_000), "",
                null, 100, TrangThaiSanPham.DANG_KINH_DOANH, BigDecimal.ZERO, null, List.of(), pbs);
    }
}
