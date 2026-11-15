package nhom10.phonenest.config.schema;

import nhom10.phonenest.enums.LyDoKhoaBan;
import nhom10.phonenest.enums.TrangThaiPheDuyet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logic thuan (khong phu thuoc Spring / JDBC) kiem tra schema co tuong thich voi phien ban code hien tai
 * (2B.7D). Dau vao la anh chup metadata doc tu information_schema; dau ra la danh sach van de, rong = hop le.
 *
 * <p>Nguyen tac: SCHEMA THUC TE la can cu chinh; migration log chi duoc dung de doi chieu tinh nhat quan,
 * khong bao gio thay the viec kiem tra cot.</p>
 */
public final class SchemaCompatibilityChecker {

    /** Thong tin mot cot: kieu day du (vd. "decimal(15,2)", "enum('A','B')") va nullable. */
    public record Cot(String kieu, boolean nullable) {
    }

    /** Ban ghi cuoi cung cua tung migration trong pn_migration_log (null neu khong co dong nao). */
    public record AnhChup(
            Set<String> bang,
            Map<String, Cot> cotPhienBan,
            Map<String, String> trangThaiMigrationCuoi,
            long soDongTrangThaiPheDuyetKhongHopLe,
            long soDongLyDoKhoaKhongHopLe) {
    }

    static final List<String> BANG_BAT_BUOC = List.of(
            "sanpham", "phienbansanpham", "donhang", "chitietdonhang", "nhatkyhoatdong", "pn_migration_log");

    private static final Set<String> M15_HOP_LE = Set.of("COMPLETED", "SKIPPED_EXISTING_DATA");
    private static final Set<String> M16_HOP_LE = Set.of("COMPLETED");

    private SchemaCompatibilityChecker() {
    }

    public static List<String> kiemTra(AnhChup a) {
        List<String> loi = new ArrayList<>();

        for (String b : BANG_BAT_BUOC) {
            if (!a.bang().contains(b)) {
                loi.add("Thiếu bảng '" + b + "'");
            }
        }
        Map<String, Cot> c = a.cotPhienBan();
        if (c.isEmpty()) {
            loi.add("Không đọc được cột của bảng phienbansanpham (thiếu bảng hoặc tài khoản không có quyền SELECT)");
            return loi;
        }

        // ---- cot nghiep vu co ban
        canKieu(loi, c, "giaBan", "decimal(15,2)", false);
        canKieuBatDau(loi, c, "soLuongTon", "int", false);

        // ---- Migration 15: trang thai phe duyet
        canEnum(loi, c, "trangThaiPheDuyet", ten(TrangThaiPheDuyet.values()), false);
        canVarchar(loi, c, "nguoiPheDuyet", 50);
        canKieu(loi, c, "ngayPheDuyet", "datetime", true);
        canVarchar(loi, c, "lyDoPheDuyet", 255);

        // ---- Migration 16: du lieu xac minh + dau van tay
        canKieu(loi, c, "giaPheDuyet", "decimal(15,2)", true);
        canKieu(loi, c, "giaNiemYetPheDuyet", "decimal(15,2)", true);
        canVarchar(loi, c, "nguonXacMinh", 500);
        Cot dvt = c.get("dauVanTayPheDuyet");
        if (dvt == null) {
            loi.add("Thiếu cột phienbansanpham.dauVanTayPheDuyet (Migration 16)");
        } else if (!dvt.kieu().equals("char(64)") && !dvt.kieu().equals("varchar(64)")) {
            loi.add("Cột dauVanTayPheDuyet phải là char(64)/varchar(64), hiện là " + dvt.kieu());
        }
        canEnum(loi, c, "lyDoKhoa", ten(LyDoKhoaBan.values()), true);
        canKieu(loi, c, "giaBanKhiKhoa", "decimal(15,2)", true);

        // ---- du lieu khong hop le (vd. chuoi rong do insert sai o che do khong strict)
        if (a.soDongTrangThaiPheDuyetKhongHopLe() > 0) {
            loi.add(a.soDongTrangThaiPheDuyetKhongHopLe() + " dòng có trangThaiPheDuyet NULL/rỗng/không hợp lệ");
        }
        if (a.soDongLyDoKhoaKhongHopLe() > 0) {
            loi.add(a.soDongLyDoKhoaKhongHopLe() + " dòng có lyDoKhoa rỗng/không hợp lệ");
        }

        // ---- doi chieu migration log voi schema thuc te
        if (a.bang().contains("pn_migration_log")) {
            String m15 = a.trangThaiMigrationCuoi().get("15");
            String m16 = a.trangThaiMigrationCuoi().get("16");
            if (m15 == null) {
                loi.add("pn_migration_log không có bản ghi Migration 15 dù schema đã có cột: log không nhất quán "
                        + "(chạy lại 15_add_sales_approval_status.sql để ghi nhận trạng thái; script không ghi đè dữ liệu)");
            } else if (!M15_HOP_LE.contains(m15)) {
                loi.add("Trạng thái cuối của Migration 15 là " + m15 + " (cần COMPLETED hoặc SKIPPED_EXISTING_DATA)");
            }
            if (m16 == null) {
                loi.add("pn_migration_log không có bản ghi Migration 16: log không nhất quán với schema");
            } else if (!M16_HOP_LE.contains(m16)) {
                loi.add("Trạng thái cuối của Migration 16 là " + m16 + " (cần COMPLETED)");
            }
        }
        return loi;
    }

    // ------------------------------------------------------------------ tien ich

    private static void canKieu(List<String> loi, Map<String, Cot> c, String cot, String kieu, boolean nullable) {
        Cot x = c.get(cot);
        if (x == null) {
            loi.add("Thiếu cột phienbansanpham." + cot);
            return;
        }
        if (!x.kieu().equals(kieu)) {
            loi.add("Cột " + cot + " phải là " + kieu + ", hiện là " + x.kieu());
        }
        if (x.nullable() != nullable) {
            loi.add("Cột " + cot + (nullable ? " phải cho phép NULL" : " phải NOT NULL"));
        }
    }

    private static void canKieuBatDau(List<String> loi, Map<String, Cot> c, String cot, String tienTo, boolean nullable) {
        Cot x = c.get(cot);
        if (x == null) {
            loi.add("Thiếu cột phienbansanpham." + cot);
        } else if (!x.kieu().startsWith(tienTo) || x.nullable() != nullable) {
            loi.add("Cột " + cot + " có kiểu/nullable không đúng: " + x.kieu() + (x.nullable() ? " NULL" : " NOT NULL"));
        }
    }

    private static final Pattern VARCHAR = Pattern.compile("varchar\\((\\d+)\\)");

    private static void canVarchar(List<String> loi, Map<String, Cot> c, String cot, int toiThieu) {
        Cot x = c.get(cot);
        if (x == null) {
            loi.add("Thiếu cột phienbansanpham." + cot);
            return;
        }
        Matcher m = VARCHAR.matcher(x.kieu());
        if (!m.matches() || Integer.parseInt(m.group(1)) < toiThieu) {
            loi.add("Cột " + cot + " phải là varchar(>=" + toiThieu + "), hiện là " + x.kieu());
        }
        if (!x.nullable()) {
            loi.add("Cột " + cot + " phải cho phép NULL");
        }
    }

    private static void canEnum(List<String> loi, Map<String, Cot> c, String cot, List<String> giaTri, boolean nullable) {
        Cot x = c.get(cot);
        if (x == null) {
            loi.add("Thiếu cột phienbansanpham." + cot);
            return;
        }
        if (!x.kieu().startsWith("enum(")) {
            loi.add("Cột " + cot + " phải là ENUM, hiện là " + x.kieu());
            return;
        }
        List<String> coTrongDb = Arrays.stream(x.kieu().substring(5, x.kieu().length() - 1).split(","))
                .map(v -> v.trim().replace("'", "")).toList();
        for (String v : giaTri) {
            if (!coTrongDb.contains(v)) {
                loi.add("ENUM " + cot + " thiếu giá trị '" + v + "' mà code sử dụng");
            }
        }
        if (x.nullable() != nullable) {
            loi.add("Cột " + cot + (nullable ? " phải cho phép NULL" : " phải NOT NULL"));
        }
    }

    private static List<String> ten(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    static String chuan(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
