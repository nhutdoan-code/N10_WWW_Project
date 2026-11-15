package nhom10.phonenest.config.schema;

import nhom10.phonenest.config.schema.SchemaCompatibilityChecker.AnhChup;
import nhom10.phonenest.config.schema.SchemaCompatibilityChecker.Cot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test (KHONG can database) cho logic kiem tra schema khi khoi dong — 2B.7D. */
class SchemaCompatibilityCheckerTest {

    /** Dung y het metadata doc tu clone da chay M15 v2 + M16 (claude_2b7c_it_160041). */
    private static Map<String, Cot> cotHopLe() {
        Map<String, Cot> c = new LinkedHashMap<>();
        c.put("giaBan", new Cot("decimal(15,2)", false));
        c.put("giaNiemYet", new Cot("decimal(15,2)", true));
        c.put("soLuongTon", new Cot("int(11)", false));
        c.put("trangThaiPheDuyet", new Cot("enum('approved','pending_review','blocked')".toUpperCase()
                .replace("ENUM", "enum"), false));
        c.put("nguoiPheDuyet", new Cot("varchar(50)", true));
        c.put("ngayPheDuyet", new Cot("datetime", true));
        c.put("lyDoPheDuyet", new Cot("varchar(255)", true));
        c.put("giaPheDuyet", new Cot("decimal(15,2)", true));
        c.put("giaNiemYetPheDuyet", new Cot("decimal(15,2)", true));
        c.put("nguonXacMinh", new Cot("varchar(500)", true));
        c.put("dauVanTayPheDuyet", new Cot("char(64)", true));
        c.put("lyDoKhoa", new Cot("enum('GIA_SEED_CHUA_XAC_MINH','SAI_CAU_HINH','QUYET_DINH_QUAN_TRI','NGUNG_KINH_DOANH','KHAC')", true));
        c.put("giaBanKhiKhoa", new Cot("decimal(15,2)", true));
        return c;
    }

    private static Set<String> bang() {
        return new HashSet<>(SchemaCompatibilityChecker.BANG_BAT_BUOC);
    }

    private static Map<String, String> log(String m15, String m16) {
        Map<String, String> m = new HashMap<>();
        if (m15 != null) m.put("15", m15);
        if (m16 != null) m.put("16", m16);
        return m;
    }

    private static List<String> kt(Set<String> b, Map<String, Cot> c, Map<String, String> l, long tt, long ldk) {
        return SchemaCompatibilityChecker.kiemTra(new AnhChup(b, c, l, tt, ldk));
    }

    @Test
    void schemaHopLeKhongCoLoi() {
        assertThat(kt(bang(), cotHopLe(), log("SKIPPED_EXISTING_DATA", "COMPLETED"), 0, 0)).isEmpty();
        assertThat(kt(bang(), cotHopLe(), log("COMPLETED", "COMPLETED"), 0, 0)).isEmpty();
    }

    @Test
    void schemaCuChuaCoM16_bao_ten_tung_cot() {
        Map<String, Cot> c = cotHopLe();
        for (String k : List.of("giaPheDuyet", "giaNiemYetPheDuyet", "nguonXacMinh", "dauVanTayPheDuyet", "lyDoKhoa", "giaBanKhiKhoa")) {
            c.remove(k);
        }
        List<String> loi = kt(bang(), c, log("COMPLETED", null), 0, 0);
        assertThat(loi).anyMatch(s -> s.contains("dauVanTayPheDuyet"))
                .anyMatch(s -> s.contains("Thiếu cột phienbansanpham.lyDoKhoa"))
                .anyMatch(s -> s.contains("Migration 16"));
    }

    @Test
    void schemaChuaCoM15VaThieuBangLog() {
        Map<String, Cot> c = new LinkedHashMap<>();
        c.put("giaBan", new Cot("decimal(15,2)", false));
        c.put("soLuongTon", new Cot("int(11)", false));
        Set<String> b = bang();
        b.remove("pn_migration_log");
        List<String> loi = kt(b, c, Map.of(), 0, 0);
        assertThat(loi).anyMatch(s -> s.contains("Thiếu bảng 'pn_migration_log'"))
                .anyMatch(s -> s.contains("trangThaiPheDuyet"));
    }

    @Test
    void sai_kieu_nullable_va_thieu_gia_tri_enum() {
        Map<String, Cot> c = cotHopLe();
        c.put("giaPheDuyet", new Cot("decimal(10,0)", true));
        c.put("trangThaiPheDuyet", new Cot("enum('APPROVED','PENDING_REVIEW')", true));
        c.put("nguonXacMinh", new Cot("varchar(100)", true));
        c.put("dauVanTayPheDuyet", new Cot("varchar(40)", true));
        List<String> loi = kt(bang(), c, log("COMPLETED", "COMPLETED"), 0, 0);
        assertThat(loi).anyMatch(s -> s.contains("giaPheDuyet phải là decimal(15,2)"))
                .anyMatch(s -> s.contains("thiếu giá trị 'BLOCKED'"))
                .anyMatch(s -> s.contains("trangThaiPheDuyet phải NOT NULL"))
                .anyMatch(s -> s.contains("nguonXacMinh phải là varchar(>=500)"))
                .anyMatch(s -> s.contains("dauVanTayPheDuyet phải là char(64)"));
    }

    @Test
    void migrationLogKhongNhatQuanVoiSchema() {
        // Schema day du nhung log mat / dung o trang thai do dang -> khong khoi dong
        assertThat(kt(bang(), cotHopLe(), Map.of(), 0, 0))
                .anyMatch(s -> s.contains("không có bản ghi Migration 15"))
                .anyMatch(s -> s.contains("không có bản ghi Migration 16"));
        assertThat(kt(bang(), cotHopLe(), log("COMPLETED", "DDL_DONE"), 0, 0))
                .anyMatch(s -> s.contains("Migration 16 là DDL_DONE"));
        assertThat(kt(bang(), cotHopLe(), log("ROLLED_BACK", "COMPLETED"), 0, 0))
                .anyMatch(s -> s.contains("Migration 15 là ROLLED_BACK"));
        assertThat(kt(bang(), cotHopLe(), log("FAILED", "COMPLETED"), 0, 0)).isNotEmpty();
    }

    @Test
    void logCompletedNhungSchemaThieuCot_khongDuaVaoLog() {
        Map<String, Cot> c = cotHopLe();
        c.remove("nguonXacMinh");
        assertThat(kt(bang(), c, log("COMPLETED", "COMPLETED"), 0, 0))
                .containsExactly("Thiếu cột phienbansanpham.nguonXacMinh");
    }

    @Test
    void duLieuKhongHopLe() {
        assertThat(kt(bang(), cotHopLe(), log("COMPLETED", "COMPLETED"), 3, 2))
                .anyMatch(s -> s.startsWith("3 dòng có trangThaiPheDuyet"))
                .anyMatch(s -> s.startsWith("2 dòng có lyDoKhoa"));
    }

    @Test
    void khongDocDuocCot_vd_thieuQuyenSelect() {
        assertThat(kt(bang(), Map.of(), log("COMPLETED", "COMPLETED"), 0, 0))
                .anyMatch(s -> s.contains("Không đọc được cột"));
    }
}
