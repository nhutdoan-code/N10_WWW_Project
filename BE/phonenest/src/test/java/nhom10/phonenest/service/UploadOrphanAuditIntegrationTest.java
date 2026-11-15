package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import nhom10.phonenest.support.ItHttpBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 2B.7E: kiem ke upload mo coi qua API Admin, tham chieu lay tu database clone THAT (moi cot van ban),
 * thu muc upload la thu muc tam rieng cua test (khong dong vao uploads/ cua repo). Chi dry-run.
 */
@EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
class UploadOrphanAuditIntegrationTest extends ItHttpBase {

    private static final Path GOC;

    static {
        try {
            GOC = Files.createTempDirectory("pn-it-uploads-");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry r) {
        r.add("app.upload-dir", GOC::toString);
    }

    private static Path tao(String ten, Duration tuoi) throws Exception {
        Path p = GOC.resolve(ten);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "it");
        Files.setLastModifiedTime(p, FileTime.from(Instant.now().minus(tuoi)));
        return p;
    }

    @Test
    @DisplayName("U1 File dang dung (san pham, don hang lich su) giu lai; file moi upload chua du thoi gian cho; chi file cu khong tham chieu la ung vien; khong xoa")
    void u1() throws Exception {
        List<Path> tatCa = new ArrayList<>();
        String anhSp = jdbc.queryForObject("SELECT SUBSTRING_INDEX(duongDan,'/uploads/',-1) FROM hinhanhsanpham WHERE duongDan LIKE '/uploads/%' LIMIT 1", String.class);
        tatCa.add(tao(anhSp, Duration.ofDays(60)));
        String anhDon = jdbc.queryForObject("SELECT SUBSTRING_INDEX(hinhAnh,'/uploads/',-1) FROM chitietdonhang WHERE hinhAnh LIKE '/uploads/%' LIMIT 1", String.class);
        tatCa.add(tao(anhDon, Duration.ofDays(300)));
        tatCa.add(tao("it-vua-upload.webp", Duration.ofMinutes(5)));
        tatCa.add(tao("it-mo-coi.webp", Duration.ofDays(5)));
        JsonNode r = json(goi(get("/api/admin/uploads/orphans"), admin, null));
        assertThat(r.get("chiKiemKe").asBoolean()).isTrue();
        assertThat(r.findValuesAsText("duongDan")).containsExactly("it-mo-coi.webp");
        assertThat(r.get("chuaDuThoiGianCho").asInt()).isEqualTo(1);
        assertThat(r.get("duocThamChieu").asInt()).isEqualTo(anhSp.equals(anhDon) ? 1 : 2);
        assertThat(r.get("thamChieuTheoCot").has("chitietdonhang.hinhAnh")).isTrue();
        for (Path p : tatCa) assertThat(Files.exists(p)).as(p.toString()).isTrue();
        assertThat(goi(get("/api/admin/uploads/orphans").param("gioCho", "0"), admin, null).getResponse().getStatus()).isEqualTo(400);
        assertThat(goi(get("/api/admin/uploads/orphans"), customer, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(goi(get("/api/admin/uploads/orphans"), null, null).getResponse().getStatus()).isEqualTo(401);
    }
}
