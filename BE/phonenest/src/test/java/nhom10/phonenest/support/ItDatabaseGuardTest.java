package nhom10.phonenest.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItDatabaseGuardTest {

    @Test
    void tuChoiDatabaseChinhVaDatabaseTestCu() {
        for (String url : new String[]{
                "jdbc:mariadb://127.0.0.1:3307/bandienthoai",
                "jdbc:mariadb://127.0.0.1:3307/BanDienThoai?useSSL=false",
                "jdbc:mariadb://localhost:3306/bandienthoai_test",
                "jdbc:mariadb://127.0.0.1:3307/bandienthoai_clean_acceptance",
                "jdbc:mariadb://127.0.0.1:3307/bandienthoai_test_approval_14",
                "jdbc:mariadb://10.0.0.5:3307/claude_2b7b_it_1",
                "jdbc:mariadb://127.0.0.1:3307/phonenest_test_full",
                ""}) {
            assertThatThrownBy(() -> ItDatabaseGuard.kiemTra(url))
                    .as(url).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> ItDatabaseGuard.kiemTra(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chapNhanCloneDungMotLan() {
        assertThat(ItDatabaseGuard.kiemTra("jdbc:mariadb://127.0.0.1:3307/claude_2b7b_it_120000")).isEqualTo("claude_2b7b_it_120000");
        assertThat(ItDatabaseGuard.kiemTra("jdbc:mariadb://localhost:3307/phonenest_it_ci?useUnicode=true")).isEqualTo("phonenest_it_ci");
    }
}
