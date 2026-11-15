package nhom10.phonenest.config.schema;

import nhom10.phonenest.support.ItHttpBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2B.7D: doc metadata THAT cua clone (information_schema) — schema hop le phai cho danh sach loi rong,
 * va chinh ung dung (context Spring cua test) da khoi dong qua bo kiem tra nay.
 * Truong hop schema thieu / log khong nhat quan tren database that duoc kiem bang tien trinh that
 * (DB/tests/test_db_privileges_and_startup.sh, muc G): ung dung phai dung, khong mo cong.
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
class SchemaCompatibilityVerifierIntegrationTest extends ItHttpBase {

    @Autowired private DataSource dataSource;
    @Autowired private SchemaCompatibilityVerifier verifier;

    @Test
    @DisplayName("S1 Clone da chay M15+M16: metadata that -> khong co van de; bean verifier da chay khi khoi dong")
    void s1_cloneHopLe() throws Exception {
        try (Connection con = dataSource.getConnection()) {
            con.setReadOnly(true);
            String db = jdbc.queryForObject("SELECT DATABASE()", String.class);
            SchemaCompatibilityChecker.AnhChup a = SchemaCompatibilityVerifier.docAnhChup(con, db);
            List<String> loi = SchemaCompatibilityChecker.kiemTra(a);
            assertThat(loi).as("loi schema: " + loi).isEmpty();
            assertThat(a.cotPhienBan()).containsKeys("trangThaiPheDuyet", "dauVanTayPheDuyet", "lyDoKhoa");
        }
        assertThat(verifier).isNotNull();
    }
}
