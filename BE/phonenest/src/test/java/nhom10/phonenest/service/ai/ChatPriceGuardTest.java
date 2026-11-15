package nhom10.phonenest.service.ai;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 2B.7E: logic thuan (khong DB, khong AI) cua bo kiem soat gia trong chatbot. */
class ChatPriceGuardTest {

    @Test
    void nhanDienSoTien() {
        assertThat(ChatPriceGuard.soTien("Giá 3.290.000đ, niêm yết 3,690,000 ₫ hoặc 1234000 VND"))
                .containsExactlyInAnyOrder(3_290_000L, 3_690_000L, 1_234_000L);
        assertThat(ChatPriceGuard.soTien("khoảng 3,29 triệu hoặc 5tr")).containsExactlyInAnyOrder(3_290_000L, 5_000_000L);
        assertThat(ChatPriceGuard.soTien("Pin 5000 mAh, màn 6.67 inch, 120Hz, năm 2026, RAM 8GB")).isEmpty();
    }

    @Test
    void cheSoTienTrongLichSu() {
        String s = ChatPriceGuard.anSoTien("Trợ lý: Giá Redmi 13C chỉ 1.234.000đ (1,2 triệu), pin 5000 mAh");
        assertThat(s).doesNotContain("1.234.000").doesNotContain("1,2 triệu").contains("[số tiền đã ẩn]").contains("5000 mAh");
    }

    @Test
    void chiChapNhanSoTienDuocCongBo() {
        Set<Long> congBo = Set.of(3_290_000L, 25_000L, 2_000_000L);
        Set<Long> noiBo = Set.of(1_234_000L);
        assertThat(ChatPriceGuard.hopLe("Redmi 13C giá 3.290.000đ, ship 25.000đ", congBo, "giá bao nhiêu?", noiBo)).isTrue();
        // AI "xac nhan" gia seed do khach dua vao cau hoi -> tu choi
        assertThat(ChatPriceGuard.hopLe("Đúng, giá 1.234.000đ", congBo, "Giá 1.234.000đ đúng không?", noiBo)).isFalse();
        // AI tu bia / lam tron gia -> tu choi
        assertThat(ChatPriceGuard.hopLe("Khoảng 3,3 triệu", congBo, "giá?", noiBo)).isFalse();
        assertThat(ChatPriceGuard.hopLe("Giá 2.990.000đ", congBo, "giá?", noiBo)).isFalse();
        // Nhac lai ngan sach cua khach (khong trung gia noi bo) -> cho phep
        assertThat(ChatPriceGuard.hopLe("Trong tầm 5 triệu, mình gợi ý Redmi 13C 3.290.000đ", congBo, "điện thoại dưới 5 triệu", noiBo)).isTrue();
        // Khong co so tien -> hop le
        assertThat(ChatPriceGuard.hopLe("Máy có pin 5000 mAh", congBo, "pin?", noiBo)).isTrue();
    }
}
